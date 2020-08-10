(ns web-worklog.csv
  (:require [web-worklog.jira :as jira]
            [clojure.data.csv :as csv]))


(defn get-csv
  [logs cols]
  (let [header (into [] (keys cols))
        lines (mapv #(mapv % (into [] (vals cols))) logs)]
    (with-out-str (csv/write-csv *out*
                                 (into [header]
                                       lines)))))

(defn generate-csv-report
  [email from to jira_name jira_token jira_domain columns]
  (let [token jira_token
        author jira_name
        jira-resource (jira/make-resource-resolver jira_domain)]
    (->
     (jira/get-worklog email token jira-resource from to author)
     (jira/make-worklogs)
     (jira/extract-comments)
     (jira/prepare-items)
     (jira/filter-items from to)
     (jira/pretify-items-date)
     (jira/sort-by-date)
     (get-csv columns))))
