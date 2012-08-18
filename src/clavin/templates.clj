(ns clavin.templates
  (:use [clavin.environments :only [envs-by-dep self-referenced-params]]
        [clojure.java.io :only [file]]
        [clojure.set :only [difference union]])
  (:require [clojure.string :as string])
  (:import [java.io FilenameFilter]
           [org.stringtemplate.v4 ST STErrorListener STGroup]
           [org.stringtemplate.v4.debug EvalExprEvent]))

(def ^:private placeholder-delim \$)

(def ^:private quoted-delim (str "\\Q" placeholder-delim "\\E"))

(def ^:private placeholder-re
  (re-pattern (str quoted-delim "(.*?)" quoted-delim)))

(defn- new-st-group
  []
  (STGroup. placeholder-delim placeholder-delim))

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

(defn- add-props
  [env st]
  (dorun (map (fn [[k v]] (.add st (string/replace (name k) "-" "_") v)) env)))

(defn gen-file
  [env template-dir template-name]
  (let [st (load-template template-dir template-name)]
    (add-props env st)
    (.render st)))

(defn- template-validation-error-listener
  [template]
  (letfn [(print-err [msg] (println template "is invalid:" (str msg)))]
    (proxy [STErrorListener] []
      (compileTimeError [msg] (print-err msg))
      (runTimeError [msg] (print-err msg))
      (IOError [msg] (print-err msg))
      (internalError [msg] (print-err msg)))))

(defn- template-validating-st-group
  [template-name]
  (doto (new-st-group)
    (.setListener (template-validation-error-listener template-name))))

(defn valid-template?
  [template-dir template-name]
  (let [st-group (template-validating-st-group template-name)]
    (try
      (load-template template-dir template-name st-group)
      true
      (catch Exception _ false))))

(defn validate-templates
  ([template-dir]
     (validate-templates template-dir (list-templates template-dir)))
  ([template-dir templates]
     (every? identity (map #(valid-template? template-dir %) templates))))

(defn- placeholder-validation-error-listener
  [env-path template valid?]
  (letfn [(base-msg  []
            (str "validation of " @template " for " env-path " failed:"))
          (print-err [msg]
            (dosync (ref-set valid? false))
            (println (base-msg) (str msg)))]
    (proxy [STErrorListener] []
      (compileTimeError [msg] (print-err msg))
      (runTimeError [msg] (print-err msg))
      (IOError [msg] (print-err msg))
      (internalError [msg] (print-err msg)))))

(defn- placeholder-validating-st-group
  [env-path template valid?]
  (doto (new-st-group)
    (.setListener
     (placeholder-validation-error-listener env-path template valid?))))

(defn- find-used-params-in-template
  [st-group template-dir template env]
  (let [st        (load-template template-dir @template st-group)
        _         (add-props env st)
        events    (.getEvents st)]
    (->> events
         (filter #(instance? EvalExprEvent %))
         (map #(.expr %))
         (map #(re-matches placeholder-re %))
         (filter identity)
         (map second)
         (map #(string/replace % "_" "-"))
         (map keyword))))

(defn- find-used-params
  [st-group template-dir template env]
  (set (mapcat
        (fn [template-name]
          (dosync (ref-set template template-name))
          (find-used-params-in-template st-group template-dir template env))
        (list-templates template-dir))))

(defn- validate-placeholders-for
  [tmpl-dir [env-name dep env]]
  (let [env-path      (str env-name "." dep)
        valid?        (ref true)
        template      (ref nil)
        st-group      (placeholder-validating-st-group env-path template valid?)
        used-params   (union (self-referenced-params env)
                             (find-used-params st-group tmpl-dir template env))
        unused-params (difference (set (keys env)) used-params)]
    (when-not (empty? unused-params)
      (println "Unused parameters were detected in" (str env-path ":"))
      (dorun (map (partial println "\t") (sort unused-params))))
    (and @valid? (empty? unused-params))))

(defn validate-placeholders
  [template-dir envs]
  (or (nil? envs)
      (every? identity
              (doall (map #(validate-placeholders-for template-dir %)
                          (envs-by-dep envs))))))

(defn display-template-list
  [template-dir]
  (dorun (map println (list-templates template-dir))))
