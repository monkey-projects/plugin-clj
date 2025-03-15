(ns monkey.ci.plugin.clj
  (:require [babashka.fs :as fs]
            [clojure.xml :as xml]
            [monkey.ci.build
             [api :as api]
             [core :as b]
             [shell :as s]]
            [monkey.ci.ext.junit]))

(def version-regex #"^\d+\.\d+(\.\d+)?$")
(def all-regex #".*")

(def default-deps-img "docker.io/clojure:temurin-21-bookworm-slim")
(def default-lein-img "docker.io/clojure:temurin-21-lein-bookworm-slim")

(defn version-tag? [{:keys [tag-regex] :or {tag-regex all-regex}} ctx]
  (some->> (b/tag ctx)
           (re-matches tag-regex)))

(defn should-publish? [conf ctx]
  (or (b/main-branch? ctx)
      (version-tag? conf ctx)))

(defn- test-job-id [conf]
  (get conf :test-job-id "test"))

(defn- publish-job-id [conf]
  (get conf :publish-job-id "publish"))

(defn clj-deps
  ([id
    {:keys [clj-img]
     :or {clj-img default-deps-img}}
    cmd]
   (b/container-job id
                    {:container/image clj-img
                     ;; Must use a relative path, because running in a container results in the wrong path
                     :script [(str "clojure -Sdeps '{:mvn/local-repo \".m2\"}' " cmd)]
                     :caches [{:id "clj:mvn-repo"
                               :path ".m2"}]}))
  ([id cmd]
   (clj-deps id {} cmd)))

(defn deps-test [{:keys [test-alias artifact-id junit-file]
                  :or {test-alias ":test:junit"
                       artifact-id "test-junit"
                       junit-file "junit.xml"}
                  :as conf}]
  (-> (clj-deps (test-job-id conf) conf (str "-X" test-alias))
      (assoc :save-artifacts [{:id artifact-id
                               :path junit-file}]
             :junit {:artifact-id artifact-id
                     :path junit-file})))

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

(defn- get-version [{:keys [pom-version-reader]
                     :or {pom-version-reader read-pom-version}
                     :as conf}
                    ctx]
  (or (b/tag ctx) (pom-version-reader conf ctx)))

(defn- add-version [env
                    {:keys [version-var]
                     :or {version-var "LIB_VERSION"}
                     :as conf}
                    ctx]
  (let [v (get-version conf ctx)]
    (cond-> env
      v (assoc version-var v))))

(defn- clojars-creds-params [ctx]
  (-> (api/build-params ctx)
      (select-keys ["CLOJARS_USERNAME" "CLOJARS_PASSWORD"])))

(defn deps-publish [{:keys [publish-alias]
                     :or {publish-alias ":jar:publish"}
                     :as conf}]
  (fn [ctx]
    (when (should-publish? conf ctx)
      (-> (clj-deps (publish-job-id conf) conf (str "-X" publish-alias))
          (assoc :container/env (-> (clojars-creds-params ctx)
                                    (add-version conf ctx))
                 :dependencies [(test-job-id conf)])))))

(defn- jobs-maker [test-fn publish-fn & [conf]]
  (fn [ctx]
    (let [f (->> (cond-> [test-fn]
                   (should-publish? conf ctx) (conj publish-fn))
                 (apply juxt))]
      (f conf))))

(def deps-library
  "Creates jobs that test and deploy a clojure library using deps.edn."
  (partial jobs-maker deps-test deps-publish))

(defn clj-lein
  ([id
    {:keys [clj-img]
     :or {clj-img default-lein-img}}
    cmds]
   (b/container-job id
                    {:container/image clj-img
                     :script cmds
                     ;; TODO Cache: use lein profile for this
                     ;; :caches [{:id "clj:mvn-repo"
                     ;;           :path ".m2"}]
                     }))
  ([id cmds]
   (clj-lein id {} cmds)))

(defn lein-test [{:keys [test-alias artifact-id junit-file]
                  :or {test-alias "test-junit"
                       artifact-id "test-junit"
                       junit-file "junit.xml"}
                  :as conf}]
  (-> (clj-lein (test-job-id conf) conf [(str "lein " test-alias)])
      (assoc :save-artifacts [{:id artifact-id
                               :path junit-file}]
             :junit {:artifact-id artifact-id
                     :path junit-file})))

(defn lein-publish [{:keys [publish-alias]
                     :or {publish-alias "deploy"}
                     :as conf}]
  (fn [ctx]
    (when (should-publish? conf ctx)
      (-> (clj-lein (publish-job-id conf) conf
                    (cond->> [(str "lein " publish-alias)]
                      (version-tag? conf ctx)
                      (cons (format "lein change version set '\"%s\"'" (get-version conf ctx)))))
          (assoc :container/env (clojars-creds-params ctx)
                 :dependencies [(test-job-id conf)])))))

(def lein-library
  "Creates jobs that test and deploy a clojure library using leiningen."
  (partial jobs-maker lein-test lein-publish))
