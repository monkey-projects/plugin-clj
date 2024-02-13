(ns monkey.ci.plugin.clj
  (:require [babashka.fs :as fs]
            [clojure.xml :as xml]
            [monkey.ci.build
             [api :as api]
             [core :as b]
             [shell :as s]]))

(def version-regex #"^\d+\.\d+(\.\d+)?$")
(def all-regex #".*")

(def default-deps-img "docker.io/clojure:temurin-21-bookworm-slim")

(defn version-tag? [{:keys [tag-regex] :or {tag-regex all-regex}} ctx]
  (some->> (b/tag ctx)
           (re-matches tag-regex)))

(defn should-publish? [conf ctx]
  (or (b/main-branch? ctx)
      (version-tag? conf ctx)))

(defn clj-deps [ctx
                {:keys [clj-img]
                 :or {clj-img default-deps-img}}
                cmd]
  {:container/image clj-img
   :script [(str (format "clojure -Sdeps '{:mvn/local-repo \"%s\"}' " (s/in-work ctx ".m2")) cmd)]
   :caches [{:id "clj:mvn-repo"
             :path ".m2"}]})

(defn deps-test [{:keys [test-alias clj-img]
                  :or {test-alias ":test:junit"
                       clj-img default-deps-img}
                  :as conf}]
  {:name "test"
   :action (fn [ctx]
             (-> ctx
                 (clj-deps conf (str "-X" test-alias))
                 (assoc :name "test")))})

(defn read-pom-version
  "Given the step context, reads the `pom.xml` file from the configured location
   and returns the version tag value."
  [{:keys [pom-file] :or {pom-file "pom.xml"}} ctx]
  (let [f (s/in-work ctx pom-file)]
    (when (fs/exists? f)
      (->> (xml/parse f)
           :content
           (filter (comp (partial = :version) :tag))
           (first)
           :content
           (first)))))

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
                     :or {publish-alias ":jar:publish"}
                     :as conf}]
  {:name "publish"
   :action
   (fn [ctx]
     (when (should-publish? conf ctx)
       (-> ctx
           (clj-deps conf (str "-X" publish-alias))
           (assoc :container/env (-> (api/build-params ctx)
                                     (select-keys ["CLOJARS_USERNAME" "CLOJARS_PASSWORD"])
                                     (add-version conf ctx))))))})

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
