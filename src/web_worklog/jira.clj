(ns web-worklog.jira
  (:require [clj-http.client :as http]
            [hiccup.core :refer [html]]
            [clojure.string :as str]
            [clojure.instant :refer [read-instant-date]])
  (:use [slingshot.slingshot :only [throw+ try+]]))

(defn make-resource-resolver
  [domain]
  (fn [resource] (str "https://" domain "/rest/api/3" resource)))

(defn get-current-user [email token resolver]
  (let [url (resolver "/myself")]
    (:body (http/get url
             {:accept     :application/json
              :as         :json
              :basic-auth [email token]}))))

(defn make-worklog-query [from to author]
  (str "worklogAuthor =" author
       " and worklogDate >=" from
       " and worklogDate <=" to))

(defn make-worklog-query-params [jql]
  {:jql    jql
   :fields "timespent,issuetype,project,parent,summary,started,created,updated,worklog"})

(defn get-issues [response]
  (get-in response [:body :issues]))

(defn extract-text-from-comment [comment]
  (keep :text (tree-seq #(or (map? %) (vector? %)) identity comment)))

(defn map-comment [comment]
  (str/join "\n" (extract-text-from-comment comment)))

(defn map-author
  [worklog]
  (let [author (get-in worklog [:author])]
       {
        :name (:name author)
        :email (:emailAddress author)
        :display-name (:displayName author)
        :account-id (:accountId author)})
  )

(defn map-worklog [worklog author]
  (->> (mapv
        (fn [w] {:time-spent         (:timeSpent        w)
                 :time-spent-seconds (:timeSpentSeconds w)
                 :started            (:started          w)
                 :updated            (:updated          w)
                 :created            (:created          w)
                 :author             (map-author        w)
                 :comment            (map-comment       (:comment w))})
        (:worklogs worklog))
       (filterv #(= (get-in % [:author :account-id]) author))))


(defn map-parent [parent]
  {:key     (:key parent)
   :summary (get-in parent [:fields :summary])})

(defn map-project [project]
  (select-keys project #{:key :name}))

(defn map-issue [issue author]
  (let [fields (:fields issue)]
    {:key        (:key        issue)
     :summary    (:summary    fields)
     :time-spent (:timespent  fields)
     :parent     (map-parent  (:parent  fields))
     :project    (map-project (:project fields))
     :worklog    (map-worklog (:worklog fields) author)}))

(defn map-issues [issues author]
  (mapv
   #(map-issue % author)
   issues))

(def account-ids
  ["static-account-ids-here-for-admin-temporary-solution"
   ])

(defn get-user-names
  [users]
  (->> users
       (mapv #(select-keys % [:displayName
                              :accountId]))
       (mapv vals)))


(defn get-user-info
  [email token resolver accountId]
  (println "get-user-info email token resolver username:" email token resolver accountId)
  (let [url (resolver "/user")
        info (:body (http/get url
                     {:accept     :application/json
                      :as         :json
                      :basic-auth [email token]
                      :query-params {:accountId accountId}}))]
    (println "User '" accountId "' info: " info)
    info
    ))

(defn get-users
  [email token resolver accountIds]
  (let [url (resolver "/user/bulk")
        users (:body (http/get url
                     {:accept     :application/json
                      :as         :json
                      :basic-auth [email token]
                      :query-params {:accountId account-ids
                                     :maxResults 30}}))
        values (:values users)]
    values))

(defn get-worklog [email token resolver from to author]
  (let [url (resolver "/search")]
    (-> (http/get url
                  {:accept       :application/json
                   :basic-auth   [email token]
                   :as           :json
                   :query-params (make-worklog-query-params
                                   (make-worklog-query from to author))})
        (get-in [:body :issues])
        (map-issues author))))

(defn make-task [m]
  (->
   (select-keys m [:key :summary])))

(defn make-project [m]
  (:project m))

(defn make-parent-issue [m]
  (:parent m))

(defn make-rename-worklog
  [w]
  (-> (select-keys w [:comment :started :time-spent-seconds])))

(defn make-worklog
  [w]
  (mapv make-rename-worklog (:worklog w)))

(defn make-worklogs [i]
  (mapv
   (fn [i] {:task (make-task i)
            :project (make-project i)
            :parent (make-parent-issue i)
            :worklogs (make-worklog i)})
   i))

(defn extract-comments [worklogs]
  (reduce (fn [items item]
            (let [num-of-elems (count (:worklogs item))]
              (if (> num-of-elems 1)
                (into items (mapv merge (take num-of-elems (repeat item))
                                    (:worklogs item)))
                (conj items (merge item (first (:worklogs item)))))))
          []
          worklogs))

(defn seconds-to-hours [seconds]
  (if (nil? seconds)
    0
    (let [minutes (/ seconds 60)
          hours (/ minutes 60)]
      (str (float (/ seconds 3600))))))

(defn prepare-items [tasks]
  (mapv (fn [task]
          {:task/key (get-in task [:task :key])
           :task/summary (get-in task [:task :summary])
           :project/key (get-in task [:project :key])
           :project/name (get-in task [:project :name])
           :parent/key (get-in task [:parent :key])
           :parent/summary (get-in task [:parent :summary])
           :started (:started task)
           :comment (:comment task)
           :time-spent-hours (seconds-to-hours (:time-spent-seconds task))})
        tasks))

(defn parse-jira-date-time
  [jira-date]
  (read-instant-date (subs jira-date 0 19))
  )

(defn date-in-range?
                [date from to]
                (and
                 (or (= 1 (compare date from)) (= 0 (compare date from)))
                 (or (= -1 (compare date to)) (= 0 (compare date to)))))

(defn filter-items
  [tasks from-date to-date]
  (filterv #(date-in-range?
             (parse-jira-date-time (:started %))
             (read-instant-date from-date)
             (read-instant-date to-date))
           tasks))

(defn to-short-date
  [date-str]
  (.format (java.text.SimpleDateFormat. "yyyy/MM/dd HH:mm:ss") (parse-jira-date-time date-str)))

(defn pretify-items-date
  [items]
  (mapv #(update % :started to-short-date) items))

(defn sort-by-date
  [items]
  (sort-by :started items))
