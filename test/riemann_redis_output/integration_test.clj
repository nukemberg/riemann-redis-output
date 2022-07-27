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

(def ^:dynamic redis-spec {:port 6379})

(defn- redis-fixture [f]
  (let [redis-container (-> (tc/create {:image-name "docker.io/library/redis:6-alpine"
                                        :exposed-ports [6379]
                                        :wait-for {:strategy :exposed-ports}})
                            tc/start!)
        port (get-in redis-container [:mapped-ports 6379])]
    (binding [redis-spec {:port port}]
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
        redis-out (sink {:conn-spec redis-spec :key "test-event"})]
    (with-server (streams/with :state "tested"
                               redis-out)
      (send-events [event]))
    (let [redis-event (car/wcar {:pool :none :spec redis-spec}
                                (car/blpop "test-event" 1))
          redis-event (json/parse-string (second redis-event) true)]
      (is (= "tested" (:state redis-event))))))

(deftest output-batch
  (let [event {:host "localhost" :service "test" :state "ok"}
        events (map #(assoc event :metric % :time %) (range 10)) 
        redis-out (sink {:conn-spec redis-spec :key "test-batch-events"})]
    (with-server (streams/with :state "tested"
                               (streams/batch 4 1 redis-out))
      (send-events events))
    ; we don't wait for the batch dt window to close, so only 8 message are expected
    (is (>= 8 (car/wcar {:spec redis-spec}
                        (car/llen "test-batch-events"))))
    (is (= (assoc event :state "tested")
           (-> (car/wcar {:spec redis-spec} (car/blpop "test-batch-events" 1))
               second
               (json/parse-string true)
               (select-keys (keys event)))))))

(deftest output-byte-array
  (let [ba-encoder (fn [e] (->> e
                                json/generate-string
                                (map byte)
                                byte-array))
        event {:time 0 :state "ok" :service "test service" :host "localhost"}
        redis-out (sink {:conn-spec redis-spec :key "test-byte-array" :encoder ba-encoder})]
    (with-server (streams/with :state "tested"
                               redis-out)
      (send-events [event]))
    (let [redis-event (car/wcar {:pool :none :spec redis-spec}
                                (car/blpop "test-byte-array" 1))
          redis-event (json/parse-string (second redis-event) true)]
      (is (= "tested" (:state redis-event))))))

(deftest output-with-key-field
  (let [key-field :redis-key
        event {:time 0 :state "ok" :service "test service" :host "localhost" :description (rand-str 10) :redis-key (rand-str 10)}
        redis-out (sink {:conn-spec redis-spec :key-fn key-field})]
    (with-server (streams/sdo redis-out)
      (send-events [event]))
    (let [redis-event (car/wcar {:pool :none :spec redis-spec}
                                (car/blpop (:redis-key event) 1))
          redis-event (json/parse-string (second redis-event) true)]
      (is (= (:description event) (:description redis-event))))))

(deftest output-with-multiple-keys
  (let [events (for [_ (range 10)]
                {:time 0 :state "ok" :service "test service" :host "localhost" :description (rand-str 10)})
        redis-out (sink {:conn-spec redis-spec :key-fn #(str "riemann-" (mod (hash (:description %)) 3))})]
    (with-server (streams/sdo redis-out)
      (send-events events))
    (let [redis {:pool :none :spec redis-spec}
          lists (for [x (range 3)] (str "riemann-" x))
          list-lens (car/wcar redis (doseq [l lists] (car/llen l)))
          redis-events (car/wcar {:pool :none :spec redis-spec}
                                 (doseq [[l len] (zipmap lists list-lens)]
                                   (dotimes [_ len] (car/lpop l))))
          redis-events (map #(json/parse-string % true) redis-events)]
      (is (> 1 (count (some #(> 0 %) list-lens))))
      (is (= 10 (count redis-events))))))