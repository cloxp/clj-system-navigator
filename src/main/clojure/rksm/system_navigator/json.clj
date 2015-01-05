(ns rksm.system-navigator.json
  (:require [clojure.data.json :as json]))

(defn stringify [obj]
  (cond
    (var? obj) (:name (meta obj))
    (or (string? obj) (symbol? obj) (keyword? obj)) (name obj)
    :else (str obj)))

(defn json-str
  [obj]
  (json/write-str obj
                  :key-fn stringify
                  :value-fn (fn [_ val] (stringify val))))