(ns gh-active-issues.config
  (:require clojure.string))

(defn getenv
  "Sit in front of System/getenv for mocking, ugh"
  [env]
  (System/getenv env))

(defn gh-auth
  []
  {:auth (or (System/getenv "GITHUB_AUTH")
             (throw (ex-info "Set $GITHUB_AUTH to basic auth in order fetch github issues." {})))})

(defn gh-user
  []
  (or (System/getenv "GITHUB_USER")
      (throw (ex-info "Set $GITHUB_USER to the user who owns the issues." {}))))

(defn issue-url-regex
  []
  (re-pattern
   (or (System/getenv "GITHUB_ISSUE_REGEX")
       (str "github.com/" (gh-user)))))

(defn gh-hide-labels
  []
  (when-let [labels (getenv "GITHUB_HIDE_LABELS")]
    (clojure.string/split labels #"\s*,\s*")))

(defn app-domain
  []
  (or (System/getenv "GITHUB_APP_DOMAIN") "http://localhost:8080"))

(defn hook-forks
  []
  (System/getenv "GITHUB_HOOK_FORKS"))

(defn gh-hmac-secret
  []
  (System/getenv "GITHUB_HMAC_SECRET"))
