(ns rksm.system-navigator.repl)

(defn eval-string!
  [sym src]
  (let [namespace (-> sym .getNamespace symbol find-ns)
        ref (find-var sym)
        old-meta (select-keys (meta ref) [:file :column :line])]
    (binding [*ns* namespace]
      (eval (read-string src)))
    (alter-meta! ref merge old-meta {:source src})))
