(defproject io.github.nukemberg/riemann-redis-output "0.0.6"
  :description "A Riemann plugin to output events to Redis"
  :url "https://github.com/nukemberg/riemann-redis-output"
  :license "Apache v2"
  :dependencies [[com.taoensso/carmine "2.15.1" :exclusions [org.clojure/clojure]]]
  :profiles {:provided {:dependencies [[cheshire "5.10.2"]
                                       [org.clojure/clojure "1.10.3"]
                                       [riemann "0.3.8"]]}
             :dev {:dependencies [[clj-test-containers "0.5.0"]]}
             :kaocha {:dependencies [[lambdaisland/kaocha "1.63.998"]]}}
  :aliases {"kaocha" ["with-profile" "+kaocha" "run" "-m" "kaocha.runner"]}
  ; For some reason Leiningen doesn't like fusesource redirect to https 
  :mirrors {"fusesource" "https://repo.fusesource.com/nexus/content/groups/public/"}
  :deploy-repositories {"releases" :clojars
                        "snapshots" :clojars})
