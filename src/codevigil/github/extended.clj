(ns codevigil.github.extended
  (:require [codevigil.github.pull :as pull]
            [codevigil.github.pull-comments :refer [list-review-comments!]]
            [codevigil.github.review-requests :refer [get-requested-reviewers!]]
            [codevigil.github.pull-reviews :refer [list-reviews!]]))

(defn enrich-pull-request
  "Enriches a pull request with reviews, review comments, and requested reviewers.
   Returns a map containing the pull request and its associated data."
  [org repo pull-number]
  {:pull-request        (pull/get-pull org repo pull-number)
   :review-comments     (list-review-comments! org repo pull-number)
   :requested-reviewers (get-requested-reviewers! org repo pull-number)
   :reviews             (list-reviews! org repo pull-number)})
