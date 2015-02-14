(ns rksm.system-navigator
  (:require (rksm.system-navigator search clojars completions system-browser dependencies)
            (rksm.system-navigator.ns internals filemapping)))

(def ^{:dynamic true} *repl-source*)

; the core module to pull in the rest for cloxp tools
