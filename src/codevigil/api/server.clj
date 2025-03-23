(ns codevigil.api.server
  (:require [ring.adapter.jetty :as jetty]
            [reitit.ring :as ring]
            [reitit.coercion.spec]
            [reitit.swagger :as swagger]
            [reitit.swagger-ui :as swagger-ui]
            [reitit.ring.coercion :as coercion]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [reitit.ring.middleware.exception :as exception]
            [reitit.ring.middleware.parameters :as parameters]
            [muuntaja.core :as m]
            [codevigil.api.routes :as routes]
            [codevigil.db.core :as db]))

(def app
  (ring/ring-handler
   (ring/router
    [["/swagger.json"
      {:get {:no-doc true
             :swagger {:info {:title "CodeVigil API"
                              :description "GitHub Repository Analysis API"}}
             :handler (swagger/create-swagger-handler)}}]
     
     ["/api"
      ["/repositories"
       {:get {:summary "Get a list of all repositories"
              :responses {200 {:description "Repositories retrieved successfully"}
                          500 {:description "Server error"}}
              :handler routes/get-repositories-handler}
        :post {:summary "Register a GitHub repository for analysis"
               :parameters {:body {:repo_url string?}}
               :responses {200 {:description "Repository registered successfully"}
                           400 {:description "Invalid repository URL"}
                           500 {:description "Server error"}}
               :handler routes/register-repo-handler}}]
      
      ["/repositories/:owner/:repo/metrics"
       {:get {:summary "Get repository metrics with daily or weekly aggregation"
              :parameters {:path {:owner string?
                                 :repo string?}
                          :query {:aggregation string?}}
              :responses {200 {:description "Metrics retrieved successfully"}
                          404 {:description "Repository not found"}
                          500 {:description "Server error"}}
              :handler routes/get-repository-metrics-handler}}]]
     
     ["/swagger-ui*"
      {:get (swagger-ui/create-swagger-ui-handler
             {:url "/swagger.json"
              :config {:validatorUrl nil
                       :operationsSorter "alpha"}})}]]
    
    {:data {:coercion reitit.coercion.spec/coercion
            :muuntaja m/instance
            :middleware [swagger/swagger-feature
                         parameters/parameters-middleware
                         muuntaja/format-negotiate-middleware
                         muuntaja/format-response-middleware
                         exception/exception-middleware
                         muuntaja/format-request-middleware
                         coercion/coerce-request-middleware
                         coercion/coerce-response-middleware]}})
   (ring/routes
    (ring/create-default-handler))))

(defn start-server
  "Starts the API server on the specified port."
  [& [port]]
  (let [port (Integer. (or port (System/getenv "PORT") 3000))]
    ;; Initialize the database
    (db/init-db!)
    ;; Start the server
    (jetty/run-jetty #'app {:port port :join? false})))
