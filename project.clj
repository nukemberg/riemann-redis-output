(defproject riemann-flapjack-output "0.0.1-SNAPSHOT"
  :description "A Riemann plugin to output events to Flapjack"
  :dependencies [
                 [com.taoensso/carmine "2.15.1" :exclusions [org.clojure/clojure]]
                ]
  :profiles {
  	:dev {:dependencies [[org.clojure/clojure "1.10.3"]
                        [riemann "0.3.8"]]}}
  ; For some reason Leiningen doesn't like fusesource redirect to https 
  :mirrors {"fusesource" "https://repo.fusesource.com/nexus/content/groups/public/"})
