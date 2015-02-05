(ns rksm.system-navigator.dependencies
  (:require [clojure.data.xml :as xml])
  (:require [clojure.zip :as z])
  (:require [clojure.string :as s])
  (:require [cemerick.pomegranate :refer (add-dependencies)])
  (:require [clojure.java.io :as io]))

; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
; pom

(defn xml-tags-matching
  [xml tag-sym]
  (->> (xml-seq xml)
    (filter (fn [{tag :tag}] (= tag-sym tag)))))

(defn make-dep-vec
  [dep-from-pom]
  [(symbol (:groupId dep-from-pom) (:artifactId dep-from-pom))
   (:version dep-from-pom)])

(defn xml-dep->info
  [xml-dep]
  (->> xml-dep
    :content
    (mapcat (juxt :tag (comp first :content)))
    (apply hash-map)))

(defn pom-deps
  [pom-file]
  (let [xml (xml/parse-str (slurp pom-file))
        deps (-> (xml-tags-matching xml :dependencies) first :content)]
    (map (comp make-dep-vec xml-dep->info) deps)))

; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
; lein

(defn lein-deps
  [project-clj-file]
  (let [proj (read-string (slurp project-clj-file))
        deps (some->> proj
               (drop-while (partial not= :dependencies))
               second)
        dev-deps (some-> (drop-while (partial not= :profiles) proj)
                   second
                   :dev
                   :dependencies)]
    (apply merge (or deps []) (or dev-deps []))))

; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(defn install
  "dep like [group.id/artifact.id \"0.1.2\"]"
  [dep]
  (add-dependencies :coordinates [dep]
                    :repositories (merge cemerick.pomegranate.aether/maven-central
                                         {"clojars" "http://clojars.org/repo"})))

(defn load-deps-from-project-clj-or-pom-in
  [dir]
  (let [make-file (fn [n] (io/file (str dir java.io.File/separator n)))
        project-clj (make-file "project.clj")
        pom (make-file "pom.xml")
        deps (cond
               (.exists project-clj) (lein-deps project-clj)
               (.exists pom) (pom-deps pom)
               :default nil)
        cleaned-deps (filter (comp (partial not= 'org.clojure/clojure) first) deps)]
    (if deps (doall (map install cleaned-deps)))
    cleaned-deps))

; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(comment
  123
 (load-deps-from-project-clj-or-pom-in "/Users/robert/clojure/cloxp-blog/")
 (pom-deps "/Users/robert/clojure/cloxp-blog/pom.xml")
 (lein-deps "/Users/robert/clojure/seesaw/project.clj")

 (-> "/Users/robert/clojure/seesaw"
   (str java.io.File/separator "project.clj")
   clojure.java.io/file
   (.exists))

 )