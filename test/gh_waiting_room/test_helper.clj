(ns gh-waiting-room.test-helper)

(defn fail-request!
  [{:keys [uri server-name server-port query-string scheme]}]
  (throw (ex-info (format "Unexpected request %s. Web requests are not allowed in this test."
                          (str (name scheme) "://" server-name
                               (when server-port (str ":" server-port))
                               uri
                               (when query-string (str "?" query-string))))
                  {})))

(defmacro disallow-web-requests!
  [& body]
  `(with-redefs [clj-http.core/request fail-request!]
     ~@body))

