(ns codevigil.test.github.utils-test
  (:require [clojure.test :refer :all]
            [codevigil.github.utils :as utils]))

(deftest parse-repo-url-test
  (testing "HTTPS URL parsing"
    (is (= {:owner "octocat" :repo "Hello-World"}
           (utils/parse-repo-url "https://github.com/octocat/Hello-World")))
    (is (= {:owner "octocat" :repo "Hello-World"}
           (utils/parse-repo-url "https://github.com/octocat/Hello-World.git"))))
  
  (testing "SSH URL parsing"
    (is (= {:owner "octocat" :repo "Hello-World"}
           (utils/parse-repo-url "git@github.com:octocat/Hello-World.git")))
    (is (= {:owner "octocat" :repo "Hello-World"}
           (utils/parse-repo-url "git@github.com:octocat/Hello-World"))))
  
  (testing "Invalid URL formats"
    (is (nil? (utils/parse-repo-url "not-a-url")))
    (is (nil? (utils/parse-repo-url "https://gitlab.com/user/repo")))
    (is (nil? (utils/parse-repo-url "https://github.com/octocat")))))
