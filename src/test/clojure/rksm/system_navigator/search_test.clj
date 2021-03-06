(ns rksm.system-navigator.search-test
  (:refer-clojure :exclude [add-classpath])
  (:require [clojure.test :refer :all]
            [rksm.system-navigator.search :refer :all]
            [rksm.system-files :refer (add-classpath)]
            [clojure.java.io :as io]))

(deftest search

  (require 'rksm.system-navigator.test.dummy-1)
  (add-classpath "test-resources/dummy-2-test.jar")
  (require 'rksm.system-navigator.test.dummy-2)

  (let [ns-1 'rksm.system-navigator.test.dummy-1
        ns-2 'rksm.system-navigator.test.dummy-2
        ns-3 'rksm.system-navigator.test.dummy-3]

    (testing "finds nothing"
      (let [search (code-search-ns #"def y" ns-1)
            expected [{:ns ns-1, :finds []}]]
        (is (= search expected))))

    (testing "find code in namespace files"
      (let [search (code-search-ns #"def x" ns-1)
            expected [{:ns ns-1
                       :finds [{:line 4
                               :match "def x"
                               :source "(def x 23)"}]}]]
        (is (= search expected))))

    (testing "matches and searches mutliple namespaces"
      (let [search (code-search #"def (x|y)" :match-ns #"rksm.*dummy-[0-9]$")
            expected [{:ns ns-1,
                       :finds [{:line 4, :match ["def x" "x"], :source "(def x 23)"}]}
                      {:ns ns-2,
                        :finds [{:line 5, :match ["def y" "y"], :source "(def y 24)"}]}
                      {:ns ns-3,
                       :finds [{:line 4, :match ["def x" "x"], :source "(def x 23)"}]}]]
      (is (= search expected))))
))

; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(comment
  (run-tests)
  )
