(ns codevigil.core
  (:gen-class)
  (:require [codevigil.api.server :as server]
            [codevigil.db.core :as db]
            [codevigil.github.async :as gh-async]
            [codevigil.scheduler.github :as gh-scheduler]
            [codevigil.scheduler.metrics :as metrics-scheduler]))

(defn -main
  "Main entry point for the CodeVigil application.
   Initializes the database, starts the background workers, schedulers, and starts the API server."
  [& args]
  (println "Initializing CodeVigil...")
  (println "Checking database connection...")
  (try
    (db/init-db!)
    (println "Database initialized successfully.")
    (catch Exception e
      (println "Error initializing database:" (.getMessage e))
      (System/exit 1)))
  
  (println "Starting background worker...")
  (gh-async/start-background-worker!)
  
  (println "Starting repository update scheduler...")
  (gh-scheduler/start-scheduler!)
  
  (println "Starting metrics calculation scheduler...")
  (metrics-scheduler/start-metrics-scheduler!)
  
  (println "Starting API server...")
  (let [port (if (seq args) (Integer/parseInt (first args)) 3000)
        server (server/start-server port)]
    (println "Server started on port" port)
    (println "Swagger UI available at http://localhost:" port "/swagger-ui")
    (.addShutdownHook (Runtime/getRuntime)
                     (Thread. (fn []
                               (println "Shutting down server...")
                               (gh-scheduler/stop-scheduler!)
                               (metrics-scheduler/stop-metrics-scheduler!)
                               (.stop server)
                               (println "Server stopped."))))
    server))
