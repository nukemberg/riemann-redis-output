(ns riemann-redis-output.core
  (:require [taoensso.carmine :as car]
            [cheshire.core :as json]
            [clojure.tools.logging :refer [warn debugf]]
            [clojure.set :refer [rename-keys]]
            [riemann.service :refer [Service ServiceEquiv]]
            [riemann.config :refer [service!]]))

(def default-rename-keys-map {:service :check :host :entity :metric :perfdata :description :summary})
(def default-event-fields {:type "service" :details "" :state "ok" :summary "None"})
(def allowed-event-fields [:check :entity :perfdata :details :summary :time :type :tags :state])
(def default-opts {:buff-size 1000 :conn-spec {} :key "riemann-events"})

(def flapjack-encoder
  "Encode event to a flapjack compatible format. See http://flapjack.io/docs/1.0/development/DATA_STRUCTURES/ for details."
  (comp
   json/generate-string
   (partial merge default-event-fields)
   #(if (nil? (:perfdata %)) % (assoc % :perfdata (format "metric=%f" (float (:perfdata %))))) ; flapjack wants perfdata as nagios string, convert if present
   #(assoc % :time (int (:time %))) ; flapjack wants time as integer
   #(select-keys % allowed-event-fields) ; flapjack doesn't like fields it doesn't know
   #(rename-keys % default-rename-keys-map)
   #(into {} (filter (comp not nil? second) %)))) ; remove nil fields before we rename and merge with defaults

; for performance reasons we prefer to send events to redis using a pipeline.
(defn write [redis-conn key encoder events]
  (let [events (map encoder events)
        events-batched (partition-all 4 events)] ; apply 
    (debugf "Flushing %d events to Redis key %s" (count events) key)
    (try
      (car/wcar redis-conn
                (doseq [events events-batched]
                  (apply car/lpush key events)))
      (catch Exception e
        (warn e "Failed to send events to redis")))))

(defn- flusher [running queue flush-size redis-writer]
  (let [flush-size (dec flush-size)
        ary (java.util.ArrayList. flush-size)] 
    (while @running
      (.clear ary)
      (let [msg (.take queue)] ; block until queue has messages
        (.add msg ary)
        (.drainTo queue ary flush-size) ; drain remaining messages
        (redis-writer ary)))))

(defprotocol RedisClient
  (send-event [service event]))

(defrecord RedisFlusherService [running core queue buff-size server-conn redis-key encoder]
  ServiceEquiv
  (equiv? [this other] (= [buff-size server-conn] (select-keys other [:buff-size :server-conn])))
  Service
  (start! [this]
    (locking this
      (when-not @running
        (reset! running true)
        (let [new-queue (java.util.concurrent.ListBlockingQueue. buff-size)
              flush-size (max 40 (int (/ buff-size 2)))
              flusher-thread (Thread. #(flusher running new-queue flush-size (partial write server-conn redis-key encoder)))]
          (.start flusher-thread)
          (reset! queue new-queue)))))
  (stop! [this] (locking this (reset! running false)))
  (reload! [this new-core] (reset! core new-core))
  (conflict? [this other] (instance? RedisFlusherService other))
  RedisClient
  (send-event [{queue :queue} event]
    (.put @queue event)))

(defn output
  "Get an event handler for Redis. This function will start an asyncronous Redis flusher RedisFlusherService.
  Usage:

  (output) ; -> simplest form, no options
  (output {:conn-spec {:db 13 :host \"redis\" :port 6379} :buff-size 1000}) ; -> with options

  Options:

  :conn-spec - carmine spec, e.g. {:host \"redisserver\" :port 6379 :db 13}
  :buff-size - Event queue buffer size
  :key - Redis key to push events to
  :encoder - the encoding function to convert event map to a string. Signature: (fn [event]) -> String. Defaults to cheshire/generate-string
  "
  ([] (output {}))
  ([opts]
    (let [{:keys [conn-spec buff-size key]} (merge default-opts opts)
          server-conn {:pool {} :spec conn-spec}
          encoder (get opts :encoder json/generate-string)
          service (service!
                   (RedisFlusherService. (atom false) (atom nil) (atom nil) buff-size server-conn key encoder)) ; start the sender loop in a thread
          ]
      (partial send-event service))))
