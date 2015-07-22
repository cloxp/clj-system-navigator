(ns rksm.system-navigator.system-browser
  (:require [rksm.system-navigator.ns.internals :as i]
            [rksm.cloxp-source-reader.core :as src-rdr]
            [rksm.system-files :as fm]
            [rksm.system-navigator.changesets :as cs]
            [rksm.cloxp-repl :as repl]
            [clojure.string :as s]
            [clojure.set :as set]))

; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
; changing defs
; -=-=-=-=-=-=-=-

(defn eval-and-update-meta!
  "eval + update meta of changed def, used by change-def!"
  [sym src & [file]]
  (let [namespace (-> sym .getNamespace symbol find-ns)
        [{:keys [error value]}]
        (repl/eval-string
         src (-> sym .getNamespace symbol find-ns)
         {:keep-meta [:file :column :line], :file file, :throw-errors? true})]
    (or error value)))

(defn update-source-pos-of-defs-below!
  "shift / pull up defs follwoing def of sym to keep source location info up
  to date"
  [sym src old-src]
  (let [ref (find-var sym)
        namespace (-> sym .getNamespace symbol find-ns)]
    (if-let [line-of-changed (-> ref meta :line)]
      (let [line-diff (- (count (s/split-lines src))
                         (count (s/split-lines old-src)))]
        (when-not (zero? line-diff)
          (doseq [ref (->> (ns-interns namespace)
                        vals
                        (filter #(let [l (-> % meta :line)]
                                   (and (number? l) (> l line-of-changed)))))]
            (alter-meta! ref #(update-in % [:line] (partial + line-diff)))))))))

(defn- update-source-file!
  [sym src old-src & [file]]
  (let [ns-sym (-> (.getNamespace sym) symbol find-ns ns-name)
        file (fm/file-for-ns ns-sym file)
        old-file-src (slurp file)
        new-file-src (src-rdr/updated-source
                      sym (-> (find-var sym) meta)
                      src old-src old-file-src)]
    (spit file new-file-src)))

(defn change-def!
  "1. eval new code
  2. record a change in a changeset
  3. if `write-to-file`, update source in file-system"
  [sym new-source & [write-to-file file]]
  (let [ns-name (-> sym namespace symbol)
        file (or file (fm/file-for-ns ns-name file))
        ext (if file (str (re-find #"\.[^\.]+$" (str file))))
        old-src (i/file-source-for-sym sym file)]

    (if (and old-src write-to-file)
      (update-source-file! sym new-source old-src file))

    (eval-and-update-meta! sym new-source file)
    (update-source-pos-of-defs-below! sym new-source old-src)

    (let [old-src (i/file-source-for-sym sym file)
          change (cs/record-change! sym new-source old-src)]
      (dissoc change :source :prev-source))))

; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
; nitty-gritty details for how to "diff" changes
; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(defn- info-id
  [{:keys [ns name] :as meta-data}]
  (select-keys meta-data [:ns :name]))

(defn- without-all-interns
  [base-interns without-interns]
  (filter
   (fn [info] (not (some #(= (info-id info) (info-id %)) without-interns)))
   base-interns))

(defn- find-modified-interns
  "At this point we already now that new-ns-info and old-ns-info are in both
  new-src and old-src"
  [new-with-source old-with-source]
  (let [find-in-b (fn [a]
                    (if-let [b (first (filter
                                       (fn [b] (= (info-id a) (info-id b)))
                                       old-with-source))]
                      [a b] nil))]
    (map
     (fn [[a b]] (assoc a :prev-source (:source b) :file (or (:file a) (:file b))))
     (keep find-in-b new-with-source))))

(defn- find-added-interns
  [new-ns-info old-ns-info]
  (let [ids-new (clojure.set/difference
                 (set (map info-id new-ns-info))
                 (set (map info-id old-ns-info)))
        added (for [id ids-new info new-ns-info :when (= id (info-id info))] info)]
    added))

(defn- find-unchanged-interns
  [new-src old-interns]
  (let [sources-new (map :source (src-rdr/read-objs new-src))
        unchanged (for [src sources-new old old-interns
                        :when (= (clojure.string/trim src)
                                 (or (some-> old :source clojure.string/trim) ""))]
                    old)]
    unchanged))

(defn diff-ns
  "Figures out what has changed when the source of a namespace changes. Via def
  watchers, source comparison, and ns-intern access we figure out the removed,
  added, and changed ns interns (defs). This is used to construct changes /
  changesets."
  [ns-name new-src old-src new-ns-info old-ns-info changed-vars]
  (let [new-with-source (->> (src-rdr/read-objs new-src)
                          (filter (comp src-rdr/def? :form))
                          (map #(assoc % :ns ns-name :name (src-rdr/name-of-def (:form %))))
                          (map #(dissoc % :form)))
        old-with-source (src-rdr/add-source-to-interns-with-reader
                         (java.io.StringReader. old-src)
                         (sort-by :line old-ns-info))
        added (find-added-interns new-ns-info old-ns-info)
        unchanged (find-unchanged-interns new-src old-with-source)
        removed (without-all-interns old-with-source (concat changed-vars unchanged))
        changed (find-modified-interns
                 new-with-source
                 (without-all-interns old-with-source (concat removed unchanged)))]
    {:added added
     :removed removed
     :changed changed}))

(defn- install-watchers
  [ns change-store]
  (doseq [i (vals (ns-interns ns))]
   (add-watch
    i ::sys-nav-capture-change
    (fn [k var old new]
      (swap! change-store conj
             {:ns (-> var .ns .name)
              :name (-> var .sym)})))))

(defn- uninstall-watchers
  [ns]
  (doseq [i (vals (ns-interns ns))]
   (remove-watch i ::sys-nav-capture-change)))


; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
; high level ns change funcs
; -=-=-=-=-=-=-=-=-=-=-=-=-=-

(defn change-ns-in-runtime!
  [ns-name new-source old-src & [file]]
  (let [file (or file (fm/file-for-ns ns-name file))
        old-ns-info (if (find-ns ns-name)
                      (:interns (i/namespace-info ns-name file))
                      [])
        changed-vars (atom [])
        ext (if file (str (re-find #"\.[^\.]+$" (str file))))
        rel-path (fm/ns-name->rel-path ns-name ext)]
    (if (find-ns ns-name)
      (install-watchers ns-name changed-vars))
    (try
      (repl/eval-string new-source ns-name
                        {:file rel-path, :throw-errors? true, :line-offset 0})
      (finally (uninstall-watchers ns-name)))
    (let [new-ns-info (:interns (i/namespace-info ns-name file))
          diff (diff-ns ns-name new-source old-src new-ns-info old-ns-info @changed-vars)]
      (doseq [{n :name} (:removed diff)]
        (ns-unmap ns-name n))
      diff)))

(defn change-ns!
  "1. eval new code
  2. record a change in a changeset
  3. of `write-to-file`, update source in file-system"
  [ns-name new-source & [write-to-file file]]
  (if-let [file (or file (fm/file-for-ns ns-name file))]
    (if-let [old-src (fm/source-for-ns ns-name file)]
      (do
        (if write-to-file
          (spit file new-source))
        (let [diff (change-ns-in-runtime! ns-name new-source old-src file)
              change (cs/record-change-ns! ns-name new-source old-src diff)]
          change))
      (throw (Exception. (str "Cannot retrieve current source for " ns-name))))))

; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
; file / namespace creation

(defn create-namespace-and-file
  [ns-name dir]
  (let [fname (fm/create-namespace-file ns-name dir ".clj")]
    (change-ns! ns-name (format "(ns %s)" ns-name) true fname)
    fname))

; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(comment
 (require 'rksm.system-navigator.test.dummy-1 :reload)
 rksm.system-navigator.test.dummy-1/x

 (let [ns 'rksm.system-navigator.test.dummy-1]
   (ns-interns ns))
 (src-rdr/add-source-to-interns
  'rksm.system-navigator.test.dummy-1
  [(i/intern-info (meta #'rksm.system-navigator.test.dummy-1/x))])

 (change-def! 'rksm.system-navigator.test.dummy-1/x
                 "(def x 24)")

 (read-string "(def x 24)")

 )