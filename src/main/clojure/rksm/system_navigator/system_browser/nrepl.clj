(ns rksm.system-navigator.system-browser.nrepl
  (:require [clojure.tools.nrepl.middleware :refer (set-descriptor!)]
            [clojure.tools.nrepl.middleware.interruptible-eval :as eval]
            [clojure.tools.nrepl.transport :as t]
            [rksm.system-navigator.system-browser :refer (change-ns! change-def!)])
  (:use [clojure.tools.nrepl.misc :only (response-for returning)]))

; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
; extended eval options

(defn change
  [{:keys [op name code write-to-file file transport] :as msg}]
  (cond
    (not code) (t/send transport (response-for msg :status #{:error :no-code}))
    (not name) (t/send transport (response-for msg :status #{:error :no-name}))
    :default (try
               ((if (= op "change-ns") change-ns! change-def!)
                (symbol name) code write-to-file file)
               (t/send transport (response-for msg :status #{:done}))
               (catch Exception e
                 (t/send
                  transport
                  (response-for
                   msg
                   :status #{:error :runtime-error :done}
                   :error (pr-str e)))))))

(defn change-ns-or-def
  [h]
  (fn [{:keys [op] :as msg}]
    (if (or (= op "change-def") (= op "change-ns"))
      (change msg)
      (h msg))))

(set-descriptor! #'change-ns-or-def
  {:requires #{}
   :expects #{}
   :handles {"change-ns"
             {:doc "Modifies the runtime and optionally the file of a namespace."
              :requires {"code" "Source string of the new namespace source code."
                         "name" "name of the namespace"}
              :optional {"write-to-file" "modify the file of the namespace?"
                         "file" "full path to the namespace file. if not provided will be tried to be found from the classpath data (slow!)"}
              :returns {"status" "'error' or 'done'"}}
             "change-def"
             {:doc "Modifies the runtime and optionally the file of a namespace given the source + name of a def in it."
              :requires {"code" "Source string of the def."
                         "name" "name of the symbol (fillu qualified)"}
              :optional {"write-to-file" "modify the file of the namespace?"
                         "file" "full path to the namespace file. if not provided will be tried to be found from the classpath data (slow!)"}
              :returns {"status" "'error' or 'done'"}}}})
