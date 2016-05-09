(include "./start_states_consts.clj")
(ns plugins.director_bootup_stage2
  "director_bootup_stage2"
  (:require [riemann.config :refer :all]
            [clojure.string :as str]
            [riemann.streams :refer :all]))
(defn director_bootup_stage2 []
  (fn [e]
    (process-state-ek1-mk2val
      100 [] e
      #"EVENT 'service_directory boot' txn_id .* pid (\d+)"
      "rest_gateway is active"
      "pgtxn"
      "director_bootup_stage2"
      (fn [strm event regex-match]
        (assoc strm
          :plugin_type "director_bootup_stage2"
          :pid (get regex-match 1)
          :ttl 10
          )
       )
    )
    (process-state-ek1-mk2val
      101 [100] e
      #"init:  my_station_id_ = (\S+) ip (\S+).*"
      "service_directory ip"
      "pgtxn"
      "director_bootup_stage2"
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
      #"\[init\]: rest_gateway is active"
      "rest_gateway is active"
      "pgtxn"
      "director_bootup_stage2"
      (fn [strm event regex-match]
        (assoc strm
          :sd_name (:handler event)
          :next_expected "cdb is active"
          )
       )
    )
    (process-state-ek1-mk2val
      103 [102] e
      #"\[init\]: cdb is active"
      "cdb is active"
      "pgtxn"
      "director_bootup_stage2"
      (fn [strm event regex-match]
        (assoc strm
          :next_expected "health_monitor OR tenant_manager OR tunnel_service OR analytics_manager is active"
          )
       )
    )
    (process-state-ek1-mk2val
      104 [103 105 106 107] e
      #"\[operator\(\)\]: health_monitor is active"
      "health_monitor is active"
      "pgtxn"
      "director_bootup_stage2"
      (fn [strm event regex-match]
        (assoc strm
          :health_monitor "active"
          :next_expected ((str/join " OR " (get-next-expected-operator-service strm "health_monitor")) " is active")
          )
       )
    )
    (process-state-ek1-mk2val
      105 [103 104 106 107] e
      #"\[operator\(\)\]: tenant_manager is active"
      "tenant_manager is active"
      "pgtxn"
      "director_bootup_stage2"
      (fn [strm event regex-match]
        (assoc strm
          :tenant_manager "active"
          :next_expected ((str/join " OR " (get-next-expected-operator-service strm "tenant_manager")) " is active")
          )
       )
    )
    (process-state-ek1-mk2val
      106 [103 104 105 107] e
      #"\[operator\(\)\]: tunnel_service is active"
      "tunnel_service is active"
      "pgtxn"
      "director_bootup_stage2"
      (fn [strm event regex-match]
        (assoc strm
          :tunnel_service "active"
          :next_expected ((str/join " OR " (get-next-expected-operator-service strm "tunnel_service")) " is active")
          )
       )
    )
    (process-state-ek1-mk2val
      107 [103 104 105 106] e
      #"\[operator\(\)\]: analytics_manager is active"
      "analytics_manager is active"
      "pgtxn"
      "director_bootup_stage2"
      (fn [strm event regex-match]
        (assoc strm
          :analytics_manager "active"
          :next_expected ((str/join " OR " (get-next-expected-operator-service strm "analytics_manager")) " is active")
          )
       )
    )
    (process-state-ek1-mk2val
      108 [107] e
      #"\[init\]: CM is active"
      "connectivity_manager is active"
      "pgtxn"
      "director_bootup_stage2"
      (fn [strm event regex-match]
        (assoc strm
          :next_expected "pem_master is active"
          )
       )
    )
    (process-state-ek1-mk2val
      109 [108] e
      #"\[init\]: pem_master is active"
      "pem_master is active"
      "pgtxn"
      "director_bootup_stage2"
      (fn [strm event regex-match]
        (assoc strm
          :next_expected "vmw_agent is active"
          )
       )
    )
    (process-state-ek1-mk2val
      110 [109] e
      #"\[init\]: vmw_agent is active"
      "vmw_agent is active"
      "pgtxn"
      "director_bootup_stage2"
      (fn [strm event regex-match]
        (assoc strm
          :next_expected "metadata is active"
          )
       )
    )
    (process-state-ek1-mk2val
      111 [110] e
      #"\[init\]: metadata is active"
      "metadata is active"
      "pgtxn"
      "director_bootup_stage2"
      (fn [strm event regex-match]
        (assoc strm
          :next_expected "SD requesting CM to load topology from CDB"
          )
       )
    )
    (process-state-ek1-mk2val
      112 [111] e
      #"\[init\]: Requesting CM topology load"
      "SD requesting CM to load topology from CDB"
      "pgtxn"
      "director_bootup_stage2"
      (fn [strm event regex-match]
        (assoc strm
          :next_expected "Setting topology ready status"
          )
       )
    )
    (process-state-ek1-mk2val
      113 [112] e
      #"\[init\]: Setting topology ready status"
      "CM topology load is complete"
      "pgtxn"
      "director_bootup_stage2"
      (fn [strm event regex-match]
        (assoc strm
          :next_expected "SD INIT completed."
          )
       )
    )
    (process-state-ek1-mk2val
      114 [113] e
      #"\[init\]: SD INIT Completed my_station_id_ = (\S+)"
      "SD INIT Completed"
      "pgtxn"
      "director_bootup_stage2"
      (fn [strm event regex-match]
        (do
          (def concise-msg (apply str (now) " : "": Service directory (" (:sd_name strm) ") on director <"(:my_host strm) "> <" (:ip strm) "> got initialized successfully (took "(get-time-taken (:startTime strm) (:time strm)) " seconds). All Director services are up and active."
              )
            )
          )
        (file-write info-log-location [concise-msg "\n"])
        "DELETE"
        )
      )
    )
  )
(defn director_bootup_stage2_expired [& children]
  (fn [strm]
    (let [concise-msg (apply str (now) " : "": Service directory (" (:sd_name strm) ") on director <"(:my_host strm) "> <" (:ip strm) "> failed to initialize. Last good known state is '" (:description strm)"'. Next expected state is '" (:next_expected strm) "'.")]
      (file-write error-log-location [concise-msg "\n"])
      )
    )
  )
