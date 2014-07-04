(ns basinet.scala)

(defn option->nullable [^scala.Option option] (if (not (.isEmpty option)) (.get option)))

