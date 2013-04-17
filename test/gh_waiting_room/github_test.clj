(ns gh-waiting-room.github-test
  (:require [clojure.test :refer :all]
            tentacles.issues
            [gh-waiting-room.github :as github :refer [->issue]]
            [echo.test.mock :refer [expect has-args times once]]))

(def valid-issue {:id "cldwalker/repo#10" :owner "cldwalker" :name "repo" :type "issue" :position 10})

(defn create-comment
  [& {:keys [id issues body-expects]
      :or {id "cldwalker/repo#10"
           body-expects (constantly true)
           issues [valid-issue]}}]
  (expect [tentacles.issues/create-comment
           (->>
            (has-args ["cldwalker" "repo" 10 body-expects])
            (times once))]
          (with-redefs [github/viewable-issues (constantly issues)]
            (github/create-issue-comment {} id 10))))

(deftest create-issue-comment-test
  (testing "creates comment with correct user and name"
    (create-comment))
  (testing "throws error since no repository is found for given id"
    (is (thrown? clojure.lang.ExceptionInfo
                 (create-comment :id "cldwalker/not-found#10"))))
  (testing "creates comment for issue with correct body position and link"
    (create-comment :issues [valid-issue]
                    :body-expects
                    #(.contains % "[#10 in my list of open issues](http://localhost:8080/#cldwalker/repo#10)")))
  (testing "creates comment for issue with correct body intro"
    (create-comment :issues [(assoc valid-issue :type "issue")]
                    :body-expects #(re-find #"^Thanks for reporting your issue" %)))
  (testing "creates comment for pull request with correct body intro"
    (create-comment :issues [(assoc valid-issue :type "pull request")]
                    :body-expects #(re-find #"^Thanks for your pull" %))))

(def valid-gh-issue {:repository {:full_name "cldwalker/faceplant"
                               :name "faceplant"
                               :owner {:login "cldwalker"}}
                  :user {:login "call_me_faceplant"}
                  ;; https://github.com/cldwalker/faceplant/pull/27
                  :pull_request {:html_url nil :diff_url nil :patch_url nil}
                  :html_url "https://github.com/cldwalker/faceplant/issues/27"
                  :number 27
                  :comments 0
                  :created_at "2013-04-12T21:05:28Z"
                  :title "So I faceplanted"
                  :body "And I really didn't like it."})

(deftest ->issue-test
  (testing "it converts issue map correctly"
    (is (= (->issue valid-gh-issue)
           {:id "cldwalker/faceplant#27" :type "issue" :url "https://github.com/cldwalker/faceplant/issues/27"
            :comments 0 :title (:title valid-gh-issue) :desc (:body valid-gh-issue)
            :owner "cldwalker" :user "call_me_faceplant" :name "faceplant"
            :created "2013-04-12"})))
  (testing "fails with get-in! if fetched issue doesn't have :full_name"
    (is (thrown? clojure.lang.ExceptionInfo
                 (->issue
                  (update-in valid-gh-issue [:repository :full_name] (constantly nil))))))
  (testing "fails with get! if fetched issue doesn't have :url"
    (is (thrown? clojure.lang.ExceptionInfo
                 (->issue
                  (assoc valid-gh-issue :html_url nil)))))
  (testing "fails if fetched issue doesn't have valid :created_at"
    (is (thrown? clojure.lang.ExceptionInfo
                 (->issue
                  (assoc valid-gh-issue :created_at "2013-01")))))
  (testing "desc handles whitespace-only string"
    (is (= (:desc (->issue (assoc valid-gh-issue :body "  ")))
           "  ")))
  (testing "desc remains the same if under 100"
    (is (= (:desc (->issue (assoc valid-gh-issue :body "SHORT")))
           "SHORT")))
  (testing "desc ends with word and shortened correctly if over 100 char"
    (is (= (:desc (->issue (assoc valid-gh-issue :body (str (apply str (repeat 98 "A")) " BBBB"))))
           (str (apply str (repeat 98 "A")) " ...")))))

#_(deftest viewable-issues-test
  (testing "filters out issues with labels by default")
  (testing "filters out issues with specific labels when $GITHUB_HIDE_LABELS set")
  (testing "filters out private")
  (testing "filters out repositories with $GITHUB_ISSUE_REGEX")
  (testing "filters out")
  (testing "sorts issues by coments and then created")
  (testing "adds :position by list order"))