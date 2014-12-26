(ns rksm.system-navigator.test
  (:require [clojure.test :refer :all]
            [rksm.system-navigator.core :refer :all]
            [clojure.java.io :as io]))

(deftest system-navigator

  (testing "discover namespace sources"
    (require 'rksm.system-navigator.test.dummy-1)
    (is (->> (sys-nav/classpath-for-ns 'rksm.system-navigator.test.dummy-1)
             str
             (re-find #"src/test/clojure$")))
    (is (->> (sys-nav/file-name-for-ns 'rksm.system-navigator.test.dummy-1)
             str
             (re-find #"src/test/clojure/rksm/system_navigator/test/dummy_1.clj$")))))

; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(comment
  (run-tests 'rksm.system-navigator.test)
  )
