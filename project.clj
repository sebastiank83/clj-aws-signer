(defproject clj-aws-signer "0.1.0"
  :description "A ring middleware to sign AWS requests."
  :url "https://github.com/sebastiank83/clj-aws-signer"
  :license {:name "MIT License"
            :url "http://opensource.org/licenses/MIT"}

  :dependencies [[org.clojure/clojure "1.8.0"]
                 [com.amazonaws/aws-java-sdk-core "1.10.75"]]
  :profiles {:1.8 {:dependencies [[org.clojure/clojure "1.8.0"]]}
             :1.9 {:dependencies [[org.clojure/clojure "1.9.0"]]}}
  :aliases {"test-all" ["with-profile" "+1.8:+1.9" "do"
                        ["clean"]
                        ["test"]]})
