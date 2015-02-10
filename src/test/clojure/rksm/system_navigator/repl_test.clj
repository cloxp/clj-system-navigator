(ns rksm.system-navigator.repl-test
  (:require [clojure.test :refer :all])
  (:require [rksm.system-navigator.repl :refer :all]))

(comment

 (run-tests 'rksm.system-navigator.repl-test)


 (ns rksm.system-navigator.repl-test
   (:require [clojure.test :refer :all])
   (:require [rksm.system-navigator.repl :refer :all]))

 (declare addy-1)

 (deftest simple-eval

   (testing "def knows its source"

     (let [source "(defn addy-1 [x] (+ x 3))"
           source-2 "(defn addy-1 [x] (+ x 4))"
           sym (symbol (str *ns*) "addy-1")
           result (eval-string! sym source)]
       (is (= 5 (addy-1 2)))
       (is (= source (:source (meta (find-var sym)))))

       (testing "keeps meta data"
         (alter-meta! (find-var sym) #(assoc % :test 23))
         (is (= 23 (:test (meta (find-var sym)))))

         (eval-string! sym source-2)
         (is (= 6 (addy-1 2)))
         (is (= source-2 (:source (meta (find-var sym)))))
         (is (= 23 (:test (meta (find-var sym)))))
         ; (is (= 6 (addy-1 2)))
         )

       )))

 (test-var #'simple-eval)

 (comment

  (run-tests 'rksm.system-navigator.repl-test)
  )

 )
