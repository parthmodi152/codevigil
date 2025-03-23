(ns codevigil.test.api.routes-test
  (:require [clojure.test :refer :all]
            [ring.mock.request :as mock]
            [cheshire.core :as json]
            [codevigil.api.routes :as routes]
            [codevigil.github.utils :as gh-utils]
            [codevigil.github.async :as gh-async]
            [codevigil.db.core :as db])
  (:import [clojure.lang ExceptionInfo]))

(deftest register-repo-handler-test
  (testing "Valid repository URL with async processing"
    (with-redefs [gh-utils/parse-repo-url (constantly {:owner "octocat" :repo "Hello-World"})
                  db/upsert-repository! (constantly 1)
                  gh-async/process-repository-async! (constantly nil)]
      
      (let [request {:body-params {:repo_url "https://github.com/octocat/Hello-World"}}
            response (routes/register-repo-handler request)]
        (is (= 200 (:status response)))
        (is (= "success" (get-in response [:body :status])))
        (is (= "Repository queued for processing" (get-in response [:body :message]))))))
  
  (testing "Invalid repository URL"
    (with-redefs [gh-utils/parse-repo-url (constantly nil)]
      (let [request {:body-params {:repo_url "invalid-url"}}
            response (routes/register-repo-handler request)]
        (is (= 400 (:status response)))
        (is (= "error" (get-in response [:body :status])))
        (is (= "Invalid repository URL." (get-in response [:body :message]))))))
  
  (testing "Exception handling"
    (with-redefs [gh-utils/parse-repo-url (constantly {:owner "octocat" :repo "Hello-World"})
                  db/upsert-repository! (fn [_] (throw (Exception. "Database error")))]
      (let [request {:body-params {:repo_url "https://github.com/octocat/Hello-World"}}
            response (routes/register-repo-handler request)]
        (is (= 500 (:status response)))
        (is (= "error" (get-in response [:body :status])))
        (is (= "Database error" (get-in response [:body :message])))))))
