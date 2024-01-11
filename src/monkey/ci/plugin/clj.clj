(ns monkey.ci.plugin.clj
  (:require [monkey.ci.build.core :as b]))

(def version-regex #"^\d+\.\d+(\.\d+)?$")
(def all-regex #".*")

(def default-deps-img "docker.io/clojure:temurin-21-bookworm-slim")

;; TODO Replace this with b/main-branch when it becomes available
(defn main-branch [_]
  "main")

;; TODO Replace this with b/main-branch? when it becomes available
(defn main-branch? [ctx]
  (= (main-branch ctx)
     (b/branch ctx)))

(defn version-tag? [{:keys [tag-regex] :or {tag-regex all-regex}} ctx]
  (some->> (b/tag ctx)
           (re-matches tag-regex)))

(defn should-publish? [conf ctx]
  (or (main-branch? ctx)
      (version-tag? conf ctx)))

(defn clj-deps [{:keys [clj-img]
                 :or {clj-img default-deps-img}}
                cmd]
  {:container/image clj-img
   :script [(str "clojure " cmd)]})

(defn deps-test [{:keys [test-alias clj-img]
                  :or {test-alias ":test:junit"
                       clj-img default-deps-img}
                  :as conf}]
  (-> conf
      (clj-deps (str "-X" test-alias))
      (assoc :name "test")))

(defn deps-publish [{:keys [publish-alias]
                     :or {publish-alias "publish"}
                     :as conf}]
  (fn [ctx]
    (when (should-publish? conf ctx)
      (-> conf
          (clj-deps (str "-X" publish-alias))
          (assoc :name "deploy")))))

(defn deps-library
  "Creates a pipeline that tests and deploys a clojure library using deps.edn."
  [& [{:keys [name clj-img tag-regex]
       :or {name "build"}
       :as conf}]]
  (b/pipeline
   {:name name
    :steps
    ((juxt deps-test deps-publish) conf)}))

(defn lein-library
  "Creates a pipeline that tests and deploys a clojure library using leiningen."
  [& [conf]]
  ;; TODO
  )
