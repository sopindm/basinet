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

(defn connector [address port & {:keys [local-host local-port]}]
  (let [channel (SocketChannel/open)]
    (when (and local-host local-port)
      (.bind channel (java.net.InetSocketAddress. local-host local-port)))
  (TcpConnector. channel (InetSocketAddress. address port))))
    
(defn connect [address port & {:keys [local-host local-port]}]
  (with-open [connector (connector address port :local-host local-host :local-port local-port)]
    (basinet/pop connector)))

(defn local-address [channel] (scala/option->nullable (.localAddress channel)))
(defn remote-address [channel] (scala/option->nullable (.remoteAddress channel)))
  
