(ns devops-web.service
  (:require [io.pedestal.http :as http]
            [clj-time.core :as t]
            [postal.core :as postal]
            [io.pedestal.http.route :as route]
            [io.pedestal.http.ring-middlewares :as middleware]
            [io.pedestal.http.body-params :as body-params]
            [ring.util.response :as ring-resp]
            [io.pedestal.http.body-params :as body-params]
            [ring.util.request :as request]
            [clojure.java.io :as io]
            [clojure.pprint :as pprint]))


(defn wrap-dir-index [handler]
  (fn [req]
    (handler
      (update-in req [:uri]
         #(if (= "/" %) "/index.html" %)))))

(def email-template
  "<html>
    <body>
      <p>Name: %s</p>
      <p>Company Name: %s</p>
      <p>Email: %s</p>
      <p>Phone: %s</p>
      <p>Role in Company: %s</p>
      <p>Message: %s</p>
    </body>
  </html>")

(defn create-mail [data]
  (format email-template
     (get data "name")
     (get data "company-name")
     (get data "email")
     (get data "phone")
     (get data "role")
     (get data "message")))

(def mail-recipient "test@domain.com")
(def mail-subject "Project contact form")
(def dump-path "/tmp")

(defn data-process
  [{:keys [headers params json-params path-params] :as request}]
  (let [spam-check (get-in request [:params "check"])]
    (println "check" spam-check)
    (if (not= spam-check "")
      {:status 200
       :body   "Internal Server Error. Try again later."}
      (let [data      (get-in request [:params])
            dump-file (io/file dump-path (str (java.util.Date.) ".txt"))
            sender    (get data "email")
            mail-body (create-mail data)
            check     (get data "check")]
        ;; Dumping contact data to text file.
        (spit dump-file data)

        ;; Sending mail.
        (try
          (let [mail-send (postal/send-message {:from    "contact-form@project.com"
                                                :to      mail-recipient
                                                :reply-to sender
                                                :subject mail-subject
                                                :body    [{:type    "text/html"
                                                           :content mail-body}]})]
            (if (= (:error mail-send) :SUCCESS)
              {:status 200
               :body   "Mail successfully sent."}
              (do
                (println (format "Failed to send mail: %s, %s"
                           (:error mail-send)
                           (:message mail-send)))
                {:status 500
                 :body   "Failed to send mail."})))
          (catch Exception e
            (do
              (println (format "Caught exception while sending mail: %s"
                         (.getMessage e)))
              {:status 500
               :body   "Failed to send mail."}))))))





  #_{:status 200
     :body   "Data successfully sent"})


;; Defines "/" and "/about" routes with their associated :get handlers.
;; The interceptors defined after the verb map (e.g., {:get home-page}
;; apply to / and its children (/about).
(def common-interceptors [(middleware/multipart-params) http/html-body])

;; Tabular routes
#_(def routes #{["/submit" :post (conj common-interceptors `data-process)]
                ["/about" :get (conj common-interceptors `about-page)]})

#_(def routes #{["/submit" :post `data-process]})

;; Map-based routes
;(def routes `{"/" {:interceptors [(body-params/body-params) http/html-body]
;                   :get home-page
;                   "/about" {:get about-page}}})

;; Terse/Vector-based routes
(def routes
  `[[["/submit" {:post data-process}
      ^:interceptors [(middleware/multipart-params)]]]])



;; Consumed by devops-web.server/create-server
;; See http/default-interceptors for additional options you can configure
(def service {:env                     :prod
              ;; You can bring your own non-default interceptors. Make
              ;; sure you include routing and set it up right for
              ;; dev-mode. If you do, many other keys for configuring
              ;; default interceptors will be ignored.
              ;; ::http/interceptors []
              ::http/routes            routes

              ;; Uncomment next line to enable CORS support, add
              ;; string(s) specifying scheme, host and port for
              ;; allowed source(s):
              ;;
              ;; "http://localhost:8080"
              ;;
              ;;::http/allowed-origins ["http://localhost:3000"]

              ;; Tune the Secure Headers
              ;; and specifically the Content Security Policy appropriate to your service/application
              ;; For more information, see: https://content-security-policy.com/
              ;;   See also: https://github.com/pedestal/pedestal/issues/499
              ::http/secure-headers    {:content-security-policy-settings {:default-src "* 'unsafe-inline' data:"}}
              ;; :script-src "'unsafe-inline' 'unsafe-eval' 'strict-dynamic' https: http:"
              ;; :frame-ancestors "'none'"}}


              ;; Root for resource interceptor that is available by default.
              ::http/resource-path     "/public"

              ;; Either :jetty, :immutant or :tomcat (see comments in project.clj)
              ;;  This can also be your own chain provider/server-fn -- http://pedestal.io/reference/architecture-overview#_chain_provider
              ::http/type              :jetty
              ;;::http/host "localhost"
              ::http/port              8080
              ;; Options to pass to the container (Jetty)
              ::http/container-options {:h2c? true
                                        :h2?  false
                                        ;:keystore "test/hp/keystore.jks"
                                        ;:key-password "password"
                                        ;:ssl-port 8443
                                        :ssl? false}})

