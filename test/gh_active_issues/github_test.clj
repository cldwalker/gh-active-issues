(ns gh-active-issues.github-test
  (:require [clojure.test :refer :all]
            tentacles.issues
            [gh-active-issues.test-helper :refer [disallow-web-requests!]]
            [gh-active-issues.config :as config]
            [gh-active-issues.github :as github :refer [->issue]]
            [echo.test.mock :refer [expect has-args times once]]))

(def valid-issue {:id "cldwalker/repo#10" :owner "cldwalker" :name "repo" :type "issue" :position 10})

(defn create-comment
  [& {:keys [id issues body-expects]
      :or {id "cldwalker/repo#10"
           body-expects (constantly true)
           issues [valid-issue]}}]
  (disallow-web-requests!
   (expect [tentacles.issues/create-comment
            (->>
             (has-args ["cldwalker" "repo" 10 body-expects])
             (times once))]
           (with-redefs [github/viewable-issues (constantly issues)
                         config/app-domain (constantly "http://localhost:8080")
                         config/gh-auth (constantly "user:pass")]
             (github/create-issue-comment {} id 10)))))

(deftest create-issue-comment-test
  (testing "creates comment with correct user and name"
    (create-comment))
  (testing "throws error since no repository is found for given id"
    (is (thrown? clojure.lang.ExceptionInfo
                 (create-comment :id "cldwalker/not-found#10"))))
  (testing "creates comment for issue with correct body issue count and link"
    (create-comment :issues [valid-issue (assoc valid-issue :id "cldwalker/repo#11")]
                    :body-expects
                    #(.contains % "[one of my 2 active issues](http://localhost:8080/#cldwalker/repo#10)")))
  (testing "creates comment for issue with correct body intro"
    (create-comment :issues [(assoc valid-issue :type "issue")]
                    :body-expects #(re-find #"^Thanks for reporting your issue" %)))
  (testing "creates comment for pull request with correct body intro"
    (create-comment :issues [(assoc valid-issue :type "pull request")]
                    :body-expects #(re-find #"^Thanks for your pull" %))))

(def valid-gh-issue
  {:repository {:full_name "cldwalker/faceplant"
                :name "faceplant"
                :owner {:login "cldwalker"}}
   :user {:login "call_me_faceplant"}
   ;; https://github.com/cldwalker/faceplant/pull/27
   :pull_request {:html_url nil :diff_url nil :patch_url nil}
   :html_url "https://github.com/cldwalker/faceplant/issues/27"
   :number 27
   :comments 0
   :labels []
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
  (testing "desc can be blank"
    (is (= nil (-> valid-gh-issue (dissoc :body) ->issue :desc))))
  (testing "desc handles whitespace-only string"
    (is (= (:desc (->issue (assoc valid-gh-issue :body "  ")))
           "  ")))
  (testing "desc remains the same if under 100"
    (is (= (:desc (->issue (assoc valid-gh-issue :body "SHORT")))
           "SHORT")))
  (testing "desc ends with word and shortened correctly if over 100 char"
    (is (= (:desc (->issue (assoc valid-gh-issue :body (str (apply str (repeat 98 "A")) " BBBB"))))
           (str (apply str (repeat 98 "A")) " ...")))))

(defn create-issue
  [name & {:keys [private] :as options}]
  (let [issue (update-in valid-gh-issue [:repository :name] (constantly name))
        issue (if (contains? options :private)
                (update-in issue [:repository :private] (constantly private))
                issue)]
    (merge issue (dissoc options :private))))

(defn viewable-issues
  [db]
  (with-redefs [config/gh-user (constantly "cldwalker")]
    (github/viewable-issues db)))

(deftest issue-filter-test
  (testing "filters out private and allows public and unspecified :private issues"
    (is (= (->> {:issues [(create-issue "faceplant-public" :private false)
                          valid-gh-issue
                          (create-issue "faceplant-private" :private true)]}
             viewable-issues
             (map :name))
           ["faceplant-public" "faceplant"])))

  (testing "filters out repositories with user name by default i.e. no $GITHUB_ISSUE_REGEX"
    (is (= (->> {:issues [valid-gh-issue
                          (create-issue "pedestal" :html_url "https://github.com/pedestal/pedestal/issues/27")
                          (create-issue "ripl-multi_line" :html_url "https://github.com/janlelis/ripl-multi_line/issues/27")]}
                viewable-issues
                (map :name))
           ["faceplant"])))
  (testing "filters out repositories with $GITHUB_ISSUE_REGEX"
    (is (= (->> {:issues [valid-gh-issue
                          (create-issue "pedestal" :html_url "https://github.com/pedestal/pedestal/issues/27")
                          (create-issue "ripl-multi_line" :html_url "https://github.com/janlelis/ripl-multi_line/issues/27")]}
                (#(with-redefs [config/gh-user (constantly "(cldwalker|janlelis)")]
                    (github/viewable-issues %)))
                (map :name))
           ["faceplant" "ripl-multi_line"])))
  (testing "filters out repositories with $GITHUB_HIDE_LABELS"
    (is (= (->> {:issues [(create-issue "bug-repo" :labels [{:name "bug"}])
                          valid-gh-issue
                          (create-issue "enhancement-repo" :labels [{:name "enhancement"} {:name "old"}])
                          (create-issue "label-free-repo")]}
                (#(with-redefs [config/getenv (constantly "bug, enhancement")]
                    (viewable-issues %)))
                (map :name))
           ["faceplant" "label-free-repo"])))
  (testing "filters out repositories with labels by default i.e. when gh-hide-labels not set"
    (is (= (->> {:issues [(create-issue "old-repo" :labels [{:name "old"}])
                          valid-gh-issue
                          (create-issue "inactive-repo" :labels [{:name "enhancement"} {:name "inactive"}])]}
                viewable-issues
                (map :name))
           ["faceplant"])))
)

(deftest viewable-issues-test
  (testing "sorts issues by coments and then created"
    (is (= (->> {:issues [(create-issue "most-comments" :comments 7 :created_at "2013-03-13")
                          (create-issue "unanswered" :comments 0 :created_at "2013-01-10")
                          (create-issue "another-unanswered" :comments 0 :created_at "2013-04-01")]}
                viewable-issues
                (map :name))
           ["unanswered" "another-unanswered" "most-comments"])))
  (testing "adds :position by list order"
    (is (= (->> {:issues [(create-issue "most-comments" :comments 7 :created_at "2013-03-13")
                          (create-issue "unanswered" :comments 0 :created_at "2013-01-10")
                          (create-issue "another-unanswered" :comments 0 :created_at "2013-04-01")]}
                viewable-issues
                (map :position))
           [1 2 3]))))
