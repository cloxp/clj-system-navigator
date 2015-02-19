(ns rksm.system-navigator.project-config-test
  (:require [rksm.system-navigator.project-config :refer :all :exclude (pom)]
            [clojure.test :refer :all]))

(def pom
"<project xmlns=\"http://maven.apache.org/POM/4.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"
  xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd\">
  <modelVersion>4.0.0</modelVersion>
  <groupId>org.rksm</groupId>
  <artifactId>foo bar</artifactId>
  <packaging>clojure</packaging>
  <version>0.1.10-SNAPSHOT</version>
  <name>${artifactId}</name>
  <description>Accessing Clojure runtime meta data. Tooling for cloxp.</description>
  <url>http://maven.apache.org</url>
  <build>
    <plugins>
      <plugin>
        <groupId>com.theoryinpractise</groupId>
        <artifactId>clojure-maven-plugin</artifactId>
        <version>1.3.20</version>
        <extensions>true</extensions>
      </plugin>
    </plugins>
  </build>
  <dependencies>
    <dependency>
      <groupId>org.clojure</groupId>
      <artifactId>clojure</artifactId>
      <version>1.6.0</version>
    </dependency>
  </dependencies>
  </project>")
ns-unmap
; (ns-refers *ns*)

(def pom-with-added-dep
"<project xmlns=\"http://maven.apache.org/POM/4.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"
  xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd\">
  <modelVersion>4.0.0</modelVersion>
  <groupId>org.rksm</groupId>
  <artifactId>foo bar</artifactId>
  <packaging>clojure</packaging>
  <version>0.1.10-SNAPSHOT</version>
  <name>${artifactId}</name>
  <description>Accessing Clojure runtime meta data. Tooling for cloxp.</description>
  <url>http://maven.apache.org</url>
  <build>
    <plugins>
      <plugin>
        <groupId>com.theoryinpractise</groupId>
        <artifactId>clojure-maven-plugin</artifactId>
        <version>1.3.20</version>
        <extensions>true</extensions>
      </plugin>
    </plugins>
  </build>
  <dependencies>
    <dependency>
      <groupId>org.clojure</groupId>
      <artifactId>clojure</artifactId>
      <version>1.6.0</version>
    </dependency>
    <dependency>
      <groupId>group</groupId>
      <artifactId>artifact</artifactId>
      <version>1.2.3</version>
    </dependency>
  </dependencies>
  </project>")

(deftest update-deps-in-project-clj
  
  (testing "without :dependencies"
    (is (= '(defproject foo/bar :version "1.2.3" :dependencies [[group/artifact "1.2.3"]])
           (project-clj-with-dep
            '(defproject foo/bar :version "1.2.3")
            'group 'artifact "1.2.3"))))
  
  (testing "with :dependencies"
    (is (= '(defproject foo/bar :dependencies [[foo/bar "0.1.2"] [group/artifact "1.2.3"]] :version "1.2.3")
           (project-clj-with-dep
            '(defproject foo/bar :dependencies [[foo/bar "0.1.2"]] :version "1.2.3" )
            'group 'artifact "1.2.3")))))

(deftest update-deps-in-pom  
  (is (= pom-with-added-dep (pom-with-dep pom 'group 'artifact "1.2.3"))))


(comment
 (run-tests *ns*)
 (->> (ns-interns *ns*) keys (map (partial ns-unmap *ns*)) doall)
 (remove-ns 'rksm.system-navigator.dependencies-test))
