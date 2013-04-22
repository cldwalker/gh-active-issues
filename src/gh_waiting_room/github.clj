(ns gh-waiting-room.github
  (:require [tentacles.issues :refer [my-issues create-comment]]
            [gh-waiting-room.util :refer [get-in! get!]]
            [tentacles.repos :refer [create-hook hooks repos delete-hook]]
            [gh-waiting-room.config :refer [gh-auth issue-url-regex gh-hide-labels
                                            app-domain gh-user hook-forks]]))

; TODO: can this come from url-for?
(defn- full-url-for [path]
  (str (app-domain) path))

;;; github data fns
(defn- issue-filter
  "Filter fn used to only keep public issues that match allowed repository names
   and allowed label values."
  [issue]
  (and
   (not (get-in issue [:repository :private]))
   (re-find (issue-url-regex) (get! issue :html_url))
   (if-let [labels (gh-hide-labels)]
     (not (some
           (set labels)
           (map :name (:labels issue))))
     (= [] (:labels issue)))))

(defn ->issue
  "Converts an issue from github into a simplified issue used throughout this app."
  [issue]
  {:id (format "%s#%s"
               (get-in! issue [:repository :full_name])
               (get! issue :number))
   :type (if (get-in issue [:pull_request :html_url]) "pull request" "issue")
   :url (get! issue :html_url)
   :comments (get! issue :comments)
   :title (get! issue :title)
   :desc (let [body (get! issue :body)]
           (str (re-find #"^.{0,100}(?=\s|$)" body)
                (if (> (count body) 100) " ..." "")))
   :owner (get-in! issue [:repository :owner :login])
   :user (get-in! issue [:user :login])
   :name (get-in! issue [:repository :name])
   :created (or (re-find #"\d{4}-\d\d-\d\d"(get! issue :created_at))
                (throw (ex-info "Failed to parse date from an issue" {:issue issue})))})

(defn viewable-issues
  "Filter allowed issues, simplify their format and prepare them to be viewed on a web page."
  [db]
  (->> (:issues db)
       (filter issue-filter)
       (map ->issue)
       (sort-by (juxt :comments :created))
       (map-indexed (fn [num elem]
                      (assoc elem :position (inc num))))))

(defn- comment-body
  "Creates the comment body for an auto comment kicked off by an issue being opened or closed."
  [issue]
  (str
   (if (= (:type issue) "pull request")
     "Thanks for your pull request!"
     "Thanks for reporting your issue!")
   (format " You're [#%s in my list of open issues](%s). Use that link to check how soon your issue will be answered. Thanks for your patience."
           (:position issue)
           (full-url-for (str "/#" (:id issue))))))

;;; github fns
(defn fetch-gh-issues
  "Fetches all issues that authenticated user has push access to, public or private."
  []
  (my-issues (merge {:filter "all" :all-pages true} (gh-auth))))

(defn create-webhook
  [user name]
  (let [result
        (create-hook user name "web"
                     {:url (full-url-for "/webhook") :content_type "json"}
                     (assoc (gh-auth) :events ["issues"]))]
    (println (format "Created webhook for %s/%s with id %s"
                     user name (:id result)))))

(defn repo-hooks
  "List hooks for an individual repository."
  [user name]
  (->>
   (hooks user name (gh-auth))
   (map (fn [h]
          {:url (get-in h [:config :url]) :id (:id h) :name (:name h)}))))

(defn- list-repos
  []
  (let [all-repos (repos (assoc (gh-auth) :type "public" :all-pages true))
        filter-fn (if (hook-forks) identity (comp not :fork))]
    (->> all-repos
         (filter filter-fn)
         (map (fn [repo] {:name (get! repo :name) :owner (get-in! repo [:owner :login])})))))

(defn- list-repos-with-hooks
  []
  (map #(assoc % :hooks (repo-hooks (:owner %) (:name %))) (list-repos)))

(defn all-hooks
  "List hooks by repository for public repositories owned by user."
  []
  (let [hook-id #(or (:url %) (:name %))]
    (->> (list-repos-with-hooks)
         (map #(assoc % :hooks (map hook-id (:hooks %)))))))

(defn delete-webhook
  [user name id]
  (when (delete-hook user name id (gh-auth))
    (println (format "Deleted webhook for %s/%s" user name))))

(defn create-all-webhooks
  "Creates webhooks for all repositories that don't have one in $GITHUB_APP_DOMAIN."
  []
  (let [has-gh-webhook #(some #{(full-url-for "/webhook")} (map :url (:hooks %)))
        repos (remove has-gh-webhook (list-repos-with-hooks))]
    (doseq [repo repos]
      (create-webhook (:owner repo) (:name repo)))))

(defn create-issue-comment
  "Creates a comment for an issue given a map (db of issues)."
  [db issue-id issue-num]
  (let [issue (or
               (some #(and (= (:id %) issue-id) %) (viewable-issues db))
               (throw (ex-info "No issue found for webhook" {:issue-id issue-id})))
        body (comment-body issue)]
    (create-comment (:owner issue) (:name issue) issue-num body (gh-auth))))