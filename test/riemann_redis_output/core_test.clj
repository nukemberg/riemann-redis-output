(ns riemann-redis-output.core-test
  (:require [clojure.test :refer [deftest is]]
            [cheshire.core :refer [parse-string]]
            [riemann-redis-output.core :refer [flapjack-encoder]]))

(defn rand-str [len]
  (apply str (take len (repeatedly #(char (+ (rand 26) 65))))))

(deftest consume
  (let [messages (range 100)
        queue (java.util.concurrent.ArrayBlockingQueue. 1000)]
    (doseq [msg messages] (.put queue msg))
    (is 100 (count (#'riemann-redis-output.core/consume-until-empty queue 100))))
  (let [messages (range 100)
        queue (java.util.concurrent.ArrayBlockingQueue. 1000)]
    (doseq [msg messages] (.put queue msg))
    (is 10 (count (#'riemann-redis-output.core/consume-until-empty queue 10))))
  (let [messages (range 10)
        queue (java.util.concurrent.ArrayBlockingQueue. 1000)]
    (doseq [msg messages] (.put queue msg))
    (is 10 (count (#'riemann-redis-output.core/consume-until-empty queue 100)))))

(deftest redis-encoder
  (let [event-prototype {:host (rand-str 10)
                         :service (rand-str 10)
                         :description (rand-str 20)
                         :state (rand-str 10)}]
    (is
     {:time 1430857064
      :perfdata "metric=12312312.100000"
      :entity (:host event-prototype)
      :check (:service event-prototype)
      :details ""
      :type "service"
      :state (:state event-prototype)}
     (parse-string (flapjack-encoder (merge event-prototype {:time 1430857064.230 :metric 12312312.1}))))

    (is
     "metric=2837123.000000"
     (:perfdata (parse-string (flapjack-encoder (merge event-prototype {:time 1430857064.230 :metric 2837123})))))))