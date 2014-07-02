(ns basinet.samples.byte-messenger
  (:require [basinet :as b]
            [basinet.tcp :as tcp]))

(defn- handle-connection [socket]
  (with-open [source (b/source socket)
              sink (b/sink socket)]
    (dotimes [i 100]
      (let [bytes (doall (repeatedly 10 #(b/pop source)))]
        (reduce #(do (b/push %1 %2) %1) sink  (reverse bytes))))))
 
(defn server
  ([] (server 10000))
  ([port] (server "localhost" port))
  ([host port] (server "localhost" port 10))
  ([host port connections]
     (with-open [acceptor (tcp/acceptor host port)]
       (dotimes [i connections]
         (let [handler (agent (b/pop acceptor))]
           (send-off handler handle-connection))))))


