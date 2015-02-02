(ns rksm.system-navigator.system-browser-test
  (:refer-clojure :exclude [add-classpath])
  (:require [clojure.test :refer :all]
            [rksm.system-navigator.system-browser :refer :all]
            [rksm.system-navigator.ns.internals :refer (source-for-symbol namespace-info)]
            [rksm.system-navigator.ns.filemapping :as fm]
            [rksm.system-navigator.changesets :as cs]
            (rksm.system-navigator.test dummy-1 dummy-3)))

(defonce test-file-1 (fm/file-for-ns 'rksm.system-navigator.test.dummy-1))
(defonce test-file-2 (fm/file-for-ns 'rksm.system-navigator.test.dummy-3))
(defonce orig-source-1 (slurp test-file-1))
(defonce orig-source-2 (slurp test-file-2))
(defonce sep java.io.File/separator)
(defonce test-dir (-> (fm/file-for-ns (ns-name *ns*))
                    .getParentFile .getParentFile .getParentFile .getParentFile
                    .getCanonicalPath
                    (str sep "namespace-creation-test")))

; Here we register my-test-fixture to be called once, wrapping ALL tests
; in the namespace
(defn source-state-fixture [test]
  (reset! cs/current-changeset [])
  (require 'rksm.system-navigator.test.dummy-1 :reload)
  (require 'rksm.system-navigator.test.dummy-3 :reload)
  (test)
  (reset! cs/current-changeset [])
  (spit test-file-1 orig-source-1)
  (spit test-file-2 orig-source-2)
  (require 'rksm.system-navigator.test.dummy-1 :reload)
  (require 'rksm.system-navigator.test.dummy-3 :reload))

(use-fixtures :each source-state-fixture)

(defn delete-recursively [fname]
  (let [func (fn [func f]
               (when (.isDirectory f)
                 (doseq [f2 (.listFiles f)]
                   (func func f2)))
               (clojure.java.io/delete-file f))]
    (func func (clojure.java.io/file fname))))

; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(deftest namespace-creation
  
  (let [expected-fn (clojure.string/join sep [test-dir "rksm" "foo" "bar_baz.clj"])]
    
    (try
      
      (testing "create a file"
        (is (= expected-fn
               (create-namespace-and-file 'rksm.foo.bar-baz test-dir)))
        (is (-> expected-fn clojure.java.io/file .exists))
        (is (= "(ns rksm.foo.bar-baz)"
               (-> expected-fn slurp))))
      
      (testing "load namespace"
        (create-namespace-and-file 'rksm.foo.bar-baz test-dir)
        (is (boolean (find-ns 'rksm.foo.bar-baz)))
        (is (boolean (find-ns 'rksm.foo.bar-baz))))
      
      
      (finally
        (do
          (delete-recursively test-dir)
          (remove-ns 'rksm.foo.bar-baz))
        ))))

; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

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

  (let [expected {:file nil
                  :interns
                  [{:ns 'rksm.system-navigator.test.dummy-1,
                    :name 'x,
                    :file "rksm/system_navigator/test/dummy_1.clj",
                    :column 1,
                    :line 3,
                    :source "(def x 24)"
                    :tag nil}]}]
    (is (= expected
           (namespace-info 'rksm.system-navigator.test.dummy-1))
        "intern-info")))

; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(deftest source-location-update
  (do
   (is (= '(3 5 7 11)
          (->> (namespace-info 'rksm.system-navigator.test.dummy-3)
            :interns
            (map :line)))) 
   
   (change-def! 'rksm.system-navigator.test.dummy-3/x
                "(def x\n\n24)" true)
   
   (is (= '(3 7 9 13)
          (->> (namespace-info 'rksm.system-navigator.test.dummy-3)
            :interns
            (map :line))))))

; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(deftest modify-ns-diff-from-runtime
  (testing "diff from runtime changes"
    (require 'rksm.system-navigator.test.dummy-1 :reload)

    (is (= {:added [] :removed [] :changed []}
           (change-ns-in-runtime!
            'rksm.system-navigator.test.dummy-1
            "(ns rksm.system-navigator.test.dummy-1)\n\n(def x 23)"
            orig-source-1)))

    (is (= {:added [] :removed [] :changed [{:ns 'rksm.system-navigator.test.dummy-1, :name 'x, :file "rksm/system_navigator/test/dummy_1.clj", :prev-source "(def x 23)", :source "(def x 24)", :column 1, :line 3, :tag nil}]}
           (change-ns-in-runtime!
            'rksm.system-navigator.test.dummy-1
            "(ns rksm.system-navigator.test.dummy-1)\n\n(def x 24)"
            orig-source-1)))

    (is (= {:added [] :removed [{:ns 'rksm.system-navigator.test.dummy-1, :name 'x, :file "rksm/system_navigator/test/dummy_1.clj", :source "(def x 23)", :column 1, :line 3, :tag nil}] :changed []}
           (change-ns-in-runtime!
            'rksm.system-navigator.test.dummy-1
            "(ns rksm.system-navigator.test.dummy-1)"
            orig-source-1)))
    )
  )

(deftest modify-ns-everything

  (let [new-src
"(ns rksm.system-navigator.test.dummy-3)

(defonce dummy-atom (atom []))

(def x 24)

(defn test-func
[y]
(swap! dummy-atom conj (+ x y 42)))
"]

     (change-ns! 'rksm.system-navigator.test.dummy-3 new-src true)
    ; (change-ns! 'rksm.system-navigator.test.dummy-3 new-src false)

     (testing "evaluation"
       (is (= 24
              (eval 'rksm.system-navigator.test.dummy-3/x)))
       (is (= 67
              (last (eval '(rksm.system-navigator.test.dummy-3/test-func 1))))))

    (testing "write to file"
      (is (= new-src (slurp test-file-2))
          "written source"))


    (is (= 1 (count (cs/get-changes 'rksm.system-navigator.test.dummy-3))))

    (testing "recorded change, ns part"
     (let [change (first (cs/get-changes 'rksm.system-navigator.test.dummy-3))
           ns-part (select-keys change [:sym])]
       (is (= {:sym 'rksm.system-navigator.test.dummy-3}
              ns-part))))

    (testing "recorded change, diff part"
      (let [change (first (cs/get-changes 'rksm.system-navigator.test.dummy-3))
            expected {:added [],
                      :removed
                      '({:ns rksm.system-navigator.test.dummy-3,:name foo,
                         :file "rksm/system_navigator/test/dummy_3.clj",
                         :source "(defmacro foo\n  [x & body]\n  `(foo ~x ~@body))",
                         :column 1,:line 11,:macro true,:tag nil,:arglists ([x & body])}),
                      :changed
                      '({:ns rksm.system-navigator.test.dummy-3,:name x,
                         :file "rksm/system_navigator/test/dummy_3.clj",
                         :prev-source "(def x 23)",:source "(def x 24)",
                         :column 1,:line 5,:tag nil}
                        {:ns rksm.system-navigator.test.dummy-3,:name test-func,
                         :file "rksm/system_navigator/test/dummy_3.clj",
                         :prev-source "(defn test-func\n  [y]\n  (swap! dummy-atom conj (+ x y)))",
                         :source"(defn test-func\n[y]\n(swap! dummy-atom conj (+ x y 42)))",
                         :column 1,:line 7,:tag nil,:arglists ([y])})}]
        (is (= expected (:changes change)))))

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
       (is (= expected
              (:interns (namespace-info 'rksm.system-navigator.test.dummy-3))))
       )))

(deftest modify-ns-with-known-file

  (let [new-src "(ns rksm.system-navigator.test.dummy-3) (def x 25)"]

     (change-ns! 'rksm.system-navigator.test.dummy-3 new-src true
                 (fm/file-name-for-ns 'rksm.system-navigator.test.dummy-3))
    ; (change-ns! 'rksm.system-navigator.test.dummy-3 new-src false)

     (testing "evaluation"
       (is (= 25
              (eval 'rksm.system-navigator.test.dummy-3/x))))

    (testing "recorded change, diff part"
      (let [changes (-> (cs/get-changes 'rksm.system-navigator.test.dummy-3) first :changes)
            counts (into {} (for [[k v] changes] [k (count v)]))
            expected {:added 0, :removed 3, :changed 1}]
        (is (= expected counts))))))

; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(deftest diff-ns-test

  (testing "no  changes"
    (is (= {:added [] :removed [] :changed []}
           (diff-ns 'foo
                    "(ns foo)\n(def x 23)" "(ns foo)\n(def x 23)"
                    [{:ns 'foo, :name 'x,:file "foo.clj",:column 1,:line 2,:tag nil}]
                    [{:ns 'foo, :name 'x,:file "foo.clj",:column 1,:line 2,:tag nil}]
                    []))))

  (testing "addition"
    (is (= {:added [{:ns 'foo, :name 'y,:file "foo.clj",:column 1,:line 3,:tag nil}]
            :removed [] :changed []}
           (diff-ns 'foo
                    "(ns foo)\n(def x 23)\n(def y 24)" "(ns foo)\n(def x 23)"
                    [{:ns 'foo, :name 'x,:file "foo.clj",:column 1,:line 2,:tag nil}
                     {:ns 'foo, :name 'y,:file "foo.clj",:column 1,:line 3,:tag nil}]
                    [{:ns 'foo, :name 'x,:file "foo.clj",:column 1,:line 2,:tag nil}]
                    []))))

  (testing "removal"
    (is (= {:added []
            :removed [{:source "(def y 24)":ns 'foo, :name 'y,:file "foo.clj",:column 1,:line 3,:tag nil}]
            :changed []}
           (diff-ns 'foo
                     "(ns foo)\n(def x 23)" "(ns foo)\n(def x 23)\n(def y 24)"
                     [{:ns 'foo, :name 'x,:file "foo.clj",:column 1,:line 2,:tag nil}]
                     [{:ns 'foo, :name 'x,:file "foo.clj",:column 1,:line 2,:tag nil}
                      {:ns 'foo, :name 'y,:file "foo.clj",:column 1,:line 3,:tag nil}]
                     []))))

  (testing "changed"

   (testing "source change"
     (is (= {:added [] :removed []
             :changed [{:ns 'foo, :name 'x,:file "foo.clj",:column 1,:line 2,:tag nil
                        :source "(def x 24)" :prev-source "(def x 23)"}
                       {:ns 'foo, :name 'y,:file "foo.clj",:column 1,:line 3,:tag nil
                        :source "(def y 99)" :prev-source "(def y 98)"}]}
            (diff-ns 'foo
                     "(ns foo)\n(def x 24)\n(def y 99)" "(ns foo)\n(def x 23)\n(def y 98)"
                     [{:ns 'foo, :name 'x,:file "foo.clj",:column 1,:line 2,:tag nil}
                      {:ns 'foo, :name 'y,:file "foo.clj",:column 1,:line 3,:tag nil}]
                     [{:ns 'foo, :name 'x,:file "foo.clj",:column 1,:line 2,:tag nil}
                      {:ns 'foo, :name 'y,:file "foo.clj",:column 1,:line 3,:tag nil}]
                     [{:ns 'foo :name 'x}{:ns 'foo :name 'y}])))))
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