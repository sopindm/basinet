(ns basinet
  (:require [basinet.scala :as scala])
  (:refer-clojure :exclude [pop read drop get set])
  (:import [java.nio ByteBuffer]))

;;
;; Basic channel functions
;;

(defn open? [^basinet.Channel channel] (.isOpen channel))
(defn close [^basinet.Channel channel] (.close channel))

;;
;; Basic stream functions
;;

(defn push [^basinet.Sink sink item] (.push sink item))
(defn push-in [^basinet.Sink sink item milliseconds] (.pushIn sink item milliseconds))
(defn try-push [^basinet.Sink sink item] (.tryPush sink item))

(defn pop [^basinet.Source source] (.pop source))
(defn pop-in [^basinet.Source source milliseconds]
  (scala/option->nullable (.popIn source milliseconds)))
(defn try-pop [^basinet.Source source] (scala/option->nullable (.tryPop source)))

(defn read [^basinet.SourceChannel source ^basinet.Buffer buffer] (.read source buffer)) 
(defn write [^basinet.SinkChannel sink ^basinet.Buffer buffer] (.write sink buffer))

;;
;; Sockets
;; 

(defn ^basinet.Source source [^basinet.Pipe pipe] (.source pipe))
(defn ^basinet.Sink sink [^basinet.Pipe pipe] (.sink pipe))

(defn pipe [] (.apply basinet.NIOPipe$/MODULE$))

;;
;; Buffers
;;
  
(defn drop [n buffered] (.drop buffered n))
(defn expand [n buffered] (.expand buffered n))

(defn size [^basinet.Buffered buffered] (.size buffered))

(defn byte-buffer [size-or-coll]
  (let [buffer (if (integer? size-or-coll)
                 (ByteBuffer/allocate size-or-coll)
                 (ByteBuffer/wrap size-or-coll))]
    (when (integer? size-or-coll) (.limit buffer 0))
    (basinet.ByteBuffer. buffer)))

(defn get [buffer index] (.get buffer index))
(defn set [buffer index value] (.set buffer index value))
