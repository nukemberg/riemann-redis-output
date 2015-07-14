(ns riemann-flapjack-output.core-test
  (:use midje.sweet)
  (:use [riemann-flapjack-output.core])
  (:require [midje.util :refer [expose-testables]]))

(expose-testables riemann-flapjack-output.core)

(fact "`consume-until-empty` should consume all messages from queue"
  (let [messages (range 100)
        queue (java.util.concurrent.ArrayBlockingQueue. 1000)]
        (doseq [msg messages] (.put queue msg))
        (consume-until-empty queue 100) => (range 100)
        (doseq [msg (range 100)] (.put queue msg))
        (consume-until-empty queue 10) => (range 10)))

(fact "`transcode-event` should transcode riemann event map to flapjack compatible map"
  (transcode-event {:host ..host.. :service ..service.. :time 1430857064.230 
                          :metric 12312312.0 :description ..description.. :state ..state.. :extra-field ..extra..}) 
                => 
                {:entity ..host.. :check ..service.. :state ..state.. 
                  :time 1430857064 :summary ..description.. :perfdata "metric=12312312.000000"
                  :type "service" :details ""}
  )