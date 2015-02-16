(ns rksm.system-navigator.project-config
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
; source dirs

(defn source-dirs-of-pom
  "Returns contents of sourceDirectory, testSourceDirectory, and sources. Note:
  These are most likely relative paths."
  [pom]
  (as-> pom x
    (slurp x)
    (xml/parse-str x)
    (partial xml-tags-matching x)
    (mapcat x [:sourceDirectory :testSourceDirectory :source])
    (mapcat :content x)
    (distinct x)))

(defn source-dirs-of-lein
  [project-clj-file]
  (let [proj (read-string (slurp project-clj-file))
        source (some->> proj
                 (drop-while (partial not= :source-paths))
                 second)
        test-source (some-> (drop-while (partial not= :test-paths) proj)
                      second)]
    (into [] (apply merge source test-source))))

(defn source-dirs-in-project-conf
  [project-dir]
  (let [pclj (io/file (str project-dir "/project.clj"))
        pom (io/file (str project-dir "/pom.xml"))]
    (map (partial str project-dir "/")
         (cond
           (.exists pclj) (source-dirs-of-lein pclj)
           (.exists pom) (source-dirs-of-pom pom)
           :default []))))

(comment
 (source-dirs-of-pom "/Users/robert/clojure/cloxp-cljs/pom.xml")
 (source-dirs-of-pom "/Users/robert/clojure/system-navigator/pom.xml")
 (source-dirs-of-lein "/Users/robert/clojure/cloxp-cljs/project.clj")
 (source-dirs-in-project-conf "/Users/robert/clojure/cloxp-cljs")
 (source-dirs-in-project-conf (io/file "/Users/robert/clojure/cloxp-cljs"))
 )

; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
; lein

(defn lein-deps
  [project-clj-file]
  (let [proj (read-string (slurp project-clj-file))
        deps (some->> proj
               (drop-while (partial not= :dependencies))
               second)
        dev-deps (some-> (drop-while (partial not= :dev-dependencies) proj)
                   second)
        dev-deps-2 (some-> (drop-while (partial not= :profiles) proj)
                   second
                   :dev
                   :dependencies)]
    (into [] (apply merge deps dev-deps))))

; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
; dependencies

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
 (lein-deps "/Users/robert/clojure/cloxp-cljs/project.clj")

  
 (-> "/Users/robert/clojure/seesaw"
   (str java.io.File/separator "project.clj")
   clojure.java.io/file
   (.exists))

 )