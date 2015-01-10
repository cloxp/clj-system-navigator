(ns rksm.system-navigator.clojars
  (:require
   [clj-http.client :as http]
   [clojure.tools.reader.edn :as edn]
   [clojure.tools.reader.reader-types :as t]
   [rksm.system-navigator.json :as json])
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
     (loop [read []]
       (if-let [o (edn/read {:eof nil} rdr)]
         (recur (cons o read))
         read)))))

(defn clojars-project-defs->json
  []
  (json/json-str (clojars-project-defs)))

(comment

 (def x (clojars-project-defs))
 (type x)
 (take 10 x)
 
 (require '[clojure.string :refer [join]])
 (->> (clojars-uncompressed-content) (take 100) join)
 (time (-> (clojars-project-defs) count))
 (clojars-project-defs))


