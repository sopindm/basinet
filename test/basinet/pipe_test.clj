(ns basinet.pipe-test
  (:require [khazad-dum :refer :all]
            [basinet :as b])
  (:import [basinet Source Sink]))

(defmacro with-pipe [[source sink] & body]
  `(let [p# (b/pipe)]
     (with-open [~source (.source p#)
                 ~sink (.sink p#)]
       ~@body)))

(deftest making-pipe
  (with-pipe [r w]
    (?true (instance? Source r))
    (?true (instance? Sink w))))

(deftest pipes-are-closeable
  (with-pipe [r w]
    (b/push w (byte 10))
    (.close r)
    (?throws (b/pop r) java.nio.channels.ClosedChannelException)
    (.close w)
    (?throws (b/push w (byte 10)) java.nio.channels.ClosedChannelException)))

(deftest pushing-and-popping-for-pipe
  (with-pipe [r w]
    (b/push w (byte 10))
    (b/push w (byte 20))
    (?= (b/pop r) 10)
    (?= (b/pop r) 20)))

(deftest pushing-and-popping-in-for-pipe
  (with-pipe [r w]
    (b/push-in w (byte 123) 1000)
    (?= (b/pop-in r 1000) 123)))

(deftest trying-to-push-and-to-pop-for-pipe
  (with-pipe [r w]
    (?= (b/try-push w 123) true)
    (?= (b/try-pop r) 123)))

(deftest popping-from-empty-pipe
  (with-pipe [r w]
    (?= (b/try-pop r) nil)
    (let [f (future (b/pop-in r 3))]
      (Thread/sleep 2)
      (?false (realized? f))
      (Thread/sleep 2)
      (?true (realized? f))
      (?= @f nil))
    (let [f (future (b/pop-in r 1000))]
      (Thread/sleep 2)
      (?false (realized? f))
      (b/push w (byte 123))
      (Thread/sleep 1)
      (?true (realized? f))
      (?= @f 123))))

(deftest pushing-to-full-pipe
  (with-pipe [r w]
    (?true (some false? (repeatedly 100000 #(b/try-push w (byte 111)))))
    (let [f (future (b/push-in w (byte 123) 3))]
      (Thread/sleep 2)
      (?false (realized? f))
      (Thread/sleep 2)
      (?true (realized? f))
      (?= @f false))
    (let [f (future (b/push-in w (byte 123) 3000))]
      (Thread/sleep 2)
      (?false (realized? f))
      (dotimes [i 10000] (b/try-pop r))
      (Thread/sleep 1)
      (?true (realized? f))
      (?= @f true))))






    


