(ns clj-aws-signer.core
  (:require [clojure.java.io :as io]
            [clojure.string :as s])
  (:import com.amazonaws.DefaultRequest
           [com.amazonaws.auth
            AWS4Signer
            AWSCredentialsProviderChain
            DefaultAWSCredentialsProviderChain]
           com.amazonaws.http.HttpMethodName
           com.amazonaws.regions.Regions
           java.net.URI
           java.util.HashMap
           org.apache.http.entity.AbstractHttpEntity))

(def credentials-provider
  "Creates a new AWS Credentials provider chain.
  The default chain resolves the credentials in the following order:

  - Environment variables
  - Java system properties
  - Credential profiles
  - Instance profile credentials"
  ^AWSCredentialsProviderChain
  (DefaultAWSCredentialsProviderChain.))

(defn- credentials
  []
  (.getCredentials (credentials-provider)))

(defn- detect-region
  "Tries to detect which region should be used for API calls.
  It resolves the region in the following order:

  - Environment variable
  - Instance metadata service"
  []
  (or (System/getenv "AWS_REGION")
      (.getName (Regions/getCurrentRegion))))

(defn- sign
  "Signs the AWS request object.

  Takes three arguments:

  `req` is the AWS request object to sign.
  `service-name` is the AWS service name that should be used for the signing process.
                 e.g. `es` for elastic search
  `aws-region` the aws region that should be used for the signing process."
  [req service-name aws-region]
  (if-not (s/blank? service-name)
    (doto (AWS4Signer.)
      (.setServiceName service-name)
      (.setRegionName (if (s/blank? aws-region)
                        (detect-region)
                        aws-region))
      (.sign req (credentials)))
    (throw (IllegalArgumentException. "AWS service name missing.")))
  req)

(defn- valid-parameter? [m & ks]
  (every? #(or (% m) (throw (IllegalArgumentException. (str "Missing request parameter: " %)))) ks))

(defn- ring-params->hashmap
  "Takes a parameter map and converts it to a HashMap of type HashMap<String,List<String>>."
  [p]
  (let [map (java.util.HashMap.)]
    (doseq [[k v] p]
      (.put map (str k) (let [list (java.util.ArrayList.)]
                          (doseq [n (flatten [v])]
                            (.add list (str n))) list)))
    map))

(defn- ring->aws-request
  "Builds an AWS request object out of a ring request map."
  [req]
  (valid-parameter? req :scheme :server-name :server-port :headers :request-method :uri)
  (let [{:keys [scheme server-name server-port request-method headers uri body params]} req]
    (doto (DefaultRequest. "")
      (.setEndpoint (URI. (str (name  scheme) "://" server-name (when server-port
                                                                  (str ":" server-port)))))
      (.setHeaders (HashMap. ^HashMap headers))
      (.setHttpMethod (HttpMethodName/valueOf (s/upper-case (name request-method))))
      (.setResourcePath uri)
      ;; The ring spec [1] states that the request body should be of type InputStream.
      ;; However, clj-http.client/post and similar wraps the request bodies in an HttpEntity subclass such as StringEntity.
      ;; [1] https://github.com/ring-clojure/ring/blob/master/SPEC
      (.setContent (when body (if (instance? AbstractHttpEntity body)
                                (.getContent ^AbstractHttpEntity body)
                                body)))
      (.setParameters (ring-params->hashmap params))
      (.addHeader "presigned-expires" "false"))))

(defn wrap-sign-aws-request
  "Middleware that signs a ring http request with AWS credentials.

  Takes three arguments:

  `handler` A ring request handler.
  `service-name` is the AWS service name that will be used for the signing process.
                 e.g. `es` for elastic search.
  `aws-region` the aws region the service is running in. This is an optional argument."
  ([handler service-name]
   (wrap-sign-aws-request handler service-name nil))
  ([handler service-name aws-region]
   (fn [req]
     (handler (assoc-in req [:headers] (into {} (.getHeaders ^DefaultRequest (sign (ring->aws-request req) service-name aws-region))))))))
