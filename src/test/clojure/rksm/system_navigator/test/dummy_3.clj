(ns rksm.system-navigator.test.dummy-3)

(def x 23)

(defonce dummy-atom (atom []))

(defn test-func
  [y]
  (swap! dummy-atom conj (+ x y)))

(defmacro foo
  [x & body]
  `(foo ~x ~@body))
