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
    (let [sorted (sort (map #(if (number? %) % (double %)) coll))
          cnt (count sorted)
          half (quot cnt 2)]
      (if (odd? cnt)
        (nth sorted half)
        (/ (+ (nth sorted (dec half)) (nth sorted half)) 2.0)))))

(defn- format-duration-seconds
  "Format duration in seconds to a human-readable string."
  [seconds]
  (when (and seconds (number? seconds))
    (let [seconds-long (long seconds)
          days (quot seconds-long 86400)
          hours (quot (rem seconds-long 86400) 3600)
          minutes (quot (rem seconds-long 3600) 60)
          remaining-seconds (rem seconds-long 60)]
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

(defn- extract-metric-values
  "Extract metric values from raw metrics data."
  [metrics]
  (let [merge-times (remove nil? (map :metrics/pr_merge_time_seconds metrics))
        review-times (remove nil? (map :metrics/code_review_turnaround_seconds metrics))]
    {:merge-times merge-times
     :review-times review-times
     :total-prs (count metrics)
     :merged-prs (count merge-times)
     :reviewed-prs (count review-times)}))

(defn- ensure-numeric-value
  "Ensure value is a number or nil."
  [value]
  (when value
    (if (number? value)
      value
      (try (double value)
           (catch Exception _ nil)))))

(defn- calculate-metric-medians
  "Calculate median values for metrics."
  [metric-values]
  (let [{:keys [merge-times review-times]} metric-values
        median-merge-time (median merge-times)
        median-review-time (median review-times)]
    {:median-merge-time-safe (ensure-numeric-value median-merge-time)
     :median-review-time-safe (ensure-numeric-value median-review-time)}))

(defn- format-period-metrics
  "Format period metrics for response."
  [{:keys [period-name start-date end-date metric-values median-values]}]
  (let [{:keys [total-prs merged-prs reviewed-prs]} metric-values
        {:keys [median-merge-time-safe median-review-time-safe]} median-values]
    {:period period-name
     :start_date (str start-date)
     :end_date (str end-date)
     :total_prs total-prs
     :merged_prs merged-prs
     :reviewed_prs reviewed-prs
     :median_merge_time_seconds median-merge-time-safe
     :median_merge_time (format-duration-seconds median-merge-time-safe)
     :median_review_turnaround_seconds median-review-time-safe
     :median_review_turnaround (format-duration-seconds median-review-time-safe)}))

(defn- aggregate-metrics-for-period
  "Aggregate metrics for a specific time period.
   Returns a map with median values for PR merge time and code review turnaround time."
  [repository-id start-date end-date period-name]
  (let [metrics (get-metrics-in-date-range repository-id start-date end-date)
        metric-values (extract-metric-values metrics)
        median-values (calculate-metric-medians metric-values)]
    (format-period-metrics {:period-name period-name
                           :start-date start-date
                           :end-date end-date
                           :metric-values metric-values
                           :median-values median-values})))

(defn- create-period-name
  "Create a descriptive name for a time period."
  [period-type periods-ago]
  (cond
    (zero? periods-ago) (if (= period-type :week) "This week" "Today")
    (= period-type :day) (str periods-ago " day" (when (> periods-ago 1) "s") " ago")
    :else (str periods-ago " week" (when (> periods-ago 1) "s") " ago")))

(defn- calculate-period-dates
  "Calculate start and end dates for a period."
  [period-type periods-ago start-point]
  (let [period-fn (if (= period-type :day) jt/days jt/weeks)
        start-date (jt/minus start-point (period-fn periods-ago))
        end-date (jt/plus start-date (period-fn 1))]
    [start-date end-date]))

(defn- generate-period-ranges
  "Generate date ranges for a series of periods (days or weeks).
   Returns a vector of [start-date end-date period-name] tuples."
  [period-type num-periods]
  (let [today (jt/truncate-to (jt/zoned-date-time) :days)
        start-point (if (= period-type :week)
                      (jt/adjust today :previous-or-same-day-of-week :monday)
                      today)]
    (for [periods-ago (range num-periods)]
      (let [[start-date end-date] (calculate-period-dates period-type periods-ago start-point)
            period-name (create-period-name period-type periods-ago)]
        [start-date end-date period-name]))))

(defn- process-metrics-for-periods
  "Process metrics for a collection of time periods.
   Returns a vector of aggregated metrics for each period."
  [repository-id date-ranges]
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

(defn- get-period-metrics
  "Generic function to get metrics for a specific period type."
  [repository-id period-type num-periods]
  (try
    (log/info (str "Starting get-" (name period-type) "-metrics for repository-id:") repository-id)
    (let [date-ranges (generate-period-ranges period-type num-periods)]
      (log/info (str "Aggregating metrics for each " (name period-type)))
      (process-metrics-for-periods repository-id date-ranges))
    (catch Exception e
      (log/error (str "Error in get-" (name period-type) "-metrics:") (.getMessage e) e)
      (throw (IllegalStateException. 
               (str "Failed to get " (name period-type) " metrics: " (.getMessage e)) e)))))

(defn get-daily-metrics
  "Get daily metrics for the last 5 days.
   Returns a collection of daily metrics, each with median values."
  [repository-id]
  (get-period-metrics repository-id :day 5))

(defn get-weekly-metrics
  "Get weekly metrics for the last 5 weeks.
   Returns a collection of weekly metrics, each with median values."
  [repository-id]
  (get-period-metrics repository-id :week 5))
