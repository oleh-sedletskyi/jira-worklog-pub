(ns web-worklog.handlers
  (:require [compojure.core :refer :all]
            [web-worklog.db :as db]
            [web-worklog.jira :as jira]
            [web-worklog.views :as views]
            [web-worklog.csv :as csv]
            [web-worklog.json :as json]
            [web-worklog.date-utils :as date-u]
            [slingshot.slingshot :refer [throw+ try+]]
            [clj-time.coerce :as tc]
            [clj-time.core :as t]
            ))


(defn home-page
  [request]
  (let [cookies (:cookies request)
        uuid (get-in cookies ["uuid" :value])]
    (if (empty? uuid)
      (views/home-page views/public-links)
      (views/home-page views/private-links))))

(defn validate-email
  [email]
  (let [pattern #"[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*@(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?"]
    (and (string? email) (re-matches pattern email))))

(defn register-user
  []
  (views/registration-page))

(defn email-not-valid?
  [email]
  (not (validate-email email)))

(defn register-user-results
  [{:keys [email password]}]
  (cond
    (email-not-valid? email) (views/registration-page (str  "E-mail: " email " is not valid.  Please try again."))
    (empty? password) (views/registration-page (str "Password cannot be empty. " "Please try again."))
    (db/user-exists? email) (views/registration-page (str "E-mail: " email " is already used. " "Please try again."))
    :else (try 
      (let [id (db/add-user-to-db email password)]
        (views/registration-results-page "Registered a new user" (str "User with E-mail " email " is successfully registered.") "/login" "You can now login."))
      (catch Exception e
        (views/registration-page (str "Something went wrong. " "Please, try again."))))))

(defn login
  [request]
  (views/login-page))

(defn- user-does-not-exist?
  [email]
  (not (db/user-exists? email)))

(defn login-results
  [{{:keys [email password remember-me]} :params}]
  (cond
    (empty? email) (views/login-page "E-mail cannot be empty. Please, try again.")
    (empty? password) (views/login-page "Password cannot be empty. Please, try again.")
    (user-does-not-exist? email) (views/login-page (str  "User " email " is not registered. Please, try again."))
    (not (db/check-user email password)) (views/login-page "Login failed. Please, try again.")
    :else (let [uuid (str (java.util.UUID/randomUUID))
            expires (tc/to-timestamp
                     (t/plus (t/now)
                             (if (true? (boolean (Boolean/valueOf remember-me)))
                               (t/days 30)
                               (t/hours 2))))
            max-age (if (true? (boolean (Boolean/valueOf remember-me)))
                      (* 3600 24 30)
                      (* 3600 2)
                      )]
        (db/set-cookies-info email uuid expires)
        {:headers {"Content-Type" "text/html"}
         :cookies {:email {:value email :max-age max-age :same-site :strict}
                   :uuid {:value uuid :max-age max-age :same-site :strict}}
         :body (views/login-results-page "You have successfully logged in."
                                         views/private-links
                                         "You have successfully logged in."
                                         "/reports"
                                         "You can now generate your jira report.")})))

(defn logout-results
  [request]
  {:headers {"Content-Type" "text/html"}
   :cookies {:email {:value nil :max-age 0 :same-site :strict}
             :uuid {:value nil :max-age 0 :same-site :strict}}
   :body (views/logout-results-page "You have successfully logged out."
                                   views/public-links
                                   "You have successfully logged out."
                                   "/login"
                                   "You can login again.")})


(defn settings-page
  [request]
  (let [email (:email request)]
    (if (not (empty? email))
      (let [user (db/get-user email)
            domain (:jira_domain user)
            token (:jira_token user)]
            #_(println "hanlders/settings-page: user" user)
        (views/settings-page email domain token))))
  )

(defn settings-results
  [request]
  (let [email (:email request)
        params (:params request)
        token (:token params)
        domain (:domain params)]
    (cond
      (empty? domain) (views/settings-page email domain token "Jira domain cannot be empty. Please, try again.")
      (empty? token) (views/settings-page email domain token "Jira token cannot be empty. Please, try again.")
      :else (try+
       (let [user (jira/get-current-user email token (jira/make-resource-resolver domain))]
         (db/set-jira-info email #_(:name user) #_(:displayName user) (:accountId user) domain token)
         (views/settings-results-page "Jira info is updated." (str "Jira info is sucessfully saved. Your Jira name is: " (:displayName user) ". ")  "/reports"  "You can now generate your reports."))
       (catch [:status 404] {:keys [request-time headers body]}
         (println "404 error")
         (views/settings-page email domain token "Jira domain error. Please, try again."))
       (catch [:status 401] {:keys [request-time headers body]}
         (println "401 credentials error")
         (views/settings-page email domain token "Jira token error. Please, try again."))
       (catch Exception e
         (println "Unexpected error" (.getMessage e))
         (views/settings-page email domain token (str "Jira access error. Details: " (.getMessage e) ".  Please, try again.")))))))

(defn get-drop-down-users
  [account-id account-type full-name email token domain]
  (println "get-drop-down-users: name full-name email token domain:" account-id full-name email token domain)
  (if (= account-type 1)
      (jira/get-user-names (jira/get-users email token (jira/make-resource-resolver domain) jira/account-ids))
    [[full-name account-id]]))

(defn reports-page
  [request]
  (let [email (:email request)]
    (if (empty? email)
      (views/login-page "You need to login before generating the report.")
      (let [user (db/get-user email)
;            jira-name (:jira_name user)
            token (:jira_token user)
            domain (:jira_domain user)
            account-id (:account_id user)
            account-type (:account_type user)]
        (if (empty? account-id)
          (views/settings-page email nil nil "You need to update your Jira info before generating the reports.")
          (do
            (if (empty? account-id) ; Migration - set account-id for users that didn't have it during registration
              (let [current-user (jira/get-current-user email token (jira/make-resource-resolver domain))
                    jira-account-id (:accountId current-user)]
                (db/set-account-id email jira-account-id)))
            (let [current-user (jira/get-current-user email token (jira/make-resource-resolver domain))
                  display-name (:displayName current-user)
                    account-id (:accountId current-user)]
              (views/reports-page
               account-id
               (date-u/to-short-date (date-u/get-first-day-of-the-week))
               (date-u/to-short-date (date-u/get-last-day-of-the-week))
               (get-drop-down-users account-id account-type display-name email token domain)))))))))


(defn reports-results-page
  [request]
  (let [{:keys [jira-name from to]} (:params request) 
        email (:email request)
        account-id jira-name]
    (cond
      (empty? account-id) (views/reports-page account-id from to "Jira name cannot be empty.")
      (not (date-u/from<=to? from to)) (views/reports-page account-id from to "From date cannot be less than To date.")
      :else (let [user (db/get-user email)
                  token (:jira_token user)
                  domain (:jira_domain user)
                  user-info (jira/get-user-info email token (jira/make-resource-resolver domain) account-id)
                  display-name (:displayName user-info)]
              (views/reports-results-page account-id display-name from to email token domain)))))

(defn reports-csv-results-page
  [request]
  (let [{:keys [jira-name from to]} (:params request)
        email (:email request)] 
    (let [user (db/get-user email)
          token (:jira_token user)
          domain (:jira_domain user)
          body (str (csv/generate-csv-report email from to jira-name token domain views/columns))]
      {:headers {"Content-Type"        "text/csv"
                 "Content-Disposition" (str "attachment; filename="
                                            jira-name
                                            "_"
                                            from
                                            "-"
                                            to
                                            ".csv")}
       :body body})))

(defn reports-json-results-page
  [request]
  (let [{:keys [jira-name from to]} (:params request)
        email (:email request)] 
    (let [user (db/get-user email)
          token (:jira_token user)
          domain (:jira_domain user)
          body (str
                (json/generate-json-report email from to jira-name token domain views/columns))]
      {:headers {"Content-Type"        "text/json"
                 "Content-Disposition" (str "attachment; filename="
                                            jira-name
                                            "_"
                                            from
                                            "-"
                                            to
                                            ".json")}
       :body body})))
