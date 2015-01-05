# clj system-navigator

<img width="256" alt="Compass (PSF)" src="http://upload.wikimedia.org/wikipedia/commons/6/6e/Compass_%28PSF%29.png"/>

Mapping namespaces and code locations to files and sources using Clojure runtime information.

## Usage

### finding loaded namespaces

```clj
(require '[rksm.system-navigator :as nav])
(nav/loaded-namespaces :matching #"core")
; => (clojure.core clojure.core.cache clojure.core.cache.tests ...)
```

### namespace -> source mapping

Given a namespace symbol, figure out what file it is defined in. Also read the source.

```clj
(require '[rksm.system-navigator :as nav])
(require 'rksm.system-navigator.test.dummy-1)
(nav/file-for-ns 'rksm.system-navigator.test.dummy-1)
; => #<File .../src/test/clojure/rksm/system_navigator/test/dummy_1.clj>
(nav/source-for-ns 'rksm.system-navigator.test.dummy-1)
; => "(ns rksm.system-navigator.test.dummy-1)\n\n(def x 23)\n"
(nav/source-for-ns 'clojure.core)
; => well, what dou you expect?
```

### code search in namespaces

Search for lines matching a regexp. Namespaces can be blacklisted (`:except` or
matched with a separate regexp `:match-ns`).

```clj
(require '[rksm.system-navigator.search :as search])
(search/code-search #"def (x|y)"  :match-ns #"rksm.*dummy-[0-9]$")
; => [{:ns ns-2, ; :finds [{:line 5, :match ["def y" "y"], :source "(def y 24)"}]}
;     {:ns ns-1, ; :finds [{:line 4, :match ["def x" "x"], :source "(def x 23)"}]}]
```

### accessing namespace info

Source retrieval for ns-interns.

```clj
(require 'rksm.system-navigator.test.dummy-1)
(require '[rksm.system-navigator.ns-internals :as ns-info])
(ns-info/source-for-symbol 'rksm.system-navigator.test.dummy-1/x)
; => "(def x 23)"
```

Getting a list of all ns-interns and there meta data such as comments, source location, etc..

```clj
(namespace-info 'rksm.system-navigator.test.dummy-1)
; => {:ns 'rksm.system-navigator.test.dummy-1, :name 'x,
;     :file "rksm/system_navigator/test/dummy_1.clj", ...}
```

## Installation

[![Clojars Project](http://clojars.org/org.rksm/system-navigator/latest-version.svg)](http://clojars.org/org.rksm/system-navigator)

or

```xml
<dependency>
  <groupId>org.rksm</groupId>
  <artifactId>system-navigator</artifactId>
  <version>0.1.4</version>
</dependency>
```

<!--
```sh
mvn clojure:nrepl -Dclojure.nrepl.port=7888
mvn clojure:test
fswatch -0 -r . | xargs -0 -I{} mvn clojure:test
mvn clean clojure:compile clojure:add-source package
-->
