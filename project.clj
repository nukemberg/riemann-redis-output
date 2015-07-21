(defproject riemann-flapjack-output "0.0.1-SNAPSHOT"
  :description "A Riemann plugin to output events to Flapjack"
  :dependencies [
                 [com.taoensso/carmine "2.7.0" :exclusions [org.clojure/clojure]]
                ]
  :profiles {
  	:dev {:dependencies [
  			[org.clojure/clojure "1.6.0"]
  			[midje "1.6.3" :exclusions [org.clojure/clojure]]
                      	[riemann "0.2.10"]]}})
