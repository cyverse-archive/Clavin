(defproject clavin "1.0.0-SNAPSHOT"
  :description "A command-line tool for loading service configurations into Zookeeper."
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [org.clojure/tools.cli "0.2.1"]
                 [org.iplantc/clojure-commons "1.1.0-SNAPSHOT"]
                 [zookeeper-clj "0.9.1"]]
  :dev-dependencies [[org.iplantc/lein-iplant-rpm "1.1.0-SNAPSHOT"]]
  :iplant-rpm {:summary "Clavin"
               :type :command
               :release 1
               :provides "iplant-clavin"}
  :aot [clavin.core]
  :main clavin.core
  :repositories {"iplantCollaborative"
                 "http://projects.iplantcollaborative.org/archiva/repository/internal/"})
