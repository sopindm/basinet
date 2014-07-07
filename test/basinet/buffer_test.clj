(ns basinet.buffer-test
  (:require [khazad-dum :refer :all]
            [basinet :as b]))

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

(deftest popping-from-empty-buffer
  (let [buffer (b/byte-buffer 1)]
    (?= (b/try-pop buffer) nil)
    (?= (b/pop-in buffer 1000) nil)
    (?throws (b/pop buffer) java.nio.BufferUnderflowException)))

(deftest reading-from-source-channel-to-buffer
  (with-open [pipe (b/pipe)]
    (dotimes [i 10] (-> pipe b/sink (b/push (byte (* i i)))))
    (let [buffer (b/byte-buffer 10)]
      (?= (b/read (b/source pipe) buffer) 10)
      (dotimes [i 10] (?= (b/pop buffer) (* i i))))))

(deftest writing-to-channel-from-buffer
  (with-open [pipe (b/pipe)]
    (let [buffer (b/byte-buffer (byte-array (map byte (range 10))))]
      (?= (b/write (b/sink pipe) buffer) 10)
      (dotimes [i 10] (?= (b/pop (b/source pipe)) i)))))

(deftest buffers-are-circular
  (let [buffer (b/byte-buffer 10)]
    (dotimes [i 8] (b/push buffer (byte i)))
    (dotimes [i 8] (?= (b/pop buffer) i))
    (?= (-> buffer b/sink b/size) 10)
    (dotimes [i 8] (b/push buffer (byte i)))
    (dotimes [i 8] (?= (b/pop buffer) i))))

(deftest writing-from-buffer-circular
  (let [buffer (b/byte-buffer 10)]
    (dotimes [i 7] (b/push buffer (byte 0)))
    (dotimes [i 7] (b/pop buffer))
    (dotimes [i 9] (b/push buffer (byte (* 3 i))))
    (with-open [pipe (b/pipe)]
      (?= (b/write (b/sink pipe) buffer) 9)
      (dotimes [i 9] (?= (b/pop (b/source pipe)) (* i 3))))
    (?= (b/size (b/source buffer)) 0)
    (?= (b/size (b/sink buffer)) 10)))

(deftest reading-to-buffer-circular
  (with-open [pipe (b/pipe)]
    (dotimes [i 7] (b/push (b/sink pipe) (byte (* i i))))
    (let [buffer (b/byte-buffer 10)]
      (dotimes [i 8] (b/push buffer (byte 0)))
      (dotimes [i 8] (b/pop buffer))
      (?= (b/read (b/source pipe) buffer) 7)
      (?= (b/size (b/source buffer)) 7)
      (?= (b/size (b/sink buffer)) 3)
      (dotimes [i 7] (?= (b/pop buffer) (byte (* i i)))))))

(deftest drop-and-expand-for-whole-buffer
  (let [b (b/byte-buffer 10)]
    (b/expand 7 b)
    (?= (b/size (b/source b)) 7)
    (?= (b/size (b/sink b)) 3)
    (?= (b/size b) 7)
    (b/drop 3 b)
    (?= (b/size (b/source b)) 4)
    (?= (b/size (b/sink b)) 6)
    (?= (b/size b) 4)))

(deftest random-access-for-buffers
  (let [b (b/byte-buffer 10)]
    (dotimes [i 7] (b/push b (byte i)))
    (dotimes [i 7] (?= (b/get b i) i))
    (dotimes [i 3] (b/set b i (byte i)))
    (b/expand 3 (b/source b))
    (dotimes [i 3] (?= (b/get b (+ 7 i)) (byte i)))))

(deftest circular-get-and-set-for-buffers
  (let [b (b/byte-buffer 10)]
    (dotimes [i 7] (b/push b (byte i)))
    (dotimes [i 3] (b/pop b))
    (dotimes [i 6] (b/set b i (byte (* i i))))
    (?throws (b/set b 7 (byte 0)) IllegalArgumentException)
    (b/expand 6 (b/source b))
    (dotimes [i 6] (?= (b/get b (+ i 4)) (* i i)))
    (b/drop 7 (b/source b))
    (?throws (b/get b 4 (byte 0)) IllegalArgumentException)))

;;buffer has capacity, size and free space
;;direct buffers
;;read/write covariance/contravariance

;;default write implementation for buffers
;;buffers have compaction threshold
;;buffers have random access



