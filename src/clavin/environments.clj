(ns clavin.environments
  (:use [clojure.java.io :only [reader]]
        [clojure.set :only [union intersection difference]])
  (:require [clojure.string :as string])
  (:import [java.io PushbackReader]))

(defn load-envs
  "Loads the environment settings from a specified file."
  [filename]
  (with-open [r (PushbackReader. (reader filename))]
    (binding [*read-eval* false]
      (read r))))

(defn replace-placeholders
  "Replaces placeholders in an environment with the actual values."
  [env]
  (letfn [(gval [k]
            (let [v (env k)]
              (if (nil? v)
                (throw (Exception. (str "bad placeholder: " v)))
                v)))
          (rep [v]
            (string/replace v #"\$\{([^\}]+)\}"
                            #(rep (gval (keyword (second %))))))]
    (into {} (map (fn [[k v]] [k (rep v)]) env))))

(defn- keyset
  "Obtains a set containing the keys of a map."
  [m]
  (set (keys m)))

(defn- envs-list
  "Grabs the property mapping objects from the environment listings."
  [envs]
  (mapcat vals (vals envs)))

(defn envs-valid?
  "Verifies that all of the environments have the same set of properties
   defined."
  [envs]
  (apply = (map keyset (envs-list envs))))

(defn- invalid-keys
  "Determines which keys are invalid (that is, not defined in all environments)
   in the defined environments."
  [envs]
  (let [env-keys (map keyset (envs-list envs))]
    (difference (apply union env-keys) (apply intersection env-keys))))

(defn show-envs-invalid-msg
  "Prints a message indicating that an environment file is not valid."
  [envs filename]
  (println filename "is not valid.")
  (println)
  (println "Please check the following properties:")
  (dorun (map #(println (str "\t" %)) (sort (invalid-keys envs)))))

(defn validate-envs
  "Ensures that the environments all have the same set of keys defined."
  [filename]
  (let [envs (load-envs filename)]
    (if (envs-valid? envs)
      (println filename "is valid.")
      (show-envs-invalid-msg envs filename))))

(defn- env-names
  "Obtains the list of environment names from the environments map."
  [envs]
  (mapcat (fn [[k v]] (map #(map name [k (key %)]) v)) envs))

(defn list-envs
  "Lists all of the environments defined in an environment file."
  [filename]
  (let [hdrs  ["environment" "deployment"]
        envs  (load-envs filename)
        names (env-names envs)
        width (apply max (map count (apply concat (conj names hdrs))))
        sep   (apply str (take width (repeat "-")))
        fcol  (fn [v w] (apply str v (take (- w (count v)) (repeat " "))))
        fcols (fn [vs w] (map #(fcol % w) vs))]
    (apply println (fcols hdrs width))
    (apply println (fcols [sep sep] width))
    (dorun (map (partial apply println) (map #(fcols % width) names)))))

(defn env-for-dep
  "Determines the name of the environment associated with a deployment name."
  [envs dep]
  (let [names (map first (filter #(= dep (second %)) (env-names envs)))]
    (when (empty? names)
      (throw (Exception. (str "no environment found for deployment " dep))))
    (when (> (count names) 1)
      (throw
       (Exception. (str "multiple environments found for deployment " dep))))
    (first names)))
