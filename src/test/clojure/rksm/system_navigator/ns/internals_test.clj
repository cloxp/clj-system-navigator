(ns rksm.system-navigator.ns.internals-test
  (:refer-clojure :exclude [add-classpath])
  (:require [clojure.test :refer :all]
            [rksm.system-navigator.ns.internals :refer :all]
            [rksm.system-navigator.test.dummy-1]))

(deftest ns-internals

  (testing "get source for intern"
    (is (= "(def x 23)\n"
           (source-for-symbol 'rksm.system-navigator.test.dummy-1/x))))

  (testing "namespace report"
    (let [expected [{:ns 'rksm.system-navigator.test.dummy-1,
                     :name 'x,
                     :file "rksm/system_navigator/test/dummy_1.clj",
                     :source "(def x 23)\n",
                     :column 1,
                     :line 3,
                     :tag nil}]]
      (is (= expected
             (namespace-info 'rksm.system-navigator.test.dummy-1))))))

; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(comment
  (run-tests 'rksm.system-navigator.ns.internals-test)
  (run-all-tests)
  )
