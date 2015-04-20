(ns rksm.system-navigator.ns.internals
  (:require [clojure.data.json :as json]
            [clojure.repl :as repl]
            [clojure.string :as s]
            [rksm.system-navigator.changesets :as cs]
            [rksm.system-files :refer (ns-name->rel-path file-name-for-ns)]
            [rksm.cloxp-source-reader.core :refer [add-source-to-interns]])
  (:import (java.io LineNumberReader InputStreamReader PushbackReader)
           (clojure.lang RT)))


(declare intern-info symbol-info)

; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(defn file-source-for-sym
  [sym & [file-path]]
  (let [ns-sym (-> (.getNamespace sym) symbol find-ns ns-name)
        interns [(intern-info (meta (find-var sym)))]
        with-source (add-source-to-interns ns-sym interns {:file file-path})]
    (:source (first with-source))))

(defn source-for-symbol
  "This method uses the RT/baseloader to lookup the files belonging to symbols.
  When files get reloaded / defs redefined this can mean that the code being
  retrieved is outdated"
  [sym & [ns-file-path]]
  (or
   (let [ns (or (some-> (namespace sym) symbol) 'user)]
     (-> (add-source-to-interns
          ns [(symbol-info ns (-> sym name symbol))]
          {:file ns-file-path, :cljx true})
       first :source))
   (try (clojure.repl/source-fn sym)
     (catch Exception e nil))))

; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
; namespace / meta data interface

(defn protocol-info
  [intern-info]
  (if-let [p (:protocol intern-info)]
    {:protocol (meta p)} {}))

(def intern-keys [:private
                  :ns :name
                  :file :column :line :end-column :end-line
                  :tag :arglists
                  :macro :source])

(defn intern-info
  [{:keys [ns name tag] :as intern-meta}]
  (if ns
    (let [intern-meta (select-keys intern-meta intern-keys)
          ns-name (.name ns)
          tag (if tag (str tag))]
      (merge intern-meta
             {:ns ns-name :tag tag}
             (protocol-info intern-meta)))))

(defn symbol-info
  [ns sym & [file-path]]
  (-> (ns-resolve ns sym)
    meta
    intern-info))

(defn namespace-info
  [ns & [file-name]]
  {:file file-name
   :interns  (->> (ns-interns ns)
               (map #(-> % val meta intern-info))
               (filter #(not= (:file %) "NO_SOURCE_PATH"))
               (sort-by :line)
               ; (add-source-to-interns ns)
               ; (add-source-to-interns-from-repl ns)
               (filter boolean))})

(defn stringify [obj]
  (cond
    (var? obj) (:name (meta obj))
    (or (string? obj) (symbol? obj) (keyword? obj)) (name obj)
    (or (seq? obj)) (vec obj)
    (or (map? obj)) obj
    ; it seems that the value-fn in json/write is not called for every value
    ; individually, only for map / collection like things... so to filter out
    ; objects that couldn't be stringified to start with we have this map stmt in
    ; here...
    (coll? obj) (map #(if (some boolean ((juxt map? coll?) %)) % (stringify %)) obj)
    :else (str obj)))

(defn symbol-info->json
  [ns sym]
  (json/write-str (symbol-info ns sym)
                  :key-fn #(if (keyword? %) (name %) (str %))
                  :value-fn (fn [_ x] (stringify x))))

(defn namespace-info->json [ns & [file-path]]
  (json/write-str (namespace-info ns file-path)
                  :key-fn #(if (keyword? %) (name %) (str %))
                  :value-fn (fn [_ x] (stringify x))))


; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(comment
  (source-for-symbol 'rksm.system-navigator.test.dummy-1/x)
  (rksm.system-navigator.ns.internals/namespace-info->json 'rksm.system-navigator.search)
  (namespace-info 'rksm.system-navigator.test.dummy-1)
  )
