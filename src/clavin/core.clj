(ns clavin.core
  (:gen-class)
  (:require [clojure.tools.cli :as cli]
            [clavin.loader :as loader]
            [clavin.zk :as zk]
            [clojure-commons.file-utils :as ft]
            [clojure.string :as string]
            [clojure-commons.props :as ccprops]))

(defn parse-args
  [args]
  (cli/cli 
    args 
    ["-h" "--help" "Show help." :default false :flag true]
    ["--dir" "Read all of the configs from this directory." :default nil]
    ["--file" "Read in a specific file." :default nil]
    ["--host" "The Zookeeper host to connection to." :default nil]
    ["--port" "The Zookeeper client port to connection to." :default 2181 :parse-fn #(Integer. %)]
    ["--acl"  "The file containing Zookeeper hostname ACLs." :default nil]
    ["-a" "--app" "The application the settings are for." :default nil]
    ["-e" "--env" "The environment that the options should be entered into." :default nil]
    ["-d" "--deployment" "The deployment inside the environment that is being configured." :default nil]))

(defn parse-hosts-args
  [args]
  (cli/cli 
    args
    ["-h" "--help" "Shop help." :default false :flag true]
    ["--acl"  "The file containing Zookeeper hostname ACLs." :default nil]
    ["--host" "The Zookeeper host to connection to." :default nil]
    ["--port" "The Zookeeper client port to connection to." :default 2181 :parse-fn #(Integer. %)]))

(defn handle-hosts
  [args-vec]
  (let [[opts args help-str] (parse-hosts-args args-vec)]
    (when (:help opts)
      (println help-str)
      (System/exit 0))
    
    (cond
      (not (:acl opts))
      (do (println "--acl is required.")
        (println help-str)
        (System/exit 1))
      
      (not (:host opts))
      (do (println "--host is required.")
        (println help-str)
        (System/exit 1))
      
      (not (ft/exists? (:acl opts)))
      (do (println "--acl must reference an existing file.")
        (println help-str)
        (System/exit 1)))
    
    (println (str "Connecting to Zookeeper instance at " (:host opts) ":" (:port opts)))
    (zk/init (:host opts) (:port opts))
    
    (let [acl-props (ccprops/read-properties (:acl opts))]
      (when (not (loader/can-run? acl-props))
        (println "This machine isn't listed as an admin machine in " (:acl opts))
        (System/exit 1))
      
      (when (not (loader/is-localhost-and-admin? (:host opts) acl-props))
        (println "This machine is connecting to a local Zookeeper instance, but 127.0.0.1 isn't in the admin machines list.")
        (System/exit 1))
      
      (println "Starting to load hosts.")
      (loader/load-hosts acl-props)
      (println "Done loading hosts.")
      (System/exit 0))))

(defn handle-properties
  [args-vec]
  (let [[opts args help-str] (parse-args args-vec)]
    (when (:help opts)
      (println help-str)
      (System/exit 0))
    
    (cond
      (and (not (:dir opts)) (not (:file opts)))
      (do (println "Either --dir or --file must be set.")
        (println help-str)
        (System/exit 1))
      
      (and (:dir opts) (:file opts))
      (do (println "Provide either --dir or --file, not both.")
        (println help-str)
        (System/exit 1))
      
      (not (:acl opts))
      (do (println "--acl is required.")
        (println help-str)
        (System/exit 1))
      
      (not (:host opts))
      (do (println "--host is required.")
        (println help-str)
        (System/exit 1))
      
      (not (:env opts))
      (do (println "--env is required.")
        (println help-str)
        (System/exit 1))
      
      (not (:deployment opts))
      (do (println "--deployment is required.")
        (println help-str)
        (System/exit 1))
      
      (not (:app opts))
      (do (println "--app is required.")
        (println help-str)
        (System/exit 1))
      
      (not (ft/exists? (:acl opts)))
      (do (println "--acl must reference an existing file.")
        (println help-str)
        (System/exit 1)))

    (println (str "Connecting to Zookeeper instance at " (:host opts) ":" (:port opts)))
    (zk/init (:host opts) (:port opts))
    
    (let [fpath     (if (:dir opts) (:dir opts) (:file opts))
          app       (string/trim (:app opts))
          env       (string/trim (:env opts))
          dep       (string/trim (:deployment opts))
          acl-props (ccprops/read-properties (:acl opts))]
      
      (when (not (loader/can-run? acl-props))
        (println "This machine isn't listed as an admin machine in " (:acl opts))
        (System/exit 1))
      
      ;;This is weird. If you're connected to a Zookeeper instance that's running locally,
      ;;you need to have 127.0.0.1 listed in the admin list even if you're explicitly
      ;;connecting via an externally facing IP. Weird, weird, weird. Seems to be something
      ;;internal to Zookeeper that causes it.
      (when (not (loader/is-localhost-and-admin? (:host opts) acl-props))
        (println "This machine is connecting to a local Zookeeper instance, but 127.0.0.1 isn't in the admin machines list.")
        (System/exit 1))
      
      (println (str "Starting to load data into the " (str app "." env) " environment..."))
      
      (let [acls (loader/load-acls app env dep acl-props)]
        (loader/load-settings app env dep fpath acls))
      
      (println (str "Done loading data into the " (str app "." env) " environment."))
      (System/exit 0))))

(defn -main
  [& args-vec]
  (let [cmd        (first args-vec)
        known-cmds ["props" "hosts" "help"]
        help-str   "clavin props|hosts|help [options]\nEach command has its own --help."]
    
    (cond
      (not (contains? (set known-cmds) cmd))
      (do (println (str "Unknown command: " cmd)) 
        (println help-str)
        (System/exit 1))
      
      (= cmd "help")
      (do (println help-str)
        (System/exit 0))
      
      (= cmd "props")
      (handle-properties (into [] (drop 1 args-vec)))
      
      (= cmd "hosts")
      (handle-hosts (into [] (drop 1 args-vec)))
      
      :else
      (do (println "Something weird happened.")
        (println help-str)
        (System/exit 1)))))
