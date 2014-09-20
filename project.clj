(defproject riemann-flapjack-output "0.0.1-SNAPSHOT"
  :description "A Riemann plugin to output events to Flapjack"
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [com.taoensso/carmine "2.7.0"]
                 [cheshire "5.3.1"]
                ]
  :profiles {:dev {:dependencies [[midje "1.5.1"]
                                  [riemann "0.2.6"]]}})
