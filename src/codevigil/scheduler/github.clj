(ns codevigil.scheduler.github
  (:require [clojure.core.async :as async]
            [codevigil.db.core :as db]
            [codevigil.github.pull :as gh-pull]
            [codevigil.github.filter :as gh-filter]
            [codevigil.github.enrichment :as gh-enrichment]
            [java-time :as jt])
  (:import [java.util.concurrent Executors TimeUnit]
           [java.time Duration]))

;; Executor service for scheduled tasks
(def scheduler-executor (Executors/newScheduledThreadPool 1))

;; Channel for processing repository updates
(def update-channel (async/chan 10))

(defn update-repository!
  "Updates a repository by:
   1. Fetching new closed/merged PRs since last check
   2. Enriching all existing open PRs
   
   Parameters:
   - repo: Map containing repository information with :id, :owner, and :repo_name"
  [repo]
  (let [{:keys [id owner repo_name]} repo]
    (try
      (println (str "Updating repository: " owner "/" repo_name))
      
      ;; Get all pull requests
      (let [all-pulls (gh-pull/fetch-pull-requests owner repo_name)
            
            ;; Get existing PRs from database
            existing-prs (db/get-pull-requests-for-repository id)
            existing-pr-numbers (set (map :pull_requests/number existing-prs))
            
            ;; Find new PRs (not in database yet)
            new-prs (filter #(not (contains? existing-pr-numbers (:number %))) all-pulls)
            
            ;; Find recently closed/merged PRs (in last 7 days)
            week-ago (jt/minus (jt/instant) (jt/days 7))
            recently-closed-prs (filter #(and (or (= (:state %) "closed") 
                                                 (not (nil? (:merged_at %))))
                                             (when-let [updated (:updated_at %)]
                                               (jt/after? (jt/instant updated) week-ago)))
                                       all-pulls)
            
            ;; Find open PRs that need enrichment
            open-prs (filter #(= (:state %) "open") all-pulls)
            
            ;; Combine PRs that need processing
            prs-to-process (distinct (concat new-prs recently-closed-prs open-prs))]
        
        (println (str "Found " (count prs-to-process) " PRs to process"))
        
        ;; Enrich and save PRs
        (when (seq prs-to-process)
          (let [enriched-prs (gh-enrichment/enrich-pull-requests owner repo_name prs-to-process)]
            (db/save-enriched-pull-requests! id enriched-prs)
            (println (str "Processed " (count enriched-prs) " PRs for " owner "/" repo_name)))))
      
      ;; Update last_checked_at timestamp
      (db/update-repository-last-checked! id)
      
      (catch Exception e
        (println (str "Error updating repository " owner "/" repo_name ": " (.getMessage e)))))))

(defn process-repository-updates
  "Background worker that processes repository updates from the channel."
  []
  (async/go-loop []
    (when-let [repo (async/<! update-channel)]
      (try
        (update-repository! repo)
        (catch Exception e
          (println (str "Error in update worker: " (.getMessage e)))))
      (recur)))
  (println "Repository update worker started"))

(defn schedule-daily-updates!
  "Schedules a daily task to update all repositories.
   The task runs once per day at the specified hour (default: 2 AM)."
  [& {:keys [hour] :or {hour 2}}]
  (let [now (jt/local-date-time)
        target-time (jt/local-date-time (jt/local-date) hour 0)
        target-time (if (jt/after? now target-time)
                      (jt/plus target-time (jt/days 1))
                      target-time)
        initial-delay (jt/as (jt/duration now target-time) :seconds)
        day-in-seconds (* 24 60 60)]
    
    (.scheduleAtFixedRate scheduler-executor
                         (fn []
                           (try
                             (println "Starting daily repository update task")
                             (let [repos (db/get-all-repositories)]
                               (doseq [repo repos]
                                 (async/>!! update-channel repo)))
                             (catch Exception e
                               (println "Error in scheduled task: " (.getMessage e)))))
                         initial-delay
                         day-in-seconds
                         TimeUnit/SECONDS))
  (println (str "Daily update scheduled to run at " hour ":00 AM")))

(defn start-scheduler!
  "Starts the scheduler and background workers."
  []
  (process-repository-updates)
  (schedule-daily-updates!)
  (println "Repository update scheduler started"))

(defn stop-scheduler!
  "Stops the scheduler."
  []
  (.shutdown scheduler-executor)
  (println "Repository update scheduler stopped"))
