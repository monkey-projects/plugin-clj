(ns monkey.ci.plugin.test.clj-test
  (:require [clojure.test :refer [deftest testing is]]
            [monkey.ci.plugin.clj :as sut]))

(def test-step
  (comp first :steps))

(defn publish-step [p ctx]
  (let [s (-> p :steps second)]
    (s ctx)))

(deftest deps-library
  (testing "returns a pipeline with two steps"
    (is (= 2 (-> (sut/deps-library)
                 :steps
                 (count)))))

  (testing "test step"
    (testing "invokes default container img"
      (is (= sut/default-deps-img
             (-> (sut/deps-library)
                 (test-step)
                 :container/image))))

    (testing "invokes configured container img"
      (is (= "test-img"
             (-> (sut/deps-library {:clj-img "test-img"})
                 (test-step)
                 :container/image))))

    (testing "has `test` name"
      (is (= "test"
             (-> (sut/deps-library)
                 (test-step)
                 :name)))))

  (testing "publish step"
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
                 :container/image))))))
