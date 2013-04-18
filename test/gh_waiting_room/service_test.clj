(ns gh-waiting-room.service-test
  (:require [clojure.test :refer :all]
            [io.pedestal.service.test :refer :all]
            [gh-waiting-room.test-helper :refer [disallow-web-requests!]]
            [io.pedestal.service.http :as bootstrap]
            [gh-waiting-room.github :as github]
            [gh-waiting-room.service :as service]))

(def service
  (::bootstrap/service-fn (bootstrap/create-servlet service/service)))

(defn body-of-home-page
  []
  (disallow-web-requests!
   (with-redefs [github/viewable-issues
                 (constantly [{:position "1"
                               :id "cldwalker/gh-waiting-room#1"
                               :url "https://github.com/cldwalker/gh-waiting-room/issues/1"}])
                 service/update-gh-issues (constantly nil)
                 gh-waiting-room.config/gh-user (constantly "Hal")]
     (:body (response-for service :get "/")))))

(deftest home-page-test
  (let [body (body-of-home-page)]
    (is (.contains
         body
         "<h1>Hal's Github Waiting Room</h1>")
        "Owner of issues is clearly shown.")
    (is (.contains
         body
         "href=\"https://github.com/cldwalker/gh-waiting-room/issues/1\"")
        "Issues link back to their origin.")
    (is (.contains
         body
         "id=\"issue_1\"")
        "Issues can be referenced by position.")
    (is (.contains
         body
         "id=\"cldwalker/gh-waiting-room#1\" href=\"#cldwalker/gh-waiting-room#1\"")
        "Issues can be referenced by their unique id and users know of it.")))

(defn inc-mock-count [mocks-called key]
  (fn [& args]
   (swap! mocks-called update-in [key] (fnil inc 0))))

(defn fns-called-for-webhook-page
  [action & options]
  (disallow-web-requests!
   (let [{:keys [issues full-name]
          :or {issues [{:id "cldwalker/something"}]
               full-name "cldwalker/stub"}} options
               mocks-called (atom {})
               json {"action" action
                     "repository" {"full_name" full-name}
                     "issue" {"number" "1"}}]
     (with-redefs [service/update-gh-issues (inc-mock-count mocks-called :update-gh-issues)
                   github/create-issue-comment (inc-mock-count mocks-called :create-issue-comment)
                   github/viewable-issues (constantly issues)]
       (service/webhook-page {:json-params json}))
     @mocks-called)))

;;; Doesn't use response-for as it doesn't support :post yet
(deftest webhook-page-test
  (is (= (fns-called-for-webhook-page "created")
         {:update-gh-issues 1 :create-issue-comment 1})
      "Updates issues and creates comment for a newly created issue")
  (is (= (fns-called-for-webhook-page "closed")
         {})
      "Doesn't update issues for an inactive issue that is closed")
  (is (= (fns-called-for-webhook-page "closed"
                                      :full-name "cldwalker/repo"
                                      :issues [{:id "cldwalker/repo#1"}] )
         {:update-gh-issues 1})
      "Updates issues for an active issue that is closed"))