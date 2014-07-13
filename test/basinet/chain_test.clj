(ns basinet.chain-test
  (:require [khazad-dum :refer :all]
            [basinet :as b]))

(deftest making-input-chain
  (with-open [p (b/pipe)]
    (let [ci (b/chain (b/source p) (b/byte-buffer 10))]
      (dotimes [i 3] (b/push (b/sink p) (byte i)))
      (dotimes [i 3] (?= (b/try-pop ci) i)))))

(deftest closing-input-chain
  (with-open [p (b/pipe)]
    (with-open [ci (b/chain (b/source p) (b/byte-buffer 10))])
    (?false (b/open? (b/source p)))))

(deftest input-chains-source
  (with-open [p (b/pipe)]
    (let [b (b/byte-buffer 10)]
      (with-open [ci (b/chain (b/source p) b)]
        (?= (b/source ci) (b/source b))))))

(deftest updating-input-chain
  (with-open [p (b/pipe)]
    (with-open [ci (b/chain (b/source p) (b/byte-buffer 10))]
      (dotimes [i 5] (b/push (b/sink p) (byte 0)))
      (b/update ci)
      (?= (b/size ci) 5))))

(deftest making-output-chain
  (with-open [p (b/pipe)]
    (let [co (b/chain (b/byte-buffer 10) (b/sink p))]
      (dotimes [i 5] (?true (b/try-push co (byte i))))
      (dotimes [i 5] (?= (b/try-pop (b/source p)) i)))))

(deftest updating-output-chain
  (with-open [p (b/pipe)]
    (with-open [co (b/chain (b/byte-buffer 10) (b/sink p))]
      (dotimes [i 5] (b/set co i (byte (- 5 i))))
      (b/drop 5 co)
      (b/update co)
      (dotimes [i 5] (?= (b/pop (b/source p)) (- 5 i))))))

(deftest output-chain-source
  (with-open [p (b/pipe)]
    (let [b (b/byte-buffer 10)]
      (with-open [co (b/chain b (b/sink p))]
        (?= (b/sink co) (b/sink b))))))

(deftest closing-output-chain
  (with-open [p (b/pipe)]
    (with-open [co (b/chain (b/byte-buffer 10) (b/sink p))])
    (?false (b/open? (b/sink p)))))

(deftest output-chain-size
  (with-open [p (b/pipe)
              co (b/chain (b/byte-buffer 10) (b/sink p))]
    (?= (b/size co) 10)
    (b/drop 3 co)
    (?= (b/size co) 7)))

;;writing from input chain to output channel
