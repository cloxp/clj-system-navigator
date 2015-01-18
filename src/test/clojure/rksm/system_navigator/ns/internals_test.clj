(ns rksm.system-navigator.ns.internals-test
  (:refer-clojure :exclude [add-classpath])
  (:require [clojure.test :refer :all]
            [rksm.system-navigator.ns.internals :refer :all]
            [rksm.system-navigator.test.dummy-1]))

(deftest reading

  (testing "simple read"
    (is (= [{:form '(ns rksm.system-navigator.test.dummy-3),
             :source "(ns rksm.system-navigator.test.dummy-3)",
             :line 1,
             :column 1}
            {:form '(def x 23), :source "(def x 23)", :line 2, :column 3}]
         (read-objs "(ns rksm.system-navigator.test.dummy-3)\n  (def x 23)\n")))))

; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(deftest parsing

  (testing "parse source"
    (let [src "(ns rksm.system-navigator.test.dummy-3)\n  (defmacro b [] `~23)\n(+ 2 3)\n(defn foo [] `~23)\n"
          expected [{:locals {}, :ns 'rksm.system-navigator.test.dummy-3,
                     :name 'foo, :file "NO_SOURCE_PATH",
                     :form '(defn foo [] 23), :source "(defn foo [] `~23)",
                     :end-column 19, :column 1, :line 4, :end-line 4,
                     :context :ctx.invoke/param}
                    {:locals {}, :ns 'rksm.system-navigator.test.dummy-3,
                     :name 'b, :file "NO_SOURCE_PATH",
                     :source "(defmacro b [] `~23)",
                     :form '(defmacro b [] 23)
                     :end-column 23, :column 3, :line 2, :end-line 2,
                     :context :ctx/statement}]]
      (is (= expected
             (read-and-parse src 'rksm.system-navigator.test.dummy-3))))))

; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

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
      (is (= [{:source "(def x 23)" :column 1,:line 1} {:source "" :column 1,:line 6}]
             (let [entities [{:column 1,:line 1} {:column 1,:line 6}]
                   source (java.io.StringReader. "(def x 23)")]
               (add-source-to-interns-with-reader source entities)))))
    
    (testing "not entities in source"
      "this might be kind of unexpected but the reader des not care bout lines"
      (is (= [{:source "(def y 24)" :column 1,:line 3} {:source "" :column 1,:line 6}]
             (let [entities [{:column 1,:line 3} {:column 1,:line 6}]
                   source (java.io.StringReader. "(def x 23)\n\n(def y 24)")]
               (add-source-to-interns-with-reader source entities))))))

  (testing "namespace report"
    (let [expected [{:ns 'rksm.system-navigator.test.dummy-1,
                     :name 'x,
                     :file "rksm/system_navigator/test/dummy_1.clj",
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

  (namespace-info 'rksm.system-navigator.ns.internals)
  (require '[clj-stacktrace.repl :refer (pst)])
  (require '[rksm:refer (pst)])
  source-retrieval
  pst
  (source-retrieval 'rksm.system-navigator.test.dummy-1/x)
  (source-for-symbol 'rksm.system-navigator.test.dummy-1/x)
  (meta #'rksm.system-navigator.test.dummy-1/x)
  (pst)
  
  )
