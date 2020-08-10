(ns web-worklog.middleware
  (:require [ring.util.response :refer [response redirect]]
            [clj-time.coerce :as tc]
            [clj-time.core :as t]
            [web-worklog.db :as db]))

(defn wrap-debug-print-request [handler]
  (fn [request]
    (println "DEBUG REQUEST:" request)
    (let [response (handler request)]
      response)))

(def public-rotes
  ["/"
   "/login"
   "/registration"
   "/css/styles.css"
   "/favicon.ico"
   ])

(defn cookie-value-pattern
  [cookie-name]
  (str cookie-name "=(.[^; ]+)"))

(defn match-cookie-value
  [str cookie-name]
  (when (not (empty? str))
    (let [match (last (re-find (re-pattern (cookie-value-pattern cookie-name)) str))]
      (when (not (empty? match))
        (java.net.URLDecoder/decode match)))))

(defn cookie-valid?
  [cookies]
  (if (empty? cookies)
    false
    (let [expires (:cookie_expiration cookies)
          now (t/now)]
      (when (not (empty? (str expires)))
        (= 1 (compare (tc/from-sql-time expires) now))))))

(defn get-cookies-from-request
  [request]
  (let [headers (get-in request [:headers])
        cookies (get-in headers ["cookie"])]
    cookies))


(defn wrap-auth [handler]
  (fn [request]
    (let [uri (:uri request)
          cookies (get-cookies-from-request request)
          cookie-id (match-cookie-value cookies "uuid")]
      (when (not (empty?  cookie-id))
        (let [cookies-info (db/get-cookies-info cookie-id)]))
      (if (some #(= uri %) public-rotes)
        (let [response (handler request)]
          response)
        (if (and
            (not (empty? cookie-id))
            (cookie-valid? (db/get-cookies-info cookie-id)))
          (let [response (handler request)]
            response)
          (let [response (redirect "/login")]
            response))))))

(defn wrap-email
  [handler]
  (fn [request]
    (let [cookies (get-cookies-from-request request)
          cookie-id (match-cookie-value cookies "uuid")]
      (if (not (empty? cookie-id))
        (let [cookies-info (db/get-cookies-info cookie-id)]
          (handler (assoc request :email (:email cookies-info))))
        (let [response (handler request)]
          response)))))
