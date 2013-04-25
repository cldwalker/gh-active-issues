(ns gh-active-issues.service-test
  (:require [clojure.test :refer :all]
            [io.pedestal.service.test :refer :all]
            [gh-active-issues.test-helper :refer [disallow-web-requests!]]
            [io.pedestal.service.http :as bootstrap]
            [gh-active-issues.github :as github]
            [gh-active-issues.config :as config]
            [clojure.data.json :as json]
            [gh-active-issues.service :as service]))

(def service
  (::bootstrap/service-fn (bootstrap/create-servlet service/service)))

(defn body-of-home-page
  []
  (disallow-web-requests!
   (with-redefs [github/viewable-issues
                 (constantly [{:position "1"
                               :id "cldwalker/gh-active-issues#1"
                               :url "https://github.com/cldwalker/gh-active-issues/issues/1"}])
                 service/update-gh-issues (constantly nil)
                 config/gh-user (constantly "Hal")]
     (:body (response-for service :get "/")))))

(deftest home-page-test
  (let [body (body-of-home-page)]
    (is (.contains
         body
         "<h1>Hal's Active Github Issues</h1>")
        "Owner of issues is clearly shown.")
    (is (.contains
         body
         "href=\"https://github.com/cldwalker/gh-active-issues/issues/1\"")
        "Issues link back to their origin.")
    (is (.contains
         body
         "id=\"issue_1\"")
        "Issues can be referenced by position.")
    (is (.contains
         body
         "id=\"cldwalker/gh-active-issues#1\" href=\"#cldwalker/gh-active-issues#1\"")
        "Issues can be referenced by their unique id and users know of it.")))

(defn inc-mock-count [mocks-called key]
  (fn [& args]
   (swap! mocks-called update-in [key] (fnil inc 0))))

(defn fns-called-for-webhook-page
  [action & options]
  (disallow-web-requests!
   (let [{:keys [issues full-name secret sha1]
          :or {issues [{:id "cldwalker/something"}]
               full-name "cldwalker/stub"}} options
               mocks-called (atom {})
               json {"action" action
                     "repository" {"full_name" full-name}
                     "issue" {"number" "1"}}
               body (json/write-str json)
               headers (if sha1 {"x-hub-signature" sha1} {})]
     (with-redefs [service/update-gh-issues (inc-mock-count mocks-called :update-gh-issues)
                   github/create-issue-comment (inc-mock-count mocks-called :create-issue-comment)
                   github/viewable-issues (constantly issues)
                   ;; TODO: just create an actual stream
                   slurp (constantly body)
                   config/gh-hmac-secret (constantly secret)]
       (service/webhook-page {:body body :headers headers}))
     @mocks-called)))

;;; Doesn't use response-for as it doesn't support :post yet
(deftest webhook-page-test-without-secret
  (is (= (fns-called-for-webhook-page "opened")
         {:update-gh-issues 1 :create-issue-comment 1})
      "Updates issues and creates comment for a newly opened issue")
  (is (= (fns-called-for-webhook-page "closed")
         {})
      "Doesn't update issues for an inactive issue that is closed")
  (is (= (fns-called-for-webhook-page "closed"
                                      :full-name "cldwalker/repo"
                                      :issues [{:id "cldwalker/repo#1"}] )
         {:update-gh-issues 1})
      "Updates issues for an active issue that is closed"))

(deftest webhook-page-test-with-secret
  (is (thrown? clojure.lang.ExceptionInfo
               (fns-called-for-webhook-page "opened"
                                            :secret "opensesame"))
      "fails authentication with no header")
  (is (thrown? clojure.lang.ExceptionInfo
               (fns-called-for-webhook-page "opened"
                                            :secret "opensesame"
                                            :sha1 "sha1=password"))
      "fails authentication with invalid sha1")
  (is (= (fns-called-for-webhook-page "opened"
                                      :secret "opensesame"
                                      :sha1 "sha1=efc1109b206ac96607e4d161c09614f104f901f0")
         {:update-gh-issues 1 :create-issue-comment 1})
      "Updates issues and creates comment for a newly opened issue")
  (is (= (fns-called-for-webhook-page "closed"
                                      :secret "opensesame"
                                      :sha1 "sha1=79af8ab1fc2b90d39a706042f299b9639f06922e")
         {})
      "Doesn't update issues for an inactive issue that is closed")
  (is (= (fns-called-for-webhook-page "closed"
                                      :full-name "cldwalker/repo"
                                      :issues [{:id "cldwalker/repo#1"}]
                                      :secret "opensesame"
                                      :sha1 "sha1=dbf5e8c44b794de84125049f542fcc562beb3e43")
         {:update-gh-issues 1})
      "Updates issues for an active issue that is closed"))
