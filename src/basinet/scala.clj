(ns basinet.scala)

(defn option->nullable [option] (if (not (.isEmpty option)) (.get option)))

