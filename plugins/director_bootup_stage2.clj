(include "../pg_utils.clj")

(ns plugins.director-bootup-stage2
  "Director Bootup Stage1"
  (:require [riemann.config :refer :all]
            [clojure.string :as str]
            [riemann.streams :refer :all]))

(defn director-bootup-stage2 []
  (fn [e]
    (def start-state (get start-states-map :director-bootup-stage2-state))
    (def plugin-type "director_bootup_stage2")
    (process-state-ek1-mk2val
      start-state [] e
      ; RPCIF_service_directory [10c044ae:1:6]<013:04:57:37.544513>[1]: EVENT 'service_directory boot' txn_id 526cc5e8 pid 8265
      #"EVENT 'service_directory boot' txn_id .* pid (\d+)"
      "rest_gateway is active"
      "pgtxn"
      plugin-type
      (fn [strm event regex-match]
        (assoc strm
          :plugin_type plugin-type
          :pid (get regex-match 1)
          :ttl 10
          )
        )
      )
    (process-state-ek1-mk2val
      101 [100] e
      ; ServiceDirectory [10c044ae:1:8]<018:16:11:45.004840>[1]: init:  my_station_id_ = 6a0f5151 ip 172.16.1.1}
      #"init:  my_station_id_ = (\S+) ip (\S+).*"
      "service_directory ip"
      "pgtxn"
      plugin-type
      (fn [strm event regex-match]
        (assoc strm
          :ip (remove-from-end (get regex-match 2) "}")
          :next_expected "rest_gateway is active"
          :ttl 10
          )
        )
      )

    (process-state-ek1-mk2val
      102 [101] e
      ; service_directory_2fa5b111 [10c044ae:1:9]<352:05:17:26.955312>[17]: [init]: rest_gateway is active
      #"\[init\]: rest_gateway is active"
      "rest_gateway is active"
      "pgtxn"
      plugin-type
      (fn [strm event regex-match]
        (assoc strm
          :sd_name (:handler event)
          :next_expected "cdb is active"
          :ttl 10
          )
        )
      )

    (process-state-ek1-mk2val
      103 [102] e
      ; service_directory_2fa5b111 [10c044ae:1:9]<352:05:17:30.085074>[50]: [init]: cdb is active
      #"\[init\]: cdb is active"
      "cdb is active"
      "pgtxn"
      plugin-type
      (fn [strm event regex-match]
        (assoc strm
          :next_expected "health_monitor OR tenant_manager OR tunnel_service OR analytics_manager is active"
          )
        )
      )
    (process-state-ek1-mk2val
      104 [103 105 106 107] e
      ; service_directory_2fa5b111 [10c044ae:146:9]<352:05:17:30.525054>[72]: [operator()]: health_monitor is active
      #"\[operator\(\)\]: health_monitor is active"
      "health_monitor is active"
      "pgtxn"
      plugin-type
      (fn [strm event regex-match]
        (let [next_expected_str (str/join " OR " (get-next-expected-operator-service strm "health_monitor"))]
          (assoc strm
            :health_monitor "active"          ; Will be used for knowing about next expected operator service.
            :next_expected (str next_expected_str " is active")
            )
          )
        )
      )
    (process-state-ek1-mk2val
      105 [103 104 106 107] e
      ; service_directory_2fa5b111 [10c044ae:147:9]<352:05:17:32.971946>[75]: [operator()]: tenant_manager is active
      #"\[operator\(\)\]: tenant_manager is active"
      "tenant_manager is active"
      "pgtxn"
      plugin-type
      (fn [strm event regex-match]
        (let [next_expected_str (str/join " OR " (get-next-expected-operator-service strm "tenant_manager"))]
          (assoc strm
            :tenant_manager "active"
            :next_expected (str next_expected_str " is active")
            )
          )
        )
      )
    (process-state-ek1-mk2val
      106 [103 104 105 107] e
      ; service_directory_2fa5b111 [10c044ae:148:9]<352:05:17:32.980794>[76]: [operator()]: tunnel_service is active
      #"\[operator\(\)\]: tunnel_service is active"
      "tunnel_service is active"
      "pgtxn"
      plugin-type
      (fn [strm event regex-match]
        (let [next_expected_str (str/join " OR " (get-next-expected-operator-service strm "tunnel_service"))]
          (assoc strm
            :tunnel_service "active"
            :next_expected (str next_expected_str " is active")
            )
          )
        )
      )
    (process-state-ek1-mk2val
      107 [103 104 105 106] e
      ;service_directory_2fa5b111 [10c044ae:145:9]<352:05:17:38.073647>[83]: [operator()]: analytics_manager is active
      #"\[operator\(\)\]: analytics_manager is active"
      "analytics_manager is active"
      "pgtxn"
      plugin-type
      (fn [strm event regex-match]
        (let [next_expected_str (str/join " OR " (get-next-expected-operator-service strm "analytics_manager"))]
          (assoc strm
            :analytics_manager "active"
            :next_expected (str next_expected_str " is active")
            )
          )
        )
      )
    (process-state-ek1-mk2val
      108 [107] e
      ; service_directory_2fa5b111 [10c044ae:1:9]<352:05:17:38.285355>[107]: [init]: CM is active
      #"\[init\]: CM is active"
      "connectivity_manager is active"
      "pgtxn"
      plugin-type
      (fn [strm event regex-match]
        (assoc strm
          :next_expected "pem_master is active"
          )
        )
      )
    (process-state-ek1-mk2val
      109 [108] e
      ; service_directory_2fa5b111 [10c044ae:1:9]<352:05:17:38.847397>[166]: [init]: pem_master is active
      #"\[init\]: pem_master is active"
      "pem_master is active"
      "pgtxn"
      plugin-type
      (fn [strm event regex-match]
        (assoc strm
          :next_expected "vmw_agent is active"
          )
        )
      )
    (process-state-ek1-mk2val
      110 [109] e
      ; service_directory_2fa5b111 [10c044ae:1:9]<352:05:17:39.162342>[173]: [init]: vmw_agent is active
      #"\[init\]: vmw_agent is active"
      "vmw_agent is active"
      "pgtxn"
      plugin-type
      (fn [strm event regex-match]
        (assoc strm
          :next_expected "metadata is active"
          )
        )
      )
    (process-state-ek1-mk2val
      111 [110] e
      ; service_directory_2fa5b111 [10c044ae:1:9]<352:05:17:39.483390>[207]: [init]: metadata is active
      #"\[init\]: metadata is active"
      "metadata is active"
      "pgtxn"
      plugin-type
      (fn [strm event regex-match]
        (assoc strm
          :next_expected "SD requesting CM to load topology from CDB"
          )
        )
      )
    (process-state-ek1-mk2val
      112 [111] e
      ; service_directory_2fa5b111 [10c044ae:1:8]<352:05:17:39.963777>[210]: [init]: Requesting CM topology load
      #"\[init\]: Requesting CM topology load"
      "SD requesting CM to load topology from CDB"
      "pgtxn"
      plugin-type
      (fn [strm event regex-match]
        (assoc strm
          :next_expected "Setting topology ready status"
          )
        )
      )
    (process-state-ek1-mk2val
      113 [112] e
      ; service_directory_2fa5b111 [10c044ae:1:9]<352:05:17:39.963981>[211]: [init]: Setting topology ready status
      #"\[init\]: Setting topology ready status"
      "CM topology load is complete"
      "pgtxn"
      plugin-type
      (fn [strm event regex-match]
        (assoc strm
          :next_expected "SD INIT completed."
          )
        )
      )
    (process-state-ek1-mk2val
      114 [113] e
      ; service_directory_2fa5b111 [10c044ae:1:8]<352:05:17:40.467427>[224]: [init]: SD INIT Completed my_station_id_ = 2fa5b111
      #"\[init\]: SD INIT Completed my_station_id_ = (\S+)"
      "SD INIT Completed"
      "pgtxn"
      plugin-type
      (fn [strm event regex-match]
        (do
          ; process_pid with service name bridge and pg_name bridge_890xe was killed on node 2.1.1.12 with exit status 0x9. It was internall killed.
          (def concise-msg (apply str (now) ": Service directory (" (:sd_name strm) ") on director <"
                             (:my_host strm) "> <" (:ip strm) "> got initialized successfully (took "
                             (get-time-taken (:startTime strm) (:time strm)) " seconds). All Director services are up and active."
                             )
            )
          )
        (file-write info-log-location [concise-msg "\n"])
        "DELETE"
        )
      )
    )
  )


(defn director-bootup-stage2-expired [& children]
  (fn [strm]
    (let [concise-msg (apply str (now) ": Service directory (" (:sd_name strm) ") on director <"
                        (:my_host strm) "> <" (:ip strm) "> failed to initialize. Last good known state is '" (:description strm)
                        "'. Next expected state is '" (:next_expected strm) "'."
                        )]
      (file-write error-log-location [concise-msg "\n"])
      )
    )
  )
