(ns codevigil.github.pull-reviews
  (:require [clj-github.client-utils :refer [fetch-body!]]
            [codevigil.github.client :refer [client]]))

(defn list-reviews!
  "Lists all reviews for the given pull request.
   Usage: (list-reviews! \"octocat\" \"Hello-World\" 1347)"
  [org repo pull-number]
  (fetch-body! client {:method :get
                      :path   (format "/repos/%s/%s/pulls/%s/reviews" org repo pull-number)}))
