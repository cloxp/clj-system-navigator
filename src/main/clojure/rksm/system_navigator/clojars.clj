(ns rksm.system-navigator.clojars
  (:require [org.httpkit.client :as http]
            [clojure.tools.reader.edn :as edn]
            [clojure.tools.reader.reader-types :as t]
            [clojure.data.json :as json]
            [clojure.string :as s]
            [cemerick.pomegranate :refer (classloader-hierarchy get-classpath add-dependencies)]
            [rksm.system-files.jar-util :refer (namespaces-in-jar)]
            [rksm.system-files :as sf :refer (file)])
  (:import [java.util.zip.GZIPInputStream]))

(defn clojars-feed-stream
  []
  (let [req (http/get "http://clojars.org/repo/feed.clj.gz" {:as :stream})]
    (-> req deref :body)))

(defmacro with-clojars-uncompressed-content
  [s-name & body]
  `(with-open [~s-name (java.util.zip.GZIPInputStream.
                        (clojars-feed-stream))]
    ~@body))

(defn clojars-uncompressed-content
  []
  (with-clojars-uncompressed-content s
    (slurp s)))

(defn clojars-project-defs
  ([]
  (let [rdr (t/string-push-back-reader (clojars-uncompressed-content))]
    (doall (loop [read []]
             (if-let [o (edn/read {:eof nil} rdr)]
               (recur (cons o read))
               read))))))

(defn clojars-project-defs->json
  []
  (json/write-str (clojars-project-defs)))

(defn get-clojars-json-file
  []
  (let [basename "clojars-feed.json"
        ts (.format (java.text.SimpleDateFormat. "yyyy-MM-dd_HH") (new java.util.Date))]
    (if-let [workspace (or (System/getenv "WORKSPACE_LK")
                           (System/getProperty "user.dir"))]
      (clojure.java.io/file (str workspace "/" ts "-" basename))
      (java.io.File/createTempFile ts basename))))

(defn ensure-clojure-feed-in-a-file
  []
  (let [f (get-clojars-json-file)]
    (if-not (every? true? ((juxt #(.exists %) #(> (.length %) 0)) f))
      (spit f (clojars-project-defs->json)))
    f))

(defn get-probable-namespaces-of-jar
  [jar]
  (let [jar-file (if (string? (type jar))
                   (java.util.jar.JarFile. jar)
                   jar)]
    (some->> jar-file
      .entries iterator-seq
      (filter #(re-find #".clj" (str %)))
      (filter #(not (re-find #"project.clj|META|\.cljs$|\.class" (str %))))
      (map #(-> %
              .getName
              (clojure.string/replace #"/" ".")
              (clojure.string/replace #"_" "-")
              (clojure.string/replace #".clj$" "")
              symbol)))))

(defn get-probable-namespaces-for-maven-thing
  [group-id artifact-id version]
  (let [jar-name (-> (interpose "-" [artifact-id version])
                   (concat [".jar"])
                   s/join)
        path-re (->> [group-id artifact-id version jar-name]
                  (interpose java.io.File/separator)
                  s/join
                  re-pattern)
        jar (->> (sf/classpath)
              (filter (comp (partial re-find path-re) str))
              first)]
    (->> (namespaces-in-jar jar #"clj(x)?$")
      (map :ns) distinct)))

(defn install-clojar-package
  [group-id artifact-id version]
  (let [name (symbol (str group-id "/" artifact-id))
        repos (merge cemerick.pomegranate.aether/maven-central
                     {"clojars" "http://clojars.org/repo"})]
    (add-dependencies :coordinates `[[~name ~version]]
                      :repositories repos)))

(defn install-clojar-package-and-print-namespaces
  [group-id artifact-id version]
  (install-clojar-package group-id artifact-id version)
  (->> (rksm.system-navigator.clojars/get-probable-namespaces-for-maven-thing
        group-id artifact-id version)
    (interpose "\n  ")
    (clojure.string/join "")
    (format "%s/%s installed. Provided namespaces:\n  %s"
            group-id artifact-id)))

(defn install-clojar-package-and-report-namespaces
  [group-id artifact-id version]
  (install-clojar-package group-id artifact-id version)
  {:namespaces
   (get-probable-namespaces-for-maven-thing group-id artifact-id version)})

; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(comment
 (.getCanonicalPath (ensure-clojure-feed-in-a-file))
 (ensure-clojure-feed-in-a-file)
 (def x (clojars-project-defs))
 (type x)
 (-> (take 10 x) json/write-str)

 (-> (take-last 10 x) json/write-str)
 (-> (take 10 x) (json/write (clojure.java.io/writer (get-clojars-json-file))))
 (json/write (clojars-project-defs) (clojure.java.io/writer (get-clojars-json-file)))

 (require '[clojure.string :refer [join]])
 (->> (clojars-uncompressed-content) (take 100) join)
 (time (-> (clojars-project-defs) count))
 (clojars-project-defs))
