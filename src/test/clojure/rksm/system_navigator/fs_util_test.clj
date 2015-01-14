(ns rksm.system-navigator.fs-util-test
  (:refer-clojure :exclude [add-classpath])
  (:require [clojure.test :refer :all]
            [rksm.system-navigator.fs-util :refer :all]
            [rksm.system-navigator.ns.filemapping :refer (classpath-for-ns file-for-ns)]))

(deftest ns-internals

  (testing "get relative path"
    (is (= "baz.txt"
           (path-relative-to "/foo/bar/" "/foo/bar/baz.txt")))

    (is (= "baz/zork.txt"
           (path-relative-to "/foo/bar/" "/foo/bar/baz/zork.txt")))

    (is (= "../baz/zork.txt"
           (path-relative-to "/foo/bar/" "/foo/baz/zork.txt")))

    (is (= "rksm/system_navigator/ns/internals.clj"
           (path-relative-to 
            (classpath-for-ns 'rksm.system-navigator.ns.internals)
            (file-for-ns 'rksm.system-navigator.ns.internals)))))
  )

; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(comment
 "clojure/rksm/system_navigator/ns/internals.clj"
  
  )
