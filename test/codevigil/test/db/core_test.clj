(ns codevigil.test.db.core-test
  (:require [clojure.test :refer :all]
            [next.jdbc :as jdbc]
            [next.jdbc.sql :as sql]
            [cheshire.core :as json]
            [codevigil.db.core :as db]
            [codevigil.db.schema :as schema]))

(def test-db-spec
  (assoc schema/db-spec :dbname "codevigil_test"))

(defn with-test-db [f]
  (let [ds (jdbc/get-datasource test-db-spec)]
    ;; Create test tables
    (schema/create-tables! ds)
    ;; Run the test
    (f)
    ;; Clean up (optional)
    #_(jdbc/execute! ds ["DROP TABLE IF EXISTS pull_requests"])
    #_(jdbc/execute! ds ["DROP TABLE IF EXISTS repositories"])))

(deftest ^:integration repository-operations
  (testing "Repository insertion and retrieval"
    (with-redefs [db/datasource (jdbc/get-datasource test-db-spec)]
      (let [repo {:owner "test-owner" 
                  :repo_name "test-repo" 
                  :repo_url "https://github.com/test-owner/test-repo"}
            repo-id (db/upsert-repository! repo)
            retrieved (db/get-repository-by-owner-repo "test-owner" "test-repo")]
        
        (is (pos-int? repo-id))
        (is (= "test-owner" (:repositories/owner retrieved)))
        (is (= "test-repo" (:repositories/repo_name retrieved)))
        (is (= "https://github.com/test-owner/test-repo" (:repositories/repo_url retrieved)))))))

(deftest ^:integration pull-request-operations
  (testing "Pull request insertion and retrieval"
    (with-redefs [db/datasource (jdbc/get-datasource test-db-spec)]
      (let [repo {:owner "test-owner" 
                  :repo_name "test-repo" 
                  :repo_url "https://github.com/test-owner/test-repo"}
            repo-id (db/upsert-repository! repo)
            
            pull-request {:id 12345
                         :number 1
                         :state "open"
                         :title "Test PR"
                         :html_url "https://github.com/test-owner/test-repo/pull/1"
                         :created_at "2025-03-01T00:00:00Z"
                         :updated_at "2025-03-02T00:00:00Z"
                         :closed_at nil
                         :merged_at nil
                         :user {:login "test-user"}}
            
            enriched-pr {:pull-request pull-request
                        :review-comments []
                        :requested-reviewers {:users [] :teams []}
                        :reviews []}
            
            _ (db/save-pull-request! repo-id enriched-pr)
            retrieved-prs (db/get-pull-requests-for-repository repo-id)
            first-pr (first retrieved-prs)]
        
        (is (= 1 (count retrieved-prs)))
        (is (= 12345 (:pull_requests/github_id first-pr)))
        (is (= 1 (:pull_requests/number first-pr)))
        (is (= "open" (:pull_requests/state first-pr)))
        (is (= "Test PR" (:pull_requests/title first-pr)))
        (is (= "test-user" (:pull_requests/user_login first-pr)))
        
        ;; Check that review data was properly stored as JSONB
        (let [review-data (json/parse-string (:pull_requests/review_data first-pr) true)]
          (is (vector? (:review_comments review-data)))
          (is (map? (:requested_reviewers review-data)))
          (is (vector? (:reviews review-data)))))))))
