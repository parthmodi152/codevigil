(ns codevigil.metrics.calculator
  (:require [codevigil.db.core :as db]
            [java-time :as jt]))

(defn calculate-pr-merge-time
  "Calculates the PR merge time in seconds.
   PR Merge Time = merged_at - created_at
   
   Returns nil if the PR is not merged."
  [pull-request]
  (when-let [merged-at (:pull_requests/merged_at pull-request)]
    (when-let [created-at (:pull_requests/created_at pull-request)]
      (jt/as (jt/duration created-at merged-at) :seconds))))

(defn find-first-approval
  "Finds the first review with state 'APPROVED' for a pull request.
   Returns the review or nil if no approval found."
  [reviews]
  (first (filter #(= (:reviews/state %) "APPROVED") 
                (sort-by :reviews/submitted_at reviews))))

(defn calculate-code-review-turnaround
  "Calculates the code review turnaround time in seconds.
   
   Start Time: PR created_at (or first review request if available)
   End Time: First 'APPROVED' review, or merged_at, or closed_at (in that order of preference)
   
   Returns nil if no proper end time exists."
  [pull-request reviews requested-reviewers]
  (let [start-time (:pull_requests/created_at pull-request)
        
        ;; Find end time based on priority:
        ;; 1. First approved review
        ;; 2. merged_at
        ;; 3. closed_at
        first-approval (find-first-approval reviews)
        end-time (or (:reviews/submitted_at first-approval)
                     (:pull_requests/merged_at pull-request)
                     (:pull_requests/closed_at pull-request))]
    
    (when (and start-time end-time)
      {:turnaround-seconds (jt/as (jt/duration start-time end-time) :seconds)
       :first-review-requested-at (str start-time)
       :first-approval-at (when first-approval 
                            (str (:reviews/submitted_at first-approval)))})))

(defn calculate-metrics-for-pull-request
  "Calculates metrics for a single pull request.
   
   Returns a map with:
   - pr-merge-time-seconds
   - code-review-turnaround-seconds
   - first-review-requested-at
   - first-approval-at"
  [pull-request-id]
  (let [enriched-pr (db/get-enriched-pull-request pull-request-id)
        pull-request (:pull-request enriched-pr)
        reviews (:reviews enriched-pr)
        requested-reviewers (:requested-reviewers enriched-pr)
        
        ;; Calculate PR merge time
        pr-merge-time (calculate-pr-merge-time pull-request)
        
        ;; Calculate code review turnaround
        review-turnaround (calculate-code-review-turnaround 
                           pull-request reviews requested-reviewers)]
    
    (when (or pr-merge-time review-turnaround)
      {:pr-merge-time-seconds pr-merge-time
       :code-review-turnaround-seconds (:turnaround-seconds review-turnaround)
       :first-review-requested-at (:first-review-requested-at review-turnaround)
       :first-approval-at (:first-approval-at review-turnaround)})))

(defn calculate-metrics-for-repository
  "Calculates metrics for all pull requests in a repository.
   Saves the metrics to the database."
  [repository-id]
  (let [pull-requests (db/get-pull-requests-for-repository repository-id)]
    (doseq [pr pull-requests]
      (let [pr-id (:pull_requests/id pr)
            pr-number (:pull_requests/number pr)
            metrics (calculate-metrics-for-pull-request pr-id)]
        (when metrics
          (db/save-metrics! repository-id pr-id pr-number metrics))))))

(defn calculate-metrics-for-all-repositories
  "Calculates metrics for all repositories."
  []
  (let [repositories (db/get-all-repositories)]
    (doseq [repo repositories]
      (let [repo-id (:repositories/id repo)]
        (println (str "Calculating metrics for repository: " 
                      (:repositories/owner repo) "/" 
                      (:repositories/repo_name repo)))
        (calculate-metrics-for-repository repo-id)))))
