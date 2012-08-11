(defproject clavin "1.1.0-SNAPSHOT"
  :description "A command-line tool for loading service configurations into Zookeeper."
  :dependencies [[org.antlr/stringtemplate "4.0.2"]
                 [org.clojure/clojure "1.4.0"]
                 [org.clojure/tools.cli "0.2.1"]
                 [org.iplantc/clojure-commons "1.1.0-SNAPSHOT"]
                 [zookeeper-clj "0.9.1"]]
  :plugins [[org.iplantc/lein-iplant-rpm "1.3.0-SNAPSHOT"]]
  :iplant-rpm {:summary "Clavin"
               :type :command
               :provides "iplant-clavin"}
  :aot [clavin.core]
  :main clavin.core
  :repositories {"iplantCollaborative"
                 "http://projects.iplantcollaborative.org/archiva/repository/internal/"})
