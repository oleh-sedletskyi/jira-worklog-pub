(ns web-worklog.date-utils
  (:require [clj-time.core :as t]
            [clj-time.format :as f]
            [clojure.instant :refer [read-instant-date]]))

(defn get-first-day-of-the-week
  []
  (t/minus (t/now) (t/days  (t/day-of-week (t/now)))))

(defn get-last-day-of-the-week
  []
  (t/plus (t/now) (t/days (- 7 (t/day-of-week (t/now))))))
 
(defn to-short-date
  [date]
  (f/unparse (f/formatter "yyyy-MM-dd") date))


(defn parse-date
  [date-str]
  (read-instant-date date-str))

(defn from<=to?
  [from to]
  (let [from-date (parse-date from)
        to-date (parse-date to)]
    (> 1 (compare from-date to-date))))
