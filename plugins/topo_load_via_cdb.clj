(include "../pg_utils.clj")

(ns plugins.topo-load-via-cdb
  "Topology loaded via cdb"
  (:require [riemann.config :refer :all]
            [clojure.string :as str]
            [riemann.streams :refer :all]))

(defn topo-load-via-cdb []
  (fn [e]
    (def start-state (get start-states-map :topo-load-via-cdb-state)) ; 400
    (def service "topo_load_via_cdb")
;    (process-state-k1-s
;      start-state [] e
;      ; RPCIF_connectivity [1eec8df9:59:6]<361:08:34:42.161083>[9]: EVENT 'Load Domain Demo' txn_id 1eec8df9
;      #"EVENT 'Load Domain (\S+)' txn_id (\S+)"
;      "Load Topology started"
;      "pgtxn"
;      service
;      (fn [strm event regex-match]
;        (assoc strm
;          :host event
;          :service service
;          :domain_name (get regex-match 1)
;          :next_expected "Requst cdb to post config"
;          :ttl 10
;          )
;        )
;      )
    (process-state-k1-s
      start-state [] e
      ; ConnectivityManager [1eec8df9:59:8]<361:08:34:42.161117>[6]: Requesting cdb to post config for :/0/connectivity/domain/Demo
      #"Requesting cdb to post config for :(\S+)"
      "CM request CDB to post config"
      "pgtxn"
      service
      (fn [strm event regex-match]
        (let [domain-name (get-substr-after-last-char (get regex-match 1) "/")]
          (assoc strm
            :host (:host event)
            :service service
            :domain_name domain-name
            :next_expected "domain_update_cb"
            )
          )
        )
      )
    (process-state-k1-s
      401 [400] e
      ; ConnectivityManager [1eec8df9:60:8]<361:08:34:42.162342>[7]: Dom0::domain_update_cb: Method 2 for domain key=Demo
      #"Dom0::domain_update_cb: Method (\d+) for domain key=(\S+)"
      "CM received domain update callback"
      "pgtxn"
      service
      (fn [strm event regex-match]
        ;; Get domain name
        (assoc strm
          :next_expected "request config post"
          )
        )
      )
    (process-state-k1-s
      402 [401] e
      ; cdb_32c51021 [1eec8df9:146:8]<361:08:34:42.273075>[718]: request_config_post: Completed processing config request for /0/connectivity/domain/Demo in 111567 usec with status 0
      #"request_config_post: Completed processing config request for (\S+) in (\d+) usec with status (\d+)"
      "CDB completed post config"
      "pgtxn"
      service
      (fn [strm event regex-match]
        ;; Get domain name
        (assoc strm
          :next_expected "processing config request complete"
          )
        )
      )
    (process-state-k1-s
      403 [402] e
      ; ConnectivityManager [1eec8df9:59:8]<361:08:34:42.273215>[15]: request_config_post returned with status 0
      #"request_config_post returned with status 0"
      "Load Topology completed"
      "pgtxn"
      service
      (fn [strm event regex-match]
        ; VD “Demo” created successfully (took 111 msec) via CDB
        (let [concise-msg (apply str "INFO::" (:startTime strm) " VD '" (:domain_name strm) "' created successfully (took"
                            (get-time-taken (:startTime strm) (:time strm)) " secs) via CDB."
                            )]
          (file-write info-log-location [concise-msg "\n"])
          )
        "DELETE"
        )
      )
    )
  )


(defn topo-load-via-cdb-expired [& children]
  (fn [strm]
    ; VD “Demo” creation failed. Last good known state is “”. Next expected state “” Transaction id: 1eec8df9
    (let [concise-msg (apply str "ERROR::" (:startTime strm) " VD '" (:domain_name strm) "'creation failed."
                        " Last good known state is '" (:description strm) "'. Next expected state is '" (:next_expected strm)
                        ". Transaction id: " (:pgtxn strm) "."
                        )]
      (file-write error-log-location [concise-msg "\n"])
      )
    )
  )
