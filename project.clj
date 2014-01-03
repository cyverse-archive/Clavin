(defproject clavin "1.3.2-SNAPSHOT"
  :description "A command-line tool for loading service configurations into Zookeeper."
  :dependencies [[org.antlr/stringtemplate "4.0.2"]
                 [org.clojure/clojure "1.5.1"]
                 [org.clojure/tools.cli "0.2.1"]
                 [org.iplantc/clojure-commons "1.4.1-SNAPSHOT"]
                 [zookeeper-clj "0.9.1"]]
  :plugins [[org.iplantc/lein-iplant-cmdtar "0.1.2-SNAPSHOT"]
            [org.iplantc/lein-iplant-rpm "1.4.1-SNAPSHOT"]]
  :iplant-rpm {:summary "Clavin"
               :type :command
               :provides "iplant-clavin"}
  :aot [clavin.core]
  :main clavin.core
  :repositories {"iplantCollaborative"
                 "http://projects.iplantcollaborative.org/archiva/repository/internal/"})
