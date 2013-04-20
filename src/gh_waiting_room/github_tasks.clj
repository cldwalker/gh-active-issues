(ns gh-waiting-room.github-tasks
  (:require [table.core :refer [table]]
            clojure.string
            [gh-waiting-room.github :refer [all-hooks create-all-webhooks]]))

(defn print-hooks []
  (println "Fetching hooks...")
  (let [hooks (->> (all-hooks)
                   ;; convert to subvectors for table ordering
                   (map #(vec [(:name %) (:owner %) (clojure.string/join "," (:hooks %))]))
                   (cons [:name :owner :hooks]))]
    (table hooks)
    (println (format "%s rows in set" (count hooks)))))

(defn abort [msg]
  (println msg)
  (System/exit 1))

(defn create-hooks [arg]
  (when-not (System/getenv "GITHUB_APP_DOMAIN")
    (abort "$GITHUB_APP_DOMAIN must be set to use this subcommand"))
  (case arg
    ":all" (create-all-webhooks)
    (abort "Usage: lein github create-hook [:all|user/repo]")))

(defn -main [& [cmd arg]]
  (case cmd
    "hooks" (print-hooks)
    "create-hook" (create-hooks arg)
    (abort "Usage: lein github [hooks|create-hook|delete-hook]"))
  (System/exit 0))