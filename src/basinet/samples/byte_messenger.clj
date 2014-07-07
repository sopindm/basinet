(ns basinet.samples.byte-messenger
  (:gen-class :main true)
  (:require [basinet :as b]
            [basinet.tcp :as tcp]))

(defn- transform-and-reply [source transformer sink size]
  (let [data (doall (repeatedly size #(b/pop source)))]
    (reduce #(do (b/push %1 %2) %1) sink (transformer data))))

(def messages-per-connection 100000)

(defn- read-message [source buffer size]
  (let [finish-size (- (b/size (b/sink buffer)) size)]
    (while (> ^int (b/size (b/sink buffer)) ^int finish-size)
      (b/read source buffer))))

(defn- write-message [sink buffer size]
  (while (> ^int (b/size (b/source buffer)) 0)
    (b/write sink buffer)))

(defn- buffer [message-size] (b/byte-buffer message-size))

(defn- handle-connection [socket message-size]
  (with-open [source (b/source socket)
              sink (b/sink socket)]
    (let [input-buffer (buffer message-size)
          output-buffer (buffer message-size)]
      (dotimes [i messages-per-connection]
        (read-message source input-buffer message-size)
        (dotimes [i message-size]
          (b/set output-buffer
                 (- message-size i 1) (b/get input-buffer i)))
        (b/expand message-size output-buffer)
        (b/drop message-size input-buffer)
        (write-message sink output-buffer message-size)))))
 
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
          input-buffer (buffer message-size)
          output-buffer (buffer message-size)
          latency (atom 0)
          start-time (System/currentTimeMillis)]
      (dotimes [i messages-per-connection]
        (let [start-time (System/currentTimeMillis)]
          (dotimes [i message-size] (b/push output-buffer (byte 0)))
          (write-message sink output-buffer message-size)
          (read-message source input-buffer message-size)
          (dotimes [i message-size] (b/pop input-buffer))
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

(defn benchmark-all []
  (for [clients [1 2 3 4]]
    (for [message-size [1 2 5 10 100]]
      (benchmark clients message-size))))

(defn -main [clients size]
  (println (benchmark (Integer/parseInt clients) (Integer/parseInt size)))
  (System/exit 0))
             
           
           
           
