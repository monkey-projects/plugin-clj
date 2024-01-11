(ns monkey.ci.plugin.clj
  (:require [clojure.xml :as xml]
            [monkey.ci.build
             [api :as api]
             [core :as b]]))

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

(defn read-pom-version
  "Given the step context, reads the `pom.xml` file from the configured location
   and returns the version tag value."
  [{:keys [pom-file] :or {pom-file "pom.xml"}} ctx]
  (->> (xml/parse pom-file)
       :content
       (filter (comp (partial = :version) :tag))
       (first)
       :content
       (first)))

(defn- add-version [env
                    {:keys [version-var pom-version-reader]
                     :or {version-var "LIB_VERSION"
                          pom-version-reader read-pom-version}
                     :as conf}
                    ctx]
  (let [v (or (b/tag ctx) (pom-version-reader conf ctx))]
    (cond-> env
      v (assoc version-var v))))

(defn deps-publish [{:keys [publish-alias]
                     :or {publish-alias "publish"}
                     :as conf}]
  (fn [ctx]
    (when (should-publish? conf ctx)
      (-> conf
          (clj-deps (str "-X" publish-alias))
          (assoc :name "deploy"
                 :container/env (-> (api/build-params ctx)
                                    (select-keys ["CLOJARS_USERNAME" "CLOJARS_PASSWORD"])
                                    (add-version conf ctx)))))))

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
