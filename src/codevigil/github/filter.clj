(ns codevigil.github.filter
  (:import [java.time Instant Duration]))

(defn within-last-4-weeks?
  "Checks if the given created_at timestamp is within the last 4 weeks."
  [created-at-str]
  (let [created-at (Instant/parse created-at-str)
        four-weeks-ago (.minus (Instant/now) (Duration/ofDays 28))]
    (.isAfter created-at four-weeks-ago)))

(defn filter-recent-pulls
  "Filters pull requests to keep only those created within the last 4 weeks."
  [pulls]
  (filter #(within-last-4-weeks? (:created_at %)) pulls))
