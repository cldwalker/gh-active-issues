(ns gh-waiting-room.service-test
  (:require [clojure.test :refer :all]
            [io.pedestal.service.test :refer :all]
            [io.pedestal.service.http :as bootstrap]
            [gh-waiting-room.service :as service]))

(def service
  (::bootstrap/service-fn (bootstrap/create-servlet service/service)))

(defn- body-of-home-page
  []
  (with-redefs [gh-waiting-room.github/viewable-issues
                (constantly [{:position "1"
                              :id "cldwalker/gh-waiting-room#1"
                              :url "https://github.com/cldwalker/gh-waiting-room/issues/1"}])
                service/update-gh-issues (constantly nil)
                gh-waiting-room.config/gh-user (constantly "Hal")]
    (:body (response-for service :get "/"))))

(deftest home-page-test
  (is (.contains
       (body-of-home-page)
       "<h1>Hal's Github Waiting Room</h1>")
      "Owner of issues is clearly shown.")
  (is (.contains
       (body-of-home-page)
       "href=\"https://github.com/cldwalker/gh-waiting-room/issues/1\"")
      "Issues link back to their origin.")
  (is (.contains
       (body-of-home-page)
       "id=\"issue_1\"")
      "Issues can be referenced by position.")
  (is (.contains
       (body-of-home-page)
       "id=\"cldwalker/gh-waiting-room#1\" href=\"#cldwalker/gh-waiting-room#1\"")
      "Issues can be referenced by their unique id and users know of it."))