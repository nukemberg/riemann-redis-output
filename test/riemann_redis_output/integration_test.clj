(ns riemann-redis-output.integration-test
  (:require [clojure.test :refer [is deftest use-fixtures]]
            [riemann.streams :as streams]
            [riemann.core :refer [core transition! stop!]]
            [riemann.transport.tcp :refer [tcp-server]]
            [riemann.client :refer [tcp-client send-event close!]]
            [clj-test-containers.core :as tc]
            [riemann-redis-output.core :refer [sink]]
            [cheshire.core :as json]
            [taoensso.carmine :as car]
            [riemann-redis-output.core-test :refer [rand-str]]))

(def ^:dynamic redis-port 6379)
(defn- redis-fixture [f]
  (let [redis-container (-> (tc/create {:image-name "docker.io/library/redis:6-alpine"
                                        :exposed-ports [6379]
                                        :wait-for {:strategy :exposed-ports}})
                            tc/start!)
        port (get-in redis-container [:mapped-ports 6379])]
    (binding [redis-port port]
      (f))
    (tc/stop! redis-container)))

(defmacro with-server [stream & forms]
  `(let [server# (tcp-server)
         core# (transition! (core) {:services [server#]
                                   :streams [~stream]})]
     ~@forms
     (stop! core#)))

(defn- send-events [events]
  (let [client (tcp-client)]
    (doseq [event events]
      @(send-event client event))
    (close! client)))

(use-fixtures :once redis-fixture)

(deftest output
  (let [event {:time 0 :state "ok" :service "test service" :host "localhost"}
        redis-out (sink {:conn-spec {:port redis-port} :key "test-event"})]
    (with-server (streams/with :state "tested"
                                 redis-out)
      (send-events [event])
      (Thread/sleep 50))
    (let [redis-event (car/wcar {:pool :none :spec {:host "localhost" :port redis-port}}
                                (car/lpop "test-event"))
          redis-event (json/parse-string redis-event true)]
      (is (= "tested" (:state redis-event))))))

(deftest output-batch
  (let [event {:host "localhost" :service "test" :state "ok"}
        events (map #(assoc event :metric % :time %) (range 10))
        redis-spec {:port redis-port}
        redis-out (sink {:conn-spec redis-spec :key "test-batch-events"})]
    (with-server (streams/with :state "tested"
                               (streams/batch 4 1 redis-out))
      (send-events events)
      (Thread/sleep 50))
    ; we don't wait for the batch dt window to close, so only 8 message are expected
    (is (>= 8 (car/wcar {:spec redis-spec}
                        (car/llen "test-batch-events"))))
    (is (= (assoc event :state "tested")
           (-> (car/wcar {:spec redis-spec} (car/rpop "test-batch-events"))
               (json/parse-string true)
               (select-keys (keys event)))))))

(deftest output-byte-array
  (let [ba-encoder (fn [e] (->> e
                               json/generate-string
                               (map byte)
                               byte-array))
        event {:time 0 :state "ok" :service "test service" :host "localhost"}
        redis-out (sink {:conn-spec {:port redis-port} :key "test-byte-array" :encoder ba-encoder})]
    (with-server (streams/with :state "tested"
                               redis-out)
      (send-events [event])
      (Thread/sleep 50))
    (let [redis-event (car/wcar {:pool :none :spec {:host "localhost" :port redis-port}}
                                (car/lpop "test-byte-array"))
          redis-event (json/parse-string redis-event true)]
      (is (= "tested" (:state redis-event))))))

(deftest output-with-key-field
  (let [key-field :redis-key
        event {:time 0 :state "ok" :service "test service" :host "localhost" :description (rand-str 10) :redis-key (rand-str 10)}
        redis-out (sink {:conn-spec {:port redis-port} :key-fn key-field})]
    (with-server (streams/sdo redis-out)
      (send-events [event])
      (Thread/sleep 50))
    (let [redis-event (car/wcar {:pool :none :spec {:host "localhost" :port redis-port}}
                                (car/lpop (:redis-key event)))
          redis-event (json/parse-string redis-event true)]
      (is (= (:description event) (:description redis-event))))))