(ns riemann-redis-output.core-test
  (:require [clojure.test :refer [deftest is]]
            [cheshire.core :refer [parse-string]]
            [riemann-redis-output.core :refer [flapjack-encoder]]))

(defn- rand-str [len]
  (apply str (take len (repeatedly #(char (+ (rand 26) 65))))))

(deftest flapjack
  (let [event-prototype {:host (rand-str 10)
                         :service (rand-str 10)
                         :description (rand-str 20)
                         :state (rand-str 10)}]
    (is (=
         {:time 1430857064
          :perfdata "metric=12312312.100000"
          :entity (:host event-prototype)
          :check (:service event-prototype)
          :details ""
          :type "service"
          :summary (:description event-prototype)
          :state (:state event-prototype)}
         (parse-string (flapjack-encoder (merge event-prototype {:time 1430857064.230 :metric 12312312.1})) true)))

    (is (=
         "metric=2837123.000000"
         (:perfdata (parse-string (flapjack-encoder (merge event-prototype {:time 1430857064.230 :metric 2837123})) true))))))