## 1. Configuration

### Project Dependencies

resilience4clj-bulkhead is distributed through
[Clojars](https://clojars.org) with the identifier
`tessellator/resilience4clj-bulkhead`. You can find the version
information for the latest release at
https://clojars.org/tessellator/resilience4clj-bulkhead.


### Global Registry

This library creates a single global `registry`. The registry may contain
`config` values as well as bulkhead instances.

resilience4j (and thus library) supports bulkheads built on one of two
underlying implementations: semaphores and thread pools. The default registry
uses a semaphore-based bulkhead, but you can change it to one based on a thread
pool by calling `thread-pool-registry` and passing the result to
`configure-registry!`. A bulkhead registry can be created with a map of
name/config vlaue pairs. When a bulkhead is created using the register, it may
refer to one of these names to use the associated config. Note that the name
`:default` (or `"default"`) is special in that bulkheads that are created
without providing or naming a config will use this default config.

The function `bulkhead!` will look up or create a bulkhead in the global
registry. The function accepts a national and optionally the name of a config or
a config map.

```clojure
(ns myproject.core
  (:require [resilience4clj.bulkhead :as bh])

;; The following creates a semaphore-based registry with two configs and uses
;; it as the global registry. The two configs are the default config and the
;; MoreAllowances config. The default config uses only the defaults and will be
;; used to create bulkheads that do not specify a config to use.
(bh/configure-registry!
  (bh/semaphore-registry {"default"        {}
                          "MoreAllowances" {:max-concurrent-calls 50}}))

;; create a bulkhead named :name using the "default" config from the registry
;; and store the result in the registry
(bh/bulkhead! :name)

;; create a bulkhead named :more-allowed using the "MoreAllowances" config
;; from the registry and store the result in the registry
(bh/bulkhead! :more-allowed "MoreAllowances")

;; create a bulkhead named :custom-config using a custom config map
;; and store the result in the registry
(bh/bulkhead! :custom-config {:max-wait-duration 1000})
```

### Configuration Options

The configurations for bulkheads differ for each type. The following sections
describe the configuration options for each type.

#### Semaphore Bulkhead Configuration

The following table describes the options available when configuring semaphore-
based bulkheads as well as default values. A `config` is a map that contains any
of the keys in the table. Note that a `config` without a particular key will use
the default value (e.g., `{}` selects all default values).

| Configuration Option    | Default Value | Description                                                  |
|-------------------------|---------------|--------------------------------------------------------------|
| `:max-concurrent-calls` |            25 | The maximum number of calls that can occur concurrently      |
| `:max-wait-duration`    |             0 | The number of milliseconds to wait for permission to execute |

A `config` can be used to configure the global registry or a single bulkhead
when it is created with the `bulkhead!` or `bulkhead` function.

#### Thread-Pool Bulkhead Configuration

The following table describes the options available when configuring bulkheads
based on thread pools as well as default values. A `config` is a map that
contains any of the keys in the table. Note that a `config` without a particular
key will use the default value (e.g., `{}` selects all default values).

| Configuration Option     | Default Value                | Description                                                                                |
|--------------------------|------------------------------|--------------------------------------------------------------------------------------------|
| `:max-thread-pool-size`  |       # available processors | The maximum number threads in the pool                                                     |
| `:core-thread-pool-size` | (# available processors) - 1 | The core number of threads in the pool                                                     |
| `:queue-capacity`        |                          100 | The capacity of the queue                                                                  |
| `:keep-alive-duration`   |                           20 | The number of milliseconds for excess threads (beyond the core) to idle before terminating |

A `config` can be used to configure the global registry or a single bulkhead
when it is created with the `bulkhead!` or `thread-pool-bulkhead` function.


### Custom Bulkheads

While convenient, it is not required to use the global registry. You may instead
choose to create bulkheads and manage them yourself.

In order to create a bulkhead that is outsie the global registry, use the
`bulkhead` or `thread-pool-bulkhead` function, each of which accepts a name and
config map appropriate for the type.

The following code creates a new bulkhead with the default config options.

```clojure
(ns myproject.core
  (:require [resilience4clj.bulkhead :as bh]))

(def my-bulkhead (bh/bulkhead :my-bulkhead {}))
```
