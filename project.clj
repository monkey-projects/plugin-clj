(defproject com.monkeyci/plugin-clj "0.4.0-SNAPSHOT"
  :description "MonkeyCI plugin for clj projects"
  :url "https://github.com/monkey-projects/plugin-clj"
  :license {:name "MIT License"
            :url "https://mit-license.org/"
            :distribution :repo}
  :dependencies [[org.clojure/clojure "1.12.2"]
                 [com.monkeyprojects/oci-common "0.2.2"]
                 [com.monkeyci/app "0.20.1"]
                 [com.monkeyci/plugin-junit "0.2.0"]]

  :profiles
  {:dev
   {:dependencies [[lambdaisland/kaocha "1.91.1392"]
                   [lambdaisland/kaocha-junit-xml "1.17.101"]]}}

  :aliases
  {"test-junit" ["run" "-m" "kaocha.runner"
                 "--plugin" "kaocha.plugin/junit-xml"
                 "--junit-xml-file" "junit.xml"]})

