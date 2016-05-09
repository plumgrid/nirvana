(include "./start_states_consts.clj")
(ns plugins.edge_reconnect
  "edge_reconnect"
  (:require [riemann.config :refer :all]
            [clojure.string :as str]
            [riemann.streams :refer :all]))
(defn edge_reconnect []
  (fn [e]
    (process-state-ek1-mk2val
      250 [] e
      #"\[nos_monitor\]: connection to NOS (\S+) failed"
      "NOS connection failed"
      "edge_reconnect"
      "handler"
      (fn [strm event regex-match]
        (assoc strm
          :plugin_type "edge_reconnect"
          :ip (convert-hexip-to-dottedip (get-substr-after-last-char (:handler strm) "_"))
          :old_nos_id (get regex-match 1)
          :next_expected "NOS join success"
          :ttl 10
          )
       )
    )
    (process-state-ek1-mk2val
      251 [250] e
      #"\[nos_monitor\]: NOS Join Success; NOS Id=(\S+)"
      "NOS join success"
      "edge_reconnect"
      "handler"
      (fn [strm event regex-match]
        (assoc strm
          :new_nos_id (get regex-match 1)
          :next_expected "Restart PE condition met"
          )
       )
    )
    (process-state-ek1-mk2val
      252 [251] e
      #"\[nos_monitor\]: Restart PE Condition met"
      "Restart PE condition met"
      "edge_reconnect"
      "handler"
      (fn [strm event regex-match]
        (assoc strm
          :next_expected "NOS ID changed"
          )
       )
    )
    (process-state-ek1-mk2val
      253 [252] e
      #"\[nos_monitor\]: NOS ID Changed old_id:(\S+) new_id:(\S+)"
      "NOS ID changed"
      "edge_reconnect"
      "handler"
      (fn [strm event regex-match]
        (assoc strm
          :next_expected "entering old_nos_id"
          )
       )
    )
    (process-state-ek1-mk2val
      254 [253] e
      #"\[handle_nos_reconnect\]: entering old_nos_id (\S+)"
      "entering old_nos_id"
      "edge_reconnect"
      "handler"
      (fn [strm event regex-match]
        (assoc strm
          :next_expected "waiting for NOS readiness"
          )
       )
    )
    (process-state-ek1-mk2val
      255 [254] e
      #"\[handle_nos_reconnect\]: waiting for NOS readiness, iteration no: (\d+)"
      "waiting for NOS readiness"
      "edge_reconnect"
      "handler"
      (fn [strm event regex-match]
        (assoc strm
          :next_expected "NOS ready, Will start SE re-init sequence."
          )
       )
    )
    (process-state-ek1-mk2val
      256 [255] e
      #"\[handle_nos_reconnect\]: NOS ready, Will start SE re-init sequence."
      "NOS ready, Will start SE re-init sequence."
      "edge_reconnect"
      "handler"
      (fn [strm event regex-match]
        (assoc strm
          :next_expected "killing SEM"
          )
       )
    )
    (process-state-ek1-mk2val
      257 [256] e
      #"\[handle_nos_reconnect\]: killing SE"
      "killing SEM"
      "edge_reconnect"
      "handler"
      (fn [strm event regex-match]
        (assoc strm
          :next_expected "Restart SEM"
          )
       )
    )
    (process-state-ek1-mk2val
      258 [257] e
      #"\[child_exit_monitor\]: Restarting se_manager now"
      "Restart SEM"
      "edge_reconnect"
      "handler"
      (fn [strm event regex-match]
        (assoc strm
          :next_expected "Waiting for previous SEM monitor to join"
          )
       )
    )
    (process-state-ek1-mk2val
      259 [258] e
      #"\[launch_sem\]: waiting for previous SEM monitor to join"
      "Waiting for previous SEM monitor to join"
      "edge_reconnect"
      "handler"
      (fn [strm event regex-match]
        (assoc strm
          :next_expected "launching SEM"
          )
       )
    )
    (process-state-ek1-mk2val
      260 [259] e
      #"\[launch_sem\]: SEM is being launched"
      "launching SEM"
      "edge_reconnect"
      "handler"
      (fn [strm event regex-match]
        (assoc strm
          :next_expected "launched SEM"
          )
       )
    )
    (process-state-ek1-mk2val
      261 [260] e
      #"\[launch_sem\]: se_manager launched, pid :(\d+)"
      "launched SEM"
      "edge_reconnect"
      "handler"
      (fn [strm event regex-match]
        (assoc strm
          :sem_pid (get regex-match 1)
          :next_expected "SEM is ready"
          )
       )
    )
    (process-state-ek1-mk2val
      262 [261] e
      #"\[rx_sm_msg_handler\]: SEM is ready"
      "SEM is ready"
      "edge_reconnect"
      "handler"
      (fn [strm event regex-match]
        (do
          (def concise-msg (apply str (now) " : "": Compute node on <" (:host strm) "> <" (:ip strm) "> got reconnected to director cluster (took "(get-time-taken (:startTime strm) (:time strm)) " seconds)."
              )
            )
          )
        (file-write info-log-location [concise-msg "\n"])
        "DELETE"
        )
      )
    )
  )
(defn edge_reconnect_expired [& children]
  (fn [strm]
    (let [concise-msg (apply str (now) " : "": Reconnection of compute node on <" (:my_host strm) "> <" (:ip strm) "> to director cluster failed. Last good known step is '"(:description strm) "'. Next expected step is '" (:next_expected strm) "'.")]
      (file-write error-log-location [concise-msg "\n"])
      )
    )
  )
