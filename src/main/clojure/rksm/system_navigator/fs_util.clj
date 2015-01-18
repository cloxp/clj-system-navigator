(ns rksm.system-navigator.fs-util
  (:require
   [clojure.java.io :as io]
   [clojure.string :as string]
   [clojure.java.io :as io])
  (:import java.io.File))

; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
; borrowoed from https://github.com/clojure/clojurescript/blob/master/src/clj/cljs/closure.clj

(defn path-seq
  [file-str]
  (->> File/separator
    java.util.regex.Pattern/quote
    re-pattern
    (string/split file-str)))

(defn to-path
  ([parts]
     (to-path parts File/separator))
  ([parts sep]
    (apply str (interpose sep parts))))

(defn path-relative-to
  "Generate a string which is the path to input relative to base."
  [^File base input]
  (let [base-path (path-seq (.getCanonicalPath (io/file base)))
        input-path (path-seq (.getCanonicalPath (io/file input)))
        count-base (count base-path)
        common (count (take-while true? (map #(= %1 %2) base-path input-path)))
        prefix (repeat (- count-base common) "..")]

    ; (println base-path input-path common count-base)
    (if (= count-base common)
      (to-path (drop common input-path) "/")
      (to-path (concat prefix (drop common input-path)) "/")
      )))

(comment
 (require '[rksm.system-navigator.ns.filemapping :refer (classpath-for-ns file-for-ns)])

 (-> (classpath-for-ns 'rksm.system-navigator.ns.internals) io/file .getCanonicalPath path-seq count)
 (-> (file-for-ns 'rksm.system-navigator.ns.internals) io/file .getCanonicalPath path-seq count)
 
 (path-relative-to (classpath-for-ns 'rksm.system-navigator.ns.internals)
                   (file-for-ns 'rksm.system-navigator.ns.internals))
 
 )
