(ns monkey.ci.plugin.test.clj-test
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.string :as cs]
            [monkey.ci.build.api :as api]
            [monkey.ci.plugin.clj :as sut])
  (:import java.io.File))

(deftest deps-library
  (testing "returns function"
    (let [l (sut/deps-library)]
      (is (fn? l))

      (testing "that creates one job by default"
        (let [jobs (l {:build {:git {:ref "refs/heads/feature/test"}}})]
          (is (= 1 (count jobs)))))

      (testing "that creates two jobs for main branch"
        (let [jobs (l {:build {:git {:main-branch "main"
                                     :ref "refs/heads/main"}}})]
          (is (= 2 (count jobs)))))

      (testing "that creates two jobs for tag"
        (is (= 2 (count (l {:build {:git {:ref "refs/tags/v1"}}})))))
      
      (testing "that creates two jobs configured version tag"
        (let [l (sut/deps-library {:tag-regex #"v\d+"})]
          (is (= 2 (count (l {:build {:git {:main-branch "main"
                                            :ref "refs/tags/v1"}}}))))
          (is (= 1 (count (l {:build {:git {:main-branch "main"
                                            :ref "refs/tags/other"}}}))))))))

  (testing "test job"
    (letfn [(test-job [conf ctx]
              (-> ((sut/deps-library conf) ctx)
                  first))]
      
      (testing "invokes default container img"
        (is (= sut/default-deps-img
               (-> (test-job {} {})
                   :container/image))))

      (testing "invokes configured container img"
        (is (= "test-img"
               (-> (test-job {:clj-img "test-img"} {})
                   :container/image))))

      (testing "has `test` id"
        (is (= "test"
               (:id (test-job {} {})))))

      (testing "can override id"
        (is (= "test-job-id"
               (:id (test-job {:test-job-id "test-job-id"} {})))))

      (testing "uses mvn cache as local dir"
        (let [s (test-job {} {:job {:work-dir "/test/dir"}})]
          (is (not-empty (:caches s)))
          (is (cs/includes? (first (:script s)) ":mvn/local-repo \".m2\"")
              (:script s))))

      (testing "publishes junit.xml artifact"
        (let [s (test-job {} {:job {:work-dir "/test/dir"}})]
          (is (= [{:id "test-junit"
                   :path "junit.xml"}]
                 (:save-artifacts s)))))

      (testing "adds test extension settings"
        (let [s (test-job {} {:job {:work-dir "/test/dir"}})]
          (is (not-empty (:junit s)))))))

  (testing "publish job"
    (with-redefs [api/build-params (constantly
                                    {"CLOJARS_USERNAME" "testuser"
                                     "CLOJARS_PASSWORD" "testpass"})]
      (letfn [(publish-job [conf ctx]
                (let [ctx (assoc-in ctx [:build :git :main-branch] "main")]
                  (when-let [f (-> ((sut/deps-library conf) ctx)
                                   second)]
                    (f ctx))))]
        
        (testing "`nil` if it's not the main branch or a tag"
          (is (nil? (publish-job {} {:build {:git {:ref "refs/heads/other"}}}))))

        (let [ctx {:build {:git {:ref "refs/heads/main"}}}]
          (testing "depends on test"
            (is (= ["test"]
                   (-> (publish-job {} ctx)
                       :dependencies))))

          (testing "can override id"
            (is (= "test-job-id"
                   (:id (publish-job {:publish-job-id "test-job-id"} ctx)))))

          (testing "depends on overridden test job id"
            (is (= ["test-job-id"]
                   (-> (publish-job {:test-job-id "test-job-id"} ctx)
                       :dependencies))))
          
          (testing "invokes default container img"
            (is (= sut/default-deps-img
                   (-> (publish-job {} ctx)
                       :container/image)))))

        (testing "invokes configured container img"
          (is (= "test-img"
                 (-> (publish-job {:clj-img "test-img"}
                                  {:build {:git {:ref "refs/tags/publish-me"}}})
                     :container/image))))

        (testing "takes clojars creds from build params"
          (is (= ["testuser" "testpass"]
                 (-> (publish-job {}
                                  {:build {:git {:ref "refs/heads/main"}}})
                     :container/env
                     (select-keys ["CLOJARS_USERNAME" "CLOJARS_PASSWORD"])
                     (vals)))))

        (testing "adds version env var"
          (testing "from tag"
            (is (= "test-version"
                   (-> (publish-job {}
                                    {:build {:git {:ref "refs/tags/test-version"}}})
                       :container/env
                       (get "LIB_VERSION")))))

          (testing "from `pom.xml`"
            (is (= "test-version"
                   (-> (publish-job {:pom-version-reader (constantly "test-version")
                                     :version-var "MY_VERSION"}
                                    {:build {:git {:main-branch "main"
                                                   :ref "refs/heads/main"}}})
                       :container/env
                       (get "MY_VERSION"))))))))))

(deftest lein-library
  (testing "returns function"
    (let [l (sut/lein-library)]
      (is (fn? l))

      (testing "that creates one job by default"
        (let [jobs (l {:build {:git {:ref "refs/heads/feature/test"}}})]
          (is (= 1 (count jobs)))))

      (testing "that creates two jobs for main branch"
        (let [jobs (l {:build {:git {:main-branch "main"
                                     :ref "refs/heads/main"}}})]
          (is (= 2 (count jobs)))))

      (testing "that creates two jobs for tag"
        (is (= 2 (count (l {:build {:git {:ref "refs/tags/v1"}}})))))
      
      (testing "that creates two jobs configured version tag"
        (let [l (sut/deps-library {:tag-regex #"v\d+"})]
          (is (= 2 (count (l {:build {:git {:main-branch "main"
                                            :ref "refs/tags/v1"}}}))))
          (is (= 1 (count (l {:build {:git {:main-branch "main"
                                            :ref "refs/tags/other"}}}))))))))

  (testing "test job"
    (letfn [(test-job [conf ctx]
              (-> ((sut/lein-library conf) ctx)
                  first))]
      
      (testing "invokes default container img"
        (is (= sut/default-lein-img
               (-> (test-job {} {})
                   :container/image))))

      (testing "invokes configured container img"
        (is (= "test-img"
               (-> (test-job {:clj-img "test-img"} {})
                   :container/image))))

      (testing "has `test` id"
        (is (= "test"
               (:id (test-job {} {})))))

      (testing "can override id"
        (is (= "test-job-id"
               (:id (test-job {:test-job-id "test-job-id"} {})))))

      (testing "publishes junit.xml artifact"
        (let [s (test-job {} {:job {:work-dir "/test/dir"}})]
          (is (= [{:id "test-junit"
                   :path "junit.xml"}]
                 (:save-artifacts s)))))

      (testing "adds test extension settings"
        (let [s (test-job {} {:job {:work-dir "/test/dir"}})]
          (is (not-empty (:junit s)))))

      (testing "activates additional profile for m2 cache")))

  (testing "publish job"
    (with-redefs [api/build-params (constantly
                                    {"CLOJARS_USERNAME" "testuser"
                                     "CLOJARS_PASSWORD" "testpass"})]
      (letfn [(publish-job [conf ctx]
                (let [ctx (assoc-in ctx [:build :git :main-branch] "main")]
                  (when-let [f (-> ((sut/lein-library conf) ctx)
                                   second)]
                    (f ctx))))]
        
        (testing "`nil` if it's not the main branch or a tag"
          (is (nil? (publish-job {} {:build {:git {:ref "refs/heads/other"}}}))))

        (testing "depends on test"
          (is (= ["test"]
                 (-> (publish-job {} {:build {:git {:ref "refs/heads/main"}}})
                     :dependencies))))
        
        (testing "can override id"
          (is (= "test-job-id"
                 (:id (publish-job {:publish-job-id "test-job-id"}
                                   {:build {:git {:ref "refs/heads/main"}}})))))

        (testing "when overriding test job id, depends on correct job"
          (is (= ["test-job-id"]
                 (-> (publish-job {:test-job-id "test-job-id"}
                                  {:build {:git {:ref "refs/heads/main"}}})
                     :dependencies))))

        (testing "invokes default container img"
          (is (= sut/default-lein-img
                 (-> (publish-job {} {:build {:git {:ref "refs/heads/main"}}})
                     :container/image))))

        (testing "invokes configured container img"
          (is (= "test-img"
                 (-> (publish-job {:clj-img "test-img"}
                                  {:build {:git {:ref "refs/tags/publish-me"}}})
                     :container/image))))

        (testing "takes clojars creds from build params"
          (is (= ["testuser" "testpass"]
                 (-> (publish-job {}
                                  {:build {:git {:ref "refs/heads/main"}}})
                     :container/env
                     (select-keys ["CLOJARS_USERNAME" "CLOJARS_PASSWORD"])
                     (vals)))))

        (testing "when tag specified changes version"
          (let [script (-> (publish-job {}
                                        {:build {:git {:ref "refs/tags/test-version"}}})
                           :script)]
            (is (= 2 (count script)))
            (is (= "lein change version set '\"test-version\"'"
                   (first script)))))

        (testing "when no tag specified, leaves version unchanged"
          (let [script (-> (publish-job {}
                                        {:build {:git {:main-branch "main"
                                                       :ref "refs/heads/main"}}})
                           :script)]
            (is (= 1 (count script)))
            (is (= "lein deploy" (first script)))))))))

(deftest read-pom-version
  (testing "reads `pom.xml` file and extracts version"
    (let [tmp-dir (System/getProperty "java.io.tmpdir")
          f (File/createTempFile "pom-" ".xml")]
      (is (nil? (spit f "<project><version>test-version</version></project>")))
      (is (= "test-version" (sut/read-pom-version {:pom-file (.getName f)}
                                                  {:job {:work-dir tmp-dir}})))
      (is (true? (.delete f))))))
