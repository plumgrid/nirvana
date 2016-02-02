(include "../pg_utils.clj")

(ns plugins.director-bootup-stage1
  "Director Bootup Stage1"
  (:require [riemann.config :refer :all]
            [clojure.string :as str]
            [riemann.streams :refer :all]))

(defn director-bootup-stage1 []
  (fn [e]
    (def start-state (get start-states-map :director-bootup-stage1-state))
    (def plugin-type "director_bootup_stage1")
    (process-state-ek1-ek2
      start-state [] e
      ; SMLite_0a161b1a [5ea42d3a:1:9]<341:11:24:53.969245>[1]: [SMLite]
      #"\[SMLite\]"
      "SMLite launched"
      "handler"
      "pgtxn"
      (fn [strm event regex-match]
        (assoc strm
          :plugin_type plugin-type
          :ip (convert-hexip-to-dottedip (get-substr-after-last-char (:handler strm) "_"))
          :next_expected "Broker is ready"
          :ttl 10
          )
        )
      )
    (process-state-ek1-ek2
      51 [50] e
      ; SMLite_0a161b1a [5ea42d3a:12:6]<341:11:24:54.000125>[18]: [broker_monitor]: Broker is ready
      #"\[broker_monitor\]: Broker is ready"
      "Broker is ready"
      "handler"
      "pgtxn"
      (fn [strm event regex-match]
        (assoc strm
          :next_expected "SM is ready"
          )
        )
      )
    (process-state-ek1-ek2
      52 [51] e
      ; SMLite_0a161b1a [5ea42d3a:816:9]<341:11:24:55.134114>[26]: [rx_sm_msg_handler]: SM is ready
      #"\[rx_sm_msg_handler\]: SM is ready"
      "SM is ready"
      "handler"
      "pgtxn"
      (fn [strm event regex-match]
        (do
          ; process_pid with service name bridge and pg_name bridge_890xe was killed on node 2.1.1.12 with exit status 0x9. It was internall killed.
          (def concise-msg (apply str (now) ": Broker and SM on director <" (:my_host strm) "> <" (:ip strm) "> got initialized successfully (took "
                             (get-time-taken (:startTime strm) (:time strm)) " seconds)"
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
    (let [concise-msg (apply str (now) ": SMLite initialization failed on <" (:my_host strm) "> <" (:ip strm)
                        ">. Last good known step is '" (:description strm) "'. Next expected step is '"
                        (:next_expected strm) "'.")]
      (file-write error-log-location [concise-msg "\n"])
      )
    )
  )
