(ns gh-waiting-room.service
    (:require [io.pedestal.service.http :as bootstrap]
              [io.pedestal.service.http.route :as route]
              [io.pedestal.service.http.body-params :as body-params]
              [io.pedestal.service.http.route.definition :refer [defroutes]]
              [io.pedestal.service.interceptor :refer [defon-response]]
              [clostache.parser :refer [render-resource]]
              [tentacles.issues :refer [my-issues create-comment]]
              [tentacles.repos :refer [create-hook]]
              clojure.string
              [clojure.data.json :as json]
              [ring.util.response :as ring-resp]))

(def db (atom {}))

(defn- gh-auth
  []
  {:auth (or (System/getenv "GITHUB_AUTH")
             (throw (ex-info "Set $GITHUB_AUTH to basic auth in order fetch github issues." {})))})

(defn- gh-user
  []
  (or (System/getenv "GITHUB_USER")
      (throw (ex-info "Set $GITHUB_USER to the user who owns the issues." {}))))

(defn- issue-url-regex
  []
  (re-pattern
   (or (System/getenv "GITHUB_ISSUE_REGEX")
       (str "github.com/" (gh-user)))))

(def gh-hide-labels (when-let [labels (System/getenv "GITHUB_HIDE_LABELS")]
                      (clojure.string/split labels #"\s*,\s*")))

(defn- api-options
  []
  (merge {:filter "all" :all-pages true} (gh-auth)))

(defn create-webhook []
  (create-hook "cldwalker" "gh-waiting-room" "web"
               {:url "http://localhost:8080/webhook" :content_type "json"}
               (assoc (gh-auth) :events ["issues"])))

(defn- issue-filter
  [issue]
  (and
   (not (get-in issue [:repository :private]))
   (re-find (issue-url-regex) (get! issue :html_url))
   (if gh-hide-labels
     (not (some
           (set gh-hide-labels)
           (map :name (:labels issue))))
     (= [] (:labels issue)))))

(defn- fetch-gh-issues
  []
  (swap! db assoc :issues (my-issues (api-options))))

(defn get! [m k]
  (or (get m k) (throw (ex-info "No value found for key in map" {:map m :key k}))))

(defn get-in! [m ks]
  (or (get-in m ks) (throw (ex-info "No value found for nested keys in map" {:map m :keys ks}))))

(defn- ->issue [issue]
  {:name (format "%s#%s"
                 (get-in! issue [:repository :full_name])
                 (get! issue :number))
   :type (if (get-in issue [:pull_request :html_url]) "pull request" "issue")
   :url (get! issue :html_url)
   :comments (get! issue :comments)
   :title (get! issue :title)
   :desc (let [body (get! issue :body)]
           (if (re-find #"^\s*$" body)
             ""
             (str (re-find #"^.{0,100}(?=\s|$)" body)
                  (if (> (count body) 100) " ..." ""))))
   :user (get-in! issue [:repository :owner :login])
   :repo-name (get-in! issue [:repository :name])
   :created (or (re-find #"\d{4}-\d\d-\d\d"(get! issue :created_at))
                (throw (ex-info "Failed to parse date from an issue" {:issue issue})))})

(defn- viewable-issues []
  (->> (:issues @db)
       (filter issue-filter)
       (map ->issue)
       reverse
       (map-indexed (fn [num elem]
                      (assoc elem :position (inc num))))))
(defn home-page
  [request]
  (when-not (seq (:issues @db)) (fetch-gh-issues))
  (ring-resp/response
   (render-resource "public/index.mustache"
                    {:github-user (gh-user)
                     :issues (viewable-issues)})))

; TODO: can this come from url-for?
(defn- full-url-for [path]
  (str (or (System/getenv "APP_DOMAIN") "http://localhost:8080") path))

(defn- comment-body [issue]
  (str
   (if (= (:type issue) "pull request")
     "Thanks for your pull request!"
     "Thanks for reporting your issue!")
   (format " You're [number %s in my list of open issues](%s). Use that link to check how soon your issue will be answered. Thanks for your patience."
           (:position issue)
           (full-url-for (str "/#" (:name issue))))))

(defn- update-issues-and-create-comment [full-name issue-num]
  (fetch-gh-issues)
  (let [issue (or
               (some #(and (= (:name %) (format "%s#%s" full-name issue-num)) %) (viewable-issues))
               (throw (ex-info "No issue found for webhook" {:full-name full-name :issue-num issue-num})))
        body (comment-body issue)]
    (create-comment (:user issue) (:repo-name issue) issue-num body (gh-auth))))

(defn webhook-page
  [request]
  (let [params (-> request :json-params)
        action (get! params "action")]
    (when (some #{action} ["created" "reopened"])
      (let [full-name (get-in! params ["repository" "full_name"])
            issue-num (get-in! params ["issue" "number"])]
        (update-issues-and-create-comment full-name issue-num))))
  {:status 200})

(defon-response html-content-type
  [response]
  (ring-resp/content-type response "text/html"))

(defroutes routes
  [[["/" {:get home-page}
     ^:interceptors [(body-params/body-params) html-content-type]
     ["/webhook" {:post webhook-page}]
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
