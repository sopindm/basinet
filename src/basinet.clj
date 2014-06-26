(ns basinet)

(defrecord Pipe [source sink])

(defn pipe [] (Pipe. (basinet.Source.) (basinet.Sink.)))
  
