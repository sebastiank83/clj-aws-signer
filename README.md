# clj-aws-signer
A ring middleware to sign AWS requests.

## Usage
To use `clj-aws-signer`, add this project as a dependency in your leiningen project file:

`[clj-aws-signer "0.1.0-SNAPSHOT"]`

```clojure
(wrap-sign-aws-request handler service-name region)
```
The arguments are:
* `handler`: A ring handler that will be used.
* `service-name`: The AWS service name that should be used for the signing process.
                  e.g. `es` for `elastic search`.
* `region`: An optional aws region name. If not provided, it will try to detect it through either an environment variable `AWS_REGION` or when running on EC2 through the meta data service.

### Examples
Sign a clj-http request:
```clojure
(ns my.app
  (:require
    [clj-http.client :as client]
    [clj-aws-signer.core :refer [wrap-sign-aws-request]]))

(client/with-additional-middleware [#(wrap-sign-aws-request %1 "<SERVICE NAME>")]
  (client/get "http://........")
)
```

Sign a request made with the `elastisch` library:
```clojure
...
(client/with-additional-middleware [#(wrap-sign-aws-request %1 "es")]
  (esd/search conn "myapp_development" "person" :query (q/term :biography "New York"))
)
...
```

## License
Copyright © 2016 Sebastian Kichtan

Distributed under the [MIT License](http://opensource.org/licenses/MIT).
