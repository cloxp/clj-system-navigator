(ns rksm.system-navigator.ns.filemapping
    (:refer-clojure :exclude [add-classpath])
    (:require [clojure.tools.namespace.find :as nf]
              [clojure.tools.namespace.repl :as nr]
              [clojure.java.classpath :as cp]
              [clojure.java.io :as io]
              [dynapath.util :as dp]
              [cemerick.pomegranate]
              [rksm.system-navigator.fs-util :as fs]))

(declare ns-name->rel-path classpath add-project-dir)

(defn classloaders
  []
  (->> (Thread/currentThread)
       .getContextClassLoader
       (iterate #(.getParent %))
       (take-while boolean)
       (filter dp/addable-classpath?)))

(defn add-classpath
  [cp]
  (dp/add-classpath-url
    (last (classloaders))
    (-> cp io/file .toURI .toURL)))


(def common-src-dirs ["src/main/clojure", "src/main/clj", "src/main", "src/clojure", "src/clj", "src"])
(def common-test-dirs ["src/test/clojure", "src/test/clj", "src/test", "test/clojure", "test/clj", "test"])
(def class-dirs ["classes"])

(defn- first-existing-file
  [base-dir paths]
  (->> paths
    (map #(str base-dir java.io.File/separator %))
    (map io/file)
    (filter #(.exists %))
    first))

(comment
 (first-existing-file "/Users/robert/clojure/system-navigator" common-src-dirs)
 (find-source-test-compile-dirs "/Users/robert/clojure/system-navigator"))

(defn find-source-test-compile-dirs
  [base-dir]
  (let [d base-dir
        find-first (partial first-existing-file d)]
    (->> [(find-first common-src-dirs)
          (find-first common-test-dirs)  
          (find-first class-dirs)]
      (filter boolean))))

(defn add-common-project-classpath
  [& [base-dir]]
  (add-project-dir
   (or base-dir (System/getProperty "user.dir"))))

(defn classpath-dirs
  []
  (filter #(.isDirectory %) (classpath)))

(defn- classpath-dir-known?
  [dir]
  (->> (classpath-dirs)
    (map str)
    (filter #(re-find (re-pattern dir) %))
    not-empty))

(defn maybe-add-classpath-dir
  [dir]
  (if-not (classpath-dir-known? dir)
    (add-common-project-classpath dir)))

(comment
 
 (rksm.system-navigator.ns.filemapping/maybe-add-classpath-dir "/Users/robert/clojure/system-navigator")
 (classpath-dir-known? "/Users/robert/clojure/cloxp-trace")
 (map str (classpath-dirs))
 )

; -=-=-=-=-=-=-
; jar related
; -=-=-=-=-=-=-

(defn- jar-entry-for-ns
  [jar-file ns-name]
  (let [rel-name (ns-name->rel-path ns-name)]
       (->> jar-file .entries
            iterator-seq
            (filter #(= (.getName %) rel-name))
            first)))

(defn- jar-reader-for-ns
  [class-path-file ns-name]
  (let [jar (java.util.jar.JarFile. class-path-file)
        jar-entry (jar-entry-for-ns jar ns-name)]
    (-> jar (.getInputStream jar-entry) io/reader)))

(defn classpath-from-system-cp-jar
  [jar-file]
  (some->> jar-file
           .getManifest
           .getMainAttributes
           (filter #(= "Class-Path" (-> % key str)))
           first .getValue
           (#(clojure.string/split % #" "))
           (map #(java.net.URL. %))
           (map io/as-file)))

(defn jar?
  [f]
  (and
    (.exists f)
    (not (.isDirectory f))
    (try
      (java.util.jar.JarFile. f)
      (catch Exception e false))))

; -=-=-=-=-=-=-=-=-
; class path lokup
; -=-=-=-=-=-=-=-=-

(defn system-classpath
  []
  (some->> (System/getProperty "java.class.path")
    io/file
    (#(try (java.util.jar.JarFile. %) (catch Exception _)))
    classpath-from-system-cp-jar))

(defn classpath
  []
  (distinct (concat (system-classpath)
                    (cp/classpath)
                    (->> (dp/all-classpath-urls) (map io/file)))))

(defn loaded-namespaces
  [& {m :matching}]
  (let [nss (nf/find-namespaces (classpath))
        filtered (if m (filter #(re-find m (str %)) nss) nss)]
    (-> filtered distinct sort)))

(comment
  (loaded-namespaces)
  (loaded-namespaces :matching #"rksm")
  )

(defn classpath-for-ns
  [ns-name]
  (let [cp (classpath)
        ns-per-cp (map #(nf/find-namespaces [%]) cp)]
      (some->> (zipmap cp ns-per-cp)
          (filter #(->> % val (some #{ns-name})))
          last key)))

; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
; classpath / namespace -> files
; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(defn ns-name->rel-path
  [ns-name]
  (-> ns-name str
    (clojure.string/replace #"\." "/")
    (clojure.string/replace #"-" "_")
    (str ".clj")))

(defn rel-path->ns-name
  [rel-path]
  (-> rel-path
    str
    (clojure.string/replace #"/" ".")
    (clojure.string/replace #"_" "-")
    (clojure.string/replace #".clj$" "")
    symbol))

(defn- clj-files-in-dir
  [dir]
  (->> dir
       (tree-seq #(.isDirectory %) #(.listFiles %))
       (filter #(and (not (.isDirectory %))
                     (re-find #"\.cljx?" (.getName %))))))

(defn file-for-ns
  "tries to find a filename for the given namespace"
  [ns-name & [file-name]]
  (if file-name
    (io/file file-name)
    (if-let [cp (classpath-for-ns ns-name)]
      (if (.isDirectory cp)
        (->> (clj-files-in-dir cp)
          (filter #(re-find
                    (re-pattern (str (ns-name->rel-path ns-name) "$"))
                    (.getAbsolutePath %)))
          first)
        cp))))

(defn relative-path-for-ns
  "relative path of ns in regards to its classpath"
  [ns & [file-name]]
  (if-let [fn (file-for-ns ns file-name)]
    (if (jar? fn)
      (some-> (java.util.jar.JarFile. fn)
        (jar-entry-for-ns ns)
        (.getName))
      (some-> (classpath-for-ns ns)
        (fs/path-relative-to fn)))))

(defn file-name-for-ns
  [ns]
  (.getCanonicalPath (file-for-ns ns)))

(defn source-reader-for-ns
  [ns-name & [file-name]]
  (if-let [f (file-for-ns ns-name file-name)]
    (if (jar? f)
        (jar-reader-for-ns f ns-name)
        (io/reader f))))

(defn source-for-ns
  [ns-name & [file-name]]
  (if-let [rdr (source-reader-for-ns ns-name file-name)]
    (with-open [rdr rdr] (when rdr (slurp rdr)))))

; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(defn- walk-dirs [dirpath pattern]
  (doall (filter #(re-matches pattern (.getName %))
                 (file-seq (io/file dirpath)))))
 
(comment
 (map #(println (.getPath %)) (walk-dirs "src" #".*\.clj$")))

(defn discover-ns-in-cp-dir
  [dir]
  (->> (walk-dirs dir #".*\.clj$")
    ; (map #(.getCanonicalPath %))
    (map (partial fs/path-relative-to dir))
    (map rel-path->ns-name)
    ))

(defn discover-ns-in-project-dir
  [dir]
  (->> (find-source-test-compile-dirs dir)
    (mapcat discover-ns-in-cp-dir)))

(defn add-project-dir
  [dir]
  (doseq [new-cp (find-source-test-compile-dirs dir)]
    (cemerick.pomegranate/add-classpath new-cp))
  (discover-ns-in-project-dir dir))

(defn refresh-classpath-dirs
  []
  (apply nr/set-refresh-dirs (classpath-dirs))
  (nr/refresh-all))

; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(comment
  (classpath)
  (loaded-namespaces)
  (file-for-ns 'rksm.system-navigator)
  (file-for-ns 'rksm.system-navigator "/Users/robert/clojure/system-navigator/src/main/clojure/rksm/system_navigator.clj")
  (relative-path-for-ns 'rksm.system-navigator "/Users/robert/clojure/system-navigator/src/main/clojure/rksm/system_navigator.clj")
  (refresh-classpath-dirs)
  )