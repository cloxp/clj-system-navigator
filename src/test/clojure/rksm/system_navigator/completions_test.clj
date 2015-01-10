(ns rksm.system-navigator.completions-test
  (:refer-clojure :exclude [add-classpath])
  (:require [clojure.test :refer :all]
            [rksm.system-navigator.completions :refer :all]))

(deftest completions

  (testing "gets instance methods of a string"
    (is (= ({:name "equals",
             :params ["java.lang.String" "java.lang.Object"],
             :type "boolean"}
            {:name "equals",
             :params ["java.lang.Object" "java.lang.Object"],
             :type "boolean"})
           (get (instance-elements "foo") "trim"))))

)

; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(comment
  (run-tests 'rksm.system-navigator.completions-test)

  (filter #(= "equals" (:name %)) (instance-elements "foo"))
  (-> (instance-elements "foo") first key type)
  )
