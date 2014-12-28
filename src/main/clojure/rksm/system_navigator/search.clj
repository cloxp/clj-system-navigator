(ns rksm.system-navigator.search
    (:require [rksm.system-navigator :refer (loaded-namespaces source-reader-for-ns)]))

(defn code-search-ns
  [re ns-name]
  (with-open [rdr (source-reader-for-ns ns-name)
              lrdr (clojure.lang.LineNumberingPushbackReader. rdr)]
    (loop [results {:ns ns-name :finds []}]
      (if-let [line (try (.readLine lrdr) (catch Exception e nil))]
        (recur
          (if-let [match (re-find re line)]
          (update-in results [:finds]
                     conj {:line (.getLineNumber lrdr)
                           :match match
                           :source line})
           results))
        results))))

(comment

  (code-search-ns #"(?i)map" (-> (loaded-namespaces) first ))

(time 123)
  (time
    (->> (->> (loaded-namespaces) (take 10))
         (map #(code-search-ns #"(?i)map" %))
         count))
  (time
    (->> (loaded-namespaces)
         (map #(code-search-ns #"(?i)map" %))
         (take 10)))

  
  (-> clojure.lang.LineNumberingPushbackReader iroh.core/.*)
  
(-> clojure.lang.LineNumberingPushbackReader iroh.core/.*)


(def lrdr
  )





(-> lrdr iroh.core/.*)

(def rdr (-> (loaded-namespaces) first source-reader-for-ns))
  (def x (with-open [rdr (-> (loaded-namespaces) first source-reader-for-ns)]
      (line-seq rdr)))
  (def lines (-> (loaded-namespaces) first source-reader-for-ns line-seq))
  (take 1 lines)
    x
)

; ; Count lines of a file (loses head):
; user=> (with-open [rdr (clojure.java.io/reader "/etc/passwd")]
;         (count (line-seq rdr)))

; link
; (import '(java.io BufferedReader StringReader))

; ;; line terminators are stripped
; user=> (line-seq (BufferedReader. (StringReader. "1\n2\n\n3")))
; ("1" "2" "" "3")
; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(comment
  (classpath)
  (loaded-namespaces)
  (file-for-ns 'rksm.system-navigator.core)
  )
