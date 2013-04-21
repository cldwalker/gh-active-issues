(ns gh-waiting-room.github-tasks
  (:require [table.core :refer [table]]
            clojure.string
            [gh-waiting-room.github :refer [all-hooks create-all-webhooks
                                            create-webhook delete-webhook
                                            repo-hooks]]))

(defn print-hooks []
  (println "Fetching hooks...")
  (let [hooks (->> (all-hooks)
                   (map #(assoc % :hooks (clojure.string/join "," (:hooks %)))))]
    (table hooks :fields [:name :owner :hooks] :desc true)))

(defn abort [msg]
  (println msg)
  (System/exit 1))

(defn create-hooks [args]
  (when-not (System/getenv "GITHUB_APP_DOMAIN")
    (abort "$GITHUB_APP_DOMAIN must be set to use this subcommand"))
  (if (= ":all" (first args))
    (create-all-webhooks)
    (if (= 2 (count args))
      (apply create-webhook args)
      (abort "Usage: lein github create-hook [:all|USER REPO]"))))

(defn delete-hooks [args]
  (when-not (= 3 (count args))
    (abort "Usage: lein github delete-hook USER REPO ID"))
  (apply delete-webhook args))

(defn list-hooks [args]
  (case (count args)
    0 (print-hooks)
    2 (table
       (apply repo-hooks args)
       :fields [:id :url :name] :desc true)
    (abort "Usage: lein github hooks <USER> <REPO>")))

(defn -main [& [cmd & args]]
  (case cmd
    "hooks" (list-hooks args)
    "create-hook" (create-hooks args)
    "delete-hook" (delete-hooks args)
    (abort "Usage: lein github [hooks|create-hook|delete-hook]"))
  (System/exit 0))