(ns riemann-flapjack-output.core
  (:require [taoensso.carmine :as car]
            [cheshire.core :as json]
            [clojure.tools.logging :refer [error info debug]]
            [clojure.set :refer [rename-keys]]
            [riemann.service :refer [Service ServiceEquiv]]
            [riemann.config :refer [service!]]))

(def default-rename-keys-map {:service :check :host :entity :metric :perfdata :description :summary})
(def default-event-fields {:type "service" :details "" :state "ok" :summary "None"})
(def allowed-event-fields [:check :entity :prefdata :details :summary :time :type :tags :state])
(def default-opts {:rename-keys-map default-rename-keys-map :buff-size 1000 :conn-spec {}})

(def transcode-event
  "Transcode event to a flapjack compatible format. See http://flapjack.io/docs/1.0/development/DATA_STRUCTURES/ for details.
  This is the default transcoding function for the flapjack output.
  "
  (comp
      #(select-keys % allowed-event-fields) ; flapjack doesn't like fields it doesn't know
      (partial merge default-event-fields)
      #(rename-keys % default-rename-keys-map)
      #(into {} (filter (comp not nil? second) %)) ; remove nil fields before we rename and merge with defaults
      #(assoc % :time (int (:time %))))) ; flapjack wants time as integer

(defn- consume-until-empty
  "Consume messages from a queue until no more are available or `limit` messages have been consumed.
   Returns a vector of consumed messages"
  [queue limit]
  (loop [events [] ^String event (.take queue)] ; blocks until a message is available on channel
    (if (or (nil? event) (>= (count events) limit))
      events ; return events we got and end the loop
      (recur (conj events event) (.poll queue))))) ; try to get more messages from channel

; for performance reasons we prefer to send events to redis using a pipeline. We therefor consume events from the queue in bulks using consume-until-empty
(defn- redis-flush
  "Send messages to redis. Consumes messages from queue"
  [queue server-conn flush-size]
  {:pre [(> flush-size 0) (map? server-conn)]}
  (try
    (car/wcar server-conn
      (doseq [^String event (consume-until-empty queue flush-size)]
        (debug "Sending event to redis" event)
        (car/lpush "events" event)))
    (catch Exception e
      (error e "Failed to send event to flapjack"))))

(defprotocol FlapjackClient
  (send-event [service event]))

(defrecord RedisFlusherService [running core queue buff-size server-conn]
  ServiceEquiv
  (equiv? [this other] (= [buff-size server-conn] (select-keys other [:buff-size :server-conn])))
  Service
  (start! [this]
    (locking this
      (when-not @running
        (reset! running true)
        (let [new-queue (java.util.concurrent.ArrayBlockingQueue. buff-size)
              flush-size (int (/ buff-size 2))
              flusher-thread (Thread. #(while @running (redis-flush new-queue server-conn flush-size)))]
          (.start flusher-thread)
          (reset! queue new-queue)))))
  (stop! [this] (locking this (reset! running false)))
  (reload! [this new-core] (reset! core new-core))
  (conflict? [this other] (instance? RedisFlusherService other))
  FlapjackClient
  (send-event [{queue :queue} event] (.put @queue (json/generate-string (transcode-event event)))))

(defn output
  "Get an event handler for flapjack. This function will start an asyncronous redis flusher RedisFlusherService.
  Usage:

  (output) ; -> simplest form, no options
  (output {:conn-spec {:db 13 :host \"redis\" :port 6379} :buff-size 1000}) ; -> with options

  Options:

  :conn-spec - carmine spec, e.g. {:host \"redisserver\" :port 6379 :db 13}
  :buff-size - Event queue buffer size
  :transcoder - the transcoding function to covert event map to a flapjack compatible map. Signature: (fn [event]) -> hash-map. See `transcode-event` for more details
  "
  ([] (output {}))
  ([opts]
  (let [{:keys [conn-spec buff-size]} (merge default-opts opts)
        server-conn {:pool {} :spec conn-spec}
        transcode (comp json/generate-string (get opts :transcoder transcode-event))
        service (service!
                  (RedisFlusherService. (atom false) (atom nil) (atom nil) buff-size server-conn)) ; start the sender loop in a thread
        ]
    (partial send-event service))))
