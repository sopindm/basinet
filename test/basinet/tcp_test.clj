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

;; reading from acceptor/connector returns socket (pair of channels)
;; tryPop for empty acceptor/connector
;; connecting to wrong address gets error
;; accepting several times
;; successful connector is closed
;; connector/acceptor have address/remote address
;; connector/acceptor with IPv6
;; connect/accept function (make connector/acceptor, accept/connect, close acceptor/connector, return result)
;; cannot read from closed acceptor and connector

;;
;; Tcp sockets
;;

;; tcp socket is a pair of NIOByteSource, NIOByteSink
;; closing is OK (can close read end and leave writing end working
;; have address/remote address


