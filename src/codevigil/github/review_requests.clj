(ns codevigil.github.review-requests
  (:require [clj-github.client-utils :refer [fetch-body!]]
            [codevigil.github.client :refer [client]]))

(defn get-requested-reviewers!
  "Retrieves all requested reviewers for the given pull request.
   Usage: (get-requested-reviewers! \"octocat\" \"Hello-World\" 1347)"
  [org repo pull-number]
  (fetch-body! client {:method :get
                      :path   (format "/repos/%s/%s/pulls/%s/requested_reviewers" org repo pull-number)}))
