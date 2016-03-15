(include "../pg_utils.clj")

(ns plugins.director-exit
  "Director Bootup Stage1"
  (:require [riemann.config :refer :all]
            [clojure.string :as str]
            [riemann.streams :refer :all]))

(defn director-exit []
  (fn [e]
    (def start-state (get start-states-map :director-exit-state))
    (def plugin-type "director_exit")
    (process-state-ek1-ek2
      start-state [] e
      ; SMLite_ac100101 [528d50c8:6:9]<352:05:18:28.367610>[28]: [child_exit_monitor]: PID 127 returned raw status 0x100 and exit type=[WIFEXITED: Terminated normally with status 1]
      #"\[child_exit_monitor\]: PID (\d+) returned raw status (\S+) and exit type=\[.*\]"
      "SMLite got child exit signal"
      "handler"
      "pgtxn"
      (fn [strm event regex-match]
        (assoc strm
          :plugin_type plugin-type
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
      ; SMLite_ac100101 [528d50c8:6:5]<352:05:18:28.367709>[29]: [child_exit_monitor]: Broker pid 127 exited with status 255
      #"\[child_exit_monitor\]: Broker pid (\d+) exited with status (\d)"
      "Broker exit"
      "handler"
      "pgtxn"
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
      ; SMLite_ac100101 [528d50c8:6:5]<352:05:18:28.367728>[30]: [child_exit_monitor]: SMLite exiting because broker exited
      #"\[child_exit_monitor\]: SMLite exiting because ([A-Za-z\t .]+)"
      "SMLite exit "
      "handler"
      "pgtxn"
      (fn [strm event regex-match]
        (do
          ; process_pid with service name bridge and pg_name bridge_890xe was killed on node 2.1.1.12 with exit status 0x9. It was internall killed.
          (def concise-msg (apply str (now) ": Director <" (:my_host strm) "> <" (:ip strm) "> exited with reason '"
                             (get regex-match 1) "'."
                             )
            )
          )
        (file-write warn-log-location [concise-msg "\n"])
        "DELETE"
        )
      )
    )
  )
