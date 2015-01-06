(ns rksm.system-navigator.clojars
  (:require
   ;; [clojure.tools.namespace.repl]
   ;; [clj-http.client :as http]
            ))



; (in-ns 'user)
(comment

 
 ; (require '[rksm.system-navigator])
(require '[clj-http.client :as http])
(import 'java.util.zip.GZIPInputStream)

(defn clojars-feed-stream
  []
  (let [req (http/get "http://clojars.org/repo/feed.clj.gz" {:as :stream})]
    (req :body)))

(defn clojars-uncompressed-content
  []
  (with-open [in (java.util.zip.GZIPInputStream.
                  (clojars-feed-stream))]
    (slurp in)))

(defmacro with-clojars-uncompressed-content
  [body]
  (with-open [in (java.util.zip.GZIPInputStream.
                  (clojars-feed-stream))]
    (f in)))

(defmacro with-clojars-uncompressed-content
  [s-name & body]
  `(with-open [~s-name (java.util.zip.GZIPInputStream.
                        (clojars-feed-stream))]
    ~@body))

(defn clojars-project-defs
  ([]
   (with-clojars-uncompressed-content s
     (-> s 
       t/input-stream-push-back-reader 
       clojars-project-defs)))
 	([feed-reader]
   (cons
    (edn/read feed-reader)
    (lazy-seq (clojars-project-defs feed-reader)))))

(comment

 (->> (clojars-uncompressed-content) (take 100) s/join)
 (clojars-project-defs)

 (require '[clojure.tools.reader.edn :as edn]
          '[clojure.tools.reader.reader-types :as t]) 

 (with-clojars-uncompressed-content
  ; #(loop )
  ; #(-> % clojure.java.io/reader .readLine)
  ; #(-> % clojure.java.io/reader (PushbackReader. 1))
   #(-> % t/input-stream-push-back-reader edn/read)
   )
 
 (with-clojars-uncompressed-content is
  ; #(loop )
  ; #(-> % clojure.java.io/reader .readLine)
  ; #(-> % clojure.java.io/reader (PushbackReader. 1))
   (->  is t/input-stream-push-back-reader edn/read)
   )
 
 (loop [proj (read-string (clojars-uncompressed-content))])
 (read (clojars-feed-stream))
(def x (clojars-uncompressed-content))
(def x (read-string (clojars-uncompressed-content)))
(->> x (take 100))
(->> x (take 100) s/join)
x
 (->> (clojars-uncompressed-content) (take 100) s/join)
 (->> req :body (take 100) s/join)
 (-> content count (/ 1024)(/ 1024) float)

 (def req (http/get "http://clojars.org/repo/feed.clj.gz" {:as :stream}))
 (keys req)
 (:orig-content-encoding :trace-redirects :request-time :status :headers :body)
 (require '[clojure.string :as s])
 (clojure.java.io/input-stream "foo"))

)
