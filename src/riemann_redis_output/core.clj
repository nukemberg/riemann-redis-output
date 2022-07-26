(ns riemann-redis-output.core
  (:require [taoensso.carmine :as car]
            [cheshire.core :as json]
            [clojure.tools.logging :refer [debugf]]
            [clojure.set :refer [rename-keys]]))

(def default-rename-keys-map {:service :check :host :entity :metric :perfdata :description :summary})
(def default-event-fields {:type "service" :details "" :state "ok" :summary "None"})
(def allowed-event-fields [:check :entity :perfdata :details :summary :time :type :tags :state])
(def default-opts {:buff-size 1000 :conn-spec {} :pool-spec {} :key "riemann-events" :encoder json/generate-string})

(def flapjack-encoder
  "Encode event to a flapjack compatible format. See http://flapjack.io/docs/1.0/development/DATA_STRUCTURES/ for details."
  (comp
   json/generate-string
   (partial merge default-event-fields)
   #(if (nil? (:perfdata %)) % (assoc % :perfdata (format "metric=%f" (double (:perfdata %))))) ; flapjack wants perfdata as nagios string, convert if present
   #(update % :time int) ; flapjack wants time as integer
   #(select-keys % allowed-event-fields) ; flapjack doesn't like fields it doesn't know
   #(rename-keys % default-rename-keys-map)
   #(into {} (filter (comp not nil? second) %)))) ; remove nil fields before we rename and merge with defaults

(defmacro maybe-def [symbol form]
  (when-not (ns-resolve 'clojure.core symbol)
    `(def ^:private ~symbol ~form)))

(maybe-def bytes? (comp not string?))

; byte-arrays need to be raw; bytes? added in clojure 1.9
(defn- car-encode [s]
  (if (bytes? s) (car/raw s) s))

; for performance reasons we prefer to send events to redis using a pipeline.
(defn- write [redis-conn key encoder events]
  (let [events (map (comp car-encode encoder) events)
        events-batched (partition-all 4 events)]
    (debugf "Flushing %d events to Redis key %s" (count events) key)
    (car/wcar redis-conn
              (doseq [events events-batched]
                (apply car/lpush key events)))))

(defn sink
  "Get an event handler for Redis. This function will start an asyncronous Redis flusher RedisFlusherService.
  Usage:

  (output) ; -> simplest form, no options
  (output {:conn-spec {:db 13 :host \"redis\" :port 6379} :key \"events\"}) ; -> with options

  Options:

  :conn-spec - Carmine spec, e.g. {:host \"redisserver\" :port 6379 :db 13}, see Carmine for details
  :pool-spec - Carmin pool spec (Apache commons pool options). See Carmine for details
  :key - Redis key to push events to
  :key-fn - A function to compute the Redis key from event; overrides `:key`. Signature (fn [event]) -> String
  :encoder - the encoding function to convert event map to a string. Signature: (fn [event]) -> String. Defaults to cheshire/generate-string
  "
  ([] (sink {}))
  ([opts]
   (let [{:keys [conn-spec key key-fn encoder pool-spec]} (merge default-opts opts)
         key-fn (or key-fn (constantly key))]
     (fn [events]
       (let [events (if (sequential? events) events [events])
             events-by-keys (group-by key-fn events)]
         (doseq [[key events] events-by-keys]
           (write {:pool pool-spec :spec conn-spec} key encoder events)))))))