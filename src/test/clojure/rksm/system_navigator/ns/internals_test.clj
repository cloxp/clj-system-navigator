(ns rksm.system-navigator.ns.internals-test
  (:refer-clojure :exclude [add-classpath])
  (:require [clojure.test :refer :all]
            [rksm.system-navigator.ns.internals :refer :all]
            [rksm.cloxp-source-reader.core :as src-rdr]
            [rksm.cloxp-source-reader.ast-reader :as ast-rdr]
            [rksm.system-files :refer (file-name-for-ns)]
            (rksm.system-navigator.test dummy-1 dummy-3)))


(deftest source-retrieval
  (testing "get source for intern"
    (is (= "(def x 23)"
           (source-for-symbol 'rksm.system-navigator.test.dummy-1/x)))))

(deftest ns-internals
  
  (testing "namespace report"
    (let [expected {:file nil
                    :interns [{:ns 'rksm.system-navigator.test.dummy-1,
                               :name 'x,
                               :file "rksm/system_navigator/test/dummy_1.clj",
                               :column 1,
                               :line 3,
                               :tag nil}]}]
      (is (= expected
             (namespace-info 'rksm.system-navigator.test.dummy-1)))))
  
  (testing "namespace report ignores interns without file"
    (let [expected {:file nil
                    :interns [{:ns 'rksm.system-navigator.test.dummy-1,
                               :name 'x,
                               :file "rksm/system_navigator/test/dummy_1.clj",
                               :column 1,
                               :line 3,
                               :tag nil}]}]
      (eval '(let [bindings {'*source-path* "NO_SOURCE_FILE"}]
               (in-ns 'rksm.system-navigator.test.dummy-1) (def z 99)))
      (is (= expected
             (namespace-info 'rksm.system-navigator.test.dummy-1))))))

; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(comment
  (run-tests 'rksm.system-navigator.ns.internals-test)
)