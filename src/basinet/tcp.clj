(ns basinet.tcp
  (:require [basinet :as b]
            [basinet.scala :as scala])
  (:import [basinet TcpAcceptor TcpConnector TcpAddressable TcpSocket]
           [java.net InetSocketAddress]
           [java.nio.channels SocketChannel ServerSocketChannel]))

(defn ^TcpAcceptor acceptor [address port & {:keys [backlog]}]
  (let [channel (ServerSocketChannel/open)
        address (InetSocketAddress. ^String address ^int port)]
    (if backlog (.bind channel address backlog) (.bind channel address))
    (TcpAcceptor. channel)))

(defn ^TcpConnector connector [address port & {:keys [local-host local-port]}]
  (let [channel (SocketChannel/open)]
    (when (and local-host local-port)
      (.bind channel (java.net.InetSocketAddress. ^String local-host ^int local-port)))
  (TcpConnector. channel (InetSocketAddress. ^String address ^int port))))
    
(defn ^TcpSocket connect [address port & {:keys [local-host local-port]}]
  (with-open [connector (connector address port
                                   :local-host ^String local-host
                                   :local-port ^String local-port)]
    (b/pop connector)))

(defn local-address [^TcpAddressable channel] (scala/option->nullable (.localAddress channel)))
(defn remote-address [^TcpAddressable channel] (scala/option->nullable (.remoteAddress channel)))
  
