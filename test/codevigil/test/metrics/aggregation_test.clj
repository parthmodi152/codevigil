(ns codevigil.test.metrics.aggregation-test
  (:require [clojure.test :refer :all]
            [codevigil.metrics.aggregation :as aggregation]
            [java-time :as jt]
            [next.jdbc :as next-jdbc]
            [codevigil.db.core :as db]))

(deftest median-test
  (testing "Median calculation"
    (let [median-fn #'codevigil.metrics.aggregation/median]
      ;; Empty collection
      (is (nil? (median-fn [])))
      
      ;; Odd number of elements
      (is (= 3 (median-fn [1 3 5])))
      (is (= 3 (median-fn [5 1 3])))
      
      ;; Even number of elements
      (is (= 3.0 (median-fn [1 2 4 5])))
      (is (= 3.0 (median-fn [5 1 4 2]))))))

(deftest format-duration-seconds-test
  (testing "Duration formatting"
    (let [format-fn #'codevigil.metrics.aggregation/format-duration-seconds]
      ;; Nil input
      (is (nil? (format-fn nil)))
      
      ;; Seconds only
      (is (= "45 seconds" (format-fn 45)))
      
      ;; Minutes and seconds
      (is (= "2 minutes, 30 seconds" (format-fn 150)))
      
      ;; Hours and minutes
      (is (= "2 hours, 30 minutes" (format-fn 9000)))
      
      ;; Days and hours
      (is (= "2 days, 5 hours" (format-fn 187200))))))

(deftest aggregate-metrics-for-period-test
  (testing "Metrics aggregation for a period"
    (with-redefs [next-jdbc/execute! (fn [_ _] 
                                      [{:metrics/pr_merge_time_seconds 3600
                                        :metrics/code_review_turnaround_seconds 7200}
                                       {:metrics/pr_merge_time_seconds 7200
                                        :metrics/code_review_turnaround_seconds 10800}
                                       {:metrics/pr_merge_time_seconds 5400
                                        :metrics/code_review_turnaround_seconds nil}])]
      
      (let [aggregate-fn #'codevigil.metrics.aggregation/aggregate-metrics-for-period
            now (jt/instant)
            tomorrow (jt/plus now (jt/days 1))
            result (aggregate-fn 1 now tomorrow "Test Period")]
        
        ;; Check basic structure
        (is (= "Test Period" (:period result)))
        (is (= (str now) (:start_date result)))
        (is (= (str tomorrow) (:end_date result)))
        
        ;; Check counts
        (is (= 3 (:total_prs result)))
        (is (= 3 (:merged_prs result)))
        (is (= 2 (:reviewed_prs result)))
        
        ;; Check median calculations
        (is (= 5400 (:median_merge_time_seconds result)))
        (is (= 9000 (:median_review_turnaround_seconds result)))
        
        ;; Check formatted durations
        (is (= "1 hours, 30 minutes" (:median_merge_time result)))
        (is (= "2 hours, 30 minutes" (:median_review_turnaround result)))))))

(deftest get-daily-metrics-test
  (testing "Daily metrics aggregation"
    (with-redefs [codevigil.metrics.aggregation/aggregate-metrics-for-period 
                  (fn [_ start end period]
                    {:period period
                     :start_date (str start)
                     :end_date (str end)
                     :total_prs 5
                     :merged_prs 3
                     :reviewed_prs 2
                     :median_merge_time_seconds 3600
                     :median_merge_time "1 hours, 0 minutes"
                     :median_review_turnaround_seconds 7200
                     :median_review_turnaround "2 hours, 0 minutes"})]
      
      (let [result (aggregation/get-daily-metrics 1)]
        ;; Should return 5 days of metrics
        (is (= 5 (count result)))
        
        ;; Check first day (today)
        (is (= "Today" (:period (first result))))
        
        ;; Check last day
        (is (= "4 days ago" (:period (last result))))))))

(deftest get-weekly-metrics-test
  (testing "Weekly metrics aggregation"
    (with-redefs [codevigil.metrics.aggregation/aggregate-metrics-for-period 
                  (fn [_ start end period]
                    {:period period
                     :start_date (str start)
                     :end_date (str end)
                     :total_prs 10
                     :merged_prs 8
                     :reviewed_prs 6
                     :median_merge_time_seconds 86400
                     :median_merge_time "1 days, 0 hours"
                     :median_review_turnaround_seconds 172800
                     :median_review_turnaround "2 days, 0 hours"})]
      
      (let [result (aggregation/get-weekly-metrics 1)]
        ;; Should return 5 weeks of metrics
        (is (= 5 (count result)))
        
        ;; Check first week (current)
        (is (= "This week" (:period (first result))))
        
        ;; Check last week
        (is (= "4 weeks ago" (:period (last result))))))))
