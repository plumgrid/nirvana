(include "../pg_utils.clj")

(ns plugins.edge-bootup
  "Edge Bootup"
  (:require [riemann.config :refer :all]
            [clojure.string :as str]
            [riemann.streams :refer :all]))

(defn edge-bootup []
  (fn [e]
    (def start-state (get start-states-map :edge-bootup-state)) ; 200
    (def plugin-type "edge_bootup")
    (process-state-ek1-ek2
      start-state [] e
      ; SELite_ac100102 [436c9376:1:9]<352:05:17:58.746522>[1]: [SELite]: Constructor
      #"\[SELite\]"
      "SELite launched"
      "handler"
      "pgtxn"
      (fn [strm event regex-match]
        (assoc strm
          :plugin_type plugin-type
          :ip (convert-hexip-to-dottedip (get-substr-after-last-char (:handler strm) "_"))
          :next_expected "station id"
          :ttl 10
          )
        )
      )
    (process-state-ek1-ek2
      201 [200] e
      ; SELite_ac100102 [436c9376:27:8]<352:05:17:58.749142>[9]: [nos_monitor]: my_station_id_ = 29976e41
      #"\[nos_monitor\]: my_station_id_ = (\S+)"
      "station id"
      "handler"
      "pgtxn"
      (fn [strm event regex-match]
        (assoc strm
          :station_id (get regex-match 1)
          :next_expected "Joined NOS"
          )
        )
      )
    (process-state-ek1-ek2
      202 [201] e
      ; SELite_ac100102 [436c9376:27:9]<352:05:17:58.749163>[10]: [nos_monitor]: NOS Join Success; NOS Id=0x1496630
      #"\[nos_monitor\]: NOS Join Success; NOS Id=(\S+)"
      "Joined NOS"
      "handler"
      "pgtxn"
      (fn [strm event regex-match]
        (assoc strm
          :nos_id (get regex-match 1)
          :next_expected "launching SEM"
          )
        )
      )
    (process-state-ek1-ek2
      203 [202] e
      ; SELite_ac100102 [436c9376:1:9]<352:05:17:58.846957>[11]: [launch_sem]: SEM is being launched
      #"\[launch_sem\]: SEM is being launched"
      "launching SEM"
      "handler"
      "pgtxn"
      (fn [strm event regex-match]
        (assoc strm
          :next_expected "launched SEM"
          )
        )
      )
    (process-state-ek1-ek2
      204 [203] e
      ; SELite_ac100102 [436c9376:1:9]<352:05:17:58.847511>[14]: [launch_sem]: se_manager launched, pid :62
      #"\[launch_sem\]: se_manager launched, pid :(\d+)"
      "launched SEM"
      "handler"
      "pgtxn"
      (fn [strm event regex-match]
        (assoc strm
          :sem_pid (get regex-match 1)
          :next_expected "SEM is ready"
          )
        )
      )
    (process-state-ek1-ek2
      205 [204] e
      ; SELite_ac100102 [436c9376:44:9]<352:05:17:58.914915>[21]: [rx_sm_msg_handler]: SEM is ready
      #"\[rx_sm_msg_handler\]: SEM is ready"
      "SEM is ready"
      "handler"
      "pgtxn"
      (fn [strm event regex-match]
        (do
          ; SEM with pid:62 has been launched with station_id=29976e41. SE joined NOS (id=0x1496630).
          (def concise-msg (apply str (now) ": Compute node on <" (:my_host strm) "> <" (:ip strm) "> got initialized successfully (took "
                             (get-time-taken (:startTime strm) (:time strm)) " seconds)."
                             )
            )
          )
        (file-write info-log-location [concise-msg "\n"])
        "DELETE"
        )
      )
    )
  )


(defn edge-bootup-expired [& children]
  (fn [strm]
    (let [concise-msg (apply str (now) ": Initialization of compute node on <" (:my_host strm) "> <" (:ip strm) "> failed. Last good known step is '"
                        (:description strm) "'. Next expected step is '" (:next_expected strm) "'.")]
      (file-write error-log-location [concise-msg "\n"])
      )
    )
  )
