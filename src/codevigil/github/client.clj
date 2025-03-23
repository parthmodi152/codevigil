(ns codevigil.github.client
  (:require [clj-github.httpkit-client :as github-client]))

(defn create-client
  "Creates a GitHub client using the personal access token from environment variables."
  []
  (github-client/new-client {:token (System/getenv "GITHUB_TOKEN")}))

(def client (create-client))
