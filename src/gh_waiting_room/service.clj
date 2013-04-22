(ns gh-waiting-room.service
    (:require [io.pedestal.service.http :as bootstrap]
              [io.pedestal.service.http.route :as route]
              [io.pedestal.service.http.body-params :as body-params]
              [io.pedestal.service.http.route.definition :refer [defroutes]]
              [io.pedestal.service.interceptor :refer [defon-response]]
              [clostache.parser :refer [render-resource]]
              [clojure.data.json :as json]
              [gh-waiting-room.config :refer [gh-user gh-hmac-secret]]
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

(defn- hex-hmac-sha1
  [key input]
  (let [secret (javax.crypto.spec.SecretKeySpec. (. key getBytes "UTF-8") "HmacSHA1")
        hmac-sha1 (doto (javax.crypto.Mac/getInstance "HmacSHA1") (.init secret))
        bytes (. hmac-sha1 doFinal (. input getBytes "UTF-8"))
        hex (apply str (map (partial format "%02x") bytes))]
    hex))

(defn verify-secret!
  [secret body x-hub-signature]
  (let [expected (re-find #"(?<=sha1=).*$" (str x-hub-signature))
        actual (hex-hmac-sha1 secret body)]
    (prn "ACTUAL, EXPECTED:" actual expected)
    (when-not (= actual expected)
      (throw (ex-info (format "Expected sha1 '%s' but received '%s'" expected actual)
                      {:expected expected :actual actual})))))

(defn- json-payload->issue [body]
  (let [json (json/read-str body)
        full-name (get-in! json ["repository" "full_name"])
        num (get-in! json ["issue" "number"])]
    {:id (format "%s#%s" full-name num)
     :number num
     :action (get! json "action")}))

(defn webhook-page
  [request]
  (let [body (slurp (:body request))]
    (when-let [secret (gh-hmac-secret)]
      (verify-secret! secret body (-> request :headers (get "x-hub-signature"))))

    (let [issue (json-payload->issue body)]
      (case (:action issue)
        "created" (do (update-gh-issues)
                      (create-issue-comment @db (:id issue) (:number issue)))
        "reopened" (do (update-gh-issues)
                       (create-issue-comment @db (:id issue) (:number issue)))
        ;; for closed
        (when (some #(= (:id %) (:id issue)) (viewable-issues @db))
                   (update-gh-issues)))))
  {:status 200})

(defon-response html-content-type
  [response]
  (ring-resp/content-type response "text/html"))

(defroutes routes
  [[["/" {:get home-page}
     ^:interceptors [html-content-type]
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
