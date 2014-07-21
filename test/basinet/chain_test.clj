(ns basinet.chain-test
  (:require [khazad-dum :refer :all]
            [basinet :as b]))

(comment
  (deftest making-input-chain
    (with-open [p (b/pipe)]
      (let [ci (b/chain1 (b/source p) (b/byte-buffer 10))]
        (dotimes [i 3] (b/push (b/sink p) (byte i)))
        (dotimes [i 3] (?= (b/try-pop ci) i)))))

  (deftest closing-input-chain
    (with-open [p (b/pipe)]
      (with-open [ci (b/chain1 (b/source p) (b/byte-buffer 10))])
      (?false (b/open? (b/source p)))))

  (deftest input-chains-source
    (with-open [p (b/pipe)]
      (let [b (b/byte-buffer 10)]
        (with-open [ci (b/chain1 (b/source p) b)]
          (?= (b/source ci) (b/source b))))))

  (deftest updating-input-chain
    (with-open [p (b/pipe)]
      (with-open [ci (b/chain1 (b/source p) (b/byte-buffer 10))]
        (dotimes [i 5] (b/push (b/sink p) (byte 0)))
        (b/update ci)
        (?= (b/size ci) 5))))

  (deftest making-output-chain
    (with-open [p (b/pipe)]
      (let [co (b/chain1 (b/byte-buffer 10) (b/sink p))]
        (dotimes [i 5] (?true (b/try-push co (byte i))))
        (dotimes [i 5] (?= (b/try-pop (b/source p)) i)))))

  (deftest updating-output-chain
    (with-open [p (b/pipe)]
      (with-open [co (b/chain1 (b/byte-buffer 10) (b/sink p))]
        (dotimes [i 5] (b/set co i (byte (- 5 i))))
        (b/drop 5 co)
        (b/update co)
        (dotimes [i 5] (?= (b/pop (b/source p)) (- 5 i))))))

  (deftest output-chain-source
    (with-open [p (b/pipe)]
      (let [b (b/byte-buffer 10)]
        (with-open [co (b/chain1 b (b/sink p))]
          (?= (b/sink co) (b/sink b))))))

  (deftest closing-output-chain
    (with-open [p (b/pipe)]
      (with-open [co (b/chain1 (b/byte-buffer 10) (b/sink p))])
      (?false (b/open? (b/sink p)))))

  (deftest output-chain-size
    (with-open [p (b/pipe)
                co (b/chain1 (b/byte-buffer 10) (b/sink p))]
      (?= (b/size co) 10)
      (b/drop 3 co)
      (?= (b/size co) 7))))

;;
;; Making chains right
;;

(deftest making-chain
  (with-open [p (b/pipe)
              b (b/byte-buffer 10)
              chain (b/chain1 (b/source p) (b/sink b) (b/byte-channel-reader))]
    (?true (instance? basinet.Channel chain))
    (?false (instance? basinet.Source chain))
    (?false (instance? basinet.Sink chain))
    (dotimes [i 10] (b/push (b/sink p) (byte i)))
    (?= (b/update chain) basinet.Result/OVERFLOW)
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
;; Updating chains
;;

(deftest chain-update-is-underflow-if-some-result-is-underflow
  (let [underflow-chain #(b/chain (b/object-buffer [1]) (b/object-buffer 2))]
    (?true (b/underflow? (b/update (underflow-chain))))
    (?true (b/underflow? (b/update (b/chain (underflow-chain) (b/object-buffer 10)))))
    (?true (b/underflow? (b/update (b/chain (b/object-buffer (range 10)) (underflow-chain)))))))

(deftest chain-update-if-overflow-if-some-result-if-overflow
  (let [overflow-chain #(b/chain (b/object-buffer (range 10)) (b/object-buffer 1))]
    (?true (b/overflow? (b/update (overflow-chain))))
    (?true (b/overflow? (b/update (b/chain (overflow-chain) (b/object-buffer 1000)))))
    (?true (b/overflow? (b/update (b/chain (b/object-buffer []) (overflow-chain)))))))

(deftest update-with-closed-sink-closes-source
  (with-open [source (b/object-buffer 10)
              sink (b/object-buffer 10)
              chain (b/chain1 source sink)]
    (b/close sink)
    (?= (b/update chain) basinet.Result/OVERFLOW)
    (?false (b/open? source))))

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
    (b/update chain)
    (?= (b/source chain) (b/source sink))
    (?true (b/poppable chain))
    (dotimes [i 10] (?= (pop-somehow chain i) (- i 5)))))

(deftest sink-chain
  (with-open [source (b/byte-buffer 10)
              sink (b/pipe)
              chain (b/chain1 source (b/sink sink))]
    (?= (b/sink chain) (b/sink source))
    (dotimes [i 10] (push-somehow chain i (byte i)))
    (?= (b/update chain) basinet.Result/UNDERFLOW)
    (?true (b/pushable chain))
    (dotimes [i 10] (?= (b/try-pop sink) (byte i)))))

(deftest pipe-chain
  (with-open [source (b/byte-buffer 5)
              sink (b/char-buffer 5)
              chain (b/chain1 source sink)]
    (?= (b/source chain) (b/source sink))
    (?= (b/sink chain) (b/sink source))
    (?false (b/poppable chain))
    (dotimes [i 5] (push-somehow chain i (byte (nth "hello" i))))
    (b/update chain)
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
    (b/push chain 142)
    (?false (b/push-in chain 242 1000))))

(deftest popping-from-chain
  (let [chain (b/chain (b/object-buffer (range 2)) (b/object-buffer 1))]
    (?= (b/pop chain) 0)
    (?= (b/pop-in chain 1000) 1)
    (?= (b/pop-in chain 1000) nil)))

(deftest long-chain
  (with-open [chain (b/chain (b/object-buffer 5)
                             (b/line-sink)
                             (b/char-buffer 10)
                             (b/byte-buffer 1)
                             (b/pipe)
                             (b/byte-buffer 1)
                             (b/char-buffer 10)
                             (b/line-source)
                             (b/object-buffer 5))]
    (let [text [(format "hello one") "hello two" "hi" "hi" "hi"]]
      (dotimes [i 5] (b/push chain (nth text i)))
      (dotimes [i 5] (?= (b/pop chain) (nth text i))))))

(deftest long-chain-with-wire
  (with-open [chain (b/chain (b/object-buffer 10) :by (b/object-buffer-reader)
                             (b/object-buffer 5) :by (b/object-buffer-reader)
                             (b/line-sink) (b/char-buffer 10) :by (b/line-writer)
                             (b/line-source))]
    (let [text ["hi" "hello" "hullo"]]
      (dotimes [i (count text)] (b/push chain (nth text i)))
      (dotimes [i (count text)] (?= (b/pop chain) (nth text i))))))
    
    

    


    
    


