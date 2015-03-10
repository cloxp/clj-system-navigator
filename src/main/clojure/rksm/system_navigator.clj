(ns rksm.system-navigator
  (:require (rksm.system-navigator search clojars completions system-browser)
            (rksm.system-navigator.ns internals) 
            rksm.system-files))

; the core module to pull in the rest for cloxp tools
