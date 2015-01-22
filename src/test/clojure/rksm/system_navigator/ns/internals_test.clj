(ns rksm.system-navigator.ns.internals-test
  (:refer-clojure :exclude [add-classpath])
  (:require [clojure.test :refer :all]
            [rksm.system-navigator.ns.internals :refer :all]
            [rksm.system-navigator.ns.filemapping :refer (file-name-for-ns)]
            (rksm.system-navigator.test dummy-1 dummy-3)))

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
          expected [{:ns 'rksm.system-navigator.test.dummy-3,
                     :name 'foo,
                     :source "(defn foo [] `~23)",
                     :line 4}
                    {:ns 'rksm.system-navigator.test.dummy-3,
                     :name 'b,
                     :source "(defmacro b [] `~23)"
                     :line 2}]]
      (is (= expected
             (map #(select-keys % [:name :ns :source :line])
                  (read-and-parse src 'rksm.system-navigator.test.dummy-3)))))))

; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-


(deftest source-retrieval
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
               (add-source-to-interns-with-reader source entities)))))))

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