(ns codevigil.api.routes
  (:require [ring.util.response :as resp]
            [codevigil.github.utils :as gh-utils]
            [codevigil.github.async :as gh-async]
            [codevigil.metrics.calculator :as metrics-calculator]
            [codevigil.metrics.aggregation :as metrics-aggregation]
            [codevigil.db.core :as db]
            [clojure.tools.logging :as log]))

(defn register-repo-handler
  "Handler for POST /api/repositories endpoint.
   Accepts a GitHub repository URL and immediately returns a response.
   The pull request fetching, enrichment, and database storage happens
   asynchronously in the background.
   Metrics are calculated immediately for new repositories."
  [request]
  (let [{:keys [repo_url]} (:body-params request)]
    (if-let [{:keys [owner repo]} (gh-utils/parse-repo-url repo_url)]
      (try
        ;; Store repository in database
        (let [repo-id (db/upsert-repository! {:owner owner 
                                             :repo_name repo 
                                             :repo_url repo_url})]
          
          ;; Queue repository for asynchronous processing with immediate metrics calculation
          (gh-async/process-repository-async! owner repo repo_url repo-id :calculate-metrics true)
          
          ;; Return immediate success response
          (resp/response {:status "success"
                         :repository {:owner owner :repo repo :url repo_url}
                         :message "Repository queued for processing with immediate metrics calculation"}))
        
        (catch Exception e
          (println "Error queuing repository:" (.getMessage e))
          (resp/status (resp/response {:status "error" 
                                      :message (.getMessage e)}) 
                      500)))
      
      ;; Invalid repository URL
      (resp/bad-request {:status "error"
                        :message "Invalid repository URL."}))))

(defn get-repository-metrics-handler
  "Handler for GET /api/repositories/:owner/:repo/metrics endpoint.
   Returns metrics for a repository with daily or weekly aggregation.
   
   Query parameters:
   - aggregation: 'daily' or 'weekly' (default: 'weekly')
   
   Returns:
   - For weekly aggregation: metrics for the last 5 weeks
   - For daily aggregation: metrics for the last 5 days"
  [request]
  (log/info "Request received for get-repository-metrics-handler:" request)
  (let [owner (get-in request [:path-params :owner])
        repo (get-in request [:path-params :repo])
        aggregation (or (get-in request [:parameters :query :aggregation]) "weekly")
        
        ;; Validate aggregation parameter
        aggregation (if (contains? #{"daily" "weekly"} aggregation)
                      aggregation
                      "weekly")]
    
    (log/info "Processing metrics request with params - owner:" owner "repo:" repo "aggregation:" aggregation)
    
    (if-let [repository (do
                          (log/info "Attempting to fetch repository from database...")
                          (let [repo-result (db/get-repository-by-owner-repo owner repo)]
                            (log/info "Repository fetch result:" repo-result)
                            repo-result))]
      (try
        (log/info "Repository found. ID:" (:repositories/id repository))
        (let [repo-id (:repositories/id repository)
              
              ;; Get aggregated metrics
              metrics (do
                        (log/info "Fetching" aggregation "metrics for repo-id:" repo-id)
                        (case aggregation
                          "daily" (do
                                   (log/info "Getting daily metrics")
                                   (metrics-aggregation/get-daily-metrics repo-id))
                          "weekly" (do
                                    (log/info "Getting weekly metrics")
                                    (metrics-aggregation/get-weekly-metrics repo-id))))]
          
          (log/info "Metrics fetched successfully:" metrics)
          ;; Return metrics
          (resp/response {:status "success"
                         :repository {:owner owner :repo repo}
                         :aggregation aggregation
                         :metrics metrics}))
        
        (catch Exception e
          (log/error "Exception in get-repository-metrics-handler:" (.getMessage e) e)
          (println "Error getting repository metrics:" (.getMessage e))
          (let [cause (.getCause e)
                cause-message (when cause (.getMessage cause))
                error-message (if cause-message
                               (str (.getMessage e) " - Cause: " cause-message)
                               (.getMessage e))]
            (resp/status (resp/response {:status "error" 
                                        :message error-message
                                        :type (.getSimpleName (class e))}) 
                        500))))
      
      ;; Repository not found
      (do
        (log/warn "Repository not found:" owner "/" repo)
        (resp/not-found {:status "error"
                        :message (str "Repository " owner "/" repo " not found.")})))))

(defn get-repositories-handler
  "Handler for GET /api/repositories endpoint.
   Returns a list of all repositories in the database."
  [_]
  (try
    (let [repositories (db/get-all-repositories)
          ;; Transform the data for the response
          formatted-repos (map (fn [repo]
                                {:id (:repositories/id repo)
                                 :owner (:repositories/owner repo)
                                 :name (:repositories/repo_name repo)
                                 :url (:repositories/repo_url repo)
                                 :created_at (:repositories/created_at repo)
                                 :last_checked_at (:repositories/last_checked_at repo)})
                              repositories)]
      (resp/response {:status "success"
                     :repositories formatted-repos}))
    
    (catch Exception e
      (log/error "Exception in get-repositories-handler:" (.getMessage e) e)
      (println "Error getting repositories:" (.getMessage e))
      (resp/status (resp/response {:status "error" 
                                  :message (.getMessage e)}) 
                  500))))
