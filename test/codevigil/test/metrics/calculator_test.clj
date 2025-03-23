(ns codevigil.test.metrics.calculator-test
  (:require [clojure.test :refer :all]
            [codevigil.metrics.calculator :as calculator]
            [java-time :as jt]))

(deftest calculate-pr-merge-time-test
  (testing "PR merge time calculation"
    (let [now (jt/instant)
          one-day-ago (jt/minus now (jt/days 1))
          two-days-ago (jt/minus now (jt/days 2))
          
          ;; Test case 1: Merged PR
          merged-pr {:pull_requests/created_at two-days-ago
                     :pull_requests/merged_at one-day-ago}
          
          ;; Test case 2: Unmerged PR
          unmerged-pr {:pull_requests/created_at two-days-ago
                       :pull_requests/merged_at nil}]
      
      ;; Merged PR should have a merge time of 1 day in seconds
      (is (= 86400 (calculator/calculate-pr-merge-time merged-pr)))
      
      ;; Unmerged PR should have nil merge time
      (is (nil? (calculator/calculate-pr-merge-time unmerged-pr))))))

(deftest find-first-approval-test
  (testing "Finding first approval review"
    (let [now (jt/instant)
          one-hour-ago (jt/minus now (jt/hours 1))
          two-hours-ago (jt/minus now (jt/hours 2))
          
          ;; Test case 1: Reviews with approval
          reviews-with-approval [{:reviews/state "COMMENTED"
                                  :reviews/submitted_at two-hours-ago}
                                 {:reviews/state "APPROVED"
                                  :reviews/submitted_at one-hour-ago}]
          
          ;; Test case 2: Reviews without approval
          reviews-without-approval [{:reviews/state "COMMENTED"
                                     :reviews/submitted_at two-hours-ago}
                                    {:reviews/state "CHANGES_REQUESTED"
                                     :reviews/submitted_at one-hour-ago}]
          
          ;; Test case 3: Multiple approvals (should return earliest)
          multiple-approvals [{:reviews/state "APPROVED"
                               :reviews/submitted_at two-hours-ago}
                              {:reviews/state "APPROVED"
                               :reviews/submitted_at one-hour-ago}]]
      
      ;; Should find the approval review
      (is (= "APPROVED" (:reviews/state (calculator/find-first-approval reviews-with-approval))))
      (is (= one-hour-ago (:reviews/submitted_at (calculator/find-first-approval reviews-with-approval))))
      
      ;; Should return nil when no approval
      (is (nil? (calculator/find-first-approval reviews-without-approval)))
      
      ;; Should return earliest approval
      (is (= two-hours-ago (:reviews/submitted_at (calculator/find-first-approval multiple-approvals)))))))

(deftest calculate-code-review-turnaround-test
  (testing "Code review turnaround calculation"
    (let [now (jt/instant)
          one-day-ago (jt/minus now (jt/days 1))
          two-days-ago (jt/minus now (jt/days 2))
          three-days-ago (jt/minus now (jt/days 3))
          
          ;; Test case 1: PR with approval
          pr-with-approval {:pull_requests/created_at three-days-ago
                            :pull_requests/merged_at one-day-ago
                            :pull_requests/closed_at one-day-ago}
          reviews-with-approval [{:reviews/state "APPROVED"
                                  :reviews/submitted_at two-days-ago}]
          
          ;; Test case 2: PR without approval but merged
          pr-without-approval {:pull_requests/created_at three-days-ago
                               :pull_requests/merged_at two-days-ago
                               :pull_requests/closed_at two-days-ago}
          reviews-without-approval []
          
          ;; Test case 3: PR without approval and not merged but closed
          pr-only-closed {:pull_requests/created_at three-days-ago
                          :pull_requests/merged_at nil
                          :pull_requests/closed_at one-day-ago}
          
          ;; Test case 4: Open PR without end time
          open-pr {:pull_requests/created_at three-days-ago
                   :pull_requests/merged_at nil
                   :pull_requests/closed_at nil}]
      
      ;; PR with approval should use approval time
      (let [result (calculator/calculate-code-review-turnaround 
                    pr-with-approval reviews-with-approval [])]
        (is (= 86400 (:turnaround-seconds result))) ; 1 day in seconds
        (is (= (str three-days-ago) (:first-review-requested-at result)))
        (is (= (str two-days-ago) (:first-approval-at result))))
      
      ;; PR without approval should use merged_at
      (let [result (calculator/calculate-code-review-turnaround 
                    pr-without-approval reviews-without-approval [])]
        (is (= 86400 (:turnaround-seconds result))) ; 1 day in seconds
        (is (= (str three-days-ago) (:first-review-requested-at result)))
        (is (nil? (:first-approval-at result))))
      
      ;; PR only closed should use closed_at
      (let [result (calculator/calculate-code-review-turnaround 
                    pr-only-closed reviews-without-approval [])]
        (is (= 172800 (:turnaround-seconds result))) ; 2 days in seconds
        (is (= (str three-days-ago) (:first-review-requested-at result)))
        (is (nil? (:first-approval-at result))))
      
      ;; Open PR should return nil (no end time)
      (is (nil? (calculator/calculate-code-review-turnaround 
                 open-pr reviews-without-approval [])))))))
