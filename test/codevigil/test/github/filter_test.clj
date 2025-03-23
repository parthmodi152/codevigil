(ns codevigil.test.github.filter-test
  (:require [clojure.test :refer :all]
            [codevigil.github.filter :as filter])
  (:import [java.time Instant Duration]))

(deftest within-last-4-weeks-test
  (testing "Dates within last 4 weeks"
    (let [now (Instant/now)
          one-week-ago (.toString (.minus now (Duration/ofDays 7)))
          three-weeks-ago (.toString (.minus now (Duration/ofDays 21)))]
      (is (true? (filter/within-last-4-weeks? one-week-ago)))
      (is (true? (filter/within-last-4-weeks? three-weeks-ago)))))
  
  (testing "Dates older than 4 weeks"
    (let [now (Instant/now)
          five-weeks-ago (.toString (.minus now (Duration/ofDays 35)))
          six-months-ago (.toString (.minus now (Duration/ofDays 180)))]
      (is (false? (filter/within-last-4-weeks? five-weeks-ago)))
      (is (false? (filter/within-last-4-weeks? six-months-ago))))))

(deftest filter-recent-pulls-test
  (testing "Filtering pull requests"
    (let [now (Instant/now)
          one-week-ago (.toString (.minus now (Duration/ofDays 7)))
          five-weeks-ago (.toString (.minus now (Duration/ofDays 35)))
          pulls [{:number 1 :created_at one-week-ago}
                 {:number 2 :created_at five-weeks-ago}
                 {:number 3 :created_at one-week-ago}]]
      (is (= 2 (count (filter/filter-recent-pulls pulls))))
      (is (= [1 3] (map :number (filter/filter-recent-pulls pulls)))))))
