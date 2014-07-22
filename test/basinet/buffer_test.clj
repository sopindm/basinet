(ns basinet.buffer-test
  (:require [khazad-dum :refer :all]
            [basinet :as b])
  (:import [java.nio.channels ClosedChannelException]
           [basinet Result]))

(deftest buffer-has-push-and-pop
  (let [buffer (b/byte-buffer 10)]
    (dotimes [i 10] (b/push buffer (byte (* i i))))
    (dotimes [i 10] (?= (b/pop buffer) (byte (* i i))))))

(deftest buffer-update
  (?= (b/update (b/byte-buffer 1)) basinet.Result/NOTHING))

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
    (?false (.pushable b))))

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
    (?throws (b/drop 0 s) ClosedChannelException)
    (?throws (b/expand 0 s) ClosedChannelException)))

(deftest closing-buffer-sink
  (let [b (b/byte-buffer 10)
        s (b/sink b)]
    (.close s)
    (?false (b/open? s))
    (?false (b/pushable s))
    (?throws (b/set s 0 (byte 10)) ClosedChannelException)))

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

(defmacro ?convert= [[from to & by] result]
  `(let [from# ~from
         to# ~to]
     (?= (b/convert from# to# ~@by) ~result)))

(deftest reading-from-source-channel-to-buffer
  (with-open [pipe (b/pipe)]
    (dotimes [i 10] (-> pipe b/sink (b/push (byte (* i i)))))
    (let [buffer (b/byte-buffer 10)]
      (?convert= [pipe buffer (b/byte-channel-reader)] Result/OVERFLOW)
      (dotimes [i 10] (?= (b/pop buffer) (* i i))))))

(deftest writing-to-channel-from-buffer
    (with-open [pipe (b/pipe)]
      (let [buffer (b/byte-buffer (byte-array (map byte (range 10))))]
        (?convert= [buffer pipe (b/byte-channel-writer)] Result/UNDERFLOW)
        (dotimes [i 10] (?= (b/pop (b/source pipe)) i)))))

(deftest default-read-and-write-wires
  (with-open [pipe (b/pipe)
              buffer (b/byte-buffer (byte-array
                                     (map byte (range -5 5))))]
    (?convert= [buffer pipe] basinet.Result/UNDERFLOW)
    (?convert= [pipe buffer] basinet.Result/OVERFLOW)
    (dotimes [i 10] (?= (b/pop buffer) (- i 5)))))

(deftest reading-and-writing-with-underflow-and-overflow
  (with-open [pipe (b/pipe)
              buffer (b/byte-buffer 10)]
    (dotimes [i 9] (b/push pipe (byte i)))
    (?convert= [pipe buffer] Result/UNDERFLOW)
    (?convert= [buffer pipe] Result/UNDERFLOW)
    (?convert= [pipe (b/byte-buffer 5)] basinet.Result/OVERFLOW)
    (?convert= [(b/byte-buffer (byte-array 100000)) pipe] basinet.Result/OVERFLOW)))

(deftest writing-from-buffer-circular
  (let [buffer (b/byte-buffer 10)]
    (dotimes [i 7] (b/push buffer (byte 0)))
    (dotimes [i 7] (b/pop buffer))
    (dotimes [i 9] (b/push buffer (byte (* 3 i))))
    (with-open [pipe (b/pipe)]
      (?convert= [buffer pipe] basinet.Result/UNDERFLOW)
      (dotimes [i 3] (?= (b/pop (b/source pipe)) (* i 3))))
    (?= (b/size (b/source buffer)) 0)
    (?= (b/size (b/sink buffer)) 10)))

(deftest reading-to-buffer-circular
  (with-open [pipe (b/pipe)]
    (dotimes [i 7] (b/push (b/sink pipe) (byte (* i i))))
    (let [buffer (b/byte-buffer 10)]
      (dotimes [i 8] (b/push buffer (byte 0)))
      (dotimes [i 8] (b/pop buffer))
      (?convert= [pipe buffer] basinet.Result/UNDERFLOW)
      (?= (b/size (b/source buffer)) 7)
      (?= (b/size (b/sink buffer)) 3)
      (dotimes [i 7] (?= (b/pop buffer) (byte (* i i)))))))

(deftest making-char-buffer
  (with-open [b (b/char-buffer 10)]
    (doseq [c "hello"] (b/push b c))
    (?= (apply str (repeatedly 5 #(b/pop b))) "hello")))

(deftest get-and-set-for-char-buffers
  (with-open [b (b/char-buffer 10)]
    (doall (map-indexed (fn [i c] (b/set (b/sink b) i c)) "Hello!!!"))
    (b/drop 8 (b/sink b))
    (dotimes [i 8] (?= (b/get (b/source b) i) (nth "Hello!!!" i)))))

;;
;; Byte <-> Char conversion wires
;;

(deftest converting-simple-byte-buffer-to-chars
  (with-open [bytes (b/byte-buffer (byte-array (map byte (range 97 100))))
              chars (b/char-buffer 3)]
    (?convert= [bytes chars (b/bytes->chars (b/unicode-charset))] basinet.Result/UNDERFLOW)
    (dotimes [i 3] (?= (b/pop (b/source chars)) (nth "abc" i)))))

(deftest converting-byte-buffer-to-chars-with-overflow
  (with-open [bytes (b/byte-buffer (byte-array (map byte (range 97 100))))
              chars (b/char-buffer 1)]
    (?convert= [bytes chars (b/bytes->chars (b/unicode-charset))] basinet.Result/OVERFLOW)))

(deftest converting-big-byte-sequence-to-char
  (with-open [bytes (b/byte-buffer (byte-array (map byte [-48 -102])))
              chars (b/char-buffer 1)]
    (?convert= [bytes chars (b/bytes->chars (b/unicode-charset))] basinet.Result/UNDERFLOW)
    (?= (int (b/pop chars)) 1050)))

(deftest converting-bytes-to-char-circurlar
  (with-open [bytes (b/byte-buffer 3 1)
              chars (b/char-buffer 1)]
    (b/expand 1 (b/drop 1 (b/sink bytes)))
    (b/push bytes (byte -48))
    (b/push bytes (byte -102))
    (b/drop 0 (b/source bytes))
    (?convert= [bytes chars (b/bytes->chars (b/unicode-charset))] basinet.Result/UNDERFLOW)
    (?true (b/poppable chars))
    (?= (int (b/pop chars)) 1050)))

(deftest converting-chars-to-bytes
  (let [chars (b/char-buffer (char-array "hi!!!"))
        bytes (b/byte-buffer 5)]
    (?convert= [chars bytes (b/chars->bytes (b/unicode-charset))] basinet.Result/UNDERFLOW)
    (dotimes [i 5] (?= (b/pop bytes) (nth [104 105 33 33 33] i)))))

(deftest converting-chars-to-bytes-with-overflow
  (let [chars (b/char-buffer (char-array "hi!!!"))
        bytes (b/byte-buffer 2)]
    (?convert= [chars bytes (b/chars->bytes (b/unicode-charset))] basinet.Result/OVERFLOW)
    (dotimes [i 2] (?= (b/pop bytes) (nth [104 105] i)))))

(deftest converting-big-char-to-bytes
  (let [chars (b/char-buffer (char-array [(char 1050)]))
        bytes (b/byte-buffer 2)]
    (?convert= [chars bytes (b/chars->bytes (b/unicode-charset))] basinet.Result/UNDERFLOW)
    (dotimes [i 2] (?= (b/pop bytes) (nth [-48 -102] i)))))

(deftest bytes-to-chars-and-chars-to-bytes-default-wires
  (let [chars (b/char-buffer (char-array "hello"))
        bytes (b/byte-buffer 5)]
    (b/convert chars bytes)
    (b/convert bytes chars)
    (dotimes [i 5] (?= (b/pop chars) (nth "hello" i)))))

;;
;; Compaction threshold
;;

(deftest making-buffer-with-compaction-threshold
  (let [b (b/byte-buffer 10 2)]
    (?= (-> b b/source .begin) 2)
    (?= (-> b b/source .size) 0)
    (?= (-> b b/sink .begin) 2)
    (?= (-> b b/sink .size) 8))
  (let [b (b/byte-buffer (byte-array 10) 2)]
    (?= (-> b b/source .begin) 2)
    (?= (-> b b/source .size) 8)
    (?= (-> b b/sink .begin) 2)
    (?= (-> b b/sink .size) 0)))

(deftest compacting-buffer-source
  (let [b (b/byte-buffer 10 2)]
    (dotimes [i 8] (b/set (b/sink b) i (byte i)))
    (b/drop 8 (b/sink b))
    (b/drop 6 (b/source b))
    (?= (-> b b/source .begin) 0)
    (?= (-> b b/source .end) 2)
    (?= (b/get (b/source b) 0) 6)
    (?= (b/get (b/source b) 1) 7)))

(deftest writer-in-compaction-area
  (let [b (b/byte-buffer 10 2)]
    (dotimes [i 8] (b/set (b/sink b) i (byte i)))
    (b/drop 6 (b/sink b))
    (?= (-> b b/sink .begin) 0)
    (?= (-> b b/sink .end) 2)
    (dotimes [i 2] (b/set (b/sink b) i (byte (dec (- i)))))
    (b/drop 2 (b/sink b))
    (?= (b/get (b/source b) 6) -1)
    (?= (b/get (b/source b) 7) -2)))

;;
;; Object buffers
;;

(deftest maing-object-buffer
  (let [b (b/object-buffer 10)]
    (?= (b/size (b/source b)) 0)
    (?= (b/size (b/sink b)) 10))
  (let [b (b/object-buffer 10 5)]
    (?= (b/size (b/source b)) 0)
    (?= (b/size (b/sink b)) 5))
  (let [b (b/object-buffer (range 10))]
    (?= (b/size (b/source b)) 10)
    (?= (b/size (b/sink b)) 0)))

(deftest accessing-object-buffer
  (with-open [b (b/object-buffer 10)]
    (dotimes [i 10] (b/push (b/sink b) (byte i)))
    (dotimes [i 10] (?= (b/pop (b/source b)) i))))

(deftest accessing-object-buffer-with-compaction
  (with-open [b (b/object-buffer 10 2)]
    (b/drop 6 (b/sink b))
    (dotimes [i 2] (b/set (b/sink b) i (byte (* (inc i) 50))))
    (b/drop 2 (b/sink b))
    (?= (b/get (b/source b) 6) 50)
    (?= (b/get (b/source b) 7) 100)))

(deftest converting-from-anything-to-object-buffer
  (with-open [p (b/pipe)
              ob (b/object-buffer 10)]
    (dotimes [i 5] (b/push p (byte (- 5 i))))
    (?convert= [p ob (b/object-buffer-writer)] basinet.Result/UNDERFLOW)
    (dotimes [i 5] (?= (b/pop ob) (- 5 i))))
  (with-open [bb (b/byte-buffer (byte-array (map byte (range 10))))
              ob (b/object-buffer 10)]
    (?convert= [bb ob] basinet.Result/OVERFLOW)
    (dotimes [i 10] (?= (b/pop ob) i)))
  (with-open [cb (b/char-buffer (char-array "hello"))
              ob (b/object-buffer 10)]
    (?convert= [cb ob] basinet.Result/UNDERFLOW)
    (?= (apply str (repeatedly 5 #(b/pop ob))) "hello")))

(deftest converting-from-object-buffer-to-everything
  (with-open [p (b/pipe)
              ob (b/object-buffer 10)]
    (dotimes [i 5] (b/push ob (byte i)))
    (?convert= [ob p (b/object-buffer-reader)] basinet.Result/UNDERFLOW)
    (dotimes [i 5] (?= (b/pop p) i)))
  (with-open [ob (b/object-buffer (map byte (range -5 5)))
              bb (b/byte-buffer 5)]
    (?convert= [ob bb] basinet.Result/OVERFLOW)
    (dotimes [i 5] (?= (b/pop bb) (- i 5)))))

;;buffer has capacity
;;direct buffers



