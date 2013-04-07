(ns gh-waiting-room.service
    (:require [io.pedestal.service.http :as bootstrap]
              [io.pedestal.service.http.route :as route]
              [io.pedestal.service.http.body-params :as body-params]
              [io.pedestal.service.http.route.definition :refer [defroutes]]
              [io.pedestal.service.interceptor :refer [defon-response]]
              [clostache.parser :refer [render-resource]]
              [tentacles.issues :refer [my-issues]]
              [ring.util.response :as ring-resp]))

(def db (atom {}))

(defn- get-auth
  []
  (or (System/getenv "GITHUB_AUTH")
      (throw (ex-info "Set $GITHUB_AUTH to fetch github issues" {}))))

(defn- api-options
  []
  {:filter "all" :all-pages true :auth (get-auth)})

;; TODO: make this customizable
(defn- issue-filter
  [issue]
  (and (re-find #"github.com/cldwalker" (str (:html_url issue)))
       (= [] (:labels issue))))

(defn- fetch-gh-issues
  []
  (swap! db assoc :issues (my-issues (api-options))))

(defn ->issue [issue]
  {:name (str
          (or (re-find #"[^/]+/[^/]+(?=/issues/\d+)" (:html_url issue))
              (throw (ex-info "Failed to parse name from an issue" {:issue issue})))
          "#"
          (:number issue))
   :url (:html_url issue)
   :desc (str (re-find #".{0,100}\S" (:body issue))
              (if (> (count (:body issue)) 100) "") " ...")
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
