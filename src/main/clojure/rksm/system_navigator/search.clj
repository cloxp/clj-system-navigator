(ns rksm.system-navigator.search
    (:require [rksm.system-navigator.ns.filemapping :refer (loaded-namespaces source-reader-for-ns)]))

(defn- code-search-single-ns
  [re ns-name]
  (if-let [rdr (source-reader-for-ns ns-name)]
    (with-open [rdr rdr
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
          results)))))

(defn code-search-ns
  [re & ns-names]
  (->> ns-names
       (map (partial code-search-single-ns re))
       (filter boolean)))

(defn code-search
  "options: {:except #{SYMBOLS} :matching RE}"
  [re & {m :match-ns except :except-ns}]
  (as-> (loaded-namespaces :matching m) nss
        (apply code-search-ns re nss)
        (filter #(-> % :finds empty? not) nss)))

(comment
  (code-search #"FIXME" :match-ns #"rksm")

  (time
    (->> (->> (loaded-namespaces) (take 10))
         (map #(code-search-ns #"(?i)map" %))
         count))

  (->> (loaded-namespaces :except #{'clojure.core})
       (map #(time (code-search-ns #"(?i)map" %)))
        (take 10))
  )
