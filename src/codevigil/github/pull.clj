(ns codevigil.github.pull
  (:require [clj-github.pull :as pull]
            [codevigil.github.client :refer [client]]))

(defn fetch-pull-requests
  "Fetch all pull requests (state=all, max 100) for a repository."
  [owner repo]
  (pull/get-pulls! client owner repo {"state" "all" "per_page" "100"}))

(defn get-pull
  "Fetch a specific pull request by number."
  [owner repo pull-number]
  (pull/get-pull! client owner repo pull-number))
