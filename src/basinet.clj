(ns basinet
  (:require [basinet.scala :as scala])
  (:refer-clojure :exclude [pop read drop get set])
  (:import [java.nio ByteBuffer CharBuffer]))

;;
;; Basic channel functions
;;

(defn open? [^basinet.Channel channel] (.isOpen channel))
(defn close [^basinet.Channel channel] (.close channel))

;;
;; Basic stream functions
;;

(defn update [^basinet.Channel channel] (.update channel))

(defn pushable [sink] (.pushable sink))

(defn push [^basinet.Sink sink item] (.push sink item))
(defn push-in [^basinet.Sink sink item milliseconds] (.pushIn sink item milliseconds))
(defn try-push [^basinet.Sink sink item] (.tryPush sink item))

(defn poppable [source] (.poppable source))

(defn pop [^basinet.Source source] (.pop source))
(defn pop-in [^basinet.Source source milliseconds]
  (scala/option->nullable (.popIn source milliseconds)))
(defn try-pop [^basinet.Source source] (scala/option->nullable (.tryPop source)))

;;
;; Sockets
;; 

(defn ^basinet.Source source [^basinet.Pipe pipe] (.source pipe))
(defn ^basinet.Sink sink [^basinet.Pipe pipe] (.sink pipe))

(defn pipe [] (.apply basinet.nio.Pipe$/MODULE$))

;;
;; Buffers
;;
  
(defn drop [n ^basinet.Buffer buffered] (.drop buffered ^int n))
(defn expand [n ^basinet.Buffer buffered] (.expand buffered ^int n))

(defn size [^basinet.Buffer buffered] (.size buffered))

(defmacro -buffer [class size-or-coll]
  `(let [buffer# (if (integer? ~size-or-coll)
                   (~(symbol (name class) "allocate") ~size-or-coll)
                   (~(symbol (name class) "wrap") ~size-or-coll))]
     (when (integer? ~size-or-coll) (.limit buffer# 0))
     buffer#))

(defn byte-buffer [size-or-coll] (basinet.nio.byte.BufferPipe. (-buffer ByteBuffer size-or-coll)))
(defn char-buffer [size-or-coll] (basinet.nio.char.BufferPipe. (-buffer CharBuffer size-or-coll)))

(defn get [^basinet.BufferSource buffer index]
  (.get buffer ^int index))

(defn set [^basinet.BufferSink buffer index value]
  (.set buffer ^int index value))

;;
;; Wires
;;

(defn byte-channel-reader [] (basinet.nio.ByteChannelReader$/MODULE$))
(defn byte-channel-writer [] (basinet.nio.ByteChannelWriter$/MODULE$))

(defn bytes->chars [charset] (basinet.nio.CharsetDecoder. charset))
(defn chars->bytes [charset] (basinet.nio.CharsetEncoder. charset))

(defmulti converter (fn [from to] [(type (source from))
                                   (type (sink to))]))

(defmethod converter [basinet.nio.ByteSource
                      basinet.nio.byte.BufferSink]
  [_ _] (byte-channel-reader))

(defmethod converter [basinet.nio.byte.BufferSource basinet.nio.ByteSink]
  [_ _] (byte-channel-writer))

(defn convert ([from to wire] (.convert wire from to))
  ([from to] (.convert (converter from to) from to)))

(defn chain
  ([from to converter] (.apply basinet.Chain$/MODULE$ from to converter))
  ([from to] (chain from to (converter from to))))
