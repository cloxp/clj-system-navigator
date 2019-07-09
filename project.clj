(defproject org.rksm/system-navigator "0.2.0-SNAPSHOT"
  :description "Accessing Clojure runtime meta data. Tooling for cloxp."
  :url "http://github.com/cloxp/clj-system-navigator"
  :license {:name "MIT License"
            :url "http://opensource.org/licenses/MIT"}
  :source-paths ["src/main/clojure"]
  :test-paths ["src/test/clojure"]
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.rksm/system-files "0.2.1-SNAPSHOT"]
                 [org.rksm/cloxp-source-reader "0.1.9-SNAPSHOT"]
                 [org.rksm/cloxp-repl "0.1.8-SNAPSHOT"]
                 [org.clojure/data.json "0.2.6"]
                 [zcaudate/hara.object "2.8.7"]
                 [compliment "0.3.9"]
                 [com.cemerick/pomegranate "1.1.0"]
                 [http-kit "2.3.0"]
                 [org.clojure/tools.reader "1.3.2"]]
  :deploy-repositories [["releases" :clojars]
                        ["snapshots" :clojars]])
