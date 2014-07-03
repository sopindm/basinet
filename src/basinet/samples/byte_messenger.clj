(ns basinet.samples.byte-messenger
  (:gen-class :main true)
  (:require [basinet :as b]
            [basinet.tcp :as tcp]))

(defn- transform-and-reply [source transformer sink size]
  (let [data (doall (repeatedly size #(b/pop source)))]
    (reduce #(do (b/push %1 %2) %1) sink (transformer data))))

(def messages-per-connection 100000)

(defn- handle-connection [socket message-size]
  (with-open [source (b/source socket)
              sink (b/sink socket)]
    (dotimes [i messages-per-connection]
      (transform-and-reply source reverse sink message-size))))
 
(defn server [host port connections message-size]
  (with-open [acceptor (tcp/acceptor host port)]
    (dotimes [i connections]
      (let [handler (agent (b/pop acceptor))]
        (send-off handler handle-connection message-size)))))

(defn send-and-receive [data sink source]
  (doseq [d data] (b/push sink d))
  (doall (repeatedly (count data) #(b/pop source))))

(defn client [host port message-size]
  (with-open [socket (tcp/connect host port)]
    (let [source (b/source socket)
          sink (b/sink socket)
          latency (atom 0)
          start-time (System/currentTimeMillis)]
      (dotimes [i messages-per-connection]
        (let [start-time (System/currentTimeMillis)]
          (send-and-receive (byte-array message-size) sink source)
          (swap! latency + (- (System/currentTimeMillis) start-time))))
      (swap! latency / messages-per-connection)
      {:latency (float @latency)
       :rps (int (/ messages-per-connection (- (System/currentTimeMillis) start-time) 0.001))})))

(defn benchmark [clients message-size]
  (let [server (future (server "localhost" 10000 clients message-size))]
    (Thread/sleep 10)
    (let [clients (doall (repeatedly clients #(future (client "localhost" 10000 message-size))))
          client-results (map (fn [c] @c) clients)]
      {:latency (int (* 1000 (/ (reduce + (map :latency client-results))
                                (count client-results))))
       :rps (float (+ (reduce + (map :rps client-results))))})))

(defn -main []
  (for [clients [1 2 3 4]]
    (for [message-size [1 2 5 10 100]]
      (benchmark clients message-size))))
             
           
           
           
