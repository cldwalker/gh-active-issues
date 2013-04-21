(ns gh-waiting-room.service
    (:require [io.pedestal.service.http :as bootstrap]
              [io.pedestal.service.http.route :as route]
              [io.pedestal.service.http.body-params :as body-params]
              [io.pedestal.service.http.route.definition :refer [defroutes]]
              [io.pedestal.service.interceptor :refer [defon-response]]
              [clostache.parser :refer [render-resource]]
              [gh-waiting-room.config :refer [gh-user]]
              [gh-waiting-room.github :refer [get! get-in! create-issue-comment
                                              viewable-issues fetch-gh-issues]]
              [ring.util.response :as ring-resp]))

(def db (atom {}))

(defn- update-gh-issues
  []
  (swap! db assoc :issues (fetch-gh-issues)))

(defn home-page
  [request]
  (when-not (seq (:issues @db)) (update-gh-issues))
  (ring-resp/response
   (render-resource "public/index.mustache"
                    {:github-user (gh-user)
                     :issues (viewable-issues @db)})))

(defn webhook-page
  [request]
  (let [params (-> request :json-params)
        action (get! params "action")
        full-name (get-in! params ["repository" "full_name"])
        issue-num (get-in! params ["issue" "number"])
        issue-id (format "%s#%s" full-name issue-num)]
    (when (= action "opened")
      (update-gh-issues)
      (create-issue-comment @db issue-id issue-num))
    (when (and
           (some #{action} ["closed" "reopened"])
           (some #(= (:id %) issue-id) (viewable-issues @db)))
      (update-gh-issues)))
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
