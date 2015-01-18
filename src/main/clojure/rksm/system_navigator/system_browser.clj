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

(defn- info-id
  [{ns :ns, name :name}]
  {:ns ns :name name})

(defn- find-info-with-id
  [id infos]
  (first (filter #(= id (info-id %)) infos)))

(defn- info-indexed
  "Creates a data structure that maps ns and intern name to info
  {{:name z, :ns foo} {:ns foo, :name z, :file \"foo.clj\", :column 3, :line 1, :tag nil}}"
  [i]
  (apply merge
    (map #(hash-map (info-id %) %) i)))

(defn- modified?
  [new-info old-infos]
  (let [old-info (find-info-with-id
                  (info-id new-info)
                  old-infos)]
    (not (= (:source old-info) (:source new-info)))))

(defn- find-modified-interns
  "At this point we already now that new-ns-info and old-ns-info are in both
  new-src and old-src"
  [ns-name new-src old-src new-ns-info old-ns-info]
  (let [new-with-source (i/add-source-to-interns-with-reader
                         (java.io.StringReader. new-src)
                         (sort-by :line new-ns-info))
        old-with-source (i/add-source-to-interns-with-reader
                         (java.io.StringReader. old-src)
                         (sort-by :line old-ns-info))
        grouped (group-by info-id (concat new-with-source old-with-source))]
    (reset! capture [new-src old-src])
    ; (reset! capture [ns-name new-with-source old-with-source])
    (->> grouped
      (map val)
      (filter #(not= (first %) (second %)))
      (map #(apply (fn [a b] (assoc a :prev-source (:source b))) %)))))

(defn diff-ns
  [ns-name new-src old-src new-ns-info old-ns-info]
  (let [indexed-new (info-indexed new-ns-info)
        indexed-old (info-indexed old-ns-info)
        ids-new (-> indexed-new keys set)
        ids-old (-> indexed-old keys set)
        only-in-new (clojure.set/difference ids-new ids-old)
        in-both (clojure.set/intersection ids-old ids-new)
        added (map (partial get indexed-new) only-in-new)]
    {:added added
     :removed []
     :changed (find-modified-interns
               ns-name new-src old-src
               (map (partial get indexed-new) in-both)
               (map (partial get indexed-old) in-both))}))

(def capture (atom nil))

(defn change-ns-in-runtime!
  [ns-name new-source old-src]
  (let [old-ns-info (i/namespace-info ns-name)]
    (load-ns-source! new-source
                     (fm/relative-path-for-ns ns-name)
                     (fm/file-name-for-ns ns-name))
    (let [new-ns-info (i/namespace-info ns-name)
          diff (diff-ns ns-name new-source old-src new-ns-info old-ns-info)]
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