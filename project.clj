(defproject clavin "1.0.0-SNAPSHOT"
  :description "FIXME: write description"
  :dependencies [[org.clojure/clojure "1.3.0"]
                 [org.clojure/tools.cli "0.2.1"]
                 [org.iplantc/clojure-commons "1.1.0-SNAPSHOT"]
                 [zookeeper-clj "0.9.1"]]
  :aot [clavin.core]
  :main clavin.core)
