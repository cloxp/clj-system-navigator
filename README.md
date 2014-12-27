# clj system-navigator

<img width="256" alt="Compass (PSF)" src="http://upload.wikimedia.org/wikipedia/commons/6/6e/Compass_%28PSF%29.png"/>

Mapping namespaces and code locations to files and sources using Clojure runtime information.

## Usage

```clj
(require '[rksm.system-navigator :as nav])

(require 'rksm.system-navigator.test.dummy-1)
(nav/file-for-ns 'rksm.system-navigator.test.dummy-1)
; => #<File .../src/test/clojure/rksm/system_navigator/test/dummy_1.clj>

(require 'rksm.system-navigator.test.dummy-1)
(nav/source-for-ns 'rksm.system-navigator.test.dummy-1)
; => "(ns rksm.system-navigator.test.dummy-1)\n\n(def x 23)\n"

(nav/source-for-ns 'clojure.core)
; => well, what dou you expect?
```

<!-- ## Usage -->

<!-- ```sh -->
<!-- mvn clojure:nrepl -Dclojure.nrepl.port=7888 -->
<!-- mvn clojure:test -->
<!-- fswatch -0 -r . | xargs -0 -I{} mvn clojure:test -->
<!-- ``` -->
