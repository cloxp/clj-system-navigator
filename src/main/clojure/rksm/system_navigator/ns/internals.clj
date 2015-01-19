(ns rksm.system-navigator.ns.internals
  (:require [clojure.data.json :as json]
            [clojure.repl :as repl]
            [clojure.tools.reader :as tr]
            [clojure.tools.reader.reader-types :as trt]
            [clojure.string :as s]
            [rksm.system-navigator.ns.filemapping :refer (file-for-ns source-reader-for-ns)]
            [rksm.system-navigator.changesets :as cs]
            (clojure.tools.analyzer jvm ast))
  (:import (java.io LineNumberReader InputStreamReader PushbackReader)
           (clojure.lang RT)))


(declare intern-info symbol-info)

; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
; reading stuff

(defn purge-string!
  [rdr]
  (let [buf (-> rdr .rdr .source_log_frames var-get :buffer)
        str (.toString buf)]
    (.delete buf 0 (count str))
    str))

(defn read-objs
  [rdr-or-src]
  ; FIXME this is hacked...
  (let [rdr (trt/indexing-push-back-reader (trt/source-logging-push-back-reader rdr-or-src))]
    (loop [result []]
      (let [start-line (trt/get-line-number rdr)
            start-column (trt/get-column-number rdr)]
        (if-let [o (tr/read rdr false nil)]
          (let [raw-str (purge-string! rdr)
                lines (s/split-lines raw-str)
                no-ws-lines (take-while #(re-find #"^\s*$" %) lines)
                src-lines (drop (count no-ws-lines) lines)
                first-line-ws-match (re-matches #"^(\s*)(.*)" (first src-lines))
                src-lines (assoc (vec src-lines) 0 (nth first-line-ws-match 2))
                src (s/join "\n" src-lines)
                line (+ (count no-ws-lines) start-line)
                column (+ start-column (count (second first-line-ws-match)))]
            (when (= \newline (trt/peek-char rdr))
              (trt/read-char rdr)
              (purge-string! rdr))
            (recur (conj result {:form o
                                 :source src
                                 :line line
                                 :column column})))
          result)))))

; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
; parsing via tools.analyzer

(defn- find-defs
  [expr]
   (let [child-result (mapcat find-defs (clojure.tools.analyzer.ast/children expr))
         my-result (if (= :def (:op expr))
                     (->> expr ((juxt :env #(hash-map :name (:name %)))) (apply merge)))]
     (if my-result 
       (conj child-result my-result)
       child-result)))

(defn- file-loc-id
  [{line :line, column :column}]
  {:line line, :column column})

(defn- merge-read-objs-with-ast
  [ast-defs read-objs]
  (let [indexed-a (apply merge (map #(hash-map (file-loc-id %) %) ast-defs))
        indexed-b (apply merge (map #(hash-map (file-loc-id %) %) read-objs))
        ids-both (clojure.set/intersection (-> indexed-a keys set) (-> indexed-b keys set))
        a (map (partial get indexed-a) ids-both)
        b (map (partial get indexed-b) ids-both)]
    (->> (concat a b)
      (group-by file-loc-id)
      vals
      (map (partial apply merge)))))

(defn read-and-parse
  [src namespace]
  (let [read (read-objs src)
        forms (map :form read)
        ast (clojure.tools.analyzer.jvm/analyze
             (list forms)
             {:context :eval, :locals {}, :ns namespace})
        defs (find-defs ast)]
    (merge-read-objs-with-ast defs read)))

; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
; source retrieval -- old version

(defn- read-next-obj
  "follows the reader while it core/reads an object and returns the string in
  range of what was read"
  [rdr]
  (let [text (StringBuilder.) 
        pbr (proxy [PushbackReader] [rdr]
                   (read []
                         (let [i (proxy-super read)]
                           (if (> i -1) (.append text (char i)))
                           i)))]
    (if (= :unknown *read-eval*)
      (throw (IllegalStateException. "Unable to read source while *read-eval* is :unknown."))
      (tr/read (PushbackReader. pbr) false nil))
    (str text)))

(defn- read-entity-source
  "goes forward in line numbering reader until line of entity is reached and
  reads that as an object"
  [{lrdr :lrdr, sources :sources, :as record} meta-entity]
  (or (if-let [line (:line meta-entity)]
        (do
          (dotimes [_ (dec (- line (.getLineNumber lrdr)))] (.readLine lrdr))
          (let [new-meta (merge meta-entity {:source (read-next-obj lrdr)})]
            (update-in record [:sources] conj new-meta))))
      record))

(defn add-source-to-interns-with-reader
  [rdr interns]
  (let [source-data {:lrdr (LineNumberReader. rdr), :sources []}]
    (if-let [result (reduce read-entity-source source-data interns)]
      (:sources result)
      interns)))

(defn add-source-to-interns
  "alternative for `source-for-symbol`. Instead of using clojure.repl this
  functions uses the classloader info provided by filemapping to find more
  recent versions of the source.
  NOTE: If there are multiple versions a lib on the classpath than it is
  possible that this function will retrieve code that i not actually in the
  system! (and the system meta data will clash with the actual file contents)"
  [ns interns]
  (if-let [rdr (source-reader-for-ns ns)]
    (with-open [rdr rdr]
      (add-source-to-interns-with-reader rdr interns))
    interns))

(defn file-source-for-sym
  [sym]
  (let [ns-sym (-> (.getNamespace sym) symbol find-ns ns-name)
        interns [(intern-info (meta (find-var sym)))]
        with-source (add-source-to-interns ns-sym interns)]
    (:source (first with-source))))

(defn source-for-symbol
  "This method uses the RT/baseloader to lookup the files belonging to symbols.
  When files get reloaded / defs redefined this can mean that the code being
  retrieved is outdated"
  [sym]
  (or (some-> (cs/get-changes sym) last :source)
      (try (clojure.repl/source-fn sym)
        (catch Exception e nil))
      (let [ns (-> sym .getNamespace symbol)]
        (-> (add-source-to-interns
             ns [(symbol-info ns (-> sym name symbol))])
          first :source))))

(defn add-source-to-interns-from-repl
  "This method uses the RT/baseloader to lookup the files belonging to symbols.
  When files get reloaded / defs redefined this can mean that the code being
  retrieved is outdated"
  [ns intern-meta-data]
  (let [ns-string (str (ns-name ns))
        sym-fn (partial symbol ns-string)
        source-fn #(or (source-for-symbol (sym-fn (-> % :name str))) "")]
    (map #(assoc % :source (source-fn %)) intern-meta-data)))

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
    ; (add-source-to-interns ns)
    ; (add-source-to-interns-from-repl ns)
    (filter boolean)))

(comment
 (time (namespace-info 'clj-tuple))
 )

(defn symbol-info
  [ns sym]
  (-> (ns-resolve ns sym)
    meta
    intern-info))

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
