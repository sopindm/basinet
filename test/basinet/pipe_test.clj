(ns basinet.pipe-test
  (:require [khazad-dum :refer :all]
            [basinet :as b])
  (:import [basinet Source Sink]))

(defn pipe []
  (let [p (b/pipe)]
    [(.source p) (.sink p)]))

(deftest making-pipe
  (let [[r w] (pipe)]
    (?true (instance? Source r))
    (?true (instance? Sink w))))


