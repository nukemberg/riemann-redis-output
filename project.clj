(defproject riemann-flapjack-output "0.0.1-SNAPSHOT"
  :description "A Riemann plugin to output events to Flapjack"
  :dependencies [
                 [com.taoensso/carmine "2.7.0"]
                ]
  :profiles {
  	:dev {:dependencies [
  			[midje "1.6.3"]
        	[riemann "0.2.9"]]}})
