(ns codevigil.github.pull-comments
  (:require [clj-github.client-utils :refer [fetch-body!]]
            [codevigil.github.client :refer [client]]))

(defn list-review-comments!
  "Lists all review comments for the given pull request.
   Usage: (list-review-comments! \"octocat\" \"Hello-World\" 1347)"
  [org repo pull-number]
  (fetch-body! client {:method :get
                      :path   (format "/repos/%s/%s/pulls/%s/comments" org repo pull-number)}))
