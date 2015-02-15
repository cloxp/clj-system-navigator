(ns rksm.system-navigator.system-browser
  (:require [rksm.system-navigator.ns.internals :as i]
            [rksm.system-files :as fm]
            [rksm.system-navigator.changesets :as cs]
            [clojure.string :as s]
            [clojure.set :as set]))

; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
; changing defs
; -=-=-=-=-=-=-=-

(defn eval-and-update-meta!
  "eval + update meta of changed def, used by change-def!"
  [sym src]
  (let [namespace (-> sym .getNamespace symbol find-ns)
        ref (find-var sym)
        old-meta (select-keys (meta ref) [:file :column :line])]
    (binding [*ns* namespace]
      (eval (read-string src))) 
    (alter-meta! ref merge old-meta {:source src})))

(defn update-source-pos-of-defs-below!
  "shift / pull up defs follwoing def of sym to keep source location info up
  to date"
  [sym src old-src]
  (let [ref (find-var sym)
        namespace (-> sym .getNamespace symbol find-ns)]
   (if-let [line-of-changed (-> ref meta :line)]
     (let [line-diff (- (count (s/split-lines src)) 
                        (count (s/split-lines old-src)))]
       (doseq [ref (->> (ns-interns namespace)
                     vals
                     (filter #(let [l (-> % meta :line)]
                                (and (number? l) (> l line-of-changed)))))]
         (alter-meta! ref #(update-in % [:line] (partial + line-diff))))))))

(defn- update-source-file!
  [sym src old-src & [file]]
  (let [ns-sym (-> (.getNamespace sym) symbol find-ns ns-name)
        file (fm/file-for-ns ns-sym file)
        old-file-src (slurp file)
        new-file-src (fm/updated-source
                      sym (-> (find-var sym) meta) 
                      src old-src old-file-src)]
    (spit file new-file-src)))

(defn change-def!
  "1. eval new code
  2. record a change in a changeset
  3. if `write-to-file`, update source in file-system"
  [sym new-source & [write-to-file file]]
  (let [old-src (i/file-source-for-sym sym file)]
    (if (and old-src write-to-file)
      (update-source-file! sym new-source old-src file))
    (eval-and-update-meta! sym new-source)
    (update-source-pos-of-defs-below! sym new-source old-src)
    (let [old-src (i/file-source-for-sym sym file)
          change (cs/record-change! sym new-source old-src)]
      (dissoc change :source :prev-source))))


; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
; nitty-gritty details for how to "diff" changes
; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(defn- info-id
  [{ns :ns, name :name}]
  {:ns ns :name name})

(defn- without-all-interns
  [base-interns without-interns]
  (filter
   (fn [info] (not (some #(= (info-id info) (info-id %)) without-interns)))
   base-interns))

(defn- find-modified-interns
  "At this point we already now that new-ns-info and old-ns-info are in both
  new-src and old-src"
  [new-with-source old-with-source]
  (for [a new-with-source b old-with-source
          :when (and (= (info-id a) (info-id b))
                     (not= (:source a) (:source b)))]
      (assoc a :prev-source (:source b))))

(defn- find-added-interns
  [new-ns-info old-ns-info]
  (let [ids-new (clojure.set/difference
                 (set (map info-id new-ns-info))
                 (set (map info-id old-ns-info)))
        added (for [id ids-new info new-ns-info :when (= id (info-id info))] info)]
    added))

(defn- find-unchanged-interns
  [new-src old-interns]
  (let [sources-new (map :source (i/read-objs new-src))
        unchanged (for [src sources-new old old-interns
                        :when (= (clojure.string/trim src)
                                 (clojure.string/trim (:source old)))]
                    old)]
    unchanged))

(defn diff-ns
  "Figures out what has changed when the source of a namespace changes. Via def
  watchers, source comparison, and ns-intern access we figure out the removed,
  added, and changed ns interns (defs). This is used to construct changes /
  changesets."
  [ns-name new-src old-src new-ns-info old-ns-info changed-vars]
  (let [new-with-source (i/add-source-to-interns-with-reader
                         (java.io.StringReader. new-src)
                         (sort-by :line new-ns-info))
        old-with-source (i/add-source-to-interns-with-reader
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

(defn load-ns-source!
  "Load-file equivalent"
  [source source-path]
  (let [file-name (-> source-path
                    (s/split (re-pattern (java.io.File/separator)))
                    last)]
    (eval
     (read-string
      (apply format
        "(clojure.lang.Compiler/load (java.io.StringReader. %s) %s %s)"
        (map (fn [item]
               (binding [*print-length* nil
                         *print-level* nil]
                 (pr-str item)))
             [source source-path file-name]))))))

(defn change-ns-in-runtime!
  [ns-name new-source old-src & [file-name]]
  (let [old-ns-info (if (find-ns ns-name) 
                      (:interns (i/namespace-info ns-name file-name))
                      [])
        changed-vars (atom [])
        rel-path (fm/ns-name->rel-path ns-name)]
    (if (find-ns ns-name) 
      (install-watchers ns-name changed-vars))
    (try
      (load-ns-source! new-source rel-path)
      (finally (uninstall-watchers ns-name)))
    (let [new-ns-info (:interns (i/namespace-info ns-name file-name))
          diff (diff-ns ns-name new-source old-src new-ns-info old-ns-info @changed-vars)]
      (->> (:removed diff)
        (doseq [rem (:removed diff)]
          (ns-unmap (find-ns (:ns rem)) (:name rem))))
      diff)))

(defn change-ns!
  "1. eval new code
  2. record a change in a changeset
  3. of `write-to-file`, update source in file-system"
  [ns-name new-source & [write-to-file file]]
  (if-let [old-src (fm/source-for-ns ns-name file)]
    (do
      (if write-to-file
        (spit (fm/file-for-ns ns-name file) new-source))
      (let [diff (change-ns-in-runtime! ns-name new-source old-src file)
            change (cs/record-change-ns! ns-name new-source old-src diff)]
        change))
    (throw (Exception. (str "Cannot retrieve current source for " ns-name))))
  )

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
 (i/add-source-to-interns
  'rksm.system-navigator.test.dummy-1
  [(i/intern-info (meta #'rksm.system-navigator.test.dummy-1/x))])

 (change-def! 'rksm.system-navigator.test.dummy-1/x
                 "(def x 24)")

 (read-string "(def x 24)")

 )