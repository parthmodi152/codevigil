(ns codevigil.metrics.aggregation
  (:require [codevigil.db.core :as db]
            [java-time :as jt]
            [clojure.java.jdbc :as jdbc]
            [next.jdbc :as next-jdbc]
            [clojure.tools.logging :as log]))

(defn- median
  "Calculate the median value of a collection of numbers.
   Returns nil if the collection is empty."
  [coll]
  (when (seq coll)
    (let [sorted (sort coll)
          cnt (count sorted)
          half (quot cnt 2)]
      (if (odd? cnt)
        (nth sorted half)
        (/ (+ (nth sorted (dec half)) (nth sorted half)) 2.0)))))

(defn- format-duration-seconds
  "Format duration in seconds to a human-readable string."
  [seconds]
  (when seconds
    (let [days (quot seconds 86400)
          hours (quot (rem seconds 86400) 3600)
          minutes (quot (rem seconds 3600) 60)
          remaining-seconds (rem seconds 60)]
      (cond
        (pos? days) (format "%d days, %d hours" days hours)
        (pos? hours) (format "%d hours, %d minutes" hours minutes)
        (pos? minutes) (format "%d minutes, %d seconds" minutes remaining-seconds)
        :else (format "%d seconds" remaining-seconds)))))

(defn- get-metrics-in-date-range
  "Get metrics for pull requests created within a specific date range."
  [repository-id start-date end-date]
  (next-jdbc/execute! db/datasource
                     ["SELECT m.* 
                       FROM metrics m
                       JOIN pull_requests pr ON m.pull_request_id = pr.id
                       WHERE pr.repository_id = ?
                       AND pr.created_at >= ?
                       AND pr.created_at < ?
                       ORDER BY pr.created_at"
                      repository-id
                      (java.sql.Timestamp/from (jt/instant start-date))
                      (java.sql.Timestamp/from (jt/instant end-date))]))

(defn- aggregate-metrics-for-period
  "Aggregate metrics for a specific time period.
   Returns a map with median values for PR merge time and code review turnaround time."
  [repository-id start-date end-date period-name]
  (let [metrics (get-metrics-in-date-range repository-id start-date end-date)
        
        ;; Extract metric values
        merge-times (remove nil? (map :metrics/pr_merge_time_seconds metrics))
        review-times (remove nil? (map :metrics/code_review_turnaround_seconds metrics))
        
        ;; Calculate medians
        median-merge-time (median merge-times)
        median-review-time (median review-times)
        
        ;; Count PRs
        total-prs (count metrics)
        merged-prs (count merge-times)
        reviewed-prs (count review-times)]
    
    {:period period-name
     :start_date (str start-date)
     :end_date (str end-date)
     :total_prs total-prs
     :merged_prs merged-prs
     :reviewed_prs reviewed-prs
     :median_merge_time_seconds median-merge-time
     :median_merge_time (format-duration-seconds median-merge-time)
     :median_review_turnaround_seconds median-review-time
     :median_review_turnaround (format-duration-seconds median-review-time)}))

(defn get-daily-metrics
  "Get daily metrics for the last 5 days.
   Returns a collection of daily metrics, each with median values."
  [repository-id]
  (try
    (log/info "Starting get-daily-metrics for repository-id:" repository-id)
    (let [today (jt/truncate-to (jt/zoned-date-time) :days)
          _ (log/debug "Current date truncated to days:" today)
          
          ;; Generate date ranges for the last 5 days
          date-ranges (for [days-ago (range 5)]
                        (let [start-date (jt/minus today (jt/days days-ago))
                              end-date (jt/plus start-date (jt/days 1))
                              period-name (if (zero? days-ago)
                                            "Today"
                                            (str days-ago " day" (when (> days-ago 1) "s") " ago"))]
                          [start-date end-date period-name]))
          _ (log/debug "Date ranges calculated:" date-ranges)]
      
      ;; Aggregate metrics for each day
      (log/info "Aggregating metrics for each day")
      (mapv (fn [[start-date end-date period-name]]
              (try
                (log/debug "Aggregating metrics for period:" period-name)
                (aggregate-metrics-for-period repository-id start-date end-date period-name)
                (catch Exception e
                  (log/error "Error aggregating metrics for period" period-name ":" (.getMessage e))
                  {:period period-name
                   :start_date (str start-date)
                   :end_date (str end-date)
                   :error (.getMessage e)})))
            date-ranges))
    (catch Exception e
      (log/error "Error in get-daily-metrics:" (.getMessage e) e)
      (throw (IllegalStateException. (str "Failed to get daily metrics: " (.getMessage e)) e)))))

(defn get-weekly-metrics
  "Get weekly metrics for the last 5 weeks.
   Returns a collection of weekly metrics, each with median values."
  [repository-id]
  (try
    (log/info "Starting get-weekly-metrics for repository-id:" repository-id)
    (let [today (jt/truncate-to (jt/zoned-date-time) :days)
          _ (log/debug "Calculating start of week")
          start-of-week (jt/adjust today :previous-or-same-day-of-week :monday)
          _ (log/debug "Start of week calculated as:" start-of-week)
          
          ;; Generate date ranges for the last 5 weeks
          date-ranges (for [weeks-ago (range 5)]
                        (let [start-date (jt/minus start-of-week (jt/weeks weeks-ago))
                              end-date (jt/plus start-date (jt/weeks 1))
                              period-name (if (zero? weeks-ago)
                                            "This week"
                                            (str weeks-ago " week" (when (> weeks-ago 1) "s") " ago"))]
                          [start-date end-date period-name]))
          _ (log/debug "Date ranges calculated:" date-ranges)]
      
      ;; Aggregate metrics for each week
      (log/info "Aggregating metrics for each week")
      (mapv (fn [[start-date end-date period-name]]
              (try
                (log/debug "Aggregating metrics for period:" period-name)
                (aggregate-metrics-for-period repository-id start-date end-date period-name)
                (catch Exception e
                  (log/error "Error aggregating metrics for period" period-name ":" (.getMessage e))
                  {:period period-name
                   :start_date (str start-date)
                   :end_date (str end-date)
                   :error (.getMessage e)})))
            date-ranges))
    (catch Exception e
      (log/error "Error in get-weekly-metrics:" (.getMessage e) e)
      (throw (IllegalStateException. (str "Failed to get weekly metrics: " (.getMessage e)) e)))))
