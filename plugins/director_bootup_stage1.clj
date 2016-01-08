(include "../pg_utils.clj")

(ns plugins.director-bootup-stage1
  "Director Bootup Stage1"
  (:require [riemann.config :refer :all]
            [clojure.string :as str]
            [riemann.streams :refer :all]))

(defn director-bootup-stage1 []
  (fn [e]
    (def start-state (get start-states-map :director-bootup-stage1-state))
    (def service "director_bootup_stage1")
    (process-state-k1-s
      start-state [] e
      ; SMLite_0a161b1a [5ea42d3a:1:9]<341:11:24:53.969245>[1]: [SMLite]
      #"\[SMLite\]"
      "SMLite launched"
      "handler"
      service
      (fn [strm event regex-match]
        (assoc strm
          :service service
          :ttl 10
          :next_expected "Broker is ready"
          )
        )
      )
    (process-state-k1-s
      51 [50] e
      ; SMLite_0a161b1a [5ea42d3a:12:6]<341:11:24:54.000125>[18]: [broker_monitor]: Broker is ready
      #"\[broker_monitor\]: Broker is ready"
      "Broker is ready"
      "handler"
      service
      (fn [strm event regex-match]
        (assoc strm
          :next_expected "SM is ready"
          )
        )
      )
    (process-state-k1-s
      52 [51] e
      ; SMLite_0a161b1a [5ea42d3a:816:9]<341:11:24:55.134114>[26]: [rx_sm_msg_handler]: SM is ready
      #"\[rx_sm_msg_handler\]: SM is ready"
      "SM is ready"
      "handler"
      service
      (fn [strm event regex-match]
        (do
          ; process_pid with service name bridge and pg_name bridge_890xe was killed on node 2.1.1.12 with exit status 0x9. It was internall killed.
          (def concise-msg (apply str "INFO::" (:startTime strm) " Broker and SM are ready. It took them "
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


(defn director-bootup-stage1-expired [& children]
  (fn [strm]
    (let [concise-msg (apply str "INFO: Timeout or some other failure. Director bootup is failed")]
      (file-write error-log-location [concise-msg "\n"])
      )
    )
  )
