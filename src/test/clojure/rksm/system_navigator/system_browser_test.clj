(ns rksm.system-navigator.system-browser-test
  (:refer-clojure :exclude [add-classpath])
  (:require [clojure.test :refer :all]
            [rksm.system-navigator.system-browser :refer :all]
            [rksm.system-navigator.ns.internals :refer (source-for-symbol namespace-info)]
            [rksm.system-navigator.ns.filemapping :as fm]
            [rksm.system-navigator.changesets :as cs]
            (rksm.system-navigator.test dummy-1 dummy-3)))

(def test-file-1 (fm/file-for-ns 'rksm.system-navigator.test.dummy-1))
(def test-file-2 (fm/file-for-ns 'rksm.system-navigator.test.dummy-3))

(defn source-state-fixture [test]
  (let [orig-source-1 (slurp test-file-1)
        orig-source-2 (slurp test-file-2)]
    (reset! cs/current-changeset [])
    (require 'rksm.system-navigator.test.dummy-1 :reload)
    (require 'rksm.system-navigator.test.dummy-3 :reload)
    (test)
    (reset! cs/current-changeset [])
    (spit test-file-1 orig-source-1)
    (spit test-file-2 orig-source-2)
    (remove-ns 'rksm.system-navigator.test.dummy-1)
    (remove-ns 'rksm.system-navigator.test.dummy-3)))

; Here we register my-test-fixture to be called once, wrapping ALL tests
; in the namespace
(use-fixtures :each source-state-fixture)

(deftest modify-ns-internals
  
  (change-def! 'rksm.system-navigator.test.dummy-1/x
               "(def x 24)" true)
  
  (is (= 24
         (eval 'rksm.system-navigator.test.dummy-1/x))
      "evaluation")
  
  (is (= "(def x 24)"
         (source-for-symbol 'rksm.system-navigator.test.dummy-1/x))
      "source retrieval")
  
  (is (= "(ns rksm.system-navigator.test.dummy-1)\n\n(def x 24)"
         (slurp test-file-1))
      "written source")
  
  (is (= 1
         (count (cs/get-changes 'rksm.system-navigator.test.dummy-1/x)))
      "change set recording")
  
  (let [expected [{:ns 'rksm.system-navigator.test.dummy-1,
                   :name 'x,
                   :file "rksm/system_navigator/test/dummy_1.clj",
                   :column 1,
                   :line 3,
                   :tag nil}]]
    (is (= expected
           (namespace-info 'rksm.system-navigator.test.dummy-1))
        "intern-info")))

; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(deftest modify-ns
  (let [new-src
        "(ns rksm.system-navigator.test.dummy-3)
        
        (defonce dummy-atom (atom []))
        
        (def x 24)
        
        (defn test-func
        [y]
        (swap! dummy-atom conj (+ x y 42)))
        "]
    
    (change-ns! 'rksm.system-navigator.test.dummy-3 new-src true)
    
    (testing "evaluation"     
      (is (= 24
             (eval 'rksm.system-navigator.test.dummy-3/x)))
      (is (= 67
             (last (eval '(rksm.system-navigator.test.dummy-3/test-func 1))))))
    
    (is (= new-src (slurp test-file-2))
        "written source")
    
    (is (= 1 (count (cs/get-changes 'rksm.system-navigator.test.dummy-3))))
    
    (let [expected [{:tag nil,
                     :ns 'rksm.system-navigator.test.dummy-3,
                     :name 'dummy-atom,
                     :file "rksm/system_navigator/test/dummy_3.clj",
                     :column 1,
                     :line 3}
                    {:ns 'rksm.system-navigator.test.dummy-3,
                     :name 'x,
                     :file "rksm/system_navigator/test/dummy_3.clj",
                     :column 1,
                     :line 5,
                     :tag nil}
                    {:ns 'rksm.system-navigator.test.dummy-3,
                     :name 'test-func,
                     :file "rksm/system_navigator/test/dummy_3.clj",
                     :column 1,
                     :line 7,
                     :tag nil,
                     :arglists '([y])}]]
      (println (clojure.data/diff expected (namespace-info 'rksm.system-navigator.test.dummy-3)))
      (is (= expected
             (namespace-info 'rksm.system-navigator.test.dummy-3)))
      )))

; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(deftest diff-ns-test
  
  (testing "no  changes"
    (is (= {:added [] :removed [] :changed []}
           (diff-ns 'foo
                    "(ns foo)\n(def x 23)" "(ns foo)\n(def x 23)"
                    [{:ns 'foo, :name 'x,:file "foo.clj",:column 1,:line 2,:tag nil}]
                    [{:ns 'foo, :name 'x,:file "foo.clj",:column 1,:line 2,:tag nil}]))))
  
  (testing "addition"
    (is (= {:added [{:ns 'foo, :name 'y,:file "foo.clj",:column 1,:line 3,:tag nil}]
            :removed [] :changed []}
           (diff-ns 'foo
                    "(ns foo)\n(def x 23)\n(def y 24)" "(ns foo)\n(def x 23)"
                    [{:ns 'foo, :name 'x,:file "foo.clj",:column 1,:line 2,:tag nil}
                     {:ns 'foo, :name 'y,:file "foo.clj",:column 1,:line 3,:tag nil}]
                    [{:ns 'foo, :name 'x,:file "foo.clj",:column 1,:line 2,:tag nil}]))))
  
  (testing "removal"
    (is (= {:added []
            :removed [{:ns 'foo, :name 'y,:file "foo.clj",:column 1,:line 3,:tag nil}]
            :changed []}
           (diff-ns 'foo
                    "(ns foo)\n(def x 23)" "(ns foo)\n(def x 23)\n(def y 24)"
                    [{:ns 'foo, :name 'x,:file "foo.clj",:column 1,:line 2,:tag nil}] 
                    [{:ns 'foo, :name 'x,:file "foo.clj",:column 1,:line 2,:tag nil}
                     {:ns 'foo, :name 'y,:file "foo.clj",:column 1,:line 3,:tag nil}]))))
  
  (testing "changed"

   (testing "source change"
     (is (= {:added [] :removed []
             :changed [{:ns 'foo, :name 'x,:file "foo.clj",:column 1,:line 2,:tag nil
                        :source "(def x 24)" :prev-source "(def x 23)"}
                       {:ns 'foo, :name 'y,:file "foo.clj",:column 1,:line 3,:tag nil
                        :source "(def y 99)" :prev-source "(def y 98)"}]}
            (diff-ns 'foo
                     "(ns foo)\n(def x 23)\n(def y 98)" "(ns foo)\n(def x 24)\n(def y 99)"
                     [{:ns 'foo, :name 'x,:file "foo.clj",:column 1,:line 2,:tag nil}
                      {:ns 'foo, :name 'y,:file "foo.clj",:column 1,:line 3,:tag nil}]
                     [{:ns 'foo, :name 'x,:file "foo.clj",:column 1,:line 2,:tag nil}
                      {:ns 'foo, :name 'y,:file "foo.clj",:column 1,:line 3,:tag nil}])))))
  )

; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(comment
      (run-tests 'rksm.system-navigator.system-browser-test)
      (ns-interns 'rksm.system-navigator.system-browser-test)

      (clojure.test/test-var #'diff-ns-test)

      (let [new-src
"(ns rksm.system-navigator.test.dummy-3)

(defonce dummy-atom (atom []))

(def x 24)

(defn test-func
  [y]
  (swap! dummy-atom conj (+ x y 42)))
"]
      (change-ns! 'rksm.system-navigator.test.dummy-3 new-src false))
      (remove-ns 'rksm.system-navigator.test.dummy-3)
      (namespace-info 'rksm.system-navigator.test.dummy-3)
 )