(ns rksm.system-navigator.ns.internals
    (:require [clojure.data.json :as json]
              [clojure.repl :as repl]
              [clojure.tools.reader :as tr]
              [rksm.system-navigator.ns.filemapping :refer (file-for-ns source-reader-for-ns)])
    (:import (java.io LineNumberReader InputStreamReader PushbackReader)
             (clojure.lang LineNumberingPushbackReader)
           (clojure.lang RT)))


; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
; source retrieval

(defn- read-next-obj
  "follows the reader while it core/reads an object and returns the string in
  range of what was read"
  [rdr]
  (let [text (StringBuilder.) 
        pbr (proxy [PushbackReader] [rdr]
                   (read []
                         (let [i (proxy-super read)]
                           (if (> i -1)(.append text (char i)))
                           i)))]
    (if (= :unknown *read-eval*)
      (throw (IllegalStateException. "Unable to read source while *read-eval* is :unknown."))
      (tr/read (PushbackReader. pbr) false nil))
    (str text)))

(defn- read-entity-source
  "goes forward in line numbering reader until line of entity is reached and
  reads that as an object"
  [{lrdr :lrdr sources :sources} meta-entity]
  (or (if-let [line (:line meta-entity)]
        (do
          (dotimes [_ (dec (- line (.getLineNumber lrdr)))] (.readLine lrdr))
          {:lrdr lrdr
           :sources (conj sources
                          (assoc meta-entity
                                 :source
                                 (read-next-obj lrdr)))}))
      {:lrdr lrdr :sources sources}))

(defn add-source-to-interns-with-reader
  [rdr interns]
  (let [source-data {:lrdr (LineNumberReader. rdr) :sources []}]
    (or (some->> interns
          (reduce read-entity-source source-data)
          :sources)
        interns)))

(defn add-source-to-interns
  [ns interns]
  (if-let [rdr (source-reader-for-ns ns)]
    (with-open [rdr rdr]
      (add-source-to-interns-with-reader rdr interns))
    interns))

; (defn source-for-symbol [sym]
;   (try
;     (with-out-str (eval `(source-retrieval ~sym)))
;     (catch Exception e (str "Error retrieving source: " e))))

(defn source-for-symbol [sym]
  (try (clojure.repl/source-fn sym)
    (catch Exception e "")))

; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
; namespace / meta data interface

(defn protocol-info
  [intern-info]
  (if-let [p (:protocol intern-info)]
    {:protocol (meta p)} {}))

(defn intern-info
  [intern-meta]
  (if-let [ns (:ns intern-meta)]
    (let [name (:name intern-meta)
          ns-name (.name ns)
          tag (if-let [tag (:tag intern-meta)] (str tag))]
      (merge intern-meta
             {:ns ns-name :tag tag}
             (protocol-info intern-meta)))))

(defn namespace-info
  [ns]
  (->> (ns-interns ns)
    (map #(-> % val meta intern-info))
    (sort-by :line)
    (add-source-to-interns ns)
    (filter boolean)))

(defn symbol-info
  [ns sym]
  (intern-info
   (ns-resolve ns sym)))

(defn stringify [obj]
  (cond
    (var? obj) (:name (meta obj))
    (or (string? obj) (symbol? obj) (keyword? obj)) (name obj)
    :else (str obj)))

(defn symbol-info->json
  [ns sym]
  (json/write-str (symbol-info ns sym)
                  :key-fn stringify
                  :value-fn (fn [_ val] (stringify val))))

(defn namespace-info->json [ns]
  (json/write-str (namespace-info ns)
      :key-fn stringify
      :value-fn (fn [_ val] (stringify val))))

; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(comment
  (source-for-symbol 'rksm.system-navigator.test.dummy-1/x)
  (rksm.system-navigator.ns.internals/namespace-info->json 'rksm.system-navigator.search)
  (namespace-info 'rksm.system-navigator.search)
  (namespace-info 'rksm.system-navigator.test.dummy-1)
  (namespace-info->json 'rksm.system-navigator.test.dummy-1)
  )
