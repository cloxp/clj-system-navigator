(ns rksm.system-navigator.ns.filemapping-test
  (:refer-clojure :exclude [add-classpath])
  (:require [clojure.test :refer :all]
            [rksm.system-navigator.ns.filemapping :refer :all]
            [clojure.java.io :as io]))

(deftest system-navigator

  (add-classpath "test-resources/dummy-2-test.jar")
  (require 'rksm.system-navigator.test.dummy-1)
  (require 'rksm.system-navigator.test.dummy-2)
  (require 'rksm.system-navigator.test.dummy-3)

  (testing "find loaded namespaces"
    (is (= ['rksm.system-navigator.test.dummy-1
            'rksm.system-navigator.test.dummy-2 
            'rksm.system-navigator.test.dummy-3]
           (loaded-namespaces :matching #"rksm.*dummy-[0-9]$"))))

  (testing "namespace to classpath mapping"
    (testing "for dirs"
      
      (is
        (->> (classpath-for-ns 'rksm.system-navigator.test.dummy-1)
             str
             (re-find #"src/test/clojure$")))
      (is
        (->> (file-for-ns 'rksm.system-navigator.test.dummy-1)
             str
             (re-find #"src/test/clojure/rksm/system_navigator/test/dummy_1.clj$")))))

    (testing "for jars"
      (is
        (->> (classpath-for-ns 'rksm.system-navigator.test.dummy-2)
             str
             (re-find #"test-resources/dummy-2-test.jar$"))))

  (testing "map namespaces to sources"
    (testing "for plain clj files"
      (is (= "(ns rksm.system-navigator.test.dummy-1)\n\n(def x 23)\n"
              (source-for-ns 'rksm.system-navigator.test.dummy-1))))
    (testing "for jars"
      (is (= "(ns rksm.system-navigator.test.dummy-2\n    (:gen-class))\n\n(def y 24)\n"
              (source-for-ns 'rksm.system-navigator.test.dummy-2)))))
  
  (testing "relative namespace paths"
    (is (= "rksm/system_navigator/test/dummy_1.clj"
           (relative-path-for-ns 'rksm.system-navigator.test.dummy-1)))
    (is (= "rksm/system_navigator/test/dummy_2.clj"
           (relative-path-for-ns 'rksm.system-navigator.test.dummy-2))))
  )

; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(comment
 (.getName(relative-path-for-ns 'rksm.system-navigator.test.dummy-2))
  (run-tests 'rksm.system-navigator.ns.filemapping-test)
  (require 'rksm.system-navigator.test.dummy-2)
  (classpath-for-ns 'rksm.system-navigator.test.dummy-2)
  (source-for-ns 'clojure.core)
  (file-for-ns 'rksm.system-navigator.test.dummy-2)
  (file-for-ns 'rksm.system-navigator.test.dummy-1)
  )