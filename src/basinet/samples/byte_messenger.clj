(ns basinet.samples.byte-messenger
  (:gen-class :main true)
  (:require [basinet :as b]
            [basinet.tcp :as tcp]))

(defn- transform-and-reply [source transformer sink size]
  (let [data (doall (repeatedly size #(b/pop source)))]
    (reduce #(do (b/push %1 %2) %1) sink (transformer data))))

(def messages-per-connection 100000)

(defn- read-message [source size]
  (while (< (b/size source) size) (b/update source)))

(defn- write-message [sink size]
  (while (< (b/size sink) size) (b/update sink)))

(defn- buffer [message-size] (b/byte-buffer message-size))

(defn- handle-connection [socket message-size]
  (with-open [source (b/chain (b/source socket) (buffer message-size))
              sink (b/chain (buffer message-size) (b/sink socket))]
    (dotimes [i messages-per-connection]
      (read-message source message-size)
      (dotimes [i message-size]
        (b/set sink (- message-size i 1) (b/get source i)))
      (b/drop message-size sink)
      (b/drop message-size source)
      (write-message sink message-size))))
 
(defn server [host port connections message-size]
  (with-open [acceptor (tcp/acceptor host port)]
    (dotimes [i connections]
      (let [handler (agent (b/pop acceptor))]
        (send-off handler handle-connection message-size)))))

(defn send-and-receive [data sink source]
  (doseq [d data] (b/push sink d))
  (doall (repeatedly (count data) #(b/pop source))))

(defn client [host port message-size verbose?]
  (with-open [socket (tcp/connect host port)]
    (let [source (b/chain (b/source socket) (buffer message-size))
          sink (b/chain (buffer message-size) (b/sink socket))
          latency (atom 0)
          start-time (System/currentTimeMillis)]
      (dotimes [i messages-per-connection]
        (let [start-time (System/currentTimeMillis)]
          (if verbose?
            (dotimes [i message-size] (b/push sink (byte i)))
            (dotimes [i message-size] (b/push sink (byte 0))))
          (write-message sink message-size)
          (read-message source message-size)
          (if verbose? 
            (println (repeatedly message-size #(b/pop source)))
            (b/drop message-size source))
          (swap! latency + (- (System/currentTimeMillis) start-time))))
      (swap! latency / messages-per-connection)
      {:latency (float @latency)
       :rps (int (/ messages-per-connection (- (System/currentTimeMillis) start-time) 0.001))})))

(defn benchmark [clients message-size]
  (let [server (future (server "localhost" 10000 clients message-size))]
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

(defn -main [clients size]
  (println (benchmark (Integer/parseInt clients) (Integer/parseInt size)))
  (System/exit 0))
             
           
           
           
