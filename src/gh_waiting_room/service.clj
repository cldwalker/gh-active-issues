(ns gh-waiting-room.service
    (:require [io.pedestal.service.http :as bootstrap]
              [io.pedestal.service.http.route :as route]
              [io.pedestal.service.http.body-params :as body-params]
              [io.pedestal.service.http.route.definition :refer [defroutes]]
              [io.pedestal.service.interceptor :refer [defon-response]]
              [clostache.parser :refer [render-resource]]
              [tentacles.issues :refer [my-issues]]
              clojure.string
              [ring.util.response :as ring-resp]))

(def db (atom {}))

(def gh-auth
  {:auth (or (System/getenv "GITHUB_AUTH")
             (throw (ex-info "Set $GITHUB_AUTH to basic auth in order fetch github issues." {})))})

(def gh-user
  (or (System/getenv "GITHUB_USER")
      (throw (ex-info "Set $GITHUB_USER to the user who owns the issues." {}))))

(def issue-url-regex (re-pattern
                      (or (System/getenv "GITHUB_ISSUE_REGEX")
                          (str "github.com/" gh-user))))

(def gh-hide-labels (when-let [labels (System/getenv "GITHUB_HIDE_LABELS")]
                      (clojure.string/split labels #"\s*,\s*")))

(defn- api-options
  []
  (merge {:filter "all" :all-pages true} gh-auth))

(defn- issue-filter
  [issue]
  (and (not (get-in issue [:repository :private]))
   (re-find issue-url-regex (str (:html_url issue)))
       (if gh-hide-labels
         (not (some
               (set gh-hide-labels)
               (map :name (:labels issue))))
         (= [] (:labels issue)))))

(defn- fetch-gh-issues
  []
  (swap! db assoc :issues (my-issues (api-options))))

(defn- ->issue [{body :body :as issue}]
  {:name (format "%s#%s"
          (or (get-in issue [:repository :full_name])
              (throw (ex-info "No full name given for issue" {:issue issue})))
          (:number issue))
   :type (if (get-in issue [:pull_request :html_url]) "pull request" "issue")
   :url (:html_url issue)
   :comments (:comments issue)
   :title (:title issue)
   :desc (if (re-find #"^\s*$" body)
           ""
           (str (re-find #"^.{0,100}(?=\s|$)" body)
                (if (> (count body) 100) " ..." "")))
   :user (or (get-in issue [:user :login])
             (throw (ex-info "No user found for issue" {:issue issue})))
   :created (or (re-find #"\d{4}-\d\d-\d\d"(:created_at issue))
                (throw (ex-info "Failed to parse date from an issue" {:issue issue})))})

(defn home-page
  [request]
  (when-not (seq (:issues @db)) (fetch-gh-issues))
  (ring-resp/response
   (render-resource "public/index.mustache"
                    {:github-user (or (System/getenv "GITHUB_USER") "FIXME")
                     :issues (->> (:issues @db)
                                  (filter issue-filter)
                                  (map ->issue)
                                  reverse
                                  (map-indexed (fn [num elem]
                                                 (assoc elem :position (inc num)))))})))

(defon-response html-content-type
  [response]
  (ring-resp/content-type response "text/html"))

(defroutes routes
  [[["/" {:get home-page}
     ^:interceptors [(body-params/body-params) html-content-type]
     ]]])

;; You can use this fn or a per-request fn via io.pedestal.service.http.route/url-for
(def url-for (route/url-for-routes routes))

;; Consumed by gh-waiting-room.server/create-server
(def service {:env :prod
              ;; You can bring your own non-default interceptors. Make
              ;; sure you include routing and set it up right for
              ;; dev-mode. If you do, many other keys for configuring
              ;; default interceptors will be ignored.
              ;; :bootstrap/interceptors []
              ::bootstrap/routes routes

              ;; Uncomment next line to enable CORS support, add
              ;; string(s) specifying scheme, host and port for
              ;; allowed source(s):
              ;;
              ;; "http://localhost:8080"
              ;;
              ;;::boostrap/allowed-origins ["scheme://host:port"]

              ;; Root for resource interceptor that is available by default.
              ::bootstrap/resource-path "/public"

              ;; Either :jetty or :tomcat (see comments in project.clj
              ;; to enable Tomcat)
              ;;::bootstrap/host "localhost"
              ::bootstrap/type :jetty
              ::bootstrap/port (Integer. (or (System/getenv "PORT") 8080))})
