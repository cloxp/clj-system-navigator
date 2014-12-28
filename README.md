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

```clj
(require '[rksm.system-navigator.search :as search])
(search/code-search #"def (x|y)" :match-ns #"rksm.*dummy-[0-9]$")
; => [{:ns ns-2,
;      :finds [{:line 5, :match ["def y" "y"], :source "(def y 24)"}]}
;     {:ns ns-1,
;      :finds [{:line 4, :match ["def x" "x"], :source "(def x 23)"}]}]
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

<!-- ## Usage -->

<!-- ```sh -->
<!-- mvn clojure:nrepl -Dclojure.nrepl.port=7888 -->
<!-- mvn clojure:test -->
<!-- fswatch -0 -r . | xargs -0 -I{} mvn clojure:test -->
<!-- ``` -->
