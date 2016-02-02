(include "../pg_utils.clj")

(ns plugins.edge-reconnect
  "Edge Reconnect"
  (:require [riemann.config :refer :all]
            [clojure.string :as str]
            [riemann.streams :refer :all]))

(defn edge-reconnect []
  (fn [e]
    (def start-state (get start-states-map :edge-reconnect-state)) ; 250
    (def plugin-type "edge_reconnect")
    (process-state-ek1-mk2val
      start-state [] e
      ; SELite_ac100102 [436c9376:27:5]<352:05:18:52.812319>[26]: [nos_monitor]: connection to NOS 0x1496630 failed
      #"\[nos_monitor\]: connection to NOS (\S+) failed"
      "NOS connection failed"
      "handler"
      plugin-type
      (fn [strm event regex-match]
        (assoc strm
          :plugin_type plugin-type
          :ip (convert-hexip-to-dottedip (get-substr-after-last-char (:handler strm) "_"))
          :old_nos_id (get regex-match 1)
          :next_expected "NOS join success"
          :ttl 10
          )
        )
      )
    (process-state-ek1-mk2val
      251 [250] e
      ; SELite_ac100102 [436c9376:27:9]<352:05:18:53.812541>[27]: [nos_monitor]: NOS Join Success; NOS Id=0x51fbdce0
      #"\[nos_monitor\]: NOS Join Success; NOS Id=(\S+)"
      "NOS join success"
      "handler"
      plugin-type
      (fn [strm event regex-match]
        (assoc strm
          :new_nos_id (get regex-match 1)
          :next_expected "Restart PE condition met"
          )
        )
      )
    (process-state-ek1-mk2val
      252 [251] e
      ; SELite_ac100102 [436c9376:27:9]<352:05:18:53.812572>[28]: [nos_monitor]: Restart PE Condition met
      #"\[nos_monitor\]: Restart PE Condition met"
      "Restart PE condition met"
      "handler"
      plugin-type
      (fn [strm event regex-match]
        (assoc strm
          :next_expected "NOS ID changed"
          )
        )
      )
    (process-state-ek1-mk2val
      253 [252] e
      ; SELite_ac100102 [436c9376:27:9]<352:05:18:53.812586>[29]: [nos_monitor]: NOS ID Changed old_id:0x1496630 new_id:0x51fbdce0
      #"\[nos_monitor\]: NOS ID Changed old_id:(\S+) new_id:(\S+)"
      "NOS ID changed"
      "handler"
      plugin-type
      (fn [strm event regex-match]
        (assoc strm
          :next_expected "entering old_nos_id"
          )
        )
      )
    (process-state-ek1-mk2val
      254 [253] e
      ; SELite_ac100102 [436c9376:128:9]<352:05:18:53.812648>[30]: [handle_nos_reconnect]: entering old_nos_id 0x1496630
      #"\[handle_nos_reconnect\]: entering old_nos_id (\S+)"
      "entering old_nos_id"
      "handler"
      plugin-type
      (fn [strm event regex-match]
        (assoc strm
          :next_expected "waiting for NOS readiness"
          )
        )
      )
    (process-state-ek1-mk2val
      255 [254] e
      ; SELite_0a161b33 [6eff7b18:58:8]<014:02:37:58.480876>[30]:  [handle_nos_reconnect] waiting for NOS readiness, iteration no: 0}
      ; SELite_ac100102 [436c9376:128:8]<352:05:18:53.813165>[31]: [handle_nos_reconnect] waiting for NOS readiness, iteration no: 0
      #"\[handle_nos_reconnect\]: waiting for NOS readiness, iteration no: (\d+)"
      "waiting for NOS readiness"
      "handler"
      plugin-type
      (fn [strm event regex-match]
        (assoc strm
          :next_expected "NOS ready, Will start SE re-init sequence."
          )
        )
      )
    (process-state-ek1-mk2val
      256 [255] e
      ; SELite_ac100102 [436c9376:128:8]<352:05:19:07.822513>[45]: [handle_nos_reconnect] NOS ready, Will start SE re-init sequence.
      #"\[handle_nos_reconnect\]: NOS ready, Will start SE re-init sequence."
      "NOS ready, Will start SE re-init sequence."
      "handler"
      plugin-type
      (fn [strm event regex-match]
        (assoc strm
          :next_expected "killing SEM"
          )
        )
      )
    (process-state-ek1-mk2val
      257 [256] e
      ; SELite_ac100102 [436c9376:128:9]<352:05:19:07.822558>[46]: [handle_nos_reconnect] killing SE
      #"\[handle_nos_reconnect\]: killing SE"
      "killing SEM"
      "handler"
      plugin-type
      (fn [strm event regex-match]
        (assoc strm
          :next_expected "Restart SEM"
          )
        )
      )
    (process-state-ek1-mk2val
      258 [257] e
      ; SELite_ac100102 [436c9376:28:9]<352:05:19:07.882655>[56]: [child_exit_monitor]: Restarting se_manager now
      #"\[child_exit_monitor\]: Restarting se_manager now"
      "Restart SEM"
      "handler"
      plugin-type
      (fn [strm event regex-match]
        (assoc strm
          :next_expected "Waiting for previous SEM monitor to join"
          )
        )
      )
    (process-state-ek1-mk2val
      259 [258] e
      ; SELite_ac100102 [436c9376:137:9]<352:05:19:07.882752>[60]: [launch_sem]: waiting for previous SEM monitor to join
      #"\[launch_sem\]: waiting for previous SEM monitor to join"
      "Waiting for previous SEM monitor to join"
      "handler"
      plugin-type
      (fn [strm event regex-match]
        (assoc strm
          :next_expected "launching SEM"
          )
        )
      )
    (process-state-ek1-mk2val
      260 [259] e
      ; SELite_ac100102 [436c9376:137:9]<352:05:19:07.983577>[63]: [launch_sem]: SEM is being launched
      #"\[launch_sem\]: SEM is being launched"
      "launching SEM"
      "handler"
      plugin-type
      (fn [strm event regex-match]
        (assoc strm
          :next_expected "launched SEM"
          )
        )
      )
    (process-state-ek1-mk2val
      261 [260] e
      ; SELite_ac100102 [436c9376:137:9]<352:05:19:07.984260>[66]: [launch_sem]: se_manager launched, pid :239
      #"\[launch_sem\]: se_manager launched, pid :(\d+)"
      "launched SEM"
      "handler"
      plugin-type
      (fn [strm event regex-match]
        (assoc strm
          :sem_pid (get regex-match 1)
          :next_expected "SEM is ready"
          )
        )
      )
    (process-state-ek1-mk2val
      262 [261] e
      ; SELite_ac100102 [436c9376:143:9]<352:05:19:09.258220>[72]: [rx_sm_msg_handler]: SEM is ready
      #"\[rx_sm_msg_handler\]: SEM is ready"
      "SEM is ready"
      "handler"
      plugin-type
      (fn [strm event regex-match]
        (do
          (def concise-msg (apply str (now) ": Compute node on <" (:host strm) "> <" (:ip strm) "> got reconnected to director cluster (took "
                             (get-time-taken (:startTime strm) (:time strm)) " seconds).")
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
    (let [concise-msg (apply str (now) ": Reconnection of compute node on <" (:my_host strm) "> <" (:ip strm) "> to director cluster failed. Last good known step is '"
                        (:description strm) "'. Next expected step is '" (:next_expected strm) "'.")]
      (file-write error-log-location [concise-msg "\n"])
      )
    )
  )
