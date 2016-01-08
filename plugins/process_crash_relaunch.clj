(include "../pg_utils.clj")
(include "./start_states_consts.clj")

(ns plugins.process-crash-relaunch
  "process crash handlers"
  (:require [riemann.config :refer :all]
            [clojure.string :as str]
            [riemann.streams :refer :all]))

(defn process-crash-relaunch []
  (fn [e]
    ;(prn "**** process-crash-relaunch-start-state *****" (get start-states-map "crash_count"))
    (def start-state (get start-states-map :process-crash-relaunch-start-state))
    (def service "process_crash_relaunch")
    (process-state-k1-s
      start-state [] e
      ; [process_child_exit]: ippid 10.10.0.230:28689 Exited service pem_master pgname pem_master_b2144351
      #"\[process_child_exit\]: ippid (\S+):(\d+) Exited service (\S+) pgname (\S+).*"
      "Exited service message."
      "pgtxn"
      service
      (fn [strm event regex-match]
        (assoc strm
          :host (:pgtxn event)
          :service service
          :ip (get regex-match 1)
          :pid (get regex-match 2)
          :exited_service (get regex-match 3)
          :pgname (get regex-match 4)
          :ttl 10
          )
        )
      )
    (process-state-k1-s
      2001 [2000] e
      ; [process_child_exit]: SIGCHILD child 28689 returned raw status 0x200 and exit type=[WIFEXITED: Terminated normally with status 2]
      #"\[process_child_exit\]: SIGCHILD child (\d+) returned raw status (\S+).*"
      "Returned raw status of exit service message."
      "pgtxn"
      service
      (fn [strm event regex-match]
        (assoc strm
          :host (:pgtxn event)
          :raw_status (get regex-match 2)
          )
        )
      )
    (process-state-k1-s
      2002 [2001] e
      ; [process_child_exit]: Program /opt/pg/bin/pem_master/9.42.0/launch_pem_master PID 28689 has exited with status = 512
      #"\[process_child_exit\]: Program (\S+) PID (\d+) has exited with status = (\d+)"
      "Exited process pgname"
      "pgtxn"
      service
      (fn [strm event regex-match]
        (assoc strm
          :host (:pgtxn event) ; TODO: I guess we can remove it. Its already part of earlier indexed stream.
          :version (get (str/split (get regex-match 1) #"/") 5)
          :exit_status (get regex-match 3)
          )
        )
      )
    (process-state-k1-s
      2003 [2002] e
      ; [report_process_failure]: pgname pem_master_1339cb51 fail_from_exit 1 is_crash 1 sm_exit 0 ip_pid 10.10.0.230:5154
      #"\[report_process_failure\]: pgname (\S+) fail_from_exit"
      "HM report failures"
      "pgtxn"
      service
      )
    (process-state-k1-s
      2004 [2003] e
      ; [report_process_failure]: health_status_set_dead for pem_master_1339cb51 DONE
      #"\[report_process_failure\]: health_status_set_dead for (\S+) DONE"
      "HM set status as dead"
      "pgtxn"
      service
      )
    (process-state-k1-s
      2005 [2004] e
      ; [report_process_failure]: informed of service pem_master_b2144351 failure reason 2
      #"\[report_process_failure\]: informed of service (\S+) failure reason (\d+).*"
      "SD sees the failure"
      "pgtxn"
      service
      )
    (process-state-k1-s
      2006 [2005] e
      ; [report_process_failure]: process_death service pem_master_b2144351 done with status 'ok'
      #"\[report_process_failure\]: process_death service (\S+) done with status .*"
      "SD process failure"
      "pgtxn"
      service
      )
    (process-state-k1-s
      2007 [2006] e
      ; service_directory_2e839ed1 [25494f3c:536:9]<344:20:08:29.041525>[287]: [report_process_failure]: relaunched service pem_master_4b113441 successfully
      #"\[report_process_failure\]: relaunched service (\S+) successfully"
      "Service relaunched successfully"
      "pgtxn"
      service
      (fn [strm event regex-match]
        (do
          ; process_pid with service name bridge and pg_name bridge_890xe was killed on node 2.1.1.12 with exit status 0x9. It was internall killed.
          (prn "start-time=" (:startTime strm) "current-time=" (:time strm))
          (def concise-msg (apply str "INFO::" (:startTime strm) " service " (:exited_service strm) " with pgname " (:pgname strm)
                             " on node " (:ip strm) "'with pid:" (:pid strm) " version:" (:version strm) "' was killed with status " (:exit_status strm)
                             " and was recovered successfully. The recovery took " (get-time-taken (:startTime strm) (:time strm)) " seconds"
                             )
            )
          )
        (file-write info-log-location [concise-msg "\n"])
        "DELETE"
        )
      )
    )
  )


(defn process-crash-relaunch-expired [& children]
  (fn [strm]
    (prn "event is expired")
    (let [concise-msg (apply str "ERROR:" (:startTime strm) " service " (:exited_service strm) " with pgname " (:pgname strm)
                " on node " (:ip strm) "'with pid:" (:pid strm) " version:" (:version strm) "' was killed with status " (:exit_status strm)
                " and could NOT be recovered"
                )]
      (prn concise-msg)
      (file-write error-log-location [concise-msg "\n"])
      )
    )
  )
