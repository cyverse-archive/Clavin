(ns clavin.generator
  (:use [clojure.java.io :only [file]]
        [clavin.environments
         :only [load-envs envs-valid? replace-placeholders]])
  (:require [clojure.string :as string])
  (:import [java.io FilenameFilter StringReader]
           [java.util Properties]
           [org.stringtemplate.v4 ST]))

(defn load-template
  [template-dir template-name]
  (let [template-name (str template-name ".st")
        template-file (file template-dir template-name)]
    (ST. (slurp template-file) \$ \$)))

(defn- gen-file
  [env template-dir template-name]
  (let [st (load-template template-dir template-name)]
    (dorun (map (fn [[k v]] (.add st (string/replace (name k) "-" "_") v)) env))
    (.render st)))

(defn- write-file
  [env template-dir template-name dest-dir]
  (let [dest-file (file dest-dir (str template-name ".properties"))]
    (print "Writing" (.getPath dest-file) "...")
    (spit dest-file (gen-file env template-dir template-name))
    (println "done.")))

(defn- gen-props
  [env template-dir template-name]
  (doto (Properties.)
    (.load (StringReader. (gen-file env template-dir template-name)))))

(defn generate-props
  [env template-dir template-name]
  (let [env (replace-placeholders env)]
    (gen-props env template-dir template-name)))

(defn generate-all-props
  [env template-dir template-names]
  (let [env (replace-placeholders env)]
    (into {} (map #(vector % (gen-props env template-dir %)) template-names))))

(defn generate-file
  [env template-dir template-name dest-dir]
  (let [env (replace-placeholders env)]
    (write-file env template-dir template-name dest-dir)))

(defn generate-all-files
  [env template-dir template-names dest-dir]
  (let [env (replace-placeholders env)]
    (dorun (map #(write-file env template-dir % dest-dir) template-names))))

(defn list-templates
  [template-dir]
  (map
   #(string/replace % #"[.]st\z" "")
   (seq (.list (file template-dir)
               (proxy [FilenameFilter] []
                 (accept [dir filename]
                   (not (nil? (re-find #"[.]st\z" filename)))))))))
