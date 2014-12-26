(ns rksm.system-navigator.core
    (:require [clojure.tools.namespace.find :as tnf]
              [clojure.java.classpath :as cjc]
              [clojure.java.io :as io]))

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

(defn pomegranate-classpath
  "The project should not depend on pomegranate but should still work in its
  presence, so conditionally and lazily access pomegranate."
  []
  (if (->> (all-ns)
          (map ns-name)
          (some #{'cemerick.pomegranate}))
  (->> (load-string "(cemerick.pomegranate/get-classpath)")
       (map io/file))))

(defn system-classpath
  []
  (some->> (System/getProperty "java.class.path")
            io/file
            (#(try (java.util.jar.JarFile. %) (catch Exception _)))
            classpath-from-system-cp-jar))

(defn classpath
  []
  (concat (system-classpath)
          (cjc/classpath)
          (pomegranate-classpath)))

(defn loaded-namespaces
  []
  (-> (classpath)
      tnf/find-namespaces
      sort))

(defn classpath-for-ns
  [ns-name]
  (let [cp (classpath)
        ns-per-cp (map #(tnf/find-namespaces [%]) cp)]
      (some->> (zipmap cp ns-per-cp)
          (filter #(->> % val (some #{ns-name})))
          first
          key)))

(defn- ns-name->rel-path
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

(defn file-name-for-ns
  [ns-name]
  (let [cp (classpath-for-ns ns-name)
        rel-path (ns-name->rel-path ns-name)]
       (when (.isDirectory cp)
         (->> (classpath-for-ns ns-name)
              clj-files-in-dir
              (filter #(re-find (re-pattern (str rel-path "$"))
                                (.getAbsolutePath %)))
              first))))

; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(comment
  (classpath)
  (loaded-namespaces)
  (file-name-for-ns 'rksm.system-navigator.core)
  )
