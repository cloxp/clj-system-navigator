(ns rksm.system-navigator.system-browser
    (:require [rksm.system-navigator.ns.internals :as i])
    (:require [rksm.system-navigator.ns.filemapping :as fm])
    (:require [rksm.system-navigator.changesets :as cs])
    (:require [clojure.string :as s])
    (:require [clojure.set :as set])
    )

(defn- eval-and-update-meta!
  [sym src]
  (let [namespace (-> sym .getNamespace symbol find-ns)
        ref (find-var sym)
        old-meta (select-keys (meta ref) [:file :column :line])]
    (binding [*ns* namespace]
             (eval (read-string src))
             (alter-meta! ref merge old-meta))))

(defn updated-source
  [sym new-src-for-def old-src-for-def]
  (let [ns-sym (-> (.getNamespace sym) symbol find-ns ns-name)
        lines (-> (fm/file-for-ns ns-sym) slurp s/split-lines)
        line (-> (find-var sym) meta :line dec)
        before-lines (take line lines)
        after-lines (-> old-src-for-def s/split-lines count (drop (drop line lines)))]
    (str (s/join "\n" (concat before-lines [new-src-for-def] after-lines)))))

(defn- update-source-file!
  [sym src old-src]
  (let [new-file-src (updated-source sym src old-src)
        ns-sym (-> (.getNamespace sym) symbol find-ns ns-name)]
    (-> (fm/file-for-ns ns-sym) (spit new-file-src))))

(defn change-def!
  "1. eval new code
  2. record a change in a changeset
  3. of `write-to-file`, update source in file-system"
  [sym new-source & [write-to-file]]
  (eval-and-update-meta! sym new-source)
  (let [old-src (i/file-source-for-sym sym)
        change (cs/record-change! sym new-source old-src)]
    (if (and old-src write-to-file)
      (update-source-file! sym new-source old-src))))

(defn load-ns-source!
  [source source-path file-name]
  (eval 
   (read-string 
    (apply format
      "(clojure.lang.Compiler/load (java.io.StringReader. %s) %s %s)"
      (map (fn [item]
             (binding [*print-length* nil
                       *print-level* nil]
                      (pr-str item)))
           [source source-path file-name])))))

; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

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

(defn change-ns-in-runtime!
  [ns-name new-source old-src]
  (let [old-ns-info (i/namespace-info ns-name)
        changed-vars (atom [])]
    (install-watchers ns-name changed-vars)
    (try
      (load-ns-source! new-source
                       (fm/relative-path-for-ns ns-name)
                       (fm/file-name-for-ns ns-name))
      (finally (uninstall-watchers ns-name)))
    (let [new-ns-info (i/namespace-info ns-name)
          diff (diff-ns ns-name new-source old-src new-ns-info old-ns-info @changed-vars)]
      (->> (:removed diff)
        (doseq [rem (:removed diff)]
          (ns-unmap (find-ns (:ns rem)) (:name rem))))
      diff)))

(defn change-ns!
  "1. eval new code
  2. record a change in a changeset
  3. of `write-to-file`, update source in file-system"
  [ns-name new-source & [write-to-file]]
  (if-let [old-src (fm/source-for-ns ns-name)]    
    (let [diff (change-ns-in-runtime! ns-name new-source old-src)
          change (cs/record-change-ns! ns-name new-source old-src)]
      (if write-to-file
        (spit (fm/file-for-ns ns-name) new-source)))
    (throw (Exception. (str "Cannot retrieve current source for " ns-name))))
  )

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