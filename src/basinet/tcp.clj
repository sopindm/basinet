(ns basinet.tcp
  (:require [basinet.scala :as scala])
  (:import [basinet TcpAcceptor TcpConnector]
           [java.net InetSocketAddress]
           [java.nio.channels SocketChannel ServerSocketChannel]))

(defn acceptor [address port & {:keys [backlog]}]
  (let [channel (ServerSocketChannel/open)
        address (InetSocketAddress. address port)]
    (if backlog (.bind channel address backlog) (.bind channel address))
    (TcpAcceptor. channel)))

(defn connector [address port]
  (TcpConnector. (SocketChannel/open) (InetSocketAddress. address port)))
    
(defn local-address [channel] (scala/option->nullable (.localAddress channel)))
(defn remote-address [channel] (scala/option->nullable (.remoteAddress channel)))
  
