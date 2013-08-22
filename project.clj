(defproject gh-active-issues "0.0.1-SNAPSHOT"
  :description "A pedestal service that helps github maintainers grapple with their issues and helps users understand what's before their issue."
  :url "https://github.com/cldwalker/gh-active-issues"
  :license {:name "The MIT License"
            :url "https://en.wikipedia.org/wiki/MIT_License"}
  :dependencies [[org.clojure/clojure "1.5.0"]
                 [io.pedestal/pedestal.service "0.1.10"]
                 [de.ubercode.clostache/clostache "1.3.1"]
                 [tentacles "0.2.4"]
                 [table "0.4.0"]

                 [io.pedestal/pedestal.jetty "0.1.10"]

                 ;; Logging
                 [ch.qos.logback/logback-classic "1.0.7" :exclusions [org.slf4j/slf4j-api]]
                 [org.slf4j/jul-to-slf4j "1.7.2"]
                 [org.slf4j/jcl-over-slf4j "1.7.2"]
                 [org.slf4j/log4j-over-slf4j "1.7.2"]]
  :profiles {:dev {:source-paths ["dev"]
                   :dependencies [[org.clojars.echo/test.mock "0.1.2"]]}}
  :min-lein-version "2.0.0"
  :resource-paths ["config", "resources"]
  :main ^{:skip-aot true} gh-active-issues.server
  :aliases {"github" ["trampoline" "run" "-m" "gh-active-issues.github-tasks"]})
