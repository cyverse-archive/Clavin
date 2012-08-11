(ns clavin.generator
  (:use [clojure.java.io :only [file]]
        [clavin.environments
         :only [load-envs envs-valid? replace-placeholders]])
  (:require [clojure.string :as string])
  (:import [org.stringtemplate.v4 ST]))

(defn generate-props
  [env template-dir template-name]
  (let [st  (ST. (slurp (file template-dir (str template-name ".st"))) \$ \$)
        env (replace-placeholders env)]
    (dorun (map (fn [[k v]] (.add st (string/replace (name k) "-" "_") v)) env))
    (.render st)))
