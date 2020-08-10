(ns web-worklog.db
  (:require [clojure.java.jdbc   :as jdbc]
            [buddy.hashers       :as hashers]
            [clj-postgresql.core :as pg]))

(def db-spec
  (or (System/getenv "DATABASE_URL")
      (pg/pool
       :host "localhost"
       :user "admin"
       :dbname "jira-worklog"
       :password "local-pass-here")))

(defn add-user-to-db
  [email pass]
  (let [pass-encrypted (hashers/derive pass)]
    (jdbc/insert! db-spec :users
                  {:email email
                   :password pass-encrypted})))

(defn user-exists?
        [email] 
        (not (empty?
              (jdbc/query db-spec ["select * from users where email = lower(?) " (clojure.string/lower-case email)])))) 

(defn check-user
  [email pass]
  (->
   (jdbc/query db-spec [
                        "select password from users where email = lower(?)"
                        (clojure.string/lower-case email)])
   first
   :password
   (#(hashers/check pass %))))

(defn get-user
  [email]
  (let [results (jdbc/query db-spec
                            [
                             "select * from users where email = lower(?)"
                             (clojure.string/lower-case email)])]
    (assert (= (count results) 1))
    (first results)))

(defn get-cookies-info
  [id]
  (let [results (jdbc/query
                 db-spec
                 ["select email, cookie_expiration from users where cookie_id = lower(?)"
                  (clojure.string/lower-case id)])]
    (first results)))

(defn set-cookies-info
  [email id expires]
  (jdbc/update! db-spec :users
                {:cookie_expiration expires
                 :cookie_id id}
                ["email=lower(?)" (clojure.string/lower-case email)]))

(defn set-account-id
  [email account-id]
  (jdbc/update! db-spec :users
                {:account_id account-id}
                ["email=lower(?)" (clojure.string/lower-case email)]))


(defn set-jira-info
  [email account-id domain token]
  (jdbc/update! db-spec :users
                {:jira_domain domain
                 :jira_token token
                 :account_id account-id}
                ["email=lower(?)" (clojure.string/lower-case email)]))
