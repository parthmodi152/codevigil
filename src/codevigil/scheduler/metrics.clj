(ns codevigil.scheduler.metrics
  (:require [codevigil.metrics.calculator :as calculator])
  (:import [java.util.concurrent Executors TimeUnit]))

;; Executor service for scheduled metrics calculation
(def metrics-executor (Executors/newScheduledThreadPool 1))

(defn schedule-metrics-calculation!
  "Schedules a daily task to calculate metrics for all repositories.
   The task runs once per day at the specified hour (default: 3 AM, after PR updates)."
  [& {:keys [hour] :or {hour 3}}]
  (let [now (java.time.LocalDateTime/now)
        target-time (java.time.LocalDateTime/of 
                     (.getYear now)
                     (.getMonth now)
                     (.getDayOfMonth now) hour 0 0)
        target-time (if (.isAfter now target-time)
                      (.plusDays target-time 1)
                      target-time)
        initial-delay (java.time.Duration/between now target-time)
        day-in-seconds (* 24 60 60)]
    
    (.scheduleAtFixedRate metrics-executor
                         (fn []
                           (try
                             (println "Starting daily metrics calculation task")
                             (calculator/calculate-metrics-for-all-repositories)
                             (println "Completed metrics calculation")
                             (catch Exception e
                               (println "Error in metrics calculation task: " (.getMessage e)))))
                         (.getSeconds initial-delay)
                         day-in-seconds
                         TimeUnit/SECONDS))
  (println (str "Daily metrics calculation scheduled to run at " hour ":00 AM")))

(defn start-metrics-scheduler!
  "Starts the metrics calculation scheduler."
  []
  (schedule-metrics-calculation!)
  (println "Metrics calculation scheduler started"))

(defn stop-metrics-scheduler!
  "Stops the metrics calculation scheduler."
  []
  (.shutdown metrics-executor)
  (println "Metrics calculation scheduler stopped"))
