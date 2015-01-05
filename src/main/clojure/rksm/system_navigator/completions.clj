(ns rksm.system-navigator.completions
  (:require [compliment.core :as comp]
            [clojure.data.json :as json]
            [iroh.core :as ic]
            [iroh.pretty.classes :as ip]))

; delegate - for transparency into objects
; >ns - for importing object elements into a namespace
; >var - for importing elements into current namespace
; .% - for showing class properties
; .%> - for showing type hierarchy
; .? - for showing class elements
; .* - for showing instance elements
; .> - threading macro for reflective invocation of objects

(defn get-completions
  [string & [context]]
  (let [compl (comp/completions string context)
        doc (map comp/documentation compl)]
    (zipmap compl doc)))

(defn get-completions->json
  [string & [context]]
  (json/json-str (get-completions string)))

(defn class->string
  [cl]
  (ip/class-convert cl :string))

(defn member-info
  [ele]
  {:name (:name ele)
   :params (->> (:params ele) (map class->string))
   :type (class->string (:type ele))})

(defn instance-elements
  [obj]
  (->> obj ic/.* (map member-info)))

(defn instance-elements->json
  [obj]
  (json/json-str (instance-elements obj)))

(comment
 (merge-with concat {:foo [23]} {:foo [34]})
 (-> (ic/.* "foo") last member-info)
 (get-completions->json "rk")
 (instance-elements "foo")
 (let [elems (instance-elements "foo")]
   (filter
    (fn [el]
      (some
       (fn [other-el]
         (and (= (:name el) (:name other-el))
              (not= (:type el) (:type other-el))))
       elems))
    elems))
 (filter )

 (get (instance-elements "foo") "equals")
 (instance-elements->json "foo")
 (reduce conj)
 (hash-map :foo 123)
 (->> (ic/.* "foo") first ((fn [ele] (hash-map (:name ele) (:params ele)))) merge)
 (-> (ic/.* "foo") first :type)
 (ic/.* "foo")
 (-> (ic/.* "foo") first)
 (-> (ic/.* "foo") first (select-keys [:name]))
 (-> (ic/.* "foo") first :params)
 (-> (ic/.* "foo") first )
 )
