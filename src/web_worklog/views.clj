(ns web-worklog.views
  (:require [web-worklog.db :as db]
            [web-worklog.jira :as jira]
            [web-worklog.date-utils :as date-u]
            [clojure.string :as str]
            [hiccup.page :as page]
            [hiccup.element :as elem]
            [hiccup.form :as form]
            [ring.util.anti-forgery :as util]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [ring.util.response :refer [response redirect]]
            [clojure.data.csv :as csv]))

(defn gen-page-head
  [title]
  [:head
   [:title (str "Jira Worklog: " title)]
   (page/include-css "/css/styles.css")])


(def public-links-sub
  [
   [:a {:href "/"} "Home"]
   " | "
   [:a {:href "/registration"} "Sign Up"]
   " | "
   [:a {:href "/login"} "Sign In"]])

(def private-links-sub
  [[:a {:href "/"} "Home"]
   " | "
   [:a {:href "/reports"} "Reports"]
   " | "
   [:a {:href "/settings"} "Settings"]
   " | "
   [:a {:href "/logout"} "Log Out"]])

(def public-links
  (->  (into [:div#header-links] "[ ")
       (into public-links-sub)
       (into [" ]"])))

(def private-links
        (->  (into [:div#header-links] "[ ")
             (into private-links-sub)
             (into [" ]"])))

(defn gen-error
  [message]
  [:p {:class "error"} message])

(defn html-page
  [title links message & body]
  (page/html5
   (gen-page-head title)
   links
   (gen-error message)
   body))

(defn home-page
  [links]
  (html-page
   "Home"
   links
   nil
   [:h1 "Home"]
   [:p "Webapp to generate worklog Jira reports."]
   [:p [:i "You can ask questions or provide feedback via Skype: oleg.sedletskyi"]]))

(defn registration-page
  ([]
   (registration-page nil))
  ([error]
   (html-page
    "Register"
    public-links
    error
    [:h1 "Sign Up"]
    [:p "Please specify your Jira Email and password."]
    (form/form-to [:post "/registration"]
                  (util/anti-forgery-field) 
                  (form/label :email "E-mail:")
                  (form/email-field :email)
                  [:p 
                   (form/label :password "Password:")
                   (form/password-field :password)]
                  (form/submit-button "Register user")))))

(defn registration-results-page
  [title text link link-text]
  (html-page
   title
   public-links
   [:h1 text]
   (elem/link-to link link-text)))

(defn login-page
  ([]
   (login-page nil))
  ([error]
   (html-page
    "Login"
    public-links
    error
    [:p "Please enter your E-mail and password."]
    (form/form-to [:post "/login"]
                  (util/anti-forgery-field)
                  (form/label :email "E-mail:")
                  (form/email-field :email)
                  [:p 
                   (form/label :password "Password:")
                   (form/password-field :password)]
                  [:p
                   (form/label :remember-me "Remember me:")
                   (form/check-box :remember-me false)]
                  (form/submit-button "Login")))))

(defn login-results-page
  [title header text link link-text]
  (html-page
   title
   header
   [:h1 text]
   (elem/link-to link link-text)))

(defn logout-results-page
  [title header text link link-text]
  (html-page
   title
   header
   [:h1 text]
   (elem/link-to link link-text)))

(defn settings-page
  ([email domain token]
   (settings-page email domain token nil))
  ([email domain token error]
   (html-page
    "Settings"
    private-links
    error
    [:p "Please provide your Jira domain and token."]
    (form/form-to [:post "/settings"]
                  (util/anti-forgery-field)
                  (form/label :email "E-mail:")
                  (form/label :email email)
                  [:p 
                   (form/label :domain "Jira domain:")
                   (form/text-field :domain domain)
                   (form/label :domain "(e.g. 'avid-ondemand.atlassian.net')")]
                  [:p 
                   (form/label :token "Jira token:")
                   (form/password-field :token token)
                   (elem/link-to "https://id.atlassian.com/manage/api-tokens" "You can generate Jira token here.")
                   ]
                  
                  
                  (form/submit-button "Save")))))

(defn settings-results-page
  [title text link link-text]
  (html-page
   title
   private-links
   [:h1 text]
   (elem/link-to link link-text)))


(defn reports-page
  ([jira-name from to users]
   (reports-page jira-name from to users nil))
  ([jira-name from to users error]
   (html-page
    "Reports"
    private-links
    error
    [:p "Please provide inputs for the report generation."]
    (form/form-to [:post "/reports"]
                  (util/anti-forgery-field)
                  (form/label :jira-name "Jira name: ")
                  (form/drop-down :jira-name users jira-name)
                  [:p 
                   (form/label :from "From: ")
                   (form/text-field {:type "date"} :from from)]
                  [:p 
                   (form/label :to "To: ")
                   (form/text-field {:type "date"} :to to)]
                  (form/submit-button "Generate Report")))))

(defn get-total-hours
  [logs]
  (reduce + (map #(read-string (:time-spent-hours %)) logs)))

(defn make-html-body
  [logs cols]
  [:body
   [:h2 (str
         "Total logged time: "
        (get-total-hours logs))]
   [:table {:border "1"}
    [:tr (for [column (into [] (keys cols))]
           [:th column])]
    (for [row (mapv #(mapv % (into [] (vals cols))) logs)]
      [:tr (for [item row]
             [:td item])])]])

 (def columns
  (array-map "Project Key" :project/key
             "Project Name" :project/name
             "User Story Key" :parent/key
             "User Story Description" :parent/summary
             "Task Key" :task/key
             "Task Description" :task/summary
             "Worklog Date" :started
             "Worklog Comments" :comment
             "Worklog Time, hours" :time-spent-hours))

(defn generate-html-report
  [email from to account-id jira-token jira-domain]
  (let [token jira-token
        author account-id
        jira-resource (jira/make-resource-resolver jira-domain)]
    (->
     (jira/get-worklog email token jira-resource from to author)
     (jira/make-worklogs)
     (jira/extract-comments)
     (jira/prepare-items)
     (jira/filter-items from to)
     (jira/pretify-items-date)
     (jira/sort-by-date)
     (make-html-body columns))))

(defn get-download-form
  [type link account-id from to]
  (form/form-to [:post link]
                  (util/anti-forgery-field)
                  (form/text-field {:type "hidden"} :jira-name account-id)
                  (form/text-field {:type "hidden"} :from from)
                  (form/text-field {:type "hidden"} :to to)
                  (form/submit-button type)))

(defn reports-results-page
  ([account-id display-name from to email token domain]
   (reports-results-page account-id display-name from to email token domain nil))
  ([account-id display-name from to email token domain error]
   (html-page
    "Reports"
    private-links
    error
    [:h1 "Jira Report for " display-name " from " from " to " to "."]
    [:p "You can download report in: "]
    (get-download-form "CSV" "reports/csv" account-id from to)
    (get-download-form "JSON" "reports/json" account-id from to)
    (generate-html-report email from to account-id token domain))))

