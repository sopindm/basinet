(ns basinet.line-channels-test
  (:require [khazad-dum :refer :all]
            [basinet :as b])
  (:import [java.nio.channels ClosedChannelException]))

(deftest line-source-reads-chars-and-makes-strings
  (with-open [source (b/line-source)]
    (reduce #(do (b/push %1 %2) %1) source (format "hello%n"))
    (?= (b/pop source) "hello")))

(deftest line-source-isnt-poppalbe-until-has-full-line
  (with-open [source (b/line-source)]
    (reduce #(do (b/push %1 %2) %1) source "hello")
    (?false (b/poppable source))
    (?true (b/pushable source))
    (b/push source \newline)
    (?true (b/poppable source))
    (?false (b/pushable source))))

(deftest line-source-works-with-different-newline-sequences
  (with-open [source (b/line-source)]
    (doseq [i (map char (range 10 14))]
      (?true (b/try-push source i))
      (?= (b/try-pop source) ""))
    (b/push source (char 13))
    (?= (b/pop source) "")
    (b/push source (char 10))
    (?false (b/poppable source))))

(deftest line-writing-wire
  (with-open [cb (b/char-buffer "hello\nhi\nagain!!!\n")
              ls (b/line-source)]
    (?= (b/convert cb ls (b/line-writer)) basinet.Result/OVERFLOW)
    (?= (b/try-pop ls) "hello")
    (?= (b/convert cb ls (b/line-writer)) basinet.Result/OVERFLOW)
    (?= (b/try-pop ls) "hi")
    (?= (b/convert cb ls) basinet.Result/UNDERFLOW)
    (?= (b/try-pop ls) "again!!!")))

(deftest line-sink-reads-strings-and-writes-chars
  (with-open [sink (b/line-sink)]
    (b/push sink "hi")
    (?= (apply str (repeatedly 3 #(b/try-pop sink))) (format "hi%n"))
    (b/push sink "hello")
    (?= (apply str (repeatedly 6 #(b/try-pop sink))) (format "hello%n"))))

(deftest line-sink-isnt-popable-when-empty-and-isnt-pushable-when-not
  (with-open [sink (b/line-sink)]
    (?false (b/poppable sink))
    (?true (b/pushable sink))
    (b/push sink "")
    (?true (b/poppable sink))
    (?false (b/pushable sink))
    (?= (b/pop sink) \newline)
    (?false (b/poppable sink))
    (?true (b/pushable sink))))

(deftest line-reading-wire
  (with-open [ls (b/line-sink)
              cb (b/char-buffer 10)]
    (b/push ls "hello from nowhere")
    (?= (b/convert ls cb (b/line-reader)) basinet.Result/OVERFLOW)
    (?= (apply str (repeatedly 10 #(b/try-pop cb))) "hello from")
    (?= (b/convert ls cb) basinet.Result/UNDERFLOW)
    (?= (apply str (repeatedly 9 #(b/try-pop cb))) (format " nowhere%n"))))

(deftest configurable-newline-sequence-for-line-sink
  (with-open [s (b/line-sink "!!!")]
    (b/push s "hi")
    (?= (apply str (repeatedly 5 #(b/try-pop s))) "hi!!!")))
