(ns gh-waiting-room.github
  (:require [tentacles.issues :refer [my-issues create-comment]]
            [tentacles.repos :refer [create-hook hooks]]
            [gh-waiting-room.config :refer [gh-auth issue-url-regex gh-hide-labels app-domain]]))

;;; util fns
; TODO: can this come from url-for?
(defn- full-url-for [path]
  (str (app-domain) path))

(defn get-in! [m ks]
  (or (get-in m ks) (throw (ex-info "No value found for nested keys in map" {:map m :keys ks}))))

(defn get! [m k]
  (or (get m k) (throw (ex-info "No value found for key in map" {:map m :key k}))))

;;; github data fns
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

(defn- ->issue [issue]
  {:id (format "%s#%s"
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
   :name (get-in! issue [:repository :name])
   :created (or (re-find #"\d{4}-\d\d-\d\d"(get! issue :created_at))
                (throw (ex-info "Failed to parse date from an issue" {:issue issue})))})

(defn viewable-issues [db]
  (->> (:issues db)
       (filter issue-filter)
       (map ->issue)
       (sort-by (juxt :comments :created))
       (map-indexed (fn [num elem]
                      (assoc elem :position (inc num))))))

(defn- comment-body [issue]
  (str
   (if (= (:type issue) "pull request")
     "Thanks for your pull request!"
     "Thanks for reporting your issue!")
   (format " You're [#%s in my list of open issues](%s). Use that link to check how soon your issue will be answered. Thanks for your patience."
           (:position issue)
           (full-url-for (str "/#" (:id issue))))))

;;; github fns
(defn fetch-gh-issues
  []
  (my-issues (merge {:filter "all" :all-pages true} (gh-auth))))

(defn create-webhook [user name]
  (create-hook user name "web"
               {:url (full-url-for "/webhook") :content_type "json"}
               (assoc (gh-auth) :events ["issues"])))

(defn list-hooks [user name]
  (->>
   (hooks user name (gh-auth))
   (map (fn [h]
          {:url (get-in! h [:config :url]) :id (:id h)}))))

(defn create-issue-comment [db issue-id issue-num]
  (let [issue (or
               (some #(and (= (:id %) issue-id) %) (viewable-issues db))
               (throw (ex-info "No issue found for webhook" {:issue-id issue-id})))
        body (comment-body issue)]
    (create-comment (:user issue) (:name issue) issue-num body (gh-auth))))