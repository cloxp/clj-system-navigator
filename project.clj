(defproject org.rksm/system-navigator "0.1.11-SNAPSHOT"
  :description "Accessing Clojure runtime meta data. Tooling for cloxp."
  :url "http://github.com/cloxp/clj-system-navigator"
  :license {:name "MIT License"
            :url "http://opensource.org/licenses/MIT"}
  :source-paths ["src/main/clojure"]
  :test-paths ["src/test/clojure"]
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.rksm/system-files "0.1.3-SNAPSHOT"]
                 [org.rksm/cloxp-source-reader "0.1.0-SNAPSHOT"]
                 [org.rksm/cloxp-repl "0.1.1-SNAPSHOT"]
                 [org.clojure/data.json "0.2.3"]
                 [im.chit/iroh "0.1.11"]
                 [compliment/compliment "0.2.0"]
                 [org.clojure/tools.nrepl "0.2.7"]
                 [com.cemerick/pomegranate "0.3.0"]
                 [clj-http/clj-http "1.0.1"]
                 [org.clojure/tools.reader "0.8.16"]])
