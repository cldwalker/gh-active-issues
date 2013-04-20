(ns gh-waiting-room.github-tasks
  (:require [table.core :refer [table]]
            clojure.string
            [gh-waiting-room.github :refer [all-hooks]]))

(defn print-hooks []
  (println "Fetching hooks...")
  (let [hooks (->> (all-hooks)
                   ;; convert to subvectors for table ordering
                   (map #(vec [(:name %) (:owner %) (clojure.string/join "," (:hooks %))]))
                   (cons [:name :owner :hooks]))]
    (table hooks)
    (println (format "%s rows in set" (count hooks)))))

(defn -main [& [cmd arg]]
  (case cmd
    "hooks" (print-hooks)
    (println "Usage: lein github [hooks|create-hook|delete-hook]")))