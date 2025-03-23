(ns codevigil.github.utils
  (:require [clojure.string :as str]))

(defn parse-repo-url
  "Extracts owner and repo from a GitHub URL.
   Supports formats like:
   - https://github.com/owner/repo
   - https://github.com/owner/repo.git
   - git@github.com:owner/repo.git"
  [url]
  (cond
    ;; Handle HTTPS URLs
    (re-matches #"https://github\.com/.*" url)
    (when-let [[_ owner repo] (re-matches #"https://github\.com/([^/]+)/([^/]+?)(?:\.git)?$" url)]
      {:owner owner :repo repo})
    
    ;; Handle SSH URLs
    (re-matches #"git@github\.com:.*" url)
    (when-let [[_ owner repo] (re-matches #"git@github\.com:([^/]+)/([^/]+?)(?:\.git)?$" url)]
      {:owner owner :repo repo})
    
    ;; Return nil for unrecognized formats
    :else nil))
