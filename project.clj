(defproject org.rksm/system-navigator "0.1.19-SNAPSHOT"
  :description "Accessing Clojure runtime meta data. Tooling for cloxp."
  :url "http://github.com/cloxp/clj-system-navigator"
  :license {:name "MIT License"
            :url "http://opensource.org/licenses/MIT"}
  :source-paths ["src/main/clojure"]
  :test-paths ["src/test/clojure"]
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.rksm/system-files "0.1.7-SNAPSHOT"]
                 [org.rksm/cloxp-source-reader "0.1.9-SNAPSHOT"]
                 [org.rksm/cloxp-repl "0.1.8-SNAPSHOT"]
                 [org.clojure/data.json "0.2.6"]
                 [im.chit/iroh "0.1.11"]
                 [compliment "0.2.4"]
                 [com.cemerick/pomegranate "0.3.0"]
                 [http-kit "2.1.19"]
                 [org.clojure/tools.reader "0.9.2"]])
