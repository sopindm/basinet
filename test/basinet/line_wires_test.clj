(ns basinet.line-wires-test
  (:require [khazad-dum :refer :all]
            [basinet :as b]
            [basinet.buffer-test :refer [?convert=]])
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

