(ns codevigil.github.async
  (:require [clojure.core.async :as async]
            [codevigil.github.pull :as gh-pull]
            [codevigil.github.filter :as gh-filter]
            [codevigil.github.enrichment :as gh-enrichment]
            [codevigil.metrics.calculator :as metrics-calculator]
            [codevigil.db.core :as db]))

;; Channel for processing repository requests
(def repo-channel (async/chan 10))

;; Start the background worker process
(defn start-background-worker!
  "Starts a background worker that processes repository requests asynchronously."
  []
  (async/go-loop []
    (when-let [{:keys [owner repo repo-url repo-id calculate-metrics]} (async/<! repo-channel)]
      (try
        (println (str "Processing repository: " owner "/" repo))
        
        ;; Fetch and filter pull requests
        (let [all-pulls (gh-pull/fetch-pull-requests owner repo)
              recent-pulls (gh-filter/filter-recent-pulls all-pulls)]
          
          (println (str "Found " (count recent-pulls) " recent pull requests"))
          
          ;; Enrich pull requests concurrently
          (let [enriched-pulls (gh-enrichment/enrich-pull-requests owner repo recent-pulls)]
            
            ;; Save to database
            (db/save-enriched-pull-requests! repo-id enriched-pulls)
            
            ;; Calculate metrics immediately if requested (for new repositories)
            (when calculate-metrics
              (println (str "Calculating initial metrics for repository: " owner "/" repo))
              (metrics-calculator/calculate-metrics-for-repository repo-id))
            
            (println (str "Completed processing repository: " owner "/" repo))))
        
        (catch Exception e
          (println (str "Error processing repository " owner "/" repo ": " (.getMessage e)))))
      
      (recur)))
  (println "Background worker started"))

;; Function to queue a repository for processing
(defn process-repository-async!
  "Queues a repository for asynchronous processing.
   Returns immediately without waiting for processing to complete.
   
   Parameters:
   - repo-data: Map containing:
     - :owner - Repository owner
     - :repo - Repository name
     - :repo-url - Repository URL
     - :repo-id - Repository ID in the database
     - :calculate-metrics (optional) - Whether to calculate metrics immediately after processing"
  [{:keys [owner repo repo-url repo-id calculate-metrics] :or {calculate-metrics false}}]
  (async/>!! repo-channel {:owner owner
                          :repo repo
                          :repo-url repo-url
                          :repo-id repo-id
                          :calculate-metrics calculate-metrics}))
