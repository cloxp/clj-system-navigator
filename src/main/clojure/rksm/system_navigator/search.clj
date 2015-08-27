(ns rksm.system-navigator.search
  (:require [rksm.system-files :refer (loaded-namespaces  file-for-ns)]
            [clojure.java.io :refer [reader]]))

(defn- code-search-single-ns-and-file
  [re ns-name file]
  (if-let [rdr (reader file)]
    (with-open [rdr rdr
                lrdr (clojure.lang.LineNumberingPushbackReader. rdr)]
      (loop [results {:ns ns-name :finds []}]
        (if-let [line (try (.readLine lrdr) (catch Exception e nil))]
          (recur
            (if-let [match (re-find re line)]
              (update-in results [:finds]
                         conj {:line (.getLineNumber lrdr)
                               :match match
                               :source line
                               :file (.getPath file)})
              results))
          results)))
    []))

(defn- code-search-single-ns
  [re ns-name]
  (sequence
   (comp (filter boolean)
         (map
          (partial code-search-single-ns-and-file re ns-name)))
   ; this is to ensure we find code of a ns even if there are multiple
   ; files. this version here is a trade off between speed and thoroughness...
   [(file-for-ns ns-name nil #"\.clj$")
    (file-for-ns ns-name nil #"\.cljc$")
    (file-for-ns ns-name nil #"\.cljs$")]))

(defn code-search-ns
  [re & ns-names]
  (sequence
   (comp (filter boolean)
         (mapcat #(code-search-single-ns re %))
         (filter boolean))
   ns-names))

(defn code-search
  "options: {:except #{SYMBOLS} :matching RE}"
  [re & {m :match-ns except :except-ns}]
  (as-> (loaded-namespaces :matching m) nss
        (apply code-search-ns re nss)
        (filter #(-> % :finds empty? not) nss)))

(comment

 (code-search-ns #"FIXME"
                 'rksm.system-navigator.search
                 'rksm.system-navigator.clojars)

 (->> (code-search-ns #"string\?" 'cljs.core)
   (mapcat :finds)
   (map :file)
   distinct)

 (rksm.system-files/files-for-ns 'rksm.system-navigator.search)

 (code-search #"FIXME" :match-ns #"rksm")

  (time
    (->> (->> (loaded-namespaces) (take 10))
         (map #(code-search-ns #"(?i)map" %))
         count))

  (->> (loaded-namespaces :except #{'clojure.core})
       (map #(time (code-search-ns #"(?i)map" %)))
        (take 10))
  )
