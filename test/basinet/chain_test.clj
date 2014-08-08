(ns basinet.chain-test
  (:require [khazad-dum :refer :all :exclude [deftest]]
            [evil-ant :as e]
            [basinet :as b]))

(defmacro deftest [name & body]
  `(khazad-dum/deftest ~name
     (binding [b/*signal-set* (b/signal-set)]
       ~@body)))

(deftest making-chain
  (with-open [p (b/pipe)
              b (b/byte-buffer 10)
              chain (b/chain1 (b/source p) (b/sink b) (b/byte-channel-reader))]
    (?true (instance? basinet.Channel chain))
    (?false (instance? basinet.Source chain))
    (?false (instance? basinet.Sink chain))
    (dotimes [i 10] (?= (b/try-push (b/sink p) (byte i)) true))
    (?true (e/emit-now! (b/on-poppable p)))
    (dotimes [i 10] (?= (b/try-pop b) i))))

(deftest when-chain-closes-source-and-sink-are-close-too
  (with-open [p (b/pipe)
              b (b/byte-buffer 10)]
    (with-open [c (b/chain1 p b)])
    (?false (b/open? p))
    (?false (b/open? b))))

(deftest chain-is-open-when-source-or-sink-is-open
  (with-open [p (b/pipe)
              b (b/byte-buffer 10)
              c (b/chain1 p b)]
    (b/push b (byte 10))
    (?true (b/open? c))
    (b/close p)
    (?true (b/open? c))
    (b/close b)
    (?false (b/open? c)))
  (with-open [p (b/pipe)
              b (b/byte-buffer 10)]
    (b/close b)
    (?true (b/open? (b/chain1 p b)))))

;;
;; Chain events
;;

(deftest source-chain-onpoppable
  (let [b1 (b/object-buffer 10)
        b2 (b/object-buffer 10)]
    (?= (b/on-poppable (b/chain (b/source b1) b2)) (b/on-poppable b2))))

(deftest sink-chain-onpushable
  (let [b1 (b/object-buffer 5)
        b2 (b/object-buffer 10)]
    (?= (b/on-pushable (b/chain b1 (b/sink b2))) (b/on-pushable b1))))

(deftest pipe-chain-onpoppable
  (let [b (b/object-buffer 10)]
    (?= (b/on-poppable (b/chain (b/object-buffer 10) b)) (b/on-poppable b))))

(deftest pipe-chain-onpushable 
  (let [b (b/object-buffer 10)]
    (?= (b/on-pushable (b/chain b (b/object-buffer 10))) (b/on-pushable b))))

(deftest chain-updates-using-signals
  (let [b1 (b/object-buffer 5)
        b2 (b/object-buffer 1)
        n (atom 0)
        c (b/chain (b/source b1) (b/sink b2))]
    (?= (b/try-push (b/sink b1) 123) true)
    (b/emit-now!)
    (?= (b/try-pop b2) 123)))


    
;;
;; Updating chains
;;

(deftest update-with-closed-sink-closes-source
  (with-open [source (b/object-buffer 10)
              sink (b/object-buffer 10)
              chain (b/chain1 source sink)]
    (b/close sink)
    (?false (b/open? source))))

(deftest update-with-closed-source-closes-sink
  (with-open [source (b/object-buffer (range 5))
              b1 (b/object-buffer (range 10))
              sink (b/chain (b/object-buffer 10) b1)
              chain (b/chain source sink)]
    (b/close source)
    (?true (b/open? sink))
    (?false (b/open? (b/sink sink)))
    (?= (seq (repeatedly 10 #(b/try-pop sink))) (range 10))
    (b/emit!)
    (?= (seq (repeatedly 5 #(b/try-pop sink))) (range 5))
    (?false (b/open? sink))))

;;
;; Chain source/sinks/pipes
;;

(defn- push-somehow [sink n value]
  (case (mod value 3)
    0 (b/push sink value)
    1 (b/push-in sink value 1000)
    2 (b/try-push sink value)))

(defn- pop-somehow [source n]
  (case (mod n 3)
    0 (b/pop source)
    1 (b/pop-in source 1000)
    2 (b/try-pop source)))

(deftest source-chain
  (with-open [source (b/pipe)
              sink (b/byte-buffer 10)
              chain (b/chain1 (b/source source) sink)]
    (dotimes [i 10] (b/push source (byte (- i 5))))
    (?= (b/source chain) (b/source sink))
    (b/emit-now!)
    (?true (b/poppable chain))
    (dotimes [i 10] (?= (pop-somehow chain i) (- i 5)))))

(deftest sink-chain
  (with-open [source (b/byte-buffer 10)
              sink (b/pipe)
              chain (b/chain1 source (b/sink sink))]
    (?= (b/sink chain) (b/sink source))
    (dotimes [i 10] (push-somehow chain i (byte i)))
    (?true (b/pushable chain))
    (b/emit-now!)
    (dotimes [i 10] (?= (b/try-pop sink) (byte i)))))

(deftest pipe-chain
  (with-open [source (b/byte-buffer 5)
              sink (b/char-buffer 5)
              chain (b/chain1 source sink)]
    (?= (b/source chain) (b/source sink))
    (?= (b/sink chain) (b/sink source))
    (?false (b/poppable chain))
    (dotimes [i 5] (push-somehow chain i (byte (nth "hello" i))))
    (b/emit-now!)
    (dotimes [i 5] (?= (pop-somehow chain i) (nth "hello" i)))))

(deftest pushable-for-complex-chain
  (let [pushable #(b/object-buffer 1)
        not-pushable #(b/object-buffer (range 1))]
    (?false (b/pushable (b/chain (not-pushable) (not-pushable))))
    (?true (b/pushable (b/chain (not-pushable) (pushable))))
    (?true (b/pushable (b/chain (pushable) (not-pushable))))
    (?false (b/pushable (b/chain (not-pushable) (b/sink (not-pushable)))))
    (?true (b/pushable (b/chain (pushable) (b/sink (not-pushable)))))
    (?true (b/pushable (b/chain (not-pushable) (b/sink (pushable)))))))

(deftest poppable-for-complex-chain
  (let [poppable #(b/object-buffer (range 1))
        not-poppable #(b/object-buffer 1)]
    (?false (b/poppable (b/chain (not-poppable) (not-poppable))))
    (?true (b/poppable (b/chain (poppable) (not-poppable))))
    (?true (b/poppable (b/chain (not-poppable) (poppable))))
    (?true (b/poppable (b/chain (b/source (not-poppable)) (poppable))))
    (?true (b/poppable (b/chain (b/source (poppable)) (not-poppable))))))

(deftest pushing-to-chain
  (let [chain (b/chain (b/object-buffer (range 1)) (b/object-buffer 2))]
    (?true (b/push-in chain 42 1000))
    (b/emit-now!)
    (?true (b/push-in chain 142 1000))
    (b/emit-now!)
    (?false (b/push-in chain 242 1000))))

(deftest popping-from-chain
  (let [chain (b/chain (b/object-buffer (range 2)) (b/object-buffer 1))]
    (b/emit-now!)
    (?= (b/pop chain) 0)
    (b/emit-now!)
    (?= (b/pop-in chain 1000) 1)
    (?= (b/pop-in chain 1000) nil)))

(deftest long-chain
  (with-open [chain (b/chain (b/char-buffer 100)
                             (b/byte-buffer 1)
                             (b/object-buffer 10)
                             (b/byte-buffer 1)
                             (b/char-buffer 100))]
    (let [chars "hello, cruel hypocrite world!!!"]
      (doseq [c chars] (?= (b/try-push chain c) true))
      (b/emit!)
      (doseq [c chars] (?= (b/try-pop chain) c)))))

(deftest long-chain-with-wire
  (with-open [chain (b/chain (b/object-buffer 10) :by (b/object-buffer-reader)
                             (b/object-buffer 5) :by (b/line-reader)
                             (b/char-buffer 10) :by (b/line-writer)
                             (b/object-buffer 3))]
    (let [text ["hi" "hello" "hullo"]]
      (dotimes [i (count text)] (?= (b/try-push chain (nth text i)) true))
      (b/emit!)
      (dotimes [i (count text)] (?= (b/try-pop chain) (nth text i))))))

(deftest closing-source-chain
  (with-open [pipe (b/pipe)
              byte-buffer (b/byte-buffer 1000)
              char-buffer (b/char-buffer 1000)
              chain (b/chain (b/source pipe) byte-buffer char-buffer
                             :by (b/line-writer) (b/object-buffer 1))]
    (b/close (b/source pipe))
    (?false (b/open? chain))))
