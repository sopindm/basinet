(ns basinet.buffer-test
  (:require [khazad-dum :refer :all]
            [basinet :as b])
  (:import [java.nio.channels ClosedChannelException]))

(deftest buffer-has-push-and-pop
  (let [buffer (b/byte-buffer 10)]
    (dotimes [i 10] (b/push buffer (byte (* i i))))
    (dotimes [i 10] (?= (b/pop buffer) (byte (* i i))))))

(deftest pushing-to-full-buffer
  (let [buffer (b/byte-buffer 1)]
    (b/push buffer (byte 111))
    (?throws (b/push buffer (byte 42)) java.nio.BufferOverflowException)
    (?false (b/push-in buffer (byte 42) 1000))
    (?false (b/try-push buffer (byte 42)))))

(deftest pushable-and-poppable-for-whole-buffer
  (let [b (b/byte-buffer 10)]
    (?false (.poppable b))
    (?true (.pushable b))
    (b/expand 5 (b/source b))
    (?true (.poppable b))
    (?true (.pushable b))
    (b/expand 5 (b/source b))
    (?true (.poppable b))
    (?false (.pushable b))))0
(deftest popping-from-empty-buffer
  (let [buffer (b/byte-buffer 1)]
    (?= (b/try-pop buffer) nil)
    (?= (b/pop-in buffer 1000) nil)
    (?throws (b/pop buffer) java.nio.BufferUnderflowException)))

(deftest buffers-are-circular
  (let [buffer (b/byte-buffer 10)]
    (dotimes [i 8] (b/push buffer (byte i)))
    (dotimes [i 8] (?= (b/pop buffer) i))
    (?= (-> buffer b/sink b/size) 10)
    (dotimes [i 8] (b/push buffer (byte i)))
    (dotimes [i 8] (?= (b/pop buffer) i))))

(deftest random-access-for-buffers
  (let [b (b/byte-buffer 10)]
    (dotimes [i 7] (b/push b (byte i)))
    (dotimes [i 7] (?= (b/get (b/source b) i) i))
    (dotimes [i 3] (b/set (b/sink b) i (byte i)))
    (b/expand 3 (b/source b))
    (dotimes [i 3] (?= (b/get (b/source b) (+ 7 i)) (byte i)))))

(deftest circular-get-and-set-for-buffers
  (let [b (b/byte-buffer 10)]
    (dotimes [i 7] (b/push b (byte i)))
    (dotimes [i 3] (b/pop b))
    (dotimes [i 6] (b/set (b/sink b) i (byte (* i i))))
    (?throws (b/set (b/sink b) 7 (byte 0)) IllegalArgumentException)
    (b/expand 6 (b/source b))
    (dotimes [i 6] (?= (b/get (b/source b) (+ i 4)) (* i i)))
    (b/drop 7 (b/source b))
    (?throws (b/get (b/source b) 4 (byte 0)) IllegalArgumentException)))

(deftest sink-and-source-for-buffers-sink-and-source
  (let [b (b/byte-buffer 10)]
    (?= (b/sink (b/sink b)) (b/sink b))
    (?= (b/source (b/source b)) (b/source b))))

(deftest closing-buffer-source
  (let [b (b/byte-buffer 10)
        s (b/source b)]
    (b/push b (byte 123))
    (.close s)
    (?false (b/open? s))
    (?false (b/poppable s))
    (?throws (b/get s 0) ClosedChannelException)
    (with-open [p (b/pipe)]
      (?throws (b/convert s p) ClosedChannelException))
    (?throws (b/drop 0 s) ClosedChannelException)
    (?throws (b/expand 0 s) ClosedChannelException)))

(deftest closing-buffer-sink
  (let [b (b/byte-buffer 10)
        s (b/sink b)]
    (.close s)
    (?false (b/open? s))
    (?false (b/pushable s))
    (?throws (b/set s 0 (byte 10)) ClosedChannelException)
    (with-open [p (b/pipe)]
      (?throws (b/convert p s) ClosedChannelException))))

(deftest closing-whole-buffer
  (with-open [b (b/byte-buffer 10)]
    (.close b)
    (?false (b/open? b))
    (?false (b/open? (b/sink b)))
    (?false (b/open? (b/source b)))))

(deftest closing-buffer-source-closes-sink
  (with-open [b (b/byte-buffer 10)]
    (.close (b/source b))
    (?false (b/open? (b/sink b)))
    (?false (b/open? b))))

;;
;; byte wires
;; 

(deftest reading-from-source-channel-to-buffer
  (with-open [pipe (b/pipe)]
    (dotimes [i 10] (-> pipe b/sink (b/push (byte (* i i)))))
    (let [buffer (b/byte-buffer 10)]
      (?= (b/convert pipe buffer (b/byte-channel-reader))
          basinet.Result/OVERFLOW)
      (dotimes [i 10] (?= (b/pop buffer) (* i i))))))

(deftest writing-to-channel-from-buffer
    (with-open [pipe (b/pipe)]
      (let [buffer (b/byte-buffer (byte-array (map byte (range 10))))]
        (?= (b/convert buffer pipe (b/byte-channel-writer))
            basinet.Result/UNDERFLOW)
        (dotimes [i 10] (?= (b/pop (b/source pipe)) i)))))

(deftest default-read-and-write-wires
  (with-open [pipe (b/pipe)
              buffer (b/byte-buffer (byte-array
                                     (map byte (range -5 5))))]
    (?= (b/convert buffer pipe) basinet.Result/UNDERFLOW)
    (?= (b/convert pipe buffer) basinet.Result/OVERFLOW)
    (dotimes [i 10] (?= (b/pop buffer) (- i 5)))))

(deftest reading-and-writing-with-underflow-and-overflow
  (with-open [pipe (b/pipe)
              buffer (b/byte-buffer 10)]
    (dotimes [i 9] (b/push pipe (byte i)))
    (?= (b/convert pipe buffer) basinet.Result/UNDERFLOW)
    (?= (b/convert buffer pipe) basinet.Result/UNDERFLOW)
    (?= (b/convert pipe (b/byte-buffer 5)) basinet.Result/OVERFLOW)
    (?= (b/convert (b/byte-buffer (byte-array 100000)) pipe) basinet.Result/OVERFLOW)))

(deftest writing-from-buffer-circular
  (let [buffer (b/byte-buffer 10)]
    (dotimes [i 7] (b/push buffer (byte 0)))
    (dotimes [i 7] (b/pop buffer))
    (dotimes [i 9] (b/push buffer (byte (* 3 i))))
    (with-open [pipe (b/pipe)]
      (?= (b/convert buffer pipe) basinet.Result/UNDERFLOW)
      (dotimes [i 3] (?= (b/pop (b/source pipe)) (* i 3))))
    (?= (b/size (b/source buffer)) 0)
    (?= (b/size (b/sink buffer)) 10)))

(deftest reading-to-buffer-circular
  (with-open [pipe (b/pipe)]
    (dotimes [i 7] (b/push (b/sink pipe) (byte (* i i))))
    (let [buffer (b/byte-buffer 10)]
      (dotimes [i 8] (b/push buffer (byte 0)))
      (dotimes [i 8] (b/pop buffer))
      (?= (b/convert pipe buffer) basinet.Result/UNDERFLOW)
      (?= (b/size (b/source buffer)) 7)
      (?= (b/size (b/sink buffer)) 3)
      (dotimes [i 7] (?= (b/pop buffer) (byte (* i i)))))))

;;buffer has capacity, size and free space
;;direct buffers
;;read/write covariance/contravariance

;;default write implementation for buffers
;;buffers have compaction threshold
;;buffers have random access



