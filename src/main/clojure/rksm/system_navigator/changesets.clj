(ns rksm.system-navigator.changesets)

(def current-changeset (atom []))

(defn record-change!
  [sym source & [prev-source]]
  (let [change {:sym sym :source source
                :prev-source prev-source}]
    (swap! current-changeset conj change)
    change))

(defn record-change-ns!
  [ns-name source & [prev-source changes]]
  (let [change {:sym ns-name
                ;   :source source
                ;   :prev-source prev-source
                :changes changes}]
    (swap! current-changeset conj change)
    change))

(defn get-changes
  [sym]
  (filter #(= (:sym %) sym) @current-changeset))

(defn source-for-symbol
  [sym]
  (some-> (get-changes sym) last :source))
