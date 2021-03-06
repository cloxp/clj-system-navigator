(ns rksm.system-navigator.system-browser-test
  (:refer-clojure :exclude [add-classpath])
  (:require [clojure.test :refer :all]
            [rksm.system-navigator.system-browser :refer :all]
            [rksm.system-navigator.ns.internals :refer (source-for-symbol namespace-info)]
            [rksm.system-files :as fm]
            [rksm.system-navigator.changesets :as cs]
            [clojure.java.io :as io]))


(require 'rksm.system-navigator.test.cljc-dummy :reload)

(defonce test-file-1 (fm/file-for-ns 'rksm.system-navigator.test.dummy-1))
(defonce test-file-2 (fm/file-for-ns 'rksm.system-navigator.test.dummy-3))
(defonce test-file-3 (fm/file-for-ns 'rksm.system-navigator.test.cljc-dummy))
(defonce orig-source-1 (slurp test-file-1))
(defonce orig-source-2 (slurp test-file-2))
(defonce orig-source-3 (slurp test-file-3))
(defonce sep java.io.File/separator)
(defonce test-dir (-> (fm/file-for-ns 'rksm.system-navigator.system-browser-test)
                    .getParentFile .getParentFile .getParentFile .getParentFile
                    .getCanonicalPath
                    (str sep "namespace-creation-test")))
(defonce project-dir (-> (fm/file-for-ns 'rksm.system-navigator.system-browser-test)
                       .getParentFile .getParentFile
                       .getParentFile .getParentFile
                       .getParentFile .getParentFile
                    .getCanonicalPath))

(defn reset-test-state!
  []
  (spit test-file-1 orig-source-1)
  (spit test-file-2 orig-source-2)
  (spit test-file-3 orig-source-3)
  (require 'rksm.system-navigator.test.dummy-1 :reload)
  (require 'rksm.system-navigator.test.dummy-3 :reload)
  (require 'rksm.system-navigator.test.cljc-dummy :reload)
  (reset! cs/current-changeset []))

; Here we register my-test-fixture to be called once, wrapping ALL tests
; in the namespace
(defn source-state-fixture [test]
  (reset-test-state!)
  (test)
  (reset-test-state!))

(use-fixtures :each source-state-fixture)

(defn delete-recursively [fname]
  (let [func (fn [func f]
               (when (.isDirectory f)
                 (doseq [f2 (.listFiles f)]
                   (func func f2)))
               (clojure.java.io/delete-file f))]
    (func func (clojure.java.io/file fname))))

(defmacro codify
  [& body]
  `(clojure.string/join "\n" (map str '~body)))

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
          (remove-ns 'rksm.foo.bar-baz))))))

; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(deftest modify-ns-internals

  (change-def! 'rksm.system-navigator.test.dummy-1/x
               "(def x 24)" true)

  (is (= 24
         (eval 'rksm.system-navigator.test.dummy-1/x))
      "evaluation")

  (is (= "(def x 24)\n"
         (source-for-symbol 'rksm.system-navigator.test.dummy-1/x))
      "source retrieval")

  (is (= "(ns rksm.system-navigator.test.dummy-1)\n\n(def x 24)"
         (slurp test-file-1))
      "written source")

  (is (= 1
         (count (cs/get-changes 'rksm.system-navigator.test.dummy-1/x)))
      "change set recording")
  
  (let [{ns-file :file [{:keys [ns name file line column source]}] :interns}
        (namespace-info 'rksm.system-navigator.test.dummy-1)]
    (is (= [nil
            'rksm.system-navigator.test.dummy-1, 'x,
            "rksm/system_navigator/test/dummy_1.clj"
            3, 1, "(def x 24)\n"]
           [ns-file ns name file line column source]))))

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

    (is (= {:added [] :removed [] :changed [{:ns 'rksm.system-navigator.test.dummy-1, :name 'x, :file "rksm/system_navigator/test/dummy_1.clj", :prev-source "(def x 23)\n", :source "(def x 24)", :column 1, :line 3, :end-column 11, :end-line 3}]}
           (change-ns-in-runtime!
            'rksm.system-navigator.test.dummy-1
            "(ns rksm.system-navigator.test.dummy-1)\n\n(def x 24)"
            orig-source-1)))

    (is (= {:added [] :removed [{:ns 'rksm.system-navigator.test.dummy-1, :name 'x, :file "rksm/system_navigator/test/dummy_1.clj", :source "(def x 23)\n", :column 1, :line 3, :tag nil}] :changed []}
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
             (-> 'rksm.system-navigator.test.dummy-3/x find-var deref)))
      (is (= 67
             (last ((-> 'rksm.system-navigator.test.dummy-3/test-func find-var deref) 1))))
      (is (nil? (find-var 'rksm.system-navigator.test.dummy-3/foo))))
    
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
                         :source "(defmacro foo\n  [x & body]\n  `(foo ~x ~@body))\n",
                         :column 1,:line 11,:macro true,:tag nil, :arglists ([x & body])}),
                      :changed
                      '({:ns rksm.system-navigator.test.dummy-3,:name x,
                         :file "rksm/system_navigator/test/dummy_3.clj",
                         :prev-source "(def x 23)\n",:source "(def x 24)\n",
                         :column 1,:line 5, :end-column 1, :end-line 6}
                        {:ns rksm.system-navigator.test.dummy-3,:name test-func,
                         :file "rksm/system_navigator/test/dummy_3.clj",
                         :prev-source "(defn test-func\n  [y]\n  (swap! dummy-atom conj (+ x y)))\n",
                         :source"(defn test-func\n[y]\n(swap! dummy-atom conj (+ x y 42)))\n",
                         :column 1,:line 7, :end-column 1, :end-line 10})}]
        (is (= expected (:changes change)))))

    (let [expected [{:ns 'rksm.system-navigator.test.dummy-3, :name 'dummy-atom,
                     :file "rksm/system_navigator/test/dummy_3.clj",
                     :line 3,:column 1 :source "dummy-atom"}
                    {:ns 'rksm.system-navigator.test.dummy-3, :name 'x,
                     :file "rksm/system_navigator/test/dummy_3.clj",
                     :line 5, :column 1,
                     :source "(def x 24)\n"}
                    {:ns 'rksm.system-navigator.test.dummy-3,
                     :name 'test-func,
                     :file "rksm/system_navigator/test/dummy_3.clj",
                     :line 7 :column 1,
                     :source "(defn test-func\n[y]\n(swap! dummy-atom conj (+ x y 42)))\n",}]]
       (is (= expected
              (->> 'rksm.system-navigator.test.dummy-3
                namespace-info :interns
                (map #(select-keys % [:ns :name :file :line :column :source]))))))))


(deftest modify-ns-with-known-file

  (let [new-src "(ns rksm.system-navigator.test.dummy-3) (def x 25)"]

     (change-ns! 'rksm.system-navigator.test.dummy-3 new-src true
                 (fm/file-name-for-ns 'rksm.system-navigator.test.dummy-3))

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

  (testing "no changes"
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
            :removed [{:source "(def y 24)\n":ns 'foo, :name 'y,:file "foo.clj",:column 1,:line 3,:tag nil}]
            :changed []}
           (diff-ns 'foo
                    "(ns foo)\n(def x 23)" "(ns foo)\n(def x 23)\n(def y 24)"
                    [{:ns 'foo, :name 'x,:file "foo.clj",:column 1,:line 2,:tag nil}]
                    [{:ns 'foo, :name 'x,:file "foo.clj",:column 1,:line 2,:tag nil}
                     {:ns 'foo, :name 'y,:file "foo.clj",:column 1,:line 3,:tag nil}]
                    []))))

  (testing "changed"

    (is (= {:added [] :removed []
            :changed [{:ns 'foo, :name 'x,:file "foo.clj",:column 1,:line 2, :end-column 1, :end-line 3
                       :source "(def x 24)\n" :prev-source "(def x 23)\n"}
                      {:ns 'foo, :name 'y,:file "foo.clj",:column 1,:line 3, :end-column 1, :end-line 4
                       :source "(def y 99)\n" :prev-source "(def y 98)\n"}]}
           (diff-ns 'foo
                    "(ns foo)\n(def x 24)\n(def y 99)" "(ns foo)\n(def x 23)\n(def y 98)"
                    [{:ns 'foo, :name 'x,:file "foo.clj",:column 1,:line 2,:tag nil}
                     {:ns 'foo, :name 'y,:file "foo.clj",:column 1,:line 3,:tag nil}]
                    [{:ns 'foo, :name 'x,:file "foo.clj",:column 1,:line 2,:tag nil}
                     {:ns 'foo, :name 'y,:file "foo.clj",:column 1,:line 3,:tag nil}]
                    [{:ns 'foo :name 'x}{:ns 'foo :name 'y}])))))

; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
; multimethods

(deftest modify-ns-with-multi-methods

  (testing "modifying defmulti"
    (remove-ns 'multi-test-2)
    (let [f (java.io.File/createTempFile "multi_test_2" ".clj")
          source-1 (codify (ns multi-test-2)
                           (defmulti multi-f (fn [x & _] x))
                           (defmethod multi-f :a [_ x] (+ x 1))
                           (defmethod multi-f :b [_ x] (+ x 2)))
          source-2 (codify (ns multi-test-2)
                           (defmulti multi-f (constantly :b))
                           (defmethod multi-f :a [_ x] (+ x 1))
                           (defmethod multi-f :b [_ x] (+ x 2)))]
      (spit f source-1)
      (load-file (str f))
      (eval (read-string "(ns-unmap 'multi-test-2 'multi-f)"))
      (change-ns-in-runtime! 'multi-test-2 source-2 source-1)
      (is (= 5 (eval (read-string "(multi-test-2/multi-f :a 3)")))))))

; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
; cljc specific

(deftest modify-ns-diff-from-runtime

  (require 'rksm.system-navigator.test.cljc-dummy :reload)
  
  (testing "runtime changes"
    (is (= {:added [] :removed [] :changed [{:file "rksm/system_navigator/test/cljc_dummy.cljc",
                                             :source "(def x 22)\n",
                                             :prev-source "(def x 23)\n",
                                             :name 'x,
                                             :ns 'rksm.system-navigator.test.cljc-dummy,
                                             :end-column 1, :end-line 14, :line 13, :column 1}]}
           (change-ns-in-runtime!
            'rksm.system-navigator.test.cljc-dummy
            (clojure.string/replace orig-source-3 #"def x 23" "def x 22")
            orig-source-3))))
  
  ; this test was based on cljx, what should happen with cljc?
  #_(testing "cljc -> cljs compilation"
    (let [expected-cljs-file (io/file (str project-dir "/target/classes/rksm/system_navigator/test/cljc_dummy.cljs"))]
      (is (.exists expected-cljs-file))
      (.delete expected-cljs-file))))

; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(comment

 (run-tests *ns*)
 
 (let [w (java.io.StringWriter.)]
   (binding [*test-out* w]
     (run-tests 'rksm.system-navigator.changesets-test
                'rksm.system-navigator.completions-test
                'rksm.system-navigator.ns.internals-test
                'rksm.system-navigator.search-test
                'rksm.system-navigator.system-browser-test)
     w))
 
 (->> (ns-interns *ns*) vals (map meta) (filter #(contains? % :test)) (map :name))

 (test-var #'modify-ns-diff-from-runtime)
 (reset-test-state!)

 (test-var #'modify-ns-with-known-file)
 (reset-test-state!)

 (test-var #'modify-ns-diff-from-runtime)
 (reset-test-state!)

 (test-var #'modify-ns-internals)
 (reset-test-state!)

 (test-var #'diff-ns-test)
 (reset-test-state!)

 (test-var #'modify-ns-everything)
 (reset-test-state!)

 (test-var #'namespace-creation)
 (reset-test-state!)

 (test-var #'source-location-update)
 (reset-test-state!)
 )