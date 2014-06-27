(ns basinet.tcp
  (:import [basinet TcpAcceptor TcpConnector]
           [java.net InetSocketAddress]
           [java.nio.channels SocketChannel ServerSocketChannel]))

(defn acceptor [address port]
  (TcpAcceptor. (doto (ServerSocketChannel/open)
                  (.bind (InetSocketAddress. address port)))))

(defn connector [address port]
  (TcpConnector. (SocketChannel/open) (InetSocketAddress. address port)))
    
