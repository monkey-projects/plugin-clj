# MonkeyCI Clojure Plugin

This is a library that's meant as a plugin for [MonkeyCI](https://monkeyci.com) builds.

[![Clojars Project](https://img.shields.io/clojars/v/com.monkeyci/plugin-clj.svg)](https://clojars.org/com.monkeyci/plugin-clj)

## Getting Started

After creating an account on [MonkeyCI](https://app.monkeyci.com/sign-on), just include
the dependency in your `.monkeyci/deps.edn`:

```clojure
{:deps {com.monkeyci/plugin-clj {:mvn-version "LATEST"}}}
```

It allows you to set up builds for Clojure projects with minimal code.

## Library Builds

A library is something that you want to deploy.  Either as a snapshot when you push to
the `main`branch, or when you push a tag, in which case the tag name will be used as
the release version.  You could optionally specify a regex that is applied to the tag
name.  If the regex does not match, it is not considered to be a deployment.

In order to use it in your build script, just use the `deps-library` function, like in this
example build script:

```clojure
(require '[monkey.ci.plugin.clj :as p])

(p/deps-library {:tag-regex p/version-regex})
```

This is almost the most basic configuration.  You could also leave out the entire
config map, if you want!  The `version-regex` matches any standard version tag
(that looks like `x.x.x` or `x.x`).

It will run the unit tests, that are assumed to be declared as an alias function named
`:test` in `deps.edn` (as a default).   It reads all necessary information to deploy
the library from the committed `pom.xml`.  These values can be overridden using config
parameters.

### Options

These are the options you can use to configure the library build:
|Option|Default|Description|
|---|---|---|
|`:tag-regex`|`#".*"`|Regex to filter release tags|
|`:clj-img`|`docker.io/clojure:...`|The image to use to test and publish.  Tag depends on lein or cli library and evolves with the lib version.  See `monkey.ci.plugin.clj/default-deps-img`.|
|`:test-alias`|`:test:junit`|The alias to apply when building a library that uses `deps.edn`|
|`:publish-alias`|`:jar:publish`|The alias to apply when publishing the library|
|`:pom-file`|`pom.xml`|The location of the pom file, relative to the checkout dir.|
|`:version-var`|`LIB_VERSION`|When publishing, the version will be stored in this env var.|

Since this is Clojure, you can of course pick the parts you like.  The `...-library` functions just
return jobs, to which you can add more, or you can include it in a larger job list.  Or you can call
the functions that have been provided to create the individual jobs.  See [the
source](src/monkey/ci/plugin/clj.clj) for this.

## License

Copyright (c) 2024 by Monkey Projects.
[MIT License](LICENSE).

