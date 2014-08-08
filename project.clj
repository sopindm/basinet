(defproject basinet "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :plugins [[io.tomw/lein-scalac "0.1.2"]]
  :profiles {:dev {:dependencies [[khazad-dum "0.2.0"]]
                   :repl-options {:init (use 'khazad-dum)}}
             :benchmark {:aot :all :jvm-opts ["-server"]}}
  :source-paths ["src/"]
  :java-source-paths ["scala/"]
  :prep-tasks ["javac" "scalac"]
  :scala-source-path "scala"
  ;:jvm-opts ["-server"]
  :scala-version "2.11.1"
  :scalac-options {"addparams" "-feature"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.scala-lang/scala-library "2.10.1"]
                 [evil-ant "0.2.0-SNAPSHOT"]])
