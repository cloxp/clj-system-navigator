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
  [{:keys [groupId artifactId version] :as dep-from-pom}]
  [(symbol groupId artifactId) version])

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
    (into [] (apply merge deps dev-deps dev-deps-2))))

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
; modifying poms and project.cljss

(defn find-project-configuration-file
  [& [dir]]
  (let [dir (or dir (System/getProperty "user.dir"))
        sep java.io.File/separatorChar
        project-clj (io/file (str dir sep "project.clj"))
        pom (io/file (str dir sep "pom.xml"))]
    (cond
      (.exists project-clj) (.getCanonicalPath project-clj)
      (.exists pom) (.getCanonicalPath pom)
      :default nil)))

(defn project-clj-with-dep
  [project-clj-map group-id artifact-id version]
  (let [dep [(if group-id (symbol (str group-id) (str artifact-id)) (symbol (str artifact-id))) version]
        deps-at (inc (.indexOf project-clj-map :dependencies))]
    (if (zero? deps-at)
      (concat project-clj-map [:dependencies [dep]])
      (let [updated-deps (-> (nth project-clj-map deps-at)
                           (conj dep)
                           distinct vec)
            updated (concat (take deps-at project-clj-map) [updated-deps]  (drop (inc deps-at) project-clj-map))]
        updated))))

(defn add-dep-to-project-clj!
  [project-clj group-id artifact-id version]
  (assert (-> (str project-clj) (.endsWith "project.clj")))
  (let [project-clj (io/file project-clj)]
    (assert (.exists project-clj))
    (let [conf (-> project-clj slurp read-string)]
      (->> (project-clj-with-dep conf group-id artifact-id version)
        (#(with-out-str (clojure.pprint/pprint %)))
        (spit project-clj)))))

(defn pom-with-dep
  [pom-string group-id artifact-id version]
  (let [xml (xml/parse-str pom-string)
        deps (->> (iterate z/next (z/xml-zip xml))
               (take-while (complement z/end?))
               (filter #(= :dependencies (some-> % z/node :tag)))
               first)]
    (if-not deps
      pom-string
      (let [el (xml/sexp-as-element
                [:dependency
                 [:groupId (str group-id)]
                 [:artifactId (str artifact-id)]
                 [:version version]])
            updated-deps (z/edit deps #(update-in % [:content] cons [el]))
            ; actually it should be enough to do
            ; xml-string (-> updated-deps z/root xml/indent-str)
            ; but due to http://dev.clojure.org/jira/browse/DXML-15 this
            ; doesn't work :(
            xml-string (-> updated-deps z/node xml/indent-str)
            xml-string (s/replace xml-string #"^.*>\\?\s*|[\n\s]$" "")
            xml-string (s/replace xml-string #"(?m)^" "  ")
            start (+ (.indexOf pom-string "<dependencies>") (count "<dependencies>"))
            end (+ (.indexOf pom-string "</dependencies>") (count "</dependencies>"))]
        (str (.substring pom-string 0 start)
             "\n  "
             xml-string
             (.substring pom-string end))))))

(defn add-dep-to-pom!
  [pom-file group-id artifact-id version]
  (assert (-> (str pom-file ) (.endsWith "pom.xml")))
  (let [pom-file  (io/file pom-file)]
    (assert (.exists pom-file))
    (let [conf (-> pom-file  slurp)]
      (-> pom-file
        (spit (pom-with-dep conf group-id artifact-id version))))))

(defn add-dep-to-project-conf!
  [project-dir group-id artifact-id version]
  (let [conf-file (find-project-configuration-file project-dir)]
    (cond
      (.endsWith conf-file "pom.xml") (add-dep-to-pom! conf-file group-id artifact-id version)
      (.endsWith conf-file "project.clj") (add-dep-to-project-clj! conf-file group-id artifact-id version)
      :default (throw (Exception. (str "invalid conf file: " conf-file))))))

; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(comment
 
 (-> (find-project-configuration-file "/Users/robert/clojure/websocket-test") (add-dep-to-pom! 'foo 'bar "1.2.3"))
 
 (add-dep-to-project-conf! "/Users/robert/clojure/websocket-test" 'foo 'bar "1.2.3")
 
 (-> (find-project-configuration-file)
   slurp
   read-string
   (-> {:dependencies [['foo/bar232 "4.2.3"] ['foo/bar "1.2.3"]]}
     (update-in [:dependencies] (comp distinct (partial into []) conj) ['foo/bar "1.2.3"]))
   )
 
 (-> {:dependencies [['foo/bar232 "4.2.3"] ['foo/bar "1.2.3"]]}
   (update-in [:dependencies] (comp distinct (partial into []) conj) ['foo/bar "1.2.3"]))
 
 (find-project-configuration-file "/Users/robert/clojure/cloxp-repl/")
 
 (load-deps-from-project-clj-or-pom-in "/Users/robert/clojure/cloxp-blog/")
 (pom-deps "/Users/robert/clojure/cloxp-blog/pom.xml")
 (lein-deps "/Users/robert/clojure/seesaw/project.clj")
 (lein-deps "/Users/robert/clojure/cloxp-cljs/project.clj")
 
 
 (-> "/Users/robert/clojure/seesaw"
   (str java.io.File/separator "project.clj")
   clojure.java.io/file
   (.exists))
 
 )