(ns rksm.system-navigator.changesets-test
  (:refer-clojure :exclude [add-classpath])
  (:require [clojure.test :refer :all]
            [rksm.system-navigator.changesets :refer :all]
            [rksm.system-navigator.test.dummy-1]))

(defn source-state-fixture [test]
  (reset! current-changeset [])
  (require 'rksm.system-navigator.test.dummy-1 :reload)
  (test)
  (reset! current-changeset []))

; Here we register my-test-fixture to be called once, wrapping ALL tests
; in the namespace
(use-fixtures :each source-state-fixture)

(deftest changesets
  
  (reset! current-changeset [])
  
  (testing "changeset record affects source retrieval"
    (record-change! 'rksm.system-navigator.test.dummy-1/x
                    "(def x 24)")
    (is (= "(def x 24)"
           (source-for-symbol 'rksm.system-navigator.test.dummy-1/x))))
)

