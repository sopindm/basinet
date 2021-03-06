(ns basinet
  (:require [basinet.scala :as scala]
            [evil-ant :as e])
  (:refer-clojure :exclude [pop read drop get set])
  (:import [java.nio ByteBuffer CharBuffer]))

;;
;; Basic channel functions
;;

(defn open? [^basinet.Channel channel] (.isOpen channel))
(defn close [^basinet.Channel channel] (.close channel))

(defn underflow? [^basinet.Result result] (.isUnderflow result))
(defn overflow? [^basinet.Result result] (.isOverflow result))

;;
;; Basic stream functions
;;

(defn pushable [^basinet.Sink sink] (.pushable sink))

(defn push [^basinet.Sink sink item] (.push sink item))
(defn push-in [^basinet.Sink sink item milliseconds] (.pushIn sink item milliseconds))
(defn try-push [^basinet.Sink sink item] (.tryPush sink item))

(defn poppable [^basinet.Source source] (.poppable source))

(defn pop [^basinet.Source source] (.pop source))
(defn pop-in [^basinet.Source source milliseconds]
  (scala/option->nullable (.popIn source milliseconds)))
(defn try-pop [^basinet.Source source] (scala/option->nullable (.tryPop source)))

;;
;; Sockets
;; 

(defn ^basinet.Source source [^basinet.Pipe pipe] (.source pipe))
(defn ^basinet.Sink sink [^basinet.Pipe pipe] (.sink pipe))

(defn pipe-of [source sink] (.apply (scala/object basinet.PipeOf) source sink))
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

(defn ^java.nio.charset.Charset charset [name] (java.nio.charset.Charset/forName name))
(defn unicode-charset [] (charset "UTF-8"))
(defn bytes->chars [charset] (basinet.nio.CharsetDecoder. charset))
(defn chars->bytes [charset] (basinet.nio.CharsetEncoder. charset))

(defn line-writer [] (basinet.LineWriter.))
(defn line-reader ([newline] (basinet.LineReader. newline))
  ([] (line-reader "\n")))

(defmulti ^basinet.Wire converter (fn [from to] [(type (source from))
                                                 (type (sink to))]))

(defmethod converter [basinet.nio.ByteSource
                      basinet.nio.byte.BufferSink]
  [_ _] (byte-channel-reader))

(defmethod converter [basinet.nio.byte.BufferSource basinet.nio.ByteSink]
  [_ _] (byte-channel-writer))

(defmethod converter [Object basinet.any.BufferSink] [_ _] (object-buffer-writer))
(defmethod converter [basinet.any.BufferSource Object] [_ _] (object-buffer-reader))
(prefer-method converter [basinet.any.BufferSource Object] [Object basinet.any.BufferSink])

(defmethod converter [basinet.nio.byte.BufferSource basinet.nio.char.BufferSink] [_ _]
  (bytes->chars (unicode-charset)))

(defmethod converter [basinet.nio.char.BufferSource basinet.nio.byte.BufferSink] [_ _]
  (chars->bytes (unicode-charset)))

(defn convert ([^basinet.Source from ^basinet.Sink to ^basinet.Wire wire] (.convert wire from to))
  ([^basinet.Source from ^basinet.Sink to] (.convert (converter from to) from to)))

;;
;; Chains
;;

(def ^:dynamic *signal-set* nil)

(defn emit! [] (e/emit! *signal-set*))
(defn emit-in! [millis] (e/emit-in! *signal-set* millis))
(defn emit-now! [] (e/emit-now! *signal-set*))

(defn chain1
  ([from to wire] (let [source? (instance? basinet.Pipe to)
                        sink? (instance? basinet.Pipe from)]
                    (cond (and sink? source?) (basinet.ChainPipe. from to wire *signal-set*)
                          sink? (basinet.ChainSink. from to wire *signal-set*)
                          source? (basinet.ChainSource. from to wire *signal-set*)
                          :else (basinet.Chain. from to wire *signal-set*))))
  ([from to] (chain1 from to (converter from to))))

(defn chain [& channels]
  (if (= (count channels) 1) (first channels)
      (let [from (first channels)
            [wire to rest] (if (= (second channels) :by)
                             [(nth channels 2) (nth channels 3) (clojure.core/drop 4 channels)]
                             [nil (second channels) (clojure.core/drop 2 channels)])
            -chain (if wire (chain1 from to wire) (chain1 from to))]
        (apply chain (cons -chain rest)))))

(defn line-source [byte-source & {:keys [lines charset] :or {lines 1 charset "UTF-8"}}]
  (let [charset (basinet/charset charset)
        chars-in-byte (.maxCharsPerByte (.newDecoder charset))
        bytes-in-char (.maxBytesPerChar (.newEncoder charset))]
    (chain (source byte-source) (byte-buffer 1024 bytes-in-char) :by (bytes->chars charset)
           (char-buffer 1024 chars-in-byte) :by (line-writer) (object-buffer lines))))

(defn line-sink [byte-sink & {:keys [lines charset] :or {lines 1 charset "UTF-8"}}]
  (let [charset (basinet/charset charset)
        chars-in-byte (.maxCharsPerByte (.newDecoder charset))
        bytes-in-char (.maxBytesPerChar (.newEncoder charset))]
    (chain (object-buffer lines) :by (line-reader) (char-buffer 1024 chars-in-byte)
           :by (chars->bytes charset) (byte-buffer 1024 bytes-in-char) byte-sink)))

(defn line-socket [byte-socket & args]
  (pipe-of (apply line-source (source byte-socket) args) (apply line-sink (sink byte-socket) args)))

;;
;; Events
;;

(defn on-close [channel] (.onClose channel))

(defn on-poppable [channel] (.onPoppable channel))
(defn on-pushable [channel] (.onPushable channel))

(defn register [channel event-set] (.register channel event-set))

(defn signal-set ([] (e/signal-set))
  ([& channels] (reduce #(do (register %2 %1) %1) (signal-set) channels)))


