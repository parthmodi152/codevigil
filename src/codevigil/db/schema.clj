(ns codevigil.db.schema
  (:require [next.jdbc :as jdbc]
            [next.jdbc.sql :as sql]))

(def db-spec
  {:dbtype "postgresql"
   :dbname (or (System/getenv "DB_NAME") "codevigil")
   :host (or (System/getenv "DB_HOST") "localhost")
   :port (or (System/getenv "DB_PORT") 5432)
   :user (or (System/getenv "DB_USER") "postgres")
   :password (System/getenv "DB_PASSWORD")})

(def repositories-table
  "CREATE TABLE IF NOT EXISTS repositories (
    id SERIAL PRIMARY KEY,
    owner VARCHAR(255) NOT NULL,
    repo_name VARCHAR(255) NOT NULL,
    repo_url VARCHAR(1024) NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    last_checked_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE (owner, repo_name)
  )")

(def pull-requests-table
  "CREATE TABLE IF NOT EXISTS pull_requests (
    id SERIAL PRIMARY KEY,
    repository_id INTEGER REFERENCES repositories(id),
    github_id BIGINT NOT NULL,
    number INTEGER NOT NULL,
    state VARCHAR(20) NOT NULL,
    title VARCHAR(1024),
    html_url VARCHAR(1024),
    created_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ,
    closed_at TIMESTAMPTZ,
    merged_at TIMESTAMPTZ,
    user_login VARCHAR(255),
    UNIQUE (repository_id, number)
  )")

(def reviews-table
  "CREATE TABLE IF NOT EXISTS reviews (
    id SERIAL PRIMARY KEY,
    pull_request_id INTEGER REFERENCES pull_requests(id) ON DELETE CASCADE,
    github_id BIGINT NOT NULL,
    user_login VARCHAR(255),
    state VARCHAR(50),
    submitted_at TIMESTAMPTZ,
    commit_id VARCHAR(255),
    UNIQUE (pull_request_id, github_id)
  )")

(def review-comments-table
  "CREATE TABLE IF NOT EXISTS review_comments (
    id SERIAL PRIMARY KEY,
    pull_request_id INTEGER REFERENCES pull_requests(id) ON DELETE CASCADE,
    github_id BIGINT NOT NULL,
    user_login VARCHAR(255),
    created_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ,
    commit_id VARCHAR(255),
    path VARCHAR(1024),
    position INTEGER,
    body TEXT,
    UNIQUE (pull_request_id, github_id)
  )")

(def requested-reviewers-table
  "CREATE TABLE IF NOT EXISTS requested_reviewers (
    id SERIAL PRIMARY KEY,
    pull_request_id INTEGER REFERENCES pull_requests(id) ON DELETE CASCADE,
    user_login VARCHAR(255) NOT NULL,
    requested_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE (pull_request_id, user_login)
  )")

(def metrics-table
  "CREATE TABLE IF NOT EXISTS metrics (
    id SERIAL PRIMARY KEY,
    repository_id INTEGER REFERENCES repositories(id),
    pull_request_id INTEGER REFERENCES pull_requests(id) ON DELETE CASCADE,
    pr_number INTEGER NOT NULL,
    pr_merge_time_seconds BIGINT,
    code_review_turnaround_seconds BIGINT,
    first_review_requested_at TIMESTAMPTZ,
    first_approval_at TIMESTAMPTZ,
    calculated_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE (pull_request_id)
  )")

(def indexes
  ["CREATE INDEX IF NOT EXISTS idx_pull_requests_repository_id ON pull_requests(repository_id)"
   "CREATE INDEX IF NOT EXISTS idx_pull_requests_state ON pull_requests(state)"
   "CREATE INDEX IF NOT EXISTS idx_pull_requests_created_at ON pull_requests(created_at)"
   "CREATE INDEX IF NOT EXISTS idx_pull_requests_merged_at ON pull_requests(merged_at)"
   "CREATE INDEX IF NOT EXISTS idx_pull_requests_closed_at ON pull_requests(closed_at)"
   "CREATE INDEX IF NOT EXISTS idx_reviews_pull_request_id ON reviews(pull_request_id)"
   "CREATE INDEX IF NOT EXISTS idx_reviews_state ON reviews(state)"
   "CREATE INDEX IF NOT EXISTS idx_reviews_submitted_at ON reviews(submitted_at)"
   "CREATE INDEX IF NOT EXISTS idx_review_comments_pull_request_id ON review_comments(pull_request_id)"
   "CREATE INDEX IF NOT EXISTS idx_requested_reviewers_pull_request_id ON requested_reviewers(pull_request_id)"
   "CREATE INDEX IF NOT EXISTS idx_metrics_repository_id ON metrics(repository_id)"
   "CREATE INDEX IF NOT EXISTS idx_metrics_calculated_at ON metrics(calculated_at)"])

(defn create-tables!
  "Creates the database tables if they don't exist."
  [db]
  (jdbc/execute! db [repositories-table])
  (jdbc/execute! db [pull-requests-table])
  (jdbc/execute! db [reviews-table])
  (jdbc/execute! db [review-comments-table])
  (jdbc/execute! db [requested-reviewers-table])
  (jdbc/execute! db [metrics-table])
  
  ;; Create indexes
  (doseq [index indexes]
    (jdbc/execute! db [index])))
