(ns basinet.tcp-test
  (:require [khazad-dum :refer :all]
            [basinet :as b]
            [basinet.tcp :as tcp]
            [evil-ant :as e])
  (:import [basinet Pipe]))

(defmacro with-tcp [[acceptor connector] & body]
  `(with-open [~acceptor (tcp/acceptor "localhost" 12345)
               ~connector (tcp/connector "localhost" 12345)]
     ~@body))

(deftest reading-from-acceptor-and-connector-returns-socket
  (with-tcp [a c]
    (with-open [ssocket (b/pop a)
                csocket (b/pop c)]
      (?true (instance? Pipe ssocket))
      (?true (instance? Pipe csocket)))))

(deftest popping-from-empty-acceptor-and-connector
  (with-open [a (tcp/acceptor "localhost" 12345 :backlog 1)]
    (?= (b/try-pop a) nil)
    (let [connectors (repeatedly 5 #(tcp/connector "localhost" 12345))
          sockets (doall (map #(b/try-pop %) connectors))]
      (?true (some nil? sockets))
      (doseq [c connectors] (.close c))
      (doseq [s sockets :when (not (nil? s))] (.close s)))))
    
(deftest connecting-to-wrong-address-error
  (?throws (b/try-pop (tcp/connector "localhost" 11111)) java.net.ConnectException))

(deftest closing-acceptor
  (with-tcp [a c]
    (.close a)
    (?false (b/open? a))
    (?throws (b/try-pop a) java.nio.channels.ClosedChannelException)))

(deftest closing-connector
  (with-tcp [a c]
    (.close c)
    (?false (b/open? c))
    (?throws (b/try-pop c) java.nio.channels.ClosedChannelException)))

(deftest accepting-several-times
  (with-tcp [a c]
    (with-open [s (b/try-pop a)] (?false (nil? s)))
    (with-open [c (tcp/connector "localhost" 12345)]
      (with-open [s (b/try-pop a)] (?false (nil? s))))))

(deftest successful-connector-is-closed
  (with-tcp [a c]
    (with-open [s (b/try-pop c)])
    (?false (b/open? c))))

(defmacro call-or-nil [field expr]
  `(if ~expr (~field ~expr)))

(defmacro ?address= [expr [local-host local-port local-ip] [remote-host remote-port remote-ip]]
  `(let [expr# ~expr]
     (let [local-address# (tcp/local-address expr#)]
       (?= (call-or-nil .host local-address#) ~local-host)
       (?= (call-or-nil .port local-address#) ~local-port)
       (?= (call-or-nil .ip local-address#) ~local-ip))
     (let [remote-address# (tcp/remote-address expr#)]
       (?= (call-or-nil .host remote-address#) ~remote-host)
       (?= (call-or-nil .port remote-address#) ~remote-port)
       (?= (call-or-nil .ip remote-address#) ~remote-ip))))

(defn- localhost [] (.getHostName (java.net.InetAddress/getLocalHost)))

(deftest connector-and-acceptor-addresses
  (let [hostname (localhost)]
    (with-open [a (tcp/acceptor "127.0.0.1" 12345)
                c (tcp/connector "127.0.0.1" 12345)]
      (?address= a [hostname 12345 "127.0.0.1"] nil)
      (?address= c nil [hostname 12345 "127.0.0.1"]))
  (with-open [a (tcp/acceptor "::1" 12345)
              c (tcp/connector "::1" 12345 :local-host "::1" :local-port 23456)]
    (?address= a ["ip6-localhost" 12345 "0:0:0:0:0:0:0:1"] nil)
    (?address= c ["ip6-localhost" 23456 "0:0:0:0:0:0:0:1"] ["ip6-localhost" 12345 "0:0:0:0:0:0:0:1"]))))

(deftest connect-function
  (with-open [acceptor (tcp/acceptor "localhost" 12345)]
    (with-open [socket (tcp/connect "localhost" 12345 :local-host "localhost" :local-port 23456)]
      (?true (instance? Pipe socket)))))

(deftest cannot-read-from-closed-acceptor
  (let [acceptor (tcp/acceptor "localhost" 12345)]
    (.close acceptor)
    (?throws (b/try-pop acceptor) java.nio.channels.ClosedChannelException)))

(deftest cannot-read-from-closed-connector
  (with-tcp [a c]
    (.close c)
    (?throws (b/try-pop c) java.nio.channels.ClosedChannelException)))

(deftest connector-closes-on-success
  (with-tcp [a c]
    (with-open [s (b/pop c)]
      (?false (b/open? c))
      (?true (b/open? s))))
  (with-tcp [a c]
    (with-open [s (b/pop c)]
      (.close c)
      (?true (b/open? s)))))

(deftest connector-on-poppable-event
  (with-tcp [a c]
    (let [s (b/signal-set c)]
      (?true (e/emit-now! s)))))

(deftest acceptor-on-poppable-event
  (with-open [a (tcp/acceptor "localhost" 12345)]
    (let [s (b/signal-set a)]
      (?false (e/emit-now! s))
      (with-open [c (tcp/connector "localhost" 12345)]
        (?true (e/emit-now! s))))))

;;
;; Tcp sockets
;;

(deftest tcp-socket-sink-and-source-types
  (with-tcp [a c]
    (with-open [s (b/pop c)]
      (?true (instance? basinet.nio.ByteSource (.source s)))
      (?true (instance? basinet.nio.ByteSink (.sink s))))
    (with-open [s (b/pop a)]
      (?true (instance? basinet.nio.ByteSource (.source s)))
      (?true (instance? basinet.nio.ByteSink (.sink s))))))

(deftest closing-tcp-socket-sink-and-source
  (with-tcp [a c]
    (with-open [s (b/pop c)]
      (.close (b/sink s))
      (?true (b/open? (b/source s)))
      (.close (b/source s))
      (?false (b/open? s)))
    (with-open [s (b/pop a)]
      (.close (b/source s))
      (?true (b/open? (b/sink s))))))

(deftest tcp-sockets-have-address-and-remote-address
  (with-open [acceptor (tcp/acceptor "localhost" 12345)
              connector (tcp/connector "localhost" 12345 :local-host "localhost" :local-port 23456)]
    (with-open [s (b/pop acceptor)]
      (?address= s [(localhost) 12345 "127.0.0.1"] [(localhost) 23456 "127.0.0.1"])
      (?address= (b/source s) [(localhost) 12345 "127.0.0.1"] [(localhost) 23456 "127.0.0.1"])
      (?address= (b/sink s) [(localhost) 12345 "127.0.0.1"] [(localhost) 23456 "127.0.0.1"]))))

(deftest closing-one-sockets-end-closes-another
  (with-tcp [a c]
    (with-open [client (b/pop c)
                server (b/pop a)]
      (-> client b/close)
      (?= (-> server b/source b/try-pop) nil)
      (?false (-> server b/source b/open?))
      (?false (-> server b/sink b/open?))))
  (with-tcp [a c]
    (b/close (b/pop a))
    (with-open [client (b/pop c)]
      (?= (-> client b/source (b/pop-in 1000)) nil)
      (?false (-> client b/source b/open?))
      (?false (-> client b/sink b/open?))))
  (with-tcp [a c]
    (b/close (b/pop a))
    (with-open [client (b/pop c)]
      (?throws (b/pop (b/source client)) java.nio.channels.ClosedChannelException)
      (?false (-> client b/source b/open?))
      (?false (-> client b/sink b/open?)))))

      
      
      
