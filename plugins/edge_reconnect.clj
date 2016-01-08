(include "../pg_utils.clj")

(ns plugins.edge-reconnect
  "Edge Reconnect"
  (:require [riemann.config :refer :all]
            [clojure.string :as str]
            [riemann.streams :refer :all]))

(defn edge-reconnect []
  (fn [e]
    (def start-state (get start-states-map :edge-reconnect-state)) ; 250
    (def service "edge_reconnect")
    (process-state-k1-s
      start-state [] e
      ; SELite_ac100102 [436c9376:27:5]<352:05:18:52.812319>[26]: [nos_monitor]: connection to NOS 0x1496630 failed
      #"\[nos_monitor\]: connection to NOS (\S+) failed"
      "NOS connection failed"
      "handler"
      service
      (fn [strm event regex-match]
        (assoc strm
          :service service
          :old_nos_id (get regex-match 1)
          :next_expected "NOS join success"
          :ttl 10
          )
        )
      )
    (process-state-k1-s
      251 [250] e
      ; SELite_ac100102 [436c9376:27:9]<352:05:18:53.812541>[27]: [nos_monitor]: NOS Join Success; NOS Id=0x51fbdce0
      #"\[nos_monitor\]: NOS Join Success; NOS Id=(\S+)"
      "NOS join success"
      "handler"
      service
      (fn [strm event regex-match]
        (assoc strm
          :new_nos_id (get regex-match 1)
          :next_expected "Restart PE condition met"
          )
        )
      )
    (process-state-k1-s
      252 [251] e
      ; SELite_ac100102 [436c9376:27:9]<352:05:18:53.812572>[28]: [nos_monitor]: Restart PE Condition met
      #"\[nos_monitor\]: Restart PE Condition met"
      "Restart PE condition met"
      "handler"
      service
      (fn [strm event regex-match]
        (assoc strm
          :next_expected "NOS ID changed"
          )
        )
      )
    (process-state-k1-s
      253 [252] e
      ; SELite_ac100102 [436c9376:27:9]<352:05:18:53.812586>[29]: [nos_monitor]: NOS ID Changed old_id:0x1496630 new_id:0x51fbdce0
      #"\[nos_monitor\]: NOS ID Changed old_id:(\S+) new_id:(\S+)"
      "NOS ID changed"
      "handler"
      service
      (fn [strm event regex-match]
        (assoc strm
          :next_expected "entering old_nos_id"
          )
        )
      )
    (process-state-k1-s
      254 [253] e
      ; SELite_ac100102 [436c9376:128:9]<352:05:18:53.812648>[30]: [handle_nos_reconnect]: entering old_nos_id 0x1496630
      #"\[handle_nos_reconnect\]: entering old_nos_id (\S+)"
      "entering old_nos_id"
      "handler"
      service
      (fn [strm event regex-match]
        (assoc strm
          :next_expected "waiting for NOS readiness"
          )
        )
      )
    (process-state-k1-s
      255 [254] e
      ; SELite_ac100102 [436c9376:128:8]<352:05:18:53.813165>[31]: [handle_nos_reconnect] waiting for NOS readiness, iteration no: 0
      #"\[handle_nos_reconnect\]: waiting for NOS readiness, iteration no: (\d+)"
      "waiting for NOS readiness"
      "handler"
      service
      (fn [strm event regex-match]
        (assoc strm
          :next_expected "NOS ready, Will start SE re-init sequence."
          )
        )
      )
    (process-state-k1-s
      256 [255] e
      ; SELite_ac100102 [436c9376:128:8]<352:05:19:07.822513>[45]: [handle_nos_reconnect] NOS ready, Will start SE re-init sequence.
      #"\[handle_nos_reconnect\]: NOS ready, Will start SE re-init sequence."
      "NOS ready, Will start SE re-init sequence."
      "handler"
      service
      (fn [strm event regex-match]
        (assoc strm
          :next_expected "killing SEM"
          )
        )
      )
    (process-state-k1-s
      257 [256] e
      ; SELite_ac100102 [436c9376:128:9]<352:05:19:07.822558>[46]: [handle_nos_reconnect] killing SE
      #"\[handle_nos_reconnect\]: killing SE"
      "killing SEM"
      "handler"
      service
      (fn [strm event regex-match]
        (assoc strm
          :next_expected "Restart SEM"
          )
        )
      )
    (process-state-k1-s
      258 [257] e
      ; SELite_ac100102 [436c9376:28:9]<352:05:19:07.882655>[56]: [child_exit_monitor]: Restarting se_manager now
      #"\[child_exit_monitor\]: Restarting se_manager now"
      "Restart SEM"
      "handler"
      service
      (fn [strm event regex-match]
        (assoc strm
          :next_expected "Waiting for previous SEM monitor to join"
          )
        )
      )
    (process-state-k1-s
      259 [258] e
      ; SELite_ac100102 [436c9376:137:9]<352:05:19:07.882752>[60]: [launch_sem]: waiting for previous SEM monitor to join
      #"\[launch_sem\]: waiting for previous SEM monitor to join"
      "Waiting for previous SEM monitor to join"
      "handler"
      service
      (fn [strm event regex-match]
        (assoc strm
          :next_expected "launching SEM"
          )
        )
      )
    (process-state-k1-s
      260 [259] e
      ; SELite_ac100102 [436c9376:137:9]<352:05:19:07.983577>[63]: [launch_sem]: SEM is being launched
      #"\[launch_sem\]: SEM is being launched"
      "launching SEM"
      "handler"
      service
      (fn [strm event regex-match]
        (assoc strm
          :next_expected "launched SEM"
          )
        )
      )
    (process-state-k1-s
      261 [260] e
      ; SELite_ac100102 [436c9376:137:9]<352:05:19:07.984260>[66]: [launch_sem]: se_manager launched, pid :239
      #"\[launch_sem\]: se_manager launched, pid :(\d+)"
      "launched SEM"
      "handler"
      service
      (fn [strm event regex-match]
        (assoc strm
          :sem_pid (get regex-match 1)
          :next_expected "SEM is ready"
          )
        )
      )
    (process-state-k1-s
      262 [261] e
      ; SELite_ac100102 [436c9376:143:9]<352:05:19:09.258220>[72]: [rx_sm_msg_handler]: SEM is ready
      #"\[rx_sm_msg_handler\]: SEM is ready"
      "SEM is ready"
      "handler"
      service
      (fn [strm event regex-match]
        (do
          (def concise-msg (apply str "INFO::" (:startTime strm) "  Connection to NOS (id:" (:old_nos_id strm) ") was failed. "
                             "New connection has been established with NOS (id:" (:new_nos_id strm)
                             "). SEM is ready with pid:" (:sem_pid strm) ". It took "
                             (get-time-taken (:startTime strm) (:time strm)) " seconds"
                             )
            )
          )
        (file-write info-log-location [concise-msg "\n"])
        "DELETE"
        )
      )
    )
  )

(defn edge-reconnect-expired [& children]
  (fn [strm]
    (let [concise-msg (apply str "INFO: Timeout or some other failure. Edge reconnect is failed")]
      (file-write error-log-location [concise-msg "\n"])
      )
    )
  )
