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

;;buffers are circular
;;buffer has sink and source
;;buffer has capacity, size and free space
;;direct buffers

;;read/write covariance/contravariance

;;default write implementation for buffers
;;buffers have compaction threshold
;;buffers have random access



