(ns basinet.tcp-test
  (:require [khazad-dum :refer :all]
            [basinet :as b]
            [basinet.tcp :as tcp])
  (:import [basinet Socket]))

(defmacro with-tcp [[acceptor connector] & body]
  `(with-open [~acceptor (tcp/acceptor "localhost" 12345)
               ~connector (tcp/connector "localhost" 12345)]
     ~@body))

(deftest reading-from-acceptor-and-connector-returns-socket
  (with-tcp [a c]
    (with-open [ssocket (b/pop a)
                csocket (b/pop c)]
      (?true (instance? Socket ssocket))
      (?true (instance? Socket csocket)))))

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

(deftest connector-and-acceptor-addresses
  (let [hostname (.getHostName (java.net.InetAddress/getLocalHost))]
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
      (?true (instance? Socket socket)))))

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

;;
;; Tcp sockets
;;

;; tcp socket is a pair of NIOByteSource, NIOByteSink
;; closing is OK (can close read end and leave writing end working
;; have address/remote address


  
