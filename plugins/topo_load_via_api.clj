(include "../pg_utils.clj")

(ns plugins.topo-load-via-api
  "Topology loaded via api"
  (:require [riemann.config :refer :all]
            [clojure.string :as str]
            [riemann.streams :refer :all]))

(defn topo-load-via-api []
  (fn [e]
    (def start-state (get start-states-map :topo-load-via-api-state)) ; 450
    (def plugin-type "topo_load_via_api")
    (process-state-ek1-ek2
      start-state [] e
      ; rest_gateway_bdfd95a1 [31f9c71c:191:8]<362:12:14:49.442556>[92]: PUT /0/connectivity/domain/Demo 0xf8405f0300
      #"PUT (\S+) (\S+)"
      "Load Topology started"
      "handler"
      "pgtxn"
      (fn [strm event regex-match]
        (let [domain-name (get-substr-after-last-char (get regex-match 1) "/")]
          (assoc strm
            :host (:host event)
            :plugin_type plugin-type
            :domain_name domain-name
            :next_expected "CM received domain update callback-1"
            )
          )
        )
      )
    (process-state-ek1-ek2
      451 [450] e
      ; ConnectivityManager [31f9c71c:62:8]<362:12:14:49.444702>[5]: Dom0::domain_update_cb: Method 1 for domain key=Demo
      #"(\S+)::domain_update_cb: Method (\d+) for domain key=(\S+)"
      "CM received domain update callback-1"
      "handler"
      "pgtxn"
      (fn [strm event regex-match]
        (assoc strm
          :next_expected "CM received ne update callback-2"
          )
        )
      )
    (process-state-ek1-ek2
      452 [451] e
      ; ConnectivityManager [31f9c71c:65:8]<362:12:14:49.445850>[6]: Topology::ne_update_cb: method=1 path=/connectivity/domain/Demo/ne key=Bridge-Web type=bridge
      #"Topology::ne_update_cb: method=(\d+) path=(\S+) key=(\S+) type=(\S+)"
      "CM received ne update callback-2"
      "handler"
      "pgtxn"
      (fn [strm event regex-match]
        (assoc strm
          :next_expected "CM received ne update callback-3"
          )
        )
      )
    (process-state-ek1-ek2
      453 [452] e
      ; ConnectivityManager [31f9c71c:66:8]<362:12:14:49.445945>[7]: Topology::ne_update_cb: method=1 path=/connectivity/domain/Demo/ne key=Router type=router
      #"Topology::ne_update_cb: method=(\d+) path=(\S+) key=(\S+) type=(\S+)"
      "CM received ne update callback-3"
      "handler"
      "pgtxn"
      (fn [strm event regex-match]
        (assoc strm
          :next_expected "CM received ne update callback-4"
          )
        )
      )
    (process-state-ek1-ek2
      403 [402] e
      ; ConnectivityManager [1eec8df9:59:8]<361:08:34:42.273215>[15]: request_config_post returned with status 0
      #"request_config_post returned with status 0"
      "Load Topology completed"
      "handler"
      "pgtxn"
      (fn [strm event regex-match]
        ; VD “Demo” created successfully (took 111 msec) via CDB
        (let [concise-msg (apply str "VND '" (:domain_name strm) "' created successfully (took"
                            (get-time-taken (:startTime strm) (:time strm)) " secs) via CDB."
                            )]
          (file-write info-log-location [concise-msg "\n"])
          )
        "DELETE"
        )
      )
    )
  )


(defn topo-load-via-api-expired [& children]
  (fn [strm]
    ; VD “Demo” creation failed. Last good known state is “”. Next expected state “” Transaction id: 1eec8df9
    (let [concise-msg (apply str "VND '" (:domain_name strm) "'creation failed."
                        " Last good known state is '" (:description strm) "'. Next expected state is '" (:next_expected strm)
                        ". Transaction id: " (:pgtxn strm) "."
                        )]
      (file-write error-log-location [concise-msg "\n"])
      )
    )
  )
