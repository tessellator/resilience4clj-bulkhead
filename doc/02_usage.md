### 2. Usage

### Executing Code Protected by a Bulkhead

There are two ways to execute code to be protected by a bulkhead: `execute` and
`with-bulkhead`.

`execute` executes a single function within the context of the bulkhead and
applies any args to it. If the bulkhead cannot immediately service the call due
to the number of concurrent executions, it will cause the call to instead wait
for permission to run within a timeout period. If the bulkhead cannot service
the call within the timeout period, it will throw an exception.

```clojure
> (require '[resilience4clj.bulkhead :as bh])
;; => nil

> (bh/execute (bh/bulkhead! :my-bulkhead) map inc [1 2 3])
;; => (2 3 4) if :my-bulkhead is able to service the call
;;    OR
;;    throws an exception if the bulkhead cannot service the call within a
;;    timeout period
```

`execute` is rather low-level. To make execution more convenient, this library
also includes a `with-bulkhead` macro that executes several forms within a
context protected by the bulkhead. When you use the macro, you must either
provide a bulkhead or the name of one in the global registry. If you provide a
name and a bulkhead of that name does not already exist in the global registry,
one is created with the `:default` config.

```clojure
> (require '[resilience4clj.bulkhead :refer [with-bulkhead]])
;; => nil

> (with-bulkhead :my-bulkhead
    (http/get "https://www.example.com")
    ;; other code here
  )
;; => some value if :my-bulkhead is able to service the call
;;    OR
;;    throws an exception of :my-bulkhead is unable to service the call within a
;;    timeout period
```
