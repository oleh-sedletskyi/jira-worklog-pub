(defproject web-worklog "0.1.0"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [compojure "1.6.1"]
                 [ring/ring-defaults "0.3.2"]
                 [hiccup "1.0.5"]
                 [org.clojure/java.jdbc "0.7.9"]
                 [buddy/buddy-hashers "1.4.0"]
                 [clj-http "3.10.0"]
                 [clj-time "0.15.2"]
                 [org.clojure/data.csv "0.1.4"]
                 [org.clojure/data.json "0.2.6"]
                 [clj-postgresql "0.7.0"]
                 [environ "1.1.0"]
                 [ring/ring-jetty-adapter "1.7.1"]
                 ]
  :main ^:skip-aot web-worklog.routes
  :uberjar-name "web-worklog-standalone.jar"
  :plugins [[lein-ring "0.12.5"]]
  :ring {:handler web-worklog.routes/app}
  :profiles
  {:dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                        [ring/ring-mock "0.3.2"]]}
   :uberjar {:aot :all}})
