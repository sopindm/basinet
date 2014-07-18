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
    (?= (b/convert cb ls (b/line-writer)) basinet.Result/UNDERFLOW)
    (?= (b/try-pop ls) "again!!!")))

;;line sink gets lines and writes characters (newline sequence is parameter)
;;line sink isn't poppable when has no lines
;;line sink isn't pushable when has too much lines inside (more than parameter)
;;buffer sink interface
;;line reading wire

