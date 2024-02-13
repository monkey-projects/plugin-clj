(ns monkey.ci.plugin.test.clj-test
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.string :as cs]
            [monkey.ci.build.api :as api]
            [monkey.ci.plugin.clj :as sut])
  (:import java.io.File))

(defn- run-step [s p ctx]
  (let [s (-> p :steps s :action)]
    (s ctx)))

(def test-step (partial run-step first))
(def publish-step (partial run-step second))

(deftest deps-library
  (testing "returns a pipeline with two steps"
    (is (= 2 (-> (sut/deps-library)
                 :steps
                 (count)))))

  (testing "test step"
    (testing "invokes default container img"
      (is (= sut/default-deps-img
             (-> (sut/deps-library)
                 (test-step {})
                 :container/image))))

    (testing "invokes configured container img"
      (is (= "test-img"
             (-> (sut/deps-library {:clj-img "test-img"})
                 (test-step {})
                 :container/image))))

    (testing "has `test` name"
      (is (= "test"
             (-> (sut/deps-library)
                 (test-step {})
                 :name))))

    (testing "uses mvn cache"
      (let [s (-> (sut/deps-library)
                  (test-step {:step {:work-dir "/test/dir"}}))]
        (is (not-empty (:caches s)))
        (is (cs/includes? (first (:script s)) ":mvn/local-repo \"/test/dir/.m2\"")
            (:script s)))))

  (testing "publish step"
    (with-redefs [api/build-params (constantly
                                    {"CLOJARS_USERNAME" "testuser"
                                     "CLOJARS_PASSWORD" "testpass"})]
      (testing "`nil` if it's not the main branch or a tag"
        (is (nil? (-> (sut/deps-library)
                      (publish-step {:build {:git {:main-branch "main"
                                                   :ref "refs/heads/other"}}})))))

      (testing "invokes default container img"
        (is (= sut/default-deps-img
               (-> (sut/deps-library)
                   (publish-step {:build {:git {:main-branch "main"
                                                :ref "refs/heads/main"}}})
                   :container/image))))

      (testing "invokes configured container img"
        (is (= "test-img"
               (-> (sut/deps-library {:clj-img "test-img"})
                   (publish-step {:build {:git {:ref "refs/tags/publish-me"}}})
                   :container/image))))

      (testing "takes clojars creds from build params"
        (is (= ["testuser" "testpass"]
               (-> (sut/deps-library)
                   (publish-step {:build {:git {:main-branch "main"
                                                :ref "refs/heads/main"}}})
                   :container/env
                   (select-keys ["CLOJARS_USERNAME" "CLOJARS_PASSWORD"])
                   (vals)))))

      (testing "adds version env var"
        (testing "from tag"
          (is (= "test-version"
                 (-> (sut/deps-library)
                     (publish-step {:build {:git {:ref "refs/tags/test-version"}}})
                     :container/env
                     (get "LIB_VERSION")))))

        (testing "from `pom.xml`"
          (is (= "test-version"
                 (-> (sut/deps-library {:pom-version-reader (constantly "test-version")
                                        :version-var "MY_VERSION"})
                     (publish-step {:build {:git {:main-branch "main"
                                                  :ref "refs/heads/main"}}})
                     :container/env
                     (get "MY_VERSION")))))))))

(deftest read-pom-version
  (testing "reads `pom.xml` file and extracts version"
    (let [tmp-dir (System/getProperty "java.io.tmpdir")
          f (File/createTempFile "pom-" ".xml")]
      (is (nil? (spit f "<project><version>test-version</version></project>")))
      (is (= "test-version" (sut/read-pom-version {:pom-file (.getName f)}
                                                  {:step {:work-dir tmp-dir}})))
      (is (true? (.delete f))))))
