(ns plugin-clj.build
  (:require [monkey.ci.plugin.clj :as p]))

[(p/deps-library)
 (p/lein-test {:junit-file "junit-lein.xml"
               :test-job-id "test-lein"})]
