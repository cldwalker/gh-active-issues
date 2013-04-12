(ns gh-waiting-room.config
  (:require clojure.string))

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

(def gh-hide-labels (when-let [labels (System/getenv "GITHUB_HIDE_LABELS")]
                      (clojure.string/split labels #"\s*,\s*")))

(defn app-domain
  []
  (or (System/getenv "APP_DOMAIN") "http://localhost:8080"))