(ns clavin.zk
  (:require [clojure.string :as string]
            [clojure-commons.file-utils :as ft]
            [zookeeper :as zk]
            [zookeeper.data :as data]))

(def zk-host (atom ""))
(def zk-port (atom ""))

(def ^:dynamic zkcl nil)

(defn init
  [host port]
  (reset! zk-host host)
  (reset! zk-port port))

(defmacro with-zk
  [& body]
  `(let [cl# (zk/connect (str @zk-host ":" @zk-port))]
     (binding [zkcl cl#]
       (try (do ~@body)
         (finally (zk/close zkcl))))))

(defn- split-path
  "Splits paths into Zookeeper node paths."
  [^String npath]
  (let [npaths (string/split npath #"\/")
        all-nodes npaths]
    (into 
      [] 
      (for [idx (range 2 (+ (count all-nodes) 1))]
        (apply ft/path-join (take idx all-nodes))))))

(defn mk-node
  "Creates all nodes in npath. Must be called within a with-zk block."
  [npath]
  (let [npaths (split-path npath)]
    (loop [nps npaths]
      (when-not (zk/exists zkcl (first nps))
        (zk/create zkcl (first nps) :persistent? true))
      (if (rest nps)
        (recur (rest nps))))))

(defn set-node
  "Set the data for a node. Assumes that the node already exists."
  [npath data]
  (let [data-version (:version (zk/exists zkcl npath))]
    (zk/set-data zkcl npath (.getBytes data) data-version)))

(defn read-node
  "Reads the bytes from a node and returns them as a string."
  [npath]
  (-> (zk/data zkcl npath) data/to-string))

(defn rm-node
  "Removes the last node in the path and all sub-nodes."
  [npath]
  (when (zk/exists zkcl npath)
    (zk/delete-all zkcl npath)))

(defn node-paths
  "Assumes a node-path in the format '/env/service/property-name'."
  [node-path]
  (let [all-nodes (rest (string/split node-path #"\/"))]
    (into 
      [] 
      (for [idx (range 1 (+ (count all-nodes) 1))]
        (let [rooted-join (partial ft/path-join "/")]
          (apply rooted-join (take idx all-nodes)))))))

(defn exists?
  [node-name]
  (if (zk/exists zkcl node-name) true false))

(defn create
  [node-name]
  (zk/create zkcl node-name :persistent? true))

(defn create-nodes
  [node-path]
  (let [all-nodes (node-paths node-path)]
    (loop [nodes all-nodes]
      (when (first nodes)
        (if (not (exists? (first nodes)))
          (create (first nodes))))
      (if (> (count (rest nodes)) 0) 
        (recur (rest nodes))))))

(defn set-value
  [node-name node-value]
  (when-not (exists? node-name)
    (create node-name))
  
  (let [version (:version (zk/exists zkcl node-name))
        data    (.getBytes node-value "UTF-8")]
    (zk/set-data zkcl node-name data version)))

(defn get-value
  [node-name]
  (if (not (exists? node-name)) nil
    (data/to-string (:data (zk/data zkcl node-name)))))

(defn delete-all
  [node-name]
  (zk/delete-all zkcl node-name))

(defn env-host-acl
  [hostname]
  (zk/ip-acl hostname :read :write))

(defn admin-host-acl
  [hostname]
  (zk/ip-acl hostname :read :write :delete :create :admin))

(defn set-acl
  [node-path acls]
  (try
    (let [version (:aversion (zk/exists zkcl node-path))]
      (.setACL zkcl node-path acls version))
    (catch java.lang.InterruptedException ie
      (throw ie))
    (catch org.apache.zookeeper.KeeperException ke
      (throw ke))
    (catch org.apache.zookeeper.KeeperException$InvalidACLException iae
      (throw iae))
    (catch java.lang.IllegalArgumentException ae
      (throw ae))))

(defn add-acl
  [node-path acls]
  (let [curr-acls (zk/get-acl zkcl node-path)]
    (set-acl node-path (concat (:acl curr-acls) acls))))
