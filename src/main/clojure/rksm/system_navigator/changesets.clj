(ns rksm.system-navigator.changesets)

(def current-changeset (atom []))

(defn record-change!
  [sym source & [prev-source]]
  (swap! current-changeset conj
         {:sym sym :source source
          :prev-source prev-source}))

(defn record-change-ns!
  [ns-name source & [prev-source]]
  (swap! current-changeset conj
         {:sym ns-name :source source
          :prev-source prev-source}))

(defn get-changes
  [sym]
  (filter #(= (:sym %) sym) @current-changeset))

; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(comment
 (def x (first @rksm.system-navigator.ns.modify-internals/current-changeset))
 (-> x :ns type)
 (retrieve-changes 'rksm.system-navigator.test.dummy-1/x)

 
 (def src (rksm.system-navigator.ns.filemapping/source-for-ns 'rksm.system-navigator.test.dummy-1))
 (def src (rksm.system-navigator.ns.filemapping/source-for-ns 'rksm.system-navigator.ns.internals))
 (require '[clojure.tools.reader])
 
 (defn read-src
   ([src]
    (read-src src (clojure.tools.reader.reader-types/string-push-back-reader src)))
   ([src rdr]
    (if-let [o (clojure.tools.reader/read rdr false nil)]
      (cons o (lazy-seq (read-src src rdr))))))

 (take 1 (read-src src))
 (take 2 (read-src src))
 (read-src src)
 (cemerick.pomegranate/add-dependencies :coordinates '[[org.clojure/tools.analyzer.jvm "0.6.5"]])
 (require '[clojure.tools.analyzer :as ana2])
 (ana2/analyze (read-string src) (ana2/empty-env))
 cemerick.pomegranate.aether/resolve-dependencies*
 
 
 (require '[clojure.tools.analyzer.jvm :as ana.jvm])
 (ana.jvm/analyze (read-src src))
; (type (ana.jvm/analyze (read-src src)))
(ana.jvm/analyze (read-src src))
 (def analyzed (ana.jvm/analyze-ns 'rksm.system-navigator.ns.internals))
 analyzed
(ana.jvm/analyze (read-src src))

 (find-defs (first analyzed))

 (first (mapcat find-defs analyzed))

(defn record [expr]
  (let [v (:var expr)
        s (.sym v)]
    ; (println s v)
    expr
    ; [s v]
    ))

 (defn find-defs [expr]
    (let [rest (mapcat find-defs (clojure.tools.analyzer.ast/children expr))]
      (if (= :def (:op expr))
        (cons (record expr) rest)
        rest)))

 )
