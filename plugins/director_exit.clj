(include "./start_states_consts.clj")
(ns plugins.director_exit
  "director_exit"
  (:require [riemann.config :refer :all]
            [clojure.string :as str]
            [riemann.streams :refer :all]))
(defn director_exit []
  (fn [e]
    (process-state-ek1-ek2
      150 [] e
      #"\[child_exit_monitor\]: PID (\d+) returned raw status (\S+) and exit type=\[.*\]"
      "SMLite got child exit signal"
      "pgtxn"
      "handler"
      (fn [strm event regex-match]
        (assoc strm
          :plugin_type "director_exit"
          :ip (convert-hexip-to-dottedip (get-substr-after-last-char (:handler strm) "_"))
          :pid (get regex-match 1)
          :returned_raw_status (get regex-match 2)
          :exit_type (get regex-match 3)
          :next_expected "Broker exit"
          :ttl 10
          )
       )
    )
    (process-state-ek1-ek2
      151 [150] e
      #"\[child_exit_monitor\]: Broker pid (\d+) exited with status (\d)"
      "Broker exit"
      "pgtxn"
      "handler"
      (fn [strm event regex-match]
        (assoc strm
          :broker_pid (get regex-match 1)
          :broker_exit_status (get regex-match 2)
          :next_expected "SMLite exit"
          )
       )
    )
    (process-state-ek1-ek2
      152 [151] e
      #"\[child_exit_monitor\]: SMLite exiting because ([A-Za-z\t .]+)"
      "SMLite exit"
      "pgtxn"
      "handler"
      (fn [strm event regex-match]
        (do
          (def concise-msg (apply str (now) " : "": Director <" (:my_host strm) "> <" (:ip strm) "> exited with reason '"(get regex-match 1) "'."
              )
            )
          )
        (file-write warn-log-location [concise-msg "\n"])
        "DELETE"
        )
      )
    )
  )
(defn director_exit_expired [& children]
  (fn [strm]
    (let [concise-msg (apply str (now) " : ""Director exit got stuck")]
      (file-write error-log-location [concise-msg "\n"])
      )
    )
  )
