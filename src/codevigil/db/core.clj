(ns codevigil.db.core
  (:require [next.jdbc :as jdbc]
            [next.jdbc.sql :as sql]
            [cheshire.core :as json]
            [codevigil.db.schema :refer [db-spec create-tables!]]))

;; Initialize connection pool
(def datasource
  (jdbc/get-datasource db-spec))

(defn init-db!
  "Initializes the database by creating tables if they don't exist."
  []
  (create-tables! datasource))

(defn parse-github-timestamp
  "Parses a GitHub timestamp string into a java.sql.Timestamp object.
   Returns nil if the input is nil or empty.
   Handles both ISO-8601 format and other common date-time formats."
  [timestamp-str]
  (when (and timestamp-str (not (empty? timestamp-str)))
    (try
      ;; First try ISO-8601 format (what GitHub typically uses)
      (java.sql.Timestamp/from (java.time.Instant/parse timestamp-str))
      (catch Exception e
        (try
          ;; Try parsing as LocalDateTime (for format like "2025-03-05 19:23:21.0")
          (let [formatter (java.time.format.DateTimeFormatter/ofPattern "yyyy-MM-dd HH:mm:ss[.S]")
                local-dt (java.time.LocalDateTime/parse timestamp-str formatter)]
            ;; Convert LocalDateTime to Timestamp (assuming UTC/system default timezone)
            (java.sql.Timestamp/valueOf local-dt))
          (catch Exception e2
            ;; Log the error and return nil for unparseable timestamps
            (println (str "Failed to parse timestamp: " timestamp-str " - " (.getMessage e2)))
            nil))))))

(defn upsert-repository!
  "Inserts or updates a repository record and returns the repository id."
  [{:keys [owner repo_name repo_url]}]
  (let [result (jdbc/execute-one! datasource
                                 ["INSERT INTO repositories (owner, repo_name, repo_url)
                                   VALUES (?, ?, ?)
                                   ON CONFLICT (owner, repo_name) 
                                   DO UPDATE SET repo_url = EXCLUDED.repo_url
                                   RETURNING id"
                                  owner repo_name repo_url])]
    (:repositories/id result)))

(defn update-repository-last-checked!
  "Updates the last_checked_at timestamp for a repository."
  [repository-id]
  (jdbc/execute-one! datasource
                    ["UPDATE repositories 
                      SET last_checked_at = NOW() 
                      WHERE id = ?"
                     repository-id]))

(defn save-pull-request!
  "Saves a pull request to the database and returns the pull request id."
  [repository-id pull-request]
  (let [pr pull-request
        result (jdbc/execute-one! datasource
                                 ["INSERT INTO pull_requests 
                                   (repository_id, github_id, number, state, title, html_url, 
                                    created_at, updated_at, closed_at, merged_at, user_login)
                                   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                                   ON CONFLICT (repository_id, number)
                                   DO UPDATE SET 
                                     state = EXCLUDED.state,
                                     title = EXCLUDED.title,
                                     html_url = EXCLUDED.html_url,
                                     updated_at = EXCLUDED.updated_at,
                                     closed_at = EXCLUDED.closed_at,
                                     merged_at = EXCLUDED.merged_at,
                                     user_login = EXCLUDED.user_login
                                   RETURNING id"
                                  repository-id
                                  (:id pr)
                                  (:number pr)
                                  (:state pr)
                                  (:title pr)
                                  (:html_url pr)
                                  (parse-github-timestamp (:created_at pr))
                                  (parse-github-timestamp (:updated_at pr))
                                  (parse-github-timestamp (:closed_at pr))
                                  (parse-github-timestamp (:merged_at pr))
                                  (get-in pr [:user :login])])]
    (:pull_requests/id result)))

(defn save-reviews!
  "Saves reviews for a pull request."
  [pull-request-id reviews]
  (doseq [review reviews]
    (jdbc/execute-one! datasource
                      ["INSERT INTO reviews 
                        (pull_request_id, github_id, user_login, state, submitted_at, commit_id)
                        VALUES (?, ?, ?, ?, ?, ?)
                        ON CONFLICT (pull_request_id, github_id)
                        DO UPDATE SET 
                          user_login = EXCLUDED.user_login,
                          state = EXCLUDED.state,
                          submitted_at = EXCLUDED.submitted_at,
                          commit_id = EXCLUDED.commit_id"
                       pull-request-id
                       (:id review)
                       (get-in review [:user :login])
                       (:state review)
                       (parse-github-timestamp (:submitted_at review))
                       (:commit_id review)])))

(defn save-review-comments!
  "Saves review comments for a pull request."
  [pull-request-id comments]
  (doseq [comment comments]
    (jdbc/execute-one! datasource
                      ["INSERT INTO review_comments 
                        (pull_request_id, github_id, user_login, created_at, updated_at, 
                         commit_id, path, position, body)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT (pull_request_id, github_id)
                        DO UPDATE SET 
                          user_login = EXCLUDED.user_login,
                          updated_at = EXCLUDED.updated_at,
                          body = EXCLUDED.body"
                       pull-request-id
                       (:id comment)
                       (get-in comment [:user :login])
                       (parse-github-timestamp (:created_at comment))
                       (parse-github-timestamp (:updated_at comment))
                       (:commit_id comment)
                       (:path comment)
                       (:position comment)
                       (:body comment)])))

(defn save-requested-reviewers!
  "Saves requested reviewers for a pull request."
  [pull-request-id requested-reviewers]
  (let [users (get requested-reviewers :users [])]
    (doseq [user users]
      (jdbc/execute-one! datasource
                        ["INSERT INTO requested_reviewers 
                          (pull_request_id, user_login)
                          VALUES (?, ?)
                          ON CONFLICT (pull_request_id, user_login)
                          DO NOTHING"
                         pull-request-id
                         (:login user)]))))

(defn save-enriched-pull-request!
  "Saves a pull request and its associated enriched data."
  [repository-id {:keys [pull-request reviews review-comments requested-reviewers]}]
  (let [pr-id (save-pull-request! repository-id pull-request)]
    (save-reviews! pr-id reviews)
    (save-review-comments! pr-id review-comments)
    (save-requested-reviewers! pr-id requested-reviewers)
    pr-id))

(defn save-enriched-pull-requests!
  "Saves multiple enriched pull requests for a repository."
  [repository-id enriched-pulls]
  (doseq [pull enriched-pulls]
    (save-enriched-pull-request! repository-id pull)))

(defn get-repository-by-owner-repo
  "Gets a repository by owner and repo name."
  [owner repo-name]
  (jdbc/execute-one! datasource
                    ["SELECT * FROM repositories WHERE owner = ? AND repo_name = ?"
                     owner repo-name]))

(defn get-all-repositories
  "Gets all repositories."
  []
  (jdbc/execute! datasource
                ["SELECT * FROM repositories ORDER BY owner, repo_name"]))

(defn get-pull-requests-for-repository
  "Gets all pull requests for a repository."
  [repository-id]
  (jdbc/execute! datasource
                ["SELECT * FROM pull_requests WHERE repository_id = ? ORDER BY number"
                 repository-id]))

(defn get-open-pull-requests-for-repository
  "Gets all open pull requests for a repository."
  [repository-id]
  (jdbc/execute! datasource
                ["SELECT * FROM pull_requests WHERE repository_id = ? AND state = 'open' ORDER BY number"
                 repository-id]))

(defn get-reviews-for-pull-request
  "Gets all reviews for a pull request."
  [pull-request-id]
  (jdbc/execute! datasource
                ["SELECT * FROM reviews WHERE pull_request_id = ? ORDER BY submitted_at"
                 pull-request-id]))

(defn get-review-comments-for-pull-request
  "Gets all review comments for a pull request."
  [pull-request-id]
  (jdbc/execute! datasource
                ["SELECT * FROM review_comments WHERE pull_request_id = ? ORDER BY created_at"
                 pull-request-id]))

(defn get-requested-reviewers-for-pull-request
  "Gets all requested reviewers for a pull request."
  [pull-request-id]
  (jdbc/execute! datasource
                ["SELECT * FROM requested_reviewers WHERE pull_request_id = ?"
                 pull-request-id]))

(defn get-enriched-pull-request
  "Gets a pull request with all its enriched data."
  [pull-request-id]
  (let [pull-request (jdbc/execute-one! datasource
                                       ["SELECT * FROM pull_requests WHERE id = ?"
                                        pull-request-id])
        reviews (get-reviews-for-pull-request pull-request-id)
        review-comments (get-review-comments-for-pull-request pull-request-id)
        requested-reviewers (get-requested-reviewers-for-pull-request pull-request-id)]
    (when pull-request
      {:pull-request pull-request
       :reviews reviews
       :review-comments review-comments
       :requested-reviewers requested-reviewers})))

(defn save-metrics!
  "Saves metrics for a pull request."
  [repository-id pull-request-id pr-number metrics]
  (let [{:keys [pr-merge-time-seconds code-review-turnaround-seconds
                first-review-requested-at first-approval-at]} metrics]
    (jdbc/execute-one! datasource
                      ["INSERT INTO metrics 
                        (repository_id, pull_request_id, pr_number, 
                         pr_merge_time_seconds, code_review_turnaround_seconds,
                         first_review_requested_at, first_approval_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT (pull_request_id)
                        DO UPDATE SET 
                          pr_merge_time_seconds = EXCLUDED.pr_merge_time_seconds,
                          code_review_turnaround_seconds = EXCLUDED.code_review_turnaround_seconds,
                          first_review_requested_at = EXCLUDED.first_review_requested_at,
                          first_approval_at = EXCLUDED.first_approval_at,
                          calculated_at = NOW()"
                       repository-id
                       pull-request-id
                       pr-number
                       pr-merge-time-seconds
                       code-review-turnaround-seconds
                       (parse-github-timestamp first-review-requested-at)
                       (parse-github-timestamp first-approval-at)])))

(defn get-metrics-for-repository
  "Gets metrics for all pull requests in a repository."
  [repository-id]
  (jdbc/execute! datasource
                ["SELECT * FROM metrics WHERE repository_id = ? ORDER BY pr_number"
                 repository-id]))

(defn get-metrics-for-pull-request
  "Gets metrics for a specific pull request."
  [pull-request-id]
  (jdbc/execute-one! datasource
                    ["SELECT * FROM metrics WHERE pull_request_id = ?"
                     pull-request-id]))
