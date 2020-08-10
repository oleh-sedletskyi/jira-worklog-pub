(ns web-worklog.json
  (:require [clojure.data.json :as json]
            [web-worklog.jira :as jira]
            ))

(defn prepare-json
  [logs cols]
  (let [inverted-cols (clojure.set/map-invert cols)]
    (mapv #(clojure.set/rename-keys % inverted-cols) logs)))

(defn generate-json-report
  [email from to jira-name jira-token jira-domain cols]
  (let [token jira-token
        author jira-name
        jira-resource (jira/make-resource-resolver jira-domain)]
    (->
     (jira/get-worklog email token jira-resource from to author)
     (jira/make-worklogs)
     (jira/extract-comments)
     (jira/prepare-items)
     (jira/filter-items from to)
     (jira/pretify-items-date)
     (prepare-json cols)
     (json/write-str))))
