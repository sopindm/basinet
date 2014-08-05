(ns basinet.samples.line-messenger
  (:gen-class :main true)
  (:require [basinet :as b]
            [basinet.tcp :as tcp]))

(def messages-per-connection 100000)

(defn handle-connection [socket]
  (with-open [socket (b/line-socket socket)]
    (dotimes [i messages-per-connection] 
      (b/push socket (b/pop socket))
      (while (and (b/overflow? (b/update socket)) (b/open? (b/sink socket)))))))
 
(defn server [host port connections]
  (with-open [acceptor (tcp/acceptor host port)]
    (dotimes [i connections]
       (handle-connection (b/pop acceptor)))))
      ;(let [handler (agent (b/pop acceptor))]
      ;  (send-off handler handle-connection)))))

(defn client [host port message-size verbose?]
  (with-open [socket (b/line-socket (tcp/connect host port))]
    (let [latency (atom 0)
          start-time (System/currentTimeMillis)
          message (apply str (repeat message-size \I))]
      (dotimes [i messages-per-connection]
        (let [start-time (System/currentTimeMillis)]
          (b/push socket message)
          (while (and (b/overflow? (b/update socket)) (b/open? (b/sink socket))))
          (let [message (b/pop socket)]
            (if verbose? (println message)))
          (swap! latency + (- (System/currentTimeMillis) start-time))))
      (swap! latency / messages-per-connection)
      {:latency (float @latency)
       :rps (int (/ messages-per-connection (- (System/currentTimeMillis) start-time) 0.001))})))

(defn benchmark [clients message-size]
  (let [server (future (server "localhost" 10000 clients))]
    (Thread/sleep 10)
    (let [clients (doall (repeatedly clients #(future (client "localhost" 10000 message-size false))))
          client-results (map (fn [c] @c) clients)]
      {:latency (int (* 1000 (/ (reduce + (map :latency client-results))
                                (count client-results))))
       :rps (float (+ (reduce + (map :rps client-results))))})))

(defn benchmark-all []
  (for [clients [1 2 3 4]]
    (for [message-size [1 2 5 10 100]]
      (benchmark clients message-size))))

             
           
           
           
