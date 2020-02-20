# resilience4clj-bulkhead

A small Clojure wrapper around the
[resilience4j Bulkhead module](https://resilience4j.readme.io/docs/bulkhead).
Requires Clojure 1.9 or later.

[![clojars badge](https://img.shields.io/clojars/v/tessellator/resilience4clj-bulkhead.svg)](https://clojars.org/tessellator/resilience4clj-bulkhead)
[![cljdoc badge](https://cljdoc.org/badge/tessellator/resilience4clj-bulkhead)](https://cljdoc.org/d/tessellator/resilience4clj-bulkhead/CURRENT)


## Quick Start

The following code defines a function `make-remote-call` that will limit the
number of concurrent calls to an external service using a bulkhead named
`:my-bulkhead` in the global registry. If the bulkhead does not already exist in
the global registry, one is created.

If a call would cause the bulkhead to exceed the maximum number of allowable
concurrent calls, that call will instead wait for permission to execute. If the
call does not receive permission to execute before a timeout period expires, an
exception will be thrown.

```clojure
(ns myproject.some-client
  (:require [clj-http.client :as http]
            [resilience4clj.bulkhead :refer [with-bulkhead]])

(defn make-remote-call []
  (with-bulkhead :my-bulkhead
    (http/get "https://www.example.com")))
```

Refer to the [configuration guide](/doc/01_configuration.md) for more
information on how to configure the global registry as well as individual
bulkheads.

Refer to the [usage guide](/doc/02_usage.md) for more information on how to
use bulkheads.

## License

Copyright © 2020 Thomas C. Taylor and contributors.

Distributed under the Eclipse Public License version 2.0.
