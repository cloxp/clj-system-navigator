(ns rksm.system-navigator.ns-internals
    (:require [clojure.data.json :as json]
              [clojure.repl :as repl]))

(defn source-for-symbol [sym]
  (try
    (with-out-str (eval `(repl/source ~sym)))
    (catch Exception e (str "Error retrieving source: " e))))

(defn intern-info
  [intern-var]
    (let [data (meta intern-var)]
      (if-let [ns (:ns data)]
        (let [name (:name data)
              ns-name (.name ns)
              tag (if-let [tag (:tag data)] (str tag))
              source (source-for-symbol (symbol (format "%s/%s" ns-name name)))]
            (merge data {:ns ns-name :tag tag :source source})))))

(defn namespace-info
  [ns]
  (->> (ns-interns ns)
       (map #(-> % val intern-info))
       (filter boolean)))

(defn stringify [obj]
  (cond
    (var? obj) (:name (meta obj))
    (or (string? obj) (symbol? obj) (keyword? obj)) (name obj)
    :else (str obj)))

(defn namespace-info->json [ns]
  (json/write-str (namespace-info ns)
      :key-fn stringify
      :value-fn (fn [_ val] (stringify val))))

; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(comment
  (source-for-symbol 'rksm.system-navigator.test.dummy-1/x)
  (namespace-info 'rksm.system-navigator.test.dummy-1)
  (namespace-info->json 'rksm.system-navigator.test.dummy-1)
  )
