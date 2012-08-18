(ns clavin.generator
  (:use [clojure.java.io :only [file]]
        [clavin.environments
         :only [load-envs envs-valid? replace-placeholders]]
        [clavin.templates :only [gen-file]])
  (:require [clojure.string :as string])
  (:import [java.io FilenameFilter StringReader]
           [java.util Properties]
           [org.stringtemplate.v4 ST]))

(defn- write-file
  "Creates a properties file for a template and environment."
  [env template-dir template-name dest-dir]
  (let [dest-file (file dest-dir (str template-name ".properties"))]
    (print "Writing" (.getPath dest-file) "...")
    (spit dest-file (gen-file env template-dir template-name))
    (println "done.")))

(defn- gen-props
  "Generates the text for a properties file.  Once the text has been generated,
   it can be written to disk or loaded directly into a java.util.Properties
   instance."
  [env template-dir template-name]
  (doto (Properties.)
    (.load (StringReader. (gen-file env template-dir template-name)))))

(defn generate-props
  "Generates configuration properties for an environment and template name."
  [env template-dir template-name]
  (let [env (replace-placeholders env)]
    (gen-props env template-dir template-name)))

(defn generate-all-props
  "Generates properties for one or more templates."
  [env template-dir template-names]
  (let [env (replace-placeholders env)]
    (into {} (map #(vector % (gen-props env template-dir %)) template-names))))

(defn generate-file
  "Generates a properties file in the provided destination directory."
  [env template-dir template-name dest-dir]
  (let [env (replace-placeholders env)]
    (write-file env template-dir template-name dest-dir)))

(defn generate-all-files
  "Generates properties files for one or more templates in the provided
   destination directory."
  [env template-dir template-names dest-dir]
  (let [env (replace-placeholders env)]
    (dorun (map #(write-file env template-dir % dest-dir) template-names))))
