(ns basinet.pipe-test
  (:require [khazad-dum :refer :all]
            [basinet :as b]
            [evil-ant :as e])
  (:import [basinet Source Sink]))

(defmacro with-pipe [[source sink] & body]
  `(let [p# (b/pipe)]
     (with-open [~source (b/source p#)
                 ~sink (b/sink p#)]
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

(deftest can-close-entire-pipe
  (let [p (b/pipe)]
    (b/close p)
    (?false (b/open? p))
    (?false (b/open? (b/source p)))
    (?false (b/open? (b/sink p)))))

(deftest pipe-is-open-if-reader-or-writer-is-open
  (let [p (b/pipe)]
    (b/close (b/source p))
    (?true (b/open? p))
    (b/close (b/sink p))
    (?false (b/open? p)))
  (let [p (b/pipe)]
    (b/close (b/sink p))
    (?true (b/open? p))
    (b/close (b/source p))
    (?false (b/open? p))))

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
    (?= (b/try-push w (byte 123)) true)
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

(deftest sink-and-source-for-pipes-sink-and-source
  (with-pipe [r w]
    (?= (b/sink w) w)
    (?= (b/source r) r)))

;;
;; Events
;;

(deftest pipes-source-isnt-poppalbe-by-default
  (with-pipe [r w]
    (?false (e/emit-now! (b/on-poppable r) r))))

(deftest pipes-source-poppable-with-has-something
  (with-pipe [r w]
    (b/push w (byte 10))
    (?true (e/emit-now! (b/on-poppable r) r))))

(deftest pipes-sink-is-pushable-by-default
  (with-pipe [r w]
    (?true (e/emit-now! (b/on-pushable w) w))))

(deftest pipes-sink-isnt-pushable-when-overflown
  (with-pipe [r w]
    (while (b/try-push w (byte 0)))
    (?false (e/emit-now! (b/on-pushable w) w))))

(deftest pipes-on-poppable-and-on-pushable-closed-on-close
  (with-pipe [r w]
    (b/close r)
    (b/close w)
    (?false (.isOpen (b/on-pushable w)))
    (?false (.isOpen (b/on-poppable r)))))

