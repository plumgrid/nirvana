(include "./start_states_consts.clj")
(ns plugins.edge_bootup
  "edge_bootup"
  (:require [riemann.config :refer :all]
            [clojure.string :as str]
            [riemann.streams :refer :all]))
(defn edge_bootup []
  (fn [e]
    (process-state-ek1-ek2
      200 [] e
      #"\[SELite\]"
      "SELite launched"
      "pgtxn"
      "handler"
      (fn [strm event regex-match]
        (assoc strm
          :plugin_type "edge_bootup"
          :ip (convert-hexip-to-dottedip (get-substr-after-last-char (:handler strm) "_"))
          :next_expected "station id"
          :ttl 10
          )
       )
    )
    (process-state-ek1-ek2
      201 [200] e
      #"\[nos_monitor\]: my_station_id_ = (\S+)"
      "station id"
      "pgtxn"
      "handler"
      (fn [strm event regex-match]
        (assoc strm
          :station_id (get regex-match 1)
          :next_expected "Joined NOS"
          )
       )
    )
    (process-state-ek1-ek2
      202 [201] e
      #"\[nos_monitor\]: NOS Join Success; NOS Id=(\S+)"
      "Joined NOS"
      "pgtxn"
      "handler"
      (fn [strm event regex-match]
        (assoc strm
          :nos_id (get regex-match 1)
          :next_expected "launching SEM"
          )
       )
    )
    (process-state-ek1-ek2
      203 [202] e
      #"\[launch_sem\]: SEM is being launched"
      "launching SEM"
      "pgtxn"
      "handler"
      (fn [strm event regex-match]
        (assoc strm
          :next_expected "launched SEM"
          )
       )
    )
    (process-state-ek1-ek2
      204 [203] e
      #"\[launch_sem\]: se_manager launched, pid :(\d+)"
      "launched SEM"
      "pgtxn"
      "handler"
      (fn [strm event regex-match]
        (assoc strm
          :sem_pid (get regex-match 1)
          :next_expected "SEM is ready"
          )
       )
    )
    (process-state-ek1-ek2
      205 [204] e
      #"\[rx_sm_msg_handler\]: SEM is ready"
      "SEM is ready"
      "pgtxn"
      "handler"
      (fn [strm event regex-match]
        (do
          (def concise-msg (apply str (now) " : "": Compute node on <" (:my_host strm) "> <" (:ip strm) "> got initialized successfully (took "(get-time-taken (:startTime strm) (:time strm)) " seconds)."
              )
            )
          )
        (file-write info-log-location [concise-msg "\n"])
        "DELETE"
        )
      )
    )
  )
(defn edge_bootup_expired [& children]
  (fn [strm]
    (let [concise-msg (apply str (now) " : "": Initialization of compute node on <" (:my_host strm) "> <" (:ip strm) "> failed. Last good known step is '"(:description strm) "'. Next expected step is '" (:next_expected strm) "'.")]
      (file-write error-log-location [concise-msg "\n"])
      )
    )
  )
