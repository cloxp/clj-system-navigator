(ns rksm.system-navigator.search-test
  (:require [clojure.test :refer :all]
            [rksm.system-navigator.search :refer :all]
            [clojure.java.io :as io]))

(deftest search

  (require 'rksm.system-navigator.test.dummy-1)
  (require 'rksm.system-navigator.test.dummy-2)

  (let [ns-1 'rksm.system-navigator.test.dummy-1
        ns-2 'rksm.system-navigator.test.dummy-2]

    (testing "finds nothing"
      (let [search (code-search-ns #"def y" ns-1)
            expected {:ns ns-1, :finds []}]
        (is (= search expected))))

    (testing "find code in namespace files"
      (let [search (code-search-ns #"def x" ns-1)
            expected {:ns ns-1
                      :finds [{:line 4
                               :match "def x"
                               :source "(def x 23)"}]}]
        (is (= search expected))))

))

; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(comment
  (run-tests 'rksm.system-navigator.search-test)
  )
