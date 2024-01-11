# MonkeyCI Clojure Plugin

This is a library that's meant as a plugin for [MonkeyCI](https://monkeyci.com) builds.

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

In order to use it in your build script, just use the `library` pipeline, like in this
example build script:

```clojure
(require '[monkey.ci.plugin.clj :as p])

(p/library {:tag-regex p/version-regex})
```

This is almost the most basic configuration.  You could also leave out the entire
config map, if you want!  The `version-regex` matches any standard version tag
(that looks like `x.x.x` or `x.x`).

It will run the unit tests, that are assumed to be declared as an alias function named
`:test` in `deps.edn` (as a default).   It reads all necessary information to deploy
the library from the committed `pom.xml`.  These values can be overridden using config
parameters.

## License

Copyright (c) 2024 by Monkey Projects.
[MIT License](LICENSE).

