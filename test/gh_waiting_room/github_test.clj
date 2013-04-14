(ns gh-waiting-room.github-test
  (:require [clojure.test :refer :all]
            tentacles.issues
            [gh-waiting-room.github :as github]
            [echo.test.mock :refer [expect has-args times once]]))

(def valid-issue {:id "cldwalker/repo#10" :user "cldwalker" :name "repo" :type "issue" :position 10})

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
