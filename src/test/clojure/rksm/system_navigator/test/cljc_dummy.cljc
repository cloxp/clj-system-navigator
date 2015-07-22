(ns rksm.system-navigator.test.cljc-dummy)

(defn x-to-string
  [x]
  (let [buf #?(:clj (StringBuilder.) :cljs (gstring/StringBuffer.))]
    (.append buf "x is: ")
    (.append buf (str x))))

(reify
  #?(:clj clojure.lang.IFn :cljs cljs.core.IFn)
  (invoke [_ x] (inc x)))

(def x 23)

(def y #?(:clj 24 :cljs 25))