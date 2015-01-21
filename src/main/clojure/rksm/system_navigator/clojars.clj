(ns rksm.system-navigator.clojars
  (:require
   [clj-http.client :as http]
   [clojure.tools.reader.edn :as edn]
   [clojure.tools.reader.reader-types :as t]
   [clojure.data.json :as json])
  (:import
   [java.util.zip.GZIPInputStream]))

(defn clojars-feed-stream
  []
  (let [req (http/get "http://clojars.org/repo/feed.clj.gz" {:as :stream})]
    (req :body)))

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


