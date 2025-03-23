(ns codevigil.github.enrichment
  (:require [clojure.core.async :as async]
            [codevigil.github.extended :as extended]))

(defn enrich-pull-requests
  "Enriches multiple pull requests with reviews, comments, and requested reviewers.
   Uses core.async to process pull requests concurrently for better performance.
   
   Parameters:
   - owner: Repository owner
   - repo: Repository name
   - pull-requests: Sequence of pull request data from GitHub API
   
   Returns:
   - Vector of enriched pull requests"
  [owner repo pull-requests]
  (let [concurrency 5 ; Number of concurrent requests
        pull-count (count pull-requests)
        in-ch (async/to-chan! pull-requests)
        out-ch (async/chan pull-count)
        
        ; Process function for each pull request
        process-fn (fn [pull]
                     (try
                       (let [pull-number (:number pull)
                             enriched (extended/enrich-pull-request owner repo pull-number)]
                         enriched)
                       (catch Exception e
                         ; Return error information if enrichment fails
                         {:error true
                          :pull-request pull
                          :message (.getMessage e)})))]
    
    ; Start workers to process pull requests concurrently
    (async/pipeline-blocking concurrency
                           out-ch
                           (map process-fn)
                           in-ch)
    
    ; Collect results
    (let [result (async/<!! (async/into [] out-ch))
          ; Filter out errors
          [errors valid] (split-with :error result)]
      
      ; Log errors if any
      (when (seq errors)
        (println "Errors occurred while enriching pull requests:")
        (doseq [error errors]
          (println (str "  Error for PR #" (get-in error [:pull-request :number]) ": " (:message error)))))
      
      ; Return valid results
      valid)))

(defn process-repository
  "Process a repository by fetching recent pull requests and enriching them.
   
   Parameters:
   - owner: Repository owner
   - repo: Repository name
   - pull-requests: Sequence of pull request data from GitHub API
   
   Returns:
   - Map with repository info and enriched pull requests"
  [owner repo pull-requests]
  (let [enriched-pulls (enrich-pull-requests owner repo pull-requests)]
    {:repository {:owner owner :repo repo}
     :pull_requests enriched-pulls}))
