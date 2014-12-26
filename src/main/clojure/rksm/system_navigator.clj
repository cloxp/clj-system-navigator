(ns rksm.system-navigator
    (:refer-clojure :exclude [add-classpath])
    (:require [clojure.tools.namespace.find :as nf]
              [clojure.java.classpath :as cp]
              [clojure.java.io :as io]
              [dynapath.util :as dp]))

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
    (-> cp clojure.java.io/file .toURI .toURL)))

(comment
  (in-ns 'rksm.system-navigator)
  (dp/all-classpath-urls)
  (add-classpath (java.net.URL. "file:///Users/robert/clojure/system-navigator/src/main/clojure/"))
  (add-classpath (java.net.URL. "file:///Users/robert/clojure/system-navigator/src/test/clojure/"))
  (add-classpath "/Users/robert/clojure/system-navigator/foo/dummy-2-test.jar")

  )

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

(defn- jar-source-for-ns
  [class-path-file ns-name]
  (let [jar (java.util.jar.JarFile. class-path-file)
        jar-entry (jar-entry-for-ns jar ns-name)]
    (-> jar (.getInputStream jar-entry) slurp)))

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
  []
  (-> (classpath)
      nf/find-namespaces
      sort))

(defn classpath-for-ns
  [ns-name]
  (let [cp (classpath)
        ns-per-cp (map #(nf/find-namespaces [%]) cp)]
      (some->> (zipmap cp ns-per-cp)
          (filter #(->> % val (some #{ns-name})))
          first
          key)))


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
  [ns-name]
  (let [cp (classpath-for-ns ns-name)
        rel-path (ns-name->rel-path ns-name)]
       (if (.isDirectory cp)
         (->> (classpath-for-ns ns-name)
              clj-files-in-dir
              (filter #(re-find (re-pattern (str rel-path "$"))
                                (.getAbsolutePath %)))
              first)
         cp)))


(defn source-for-ns
  [ns-name]
  (let [f (file-for-ns ns-name)]
    (if (jar? f)
        (jar-source-for-ns f ns-name)
        (slurp f))))

; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(comment
  (classpath)
  (loaded-namespaces)
  (file-for-ns 'rksm.system-navigator.core)
  )
