(ns codevigil.test.github.async-test
  (:require [clojure.test :refer :all]
            [clojure.core.async :as async]
            [codevigil.github.async :as gh-async]
            [codevigil.github.pull :as gh-pull]
            [codevigil.github.filter :as gh-filter]
            [codevigil.github.enrichment :as gh-enrichment]
            [codevigil.db.core :as db]))

(deftest process-repository-async-test
  (testing "Asynchronous repository processing"
    (with-redefs [gh-pull/fetch-pull-requests (constantly [{:number 1} {:number 2}])
                  gh-filter/filter-recent-pulls identity
                  gh-enrichment/enrich-pull-requests (constantly [{:pull-request {:number 1}} 
                                                                 {:pull-request {:number 2}}])
                  db/save-pull-requests! (constantly nil)]
      
      ;; Test that the function returns immediately
      (let [start-time (System/currentTimeMillis)
            _ (gh-async/process-repository-async! "test-owner" "test-repo" "https://github.com/test-owner/test-repo" 1)
            end-time (System/currentTimeMillis)
            elapsed (- end-time start-time)]
        
        ;; Should return almost immediately (less than 100ms)
        (is (< elapsed 100) "Function should return immediately")))))
