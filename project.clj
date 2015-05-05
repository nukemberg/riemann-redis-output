(defproject riemann-flapjack-output "0.0.1-SNAPSHOT"
  :description "A Riemann plugin to output events to Flapjack"
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [com.taoensso/carmine "2.9.2"]
                 [cheshire "5.4.0"]
                ]
  :profiles {:dev {:dependencies [[midje "1.6.3"]
                                  [riemann "0.2.9"]]}})
