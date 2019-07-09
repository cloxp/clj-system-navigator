(ns rksm.system-navigator.completions
  (:require [compliment.core :as comp]
            [clojure.data.json :as json]
            [hara.reflect.core.query :as q]))

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
  (pr-str cl))

(defn member-info
  [ele]
  {:name (:name ele)
   :params (->> (:params ele) (map class->string))
   :type (class->string (:type ele))})

(defn instance-elements
  [obj]
  (as-> obj it
    (q/query-instance it ".*")
    (map member-info it)))

(defn instance-elements->json
  [obj]
  (json/json-str (instance-elements obj)))

(comment
 (merge-with concat {:foo [23]} {:foo [34]})
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
 (ip/class-convert (type "foo") :string)
 
 (hash-map :foo 123)
 (->> (q/query-instance "foo" ".*") first ((fn [ele] (hash-map (:name ele) (:params ele)))) merge)
 (-> (q/query-instance "foo" ".*") first :type)
 (q/query-instance "foo" ".*")
 (-> (q/query-instance "foo" ".*") first)
 (-> (q/query-instance "foo" ".*") first (select-keys [:name]))
 (-> (q/query-instance "foo" ".*") first :params)
 (-> (q/query-instance "foo" ".*") first )
 )
