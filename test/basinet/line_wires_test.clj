(ns basinet.line-wires-test
  (:require [khazad-dum :refer :all]
            [basinet :as b]
            [basinet.buffer-test :refer [?convert=]]
            [evil-ant :as e])
  (:import [java.nio.channels ClosedChannelException]
           [basinet Result]))

(deftest line-writer-readers-chars-and-makes-strings
  (with-open [cb (b/char-buffer "hello\nhi\nagain!!!\n")
              ob (b/object-buffer 1)]
    (let [lw (b/line-writer)]
      (?convert= [cb ob lw] Result/OVERFLOW)
      (?= (b/try-pop ob) "hello")
      (?convert= [cb ob lw] Result/OVERFLOW)
      (?= (b/try-pop ob) "hi")
      (?convert= [cb ob lw] Result/OVERFLOW)
      (?= (b/try-pop ob) "again!!!"))))

(deftest line-writer-underflows-when-input-has-no-full-line
  (with-open [cb (b/char-buffer "hello, world!!!")
              ob (b/object-buffer 1)]
    (?convert= [cb ob (b/line-writer)] Result/UNDERFLOW)
    (?false (b/poppable ob))))

(deftest line-writer-with-different-newline-sequences
  (with-open [cb (b/char-buffer (char-array (map char (range 10 14))))
              ob (b/object-buffer 10)]
    (let [lw (b/line-writer)]
      (?convert= [cb ob lw] Result/UNDERFLOW)
      (dotimes [i 4] (?= (b/try-pop ob) "")))))

(deftest line-reader-reads-string-and-writes-characters
  (let [ob (b/object-buffer ["hi" "hello"])
        cb (b/char-buffer 10)
        lr (b/line-reader)]
    (?convert= [ob cb lr] Result/UNDERFLOW)
    (dotimes [i 3] (?= (b/try-pop cb) (nth "hi\n" i)))
    (dotimes [i 6] (?= (b/try-pop cb) (nth "hello\n" i)))))

(defmacro ?chars= [buffer string]
  `(dotimes [i# (count ~string)] (?= (b/try-pop ~buffer) (nth ~string i#))))

(deftest line-reader-overflows-if-has-no-place-for-all-characters
  (let [ob (b/object-buffer ["hello" "hello"])
        cb (b/char-buffer 4)
        lr (b/line-reader)]
    (?convert= [ob cb lr] Result/OVERFLOW)
    (?chars= cb "hell")
    (?convert= [ob cb lr] Result/OVERFLOW)
    (?chars= cb "o\nhe")
    (?convert= [ob cb lr] Result/OVERFLOW)
    (?chars= cb "llo\n")))

(deftest configurable-newline-sequence-for-line-writer
  (let [ob (b/object-buffer ["hello" "hello"])
        cb (b/char-buffer 8)
        lr (b/line-reader "!!!")]
    (dotimes [i 2]
      (?convert= [ob cb lr] Result/OVERFLOW)
      (?chars= cb "hello!!!"))))

(comment
(deftest simple-line-source-test
  (with-open [pipe (b/pipe)
              source (b/line-source (b/source pipe))]
    (doseq [c "hello\n"] (b/push pipe (byte c)))
    (e/emit-now! (b/on-poppable pipe))
    (?= (b/pop source) "hello")))

(deftest simple-line-sink-test
  (with-open [pipe (b/pipe)
              sink (b/line-sink (b/sink pipe))]
    (b/push sink "hello")
    (doseq [c "hello\n"] (?= (b/try-pop pipe) (int c)))))

(deftest line-sink-with-lines-option
  (with-open [pipe (b/pipe)
              source (b/line-source pipe)
              sink (b/line-sink pipe  :lines 5)]
    (dotimes [i 5] (?= (b/try-push sink "hello") true))
    (e/emit-now! (b/on-poppable pipe))
    (dotimes [i 5] (?= (b/pop source) "hello"))))
    
(deftest line-source-with-lines-option
  (with-open [pipe (b/pipe)
              source (b/line-source pipe :lines 5)
              sink (b/line-sink pipe :lines 5)]
    (dotimes [i 5] (b/push sink "hello"))
    (e/emit-now! (b/on-poppable pipe))
    (dotimes [i 5] (?= (b/try-pop source) "hello"))))

(deftest line-source-with-encoding-option
  (with-open [pipe (b/pipe)
              source (b/line-source pipe :charset "UTF-16LE")]
    (doseq [b (map byte [58 4 10 0])] (b/push pipe b))
    (e/emit-now! (b/on-poppable pipe))
    (?= (b/pop source) (str (char 1082)))))

(deftest line-sink-with-encoding-option
  (with-open [pipe (b/pipe)
              sink (b/line-sink pipe :charset "UTF-16LE")]
    (b/push sink (str (char 1082)))
    (e/emit-now! (b/on-poppable pipe))
    (doseq [b (map byte [58 4 10 0])] (?= (b/pop pipe) b))))

(deftest line-socket-test
  (with-open [pipe (b/pipe)
              socket (b/line-socket pipe :lines 2)]
    (b/try-push socket "hi")
    (b/try-push socket "Hello!!!")
    (e/emit-now! (b/on-poppable pipe))
    (?= (b/try-pop socket) "hi")
    (?= (b/try-pop socket) "Hello!!!"))))
