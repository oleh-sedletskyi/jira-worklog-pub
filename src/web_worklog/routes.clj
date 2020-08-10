(ns web-worklog.routes
  (:require [compojure.core :refer :all]
            [compojure.route        :as route]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [ring.util.response :refer [response redirect]]
            [ring.middleware.cookies :refer [wrap-cookies]]
            [ring.adapter.jetty :as ring]
            [web-worklog.views      :as views]
            [web-worklog.handlers   :as handlers]
            [web-worklog.middleware :as middleware])
  (:gen-class))

(defroutes app-routes
  (GET "/"
       request
       (handlers/home-page request))
  (GET "/login"
       request
       (handlers/login request))
  (POST "/login"
        request
        (handlers/login-results request))
  (GET "/registration"
       []
       (handlers/register-user))
  (POST "/registration"
        {params :params}
        (handlers/register-user-results params))
  (GET "/logout"
       request
       (handlers/logout-results request))
  (GET "/settings"
       request
       (handlers/settings-page request))
  (POST "/settings"
        request
        (handlers/settings-results request))
  (GET "/reports"
       request
       (handlers/reports-page request))
  (POST "/reports/csv"
        request
        (handlers/reports-csv-results-page request))
  (POST "/reports/json"
        request
        (handlers/reports-json-results-page request))
  (POST "/reports"
        request
        (handlers/reports-results-page request))
  (route/resources "/")
  (route/not-found "Not Found"))


(def app
  (-> (wrap-defaults app-routes site-defaults)
      (middleware/wrap-debug-print-request)
      (middleware/wrap-auth)
      (middleware/wrap-email)))

(defn start [port]
  (ring/run-jetty app {:port port
                       :join? false}))

(defn -main [& args]
  (let [port (Integer. (or (System/getenv "PORT") "8080"))]
    (start port)))
