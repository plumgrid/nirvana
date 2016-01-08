(include "../pg_utils.clj")

(ns plugins.director-exit
  "Director Bootup Stage1"
  (:require [riemann.config :refer :all]
            [clojure.string :as str]
            [riemann.streams :refer :all]))

(defn director-exit []
  (fn [e]
    (def start-state (get start-states-map :director-exit-state))
    (def service "director_exit")
    (process-state-k1-s
      start-state [] e
      ; SMLite_ac100101 [528d50c8:6:9]<352:05:18:28.367610>[28]: [child_exit_monitor]: PID 127 returned raw status 0x100 and exit type=[WIFEXITED: Terminated normally with status 1]
      #"\[child_exit_monitor\]: PID (\d+) returned raw status (\S+) and exit type=\[.*\]"
      "SMLite got child exit signal"
      "handler"
      service
      (fn [strm event regex-match]
        (assoc strm
          :service service
          :pid (get regex-match 1)
          :returned_raw_status (get regex-match 2)
          :exit_type (get regex-match 3)
          :next_expected "Broker exit"
          :ttl 10
          )
        )
      )
    (process-state-k1-s
      151 [150] e
      ; SMLite_ac100101 [528d50c8:6:5]<352:05:18:28.367709>[29]: [child_exit_monitor]: Broker pid 127 exited with status 256
      #"\[child_exit_monitor\]: Broker pid (\d+) exited with status (\d)"
      "Broker exit"
      "handler"
      service
      (fn [strm event regex-match]
        (assoc strm
          :broker_pid (get regex-match 1)
          :broker_exit_status (get regex-match 2)
          :next_expected "SMLite exit"
          )
        )
      )
    (process-state-k1-s
      152 [151] e
      ; SMLite_ac100101 [528d50c8:6:5]<352:05:18:28.367728>[30]: [child_exit_monitor]: SMLite exiting because broker exited
      #"\[child_exit_monitor\]: SMLite exiting because (\S+)"
      "SMLite exit "
      "handler"
      service
      (fn [strm event regex-match]
        (do
          ; process_pid with service name bridge and pg_name bridge_890xe was killed on node 2.1.1.12 with exit status 0x9. It was internall killed.
          (def concise-msg (apply str "WARN::" (:startTime strm) " Director exit. PID:" (strm :pid)
                             " returned_raw_status:" (strm :returned_raw_status)
                             " Broker_pid:" (strm :broker_pid)
                             " broker_exit_status:" (strm :broker_exit_status) " SMLite exited because:" (get regex-match 1) ". It took "
                             (get-time-taken (:startTime strm) (:time strm)) " seconds"
                             )
            )
          )
        (file-write warn-log-location [concise-msg "\n"])
        "DELETE"
        )
      )
    )
  )


(defn director-exit-expired [& children]
  (fn [strm]
    (let [concise-msg (apply str "WARN: Director exit stuck. Last witnessed state was " (strm :description) ".")]
      (file-write warn-log-location [concise-msg "\n"])
      )
    )
  )
