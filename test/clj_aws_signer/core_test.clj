(ns clj-aws-signer.core-test
  (:require [clojure.test :refer :all]
            [clj-aws-signer.core :refer :all]
            [clojure.string :as s])
  (:import [com.amazonaws.auth
            BasicAWSCredentials]))

(def test-request {:request-method "post"
                   :scheme "http"
                   :server-name "example.com"
                   :server-port 80
                   :uri "/a/b/c"
                   :headers {"a" "1", "b" "2"}
                   :params {"a" "1", 1 "2", 3 [1234 "a" 4], "4" 4}
                   :body (java.io.ByteArrayInputStream. (.getBytes "TEST BODY"))})

(def test-request-clj-http (assoc test-request :body (org.apache.http.entity.StringEntity. "TEST BODY StringEntity")))

(def signing-date
  (.format (doto (java.text.SimpleDateFormat. "yyyyMMdd")
            (.setTimeZone (java.util.TimeZone/getTimeZone "UTC"))) (java.util.Date.)))

(deftest test-aws-signer
  (with-redefs [clj-aws-signer.core/credentials (fn [] (BasicAWSCredentials. "AWS_TEST_KEY" "AWS_TEST_SECRET_KEY"))]
    (let [handler (wrap-sign-aws-request identity "TST" "test-region")]
      (testing "request signing"
        (let [request test-request
              response (handler request)]
          (is (s/includes? (get-in response [:headers "Authorization"]) (str "AWS_TEST_KEY/" signing-date "/test-region/TST/aws4_request")))
          (is (not-empty (get-in response [:headers "X-Amz-Date"])))
          (is (= "false" (get-in response [:headers "presigned-expires"])))
          (is (= "1" (get-in response [:headers "a"])))))

      (testing "missing request parameters"
        (are [x y] (thrown-with-msg? IllegalArgumentException (re-pattern (str "Missing request parameter: " x)) (handler y))
          ":server-name" {:request-method "post", :scheme "https"}
          ":server-port" {:server-name "example.com", :scheme "http"}
          ":scheme" {:scheme nil}
          ":scheme" nil))

      (testing "empty request body"
        (is (not-empty (get-in (handler (update-in test-request [:headers] dissoc :body)) [:headers "Authorization"]))))

      (testing "without query parameters"
        (is (not-empty (get-in (handler (dissoc test-request :params)) [:headers "Authorization"]))))

      (testing "sign clj-http request"
        (is (= (:body test-request-clj-http) (:body (handler test-request-clj-http))))))

    (let [handler (wrap-sign-aws-request identity "")]
      (testing "missing service name"
        (is (thrown-with-msg? IllegalArgumentException #"AWS service name missing." (handler test-request)))))))
