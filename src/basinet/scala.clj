(ns basinet.scala
  (:refer-clojure :exclude [apply]))

(defn option->nullable [^scala.Option option] (if (not (.isEmpty option)) (.get option)))

(defn apply [obj] (.apply obj))
(defmacro object [class] (symbol (str (name class) \$) "MODULE$"))

