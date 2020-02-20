(ns resilience4clj.bulkhead
  "Functions to create and execute bulkheads."
  (:refer-clojure :exclude [name])
  (:require [clojure.spec.alpha :as s])
  (:import [io.github.resilience4j.bulkhead
            Bulkhead
            BulkheadConfig
            BulkheadRegistry
            ThreadPoolBulkhead
            ThreadPoolBulkheadConfig
            ThreadPoolBulkheadRegistry]
           [java.time Duration]))

(defn- build-configs-map [configs-map config-builder]
  (into {} (map (fn [[k v]] [(clojure.core/name k) (config-builder v)]) configs-map)))

(s/def ::name
  (s/or :string (s/and string? not-empty)
        :keyword keyword?))

;; -----------------------------------------------------------------------------
;; semaphore configuration

(s/def ::max-concurrent-calls nat-int?)
(s/def ::max-wait-duration nat-int?)

(s/def ::config
  (s/keys :opt-un [::max-concurrent-calls
                   ::max-wait-duration]))

(defn- build-config [config]
  (let [{:keys [max-concurrent-calls
                max-wait-duration]} config]
    (cond-> (BulkheadConfig/custom)

      max-concurrent-calls
      (.maxConcurrentCalls max-concurrent-calls)

      max-wait-duration
      (.maxWaitDuration (Duration/ofMillis max-wait-duration))

      :always
      (.build))))

(defn semaphore-registry
  "Creates a registry for semaphore-based bulkheads.

  configs-map is a map whose keys are names and vals are semaphore configs. When
  a bulkhead is created, you may specify one of the names in this map to use as
  the config for the bulkhead.

  :default is a special name. It will be used as the config for bulkheads that
  do not specify a config to use."
  [configs-map]
  (BulkheadRegistry/of (build-configs-map configs-map build-config)))

;; -----------------------------------------------------------------------------
;; thread pool configuration

(s/def ::max-thread-pool-size nat-int?)
(s/def ::core-thread-pool-size nat-int?)
(s/def ::queue-capacity nat-int?)
(s/def ::keep-alive-duration nat-int?)

(s/def ::thread-pool-config
  (s/keys :opt-un [::max-thread-pool-size
                   ::core-thread-pool-size
                   ::queue-capacity
                   ::keep-alive-duration]))

(defn- build-thread-pool-config [thread-pool-config]
  {:pre [(s/assert ::thread-pool-config thread-pool-config)]}
  (let [{:keys [max-thread-pool-size
                core-thread-pool-size
                queue-capacity
                keep-alive-duration]} thread-pool-config]
    (cond-> (ThreadPoolBulkheadConfig/custom)

      max-thread-pool-size
      (.maxThreadPoolSize max-thread-pool-size)

      core-thread-pool-size
      (.coreThreadPoolSize core-thread-pool-size)

      queue-capacity
      (.queueCapacity queue-capacity)

      keep-alive-duration
      (.keepAliveDuration (Duration/ofMillis keep-alive-duration))

      :always
      (.build))))

(defn thread-pool-registry
  "Creates a registry for thread-pool-based bulkheads and configs.

  thread-pool-configs-map is a map whose keys are names and vals are thread-pool
  configs. When a bulkhead is created, you may specify one of the names in this
  map to use as the config for the bulkhead.

  :default is a special name. It will be used as the config for bulkheads that
  do not specify a config to use."
  [thread-pool-configs-map]
  (ThreadPoolBulkheadRegistry/of (build-configs-map thread-pool-configs-map build-thread-pool-config)))

;; -----------------------------------------------------------------------------
;; registry

(def registry
  "The global bulkhead and config registry.

  By default, this registry works with semaphore-based bulkheads. If you prefer
  to use thread-pool-based bulkheads, use call `thread-pool-registry` and use
  `configure-registry!` to update this value."
  (BulkheadRegistry/ofDefaults))

(defn configure-registry!
  "Overwrites the global registry with `new-registry`."
  [new-registry]
  (alter-var-root (var registry) (constantly new-registry)))

;; -----------------------------------------------------------------------------
;; creation and lookup

(defn- build-config-for-registry [registry config-map]
  (if (instance? ThreadPoolBulkheadRegistry registry)
    (build-thread-pool-config config-map)
    (build-config config-map)))

(defn bulkhead!
  "Creates or fetches a bulkhead with the specified name and config and stores
  it in the global registry.

  The config value can be either a config map compatible with the current
  registry (e.g., semaphore-based configs for a semaphore-based registry) or the
  name of a config map stored in the global registry.

  If the bulkhead already exists in the global registry, the config value is
  ignored."
  ([name]
   {:pre [(s/assert ::name name)]}
   (.bulkhead registry (clojure.core/name name)))
  ([name config]
   {:pre [(s/assert ::name name)
          (s/assert (s/or :name ::name :config ::config) config)]}
   (if (s/valid? ::name config)
     (.bulkhead registry (clojure.core/name name) (clojure.core/name config))
     (.bulkhead registry (clojure.core/name name) (build-config-for-registry registry config)))))

(defn bulkhead
  "Creates a semaphore-based bulkhead with the specified name and config."
  [name config]
  (Bulkhead/of (clojure.core/name name) (build-config config)))

(defn thread-pool-bulkhead
  "Creates a thread-pool-based bulkhead with the specified name and config."
  [name thread-pool-config-map]
  (ThreadPoolBulkhead/of (clojure.core/name name) (build-thread-pool-config thread-pool-config-map)))

;; -----------------------------------------------------------------------------
;; execute

(defn execute
  "Apply args to f within a context protected by the bulkhead.

  The function will wait up to the configuration wait duration if the bulkhead
  has no available concurrent calls remaining. If the function is not allowed to
  execute before the wait duration expires, this function will throw an
  exception."
  [bulkhead f & args]
  (.executeCallable bulkhead #(apply f args)))

(defmacro with-bulkhead
  "Executes body within a context protected by a bulkhead.

  `bulkhead` is either a bulkhead or the name of one in the global registry.
  If you provide a name and a bulkhead of that name does not already exist in
  the global registry, one will be created with the `:default` config.

  The code in `body` will wait up to the configured wait duration if the
  bulkhead as no available concurrent calls remaining. If the function is not
  allowed to execute before the wait duration expires, an exception will be
  thrown."
  [bulkhead & body]
  `(let [bh# (if (s/valid? ::name ~bulkhead)
               (bulkhead! (clojure.core/name ~bulkhead))
               ~bulkhead)]
     (execute bh# (fn [] ~@body))))

;; -----------------------------------------------------------------------------
;; management

(defn change-config!
  "Changes the configuration of the bulkhead.

  A change in `:max-wait-duration` will not affect functions currently waiting
  for permission to execute."
  [^Bulkhead bulkhead config]
  (.changeConfig bulkhead (build-config config)))

;; -----------------------------------------------------------------------------
;; properties

(defn name
  "Gets the name of the bulkhead."
  [^Bulkhead bulkhead]
  (.getName bulkhead))
