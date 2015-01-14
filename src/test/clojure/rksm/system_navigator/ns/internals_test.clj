(ns rksm.system-navigator.ns.internals-test
  (:refer-clojure :exclude [add-classpath])
  (:require [clojure.test :refer :all]
            [rksm.system-navigator.ns.internals :refer :all]
            [rksm.system-navigator.test.dummy-1]))

(deftest ns-internals
  
  (testing "get source for intern"
    (is (= "(def x 23)"
           (source-for-symbol 'rksm.system-navigator.test.dummy-1/x))))
  
  (testing "extract meta entities from source"
    
    (testing "meta entities match source"
      (is (= [{:source "(def x 23)" :column 1,:line 1}
              {:source "(def y 24)" :column 1,:line 2}]
             (let [entities [{:column 1,:line 1} {:column 1,:line 2}]
                   source (java.io.StringReader. "(def x 23)\n(def y 24)\n")]
               (add-source-to-interns-with-reader source entities)))))
    
    (testing "less meta entities than source"
      (is (= [{:source "(def x 23)" :column 1,:line 1}
              {:source "(def y 24)" :column 1,:line 6}]
             (let [entities [{:column 1,:line 1} {:column 1,:line 6}]
                   source (java.io.StringReader. "(def x 23)\n(def baz\n\n99)\n\n(def y 24)\n")]
               (add-source-to-interns-with-reader source entities)))))
    
    (testing "more meta entities than source"
      (is (= [{:source "(def x 23)" :column 1,:line 1}]
             (let [entities [{:column 1,:line 1} {:column 1,:line 6}]
                   source (java.io.StringReader. "(def x 23)")]
               (add-source-to-interns-with-reader source entities)))))
    
    (testing "not entities in source"
      "this might be kind of unexpected but the reader des not care bout lines"
      (is (= [{:source "(def y 24)" :column 1,:line 2}]
             (let [entities [{:column 1,:line 2} {:column 1,:line 6}]
                   source (java.io.StringReader. "(def x 23)\n\n(def y 24)")]
               (add-source-to-interns-with-reader source entities))))))

  (testing "namespace report"
    (let [expected [{:ns 'rksm.system-navigator.test.dummy-1,
                     :name 'x,
                     :file "rksm/system_navigator/test/dummy_1.clj",
                     :source "(def x 23)",
                     :column 1,
                     :line 3,
                     :tag nil}]]
      (is (= expected
             (namespace-info 'rksm.system-navigator.test.dummy-1))))))

; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(comment
  (run-tests 'rksm.system-navigator.ns.internals-test)
  (run-all-tests)
  (ns-interns 'rksm.system-navigator.test.dummy-1)
  (namespace-info 'rksm.system-navigator.test.dummy-1)
  
  (rksm.system-navigator.ns.filemapping/add-classpath "test-resources/dummy-2-test.jar")
  (require 'rksm.system-navigator.test.dummy-1)
  (require 'rksm.system-navigator.test.dummy-2)

  (namespace-info 'rksm.system-navigator.test.dummy-2)
  (require '[clj-stacktrace.repl :refer (pst)])
  (require '[rksm:refer (pst)])
  source-retrieval
  pst
  (source-retrieval 'rksm.system-navigator.test.dummy-1/x)
  (source-for-symbol 'rksm.system-navigator.test.dummy-1/x)
  (meta #'rksm.system-navigator.test.dummy-1/x)
  (pst)
  
  )
