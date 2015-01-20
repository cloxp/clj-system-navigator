(ns rksm.system-navigator.ns.filemapping
    (:refer-clojure :exclude [add-classpath])
    (:require [clojure.tools.namespace.find :as nf]
              [clojure.java.classpath :as cp]
              [clojure.java.io :as io]
              [dynapath.util :as dp]
              [cemerick.pomegranate]
              [rksm.system-navigator.fs-util :as fs]))

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


(def common-src-dirs ["src/main/clojure", "src/main/clj", "src/clojure", "src/clj", "src"])
(def common-test-dirs ["src/test/clojure", "src/test/clj", "src/test", "test/clojure", "test/clj", "test"])
(def class-dirs ["classes"])

(defn- first-existing-file
  [paths]
  (->> paths
     (map #(str (System/getProperty "user.dir") "/" %))
     (map io/file)
     (filter #(.exists %))
     first))

(defn add-common-project-classpath
  []
  (some->> (first-existing-file common-src-dirs)
    (cemerick.pomegranate/add-classpath))
  (some->> (first-existing-file common-test-dirs)
    (cemerick.pomegranate/add-classpath))  
  (some->> (first-existing-file class-dirs)
    (cemerick.pomegranate/add-classpath)))

; -=-=-=-=-=-=-
; jar related
; -=-=-=-=-=-=-

(declare ns-name->rel-path)

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

(comment
  (classpath)
  (loaded-namespaces)
  (file-for-ns 'rksm.system-navigator)
  (file-for-ns 'rksm.system-navigator "/Users/robert/clojure/system-navigator/src/main/clojure/rksm/system_navigator.clj")
  (relative-path-for-ns 'rksm.system-navigator "/Users/robert/clojure/system-navigator/src/main/clojure/rksm/system_navigator.clj")
  )
