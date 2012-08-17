(ns clavin.templates
  (:use [clavin.environments :only [envs-by-dep]]
        [clojure.java.io :only [file]])
  (:require [clojure.string :as string])
  (:import [java.io FilenameFilter]
           [org.stringtemplate.v4 ST STErrorListener STGroup]))

(defn- new-st-group
  []
  (STGroup. \$ \$))

(defn load-template
  ([template-dir template-name]
     (load-template template-dir template-name (new-st-group)))
  ([template-dir template-name template-group]
     (let [template-name (str template-name ".st")
           template-file (file template-dir template-name)]
       (ST. template-group (slurp template-file)))))

(defn list-templates
  [template-dir]
  (map
   #(string/replace % #"[.]st\z" "")
   (seq (.list (file template-dir)
               (proxy [FilenameFilter] []
                 (accept [dir filename]
                   (not (nil? (re-find #"[.]st\z" filename)))))))))

(defn- validation-error-listener
  [template]
  (letfn [(print-err [msg] (println template "is invalid:" msg))]
    (proxy [STErrorListener] []
     (compileTimeError [msg] (print-err msg))
     (runTimeError [msg] (print-err msg))
     (IOError [msg] (print-err msg))
     (nternalError [msg] (print-err msg)))))

(defn- validating-st-group
  [template-name]
  (doto (new-st-group)
    (.setListener (validation-error-listener template-name))))

(defn valid-template?
  [template-dir template-name]
  (let [st-group (validating-st-group template-name)]
    (try
      (load-template template-dir template-name st-group)
      true
      (catch Exception _ false))))

(defn validate-templates
  ([template-dir]
     (validate-templates template-dir (list-templates template-dir)))
  ([template-dir templates]
     (if (every? identity (map #(valid-template? template-dir %) templates))
       (println "All templates are valid.")
       (println "Errors were found."))))

(defn display-template-list
  [template-dir]
  (dorun (map println (list-templates template-dir))))
