(include "./start_states_consts.clj")
(ns plugins.director_bootup_stage1
  "director_bootup_stage1"
  (:require [riemann.config :refer :all]
            [clojure.string :as str]
            [riemann.streams :refer :all]))
(defn director_bootup_stage1 []
  (fn [e]
    (process-state-ek1-ek2
      50 [] e
      #"\[SMLite\]"
      "SMLite launched"
      "pgtxn"
      "handler"
      (fn [strm event regex-match]
        (assoc strm
          :plugin_type "director_bootup_stage1"
          :ip (convert-hexip-to-dottedip (get-substr-after-last-char (:handler strm) "_"))
          :next_expected "Broker is ready"
          :ttl 10
          )
       )
    )
    (process-state-ek1-ek2
      51 [50] e
      #"\[broker_monitor\]: Broker is ready"
      "Broker is ready"
      "pgtxn"
      "handler"
      (fn [strm event regex-match]
        (assoc strm
          :next_expected "SM is ready"
          )
       )
    )
    (process-state-ek1-ek2
      52 [51] e
      #"\[rx_sm_msg_handler\]: SM is ready"
      "SM is ready"
      "pgtxn"
      "handler"
      (fn [strm event regex-match]
        (do
          (def concise-msg (apply str (now) " : "": Broker and SM on director <" (:my_host strm) "> <" (:ip strm) "> got initialized successfully (took "(get-time-taken (:startTime strm) (:time strm)) " seconds)"
              )
            )
          )
        (file-write info-log-location [concise-msg "\n"])
        "DELETE"
        )
      )
    )
  )
(defn director_bootup_stage1_expired [& children]
  (fn [strm]
    (let [concise-msg (apply str (now) " : "": SMLite initialization failed on <" (:my_host strm) "> <" (:ip strm) ">. Last good known step is '" (:description strm) "'. Next expected step is '"(:next_expected strm) "'.")]
      (file-write error-log-location [concise-msg "\n"])
      )
    )
  )
