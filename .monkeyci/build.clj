(ns build
  (:require [monkey.ci.plugin
             [clj :as p]
             [github :as gh]]))

[(p/deps-library)
 (p/lein-test {:junit-file "junit.xml"
               :test-job-id "test-lein"})
 (gh/release-job {:dependencies ["publish"]})]
