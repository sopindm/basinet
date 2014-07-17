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

(defn pipe [] (scala/apply (scala/object basinet.nio.Pipe)))

;;
;; Buffers
;;
  
(defn drop [n ^basinet.Buffer buffered] (.drop buffered ^int n) buffered)
(defn expand [n ^basinet.Buffer buffered] (.expand buffered ^int n) buffered)

(defn size [^basinet.Buffer buffered] (.size buffered))

(defmacro -buffer [class size-or-coll compaction-threshold]
  `(let [buffer# (if (integer? ~size-or-coll)
                   (~(symbol (name class) "allocate") ~size-or-coll)
                   (~(symbol (name class) "wrap") ~size-or-coll))]
     (if (integer? ~size-or-coll)
       (.limit buffer# 0)
       (.limit buffer# (- (.capacity buffer#) ~compaction-threshold)))
     buffer#))

(defn object-buffer
  ([size-or-coll] (object-buffer size-or-coll 0))
  ([size-or-coll compaction-threshold]
     (basinet.any.BufferPipe. (object-array size-or-coll)
                              (if (integer? size-or-coll) 0 (count size-or-coll))
                              compaction-threshold)))

(defn byte-buffer 
  ([size-or-coll] (byte-buffer size-or-coll 0))
  ([size-or-coll compaction-threshold]
     (basinet.nio.byte.BufferPipe. (-buffer ByteBuffer size-or-coll compaction-threshold)
                                   compaction-threshold)))

(defn char-buffer
  ([size-or-coll] (char-buffer size-or-coll 0))
  ([size-or-coll compaction-threshold]
     (basinet.nio.char.BufferPipe. (-buffer CharBuffer size-or-coll compaction-threshold)
                                   compaction-threshold)))

(defn get [^basinet.BufferSource buffer index]
  (.get buffer ^int index))

(defn set [^basinet.BufferSink buffer index value]
  (.set buffer ^int index value))

;;
;; Wires
;;

(defn byte-channel-reader [] (scala/object basinet.nio.ByteChannelReader))
(defn byte-channel-writer [] (scala/object basinet.nio.ByteChannelWriter))

(def -object-buffer-writer (basinet.any.BufferWriter.))
(defn object-buffer-writer [] -object-buffer-writer)
(def -object-buffer-reader (basinet.any.BufferReader.))
(defn object-buffer-reader [] -object-buffer-reader)

(defn bytes->chars [charset] (basinet.nio.CharsetDecoder. charset))
(defn chars->bytes [charset] (basinet.nio.CharsetEncoder. charset))

(defmulti converter (fn [from to] [(type (source from))
                                   (type (sink to))]))

(defmethod converter [basinet.nio.ByteSource
                      basinet.nio.byte.BufferSink]
  [_ _] (byte-channel-reader))

(defmethod converter [basinet.nio.byte.BufferSource basinet.nio.ByteSink]
  [_ _] (byte-channel-writer))

(defmethod converter [Object basinet.any.BufferSink] [_ _] (object-buffer-writer))
(defmethod converter [basinet.any.BufferSource Object] [_ _] (object-buffer-reader))

(defn convert ([from to wire] (.convert wire from to))
  ([from to] (.convert (converter from to) from to)))

(defn chain
  ([from to converter] (.apply (scala/object basinet.Chain) from to converter))
  ([from to] (chain from to (converter from to))))
