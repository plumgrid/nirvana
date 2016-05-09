(include "./start_states_consts.clj")
(ns plugins.process_crash_relaunch
  "process_crash_relaunch"
  (:require [riemann.config :refer :all]
            [clojure.string :as str]
            [riemann.streams :refer :all]))
(defn process_crash_relaunch []
  (fn [e]
    (process-state-ek1-mk2val
      1000 [] e
      #"EVENT 'report_process_failure' txn_id (\S+) pid (\d+)"
      "process child exit event started"
      "process_crash_relaunch"
      "pgtxn"
      (fn [strm event regex-match]
        (assoc strm
          :plugin_type "process_crash_relaunch"
          :next_expected "child exit event params"
          :ttl 10
          )
       )
    )
    (process-state-ek1-mk2val
      1001 [1000] e
      #"\[process_child_exit\]: ippid (\S+):(\d+) Exited service (\S+) pgname (\S+)"
      "child exit event params"
      "process_crash_relaunch"
      "pgtxn"
      (fn [strm event regex-match]
        (assoc strm
          :plugin_type "process_crash_relaunch"
          :ip_where_proc_exit (get regex-match 1)
          :pid_of_exited_proc (get regex-match 2)
          :exited_service (get regex-match 3)
          :pgname (remove-from-end (get regex-match 4) "}")
          :next_expected "child exit type"
          )
       )
    )
    (process-state-ek1-mk2val
      1002 [1001] e
      #"\[process_child_exit\]: SIGCHILD child (\d+) returned raw status (\S+) and exit type=\[(\S+): .*"
      "child exit type"
      "process_crash_relaunch"
      "pgtxn"
      (fn [strm event regex-match]
        (assoc strm
          :plugin_type "process_crash_relaunch"
          :raw_status (get regex-match 2)
          :exit_type (get regex-match 3)
          :next_expected "SM report child exit to HM"
          )
       )
    )
    (process-state-ek1-mk2val
      1003 [1002] e
      #"Reporting process failure process_child_exit ; pgname (\S+)"
      "SM report child exit to HM"
      "process_crash_relaunch"
      "pgtxn"
      (fn [strm event regex-match]
        (assoc strm
          :plugin_type "process_crash_relaunch"
          :next_expected "HM report_process_failure started"
          )
       )
    )
    (process-state-ek1-mk2val
      1004 [1003] e
      #"\[report_process_failure\]: pgname (\S+) fail_from_exit (\d+) is_crash (\d+) sm_exit (\d+) ip_pid (\S+):(\d+)"
      "HM report_process_failure started"
      "process_crash_relaunch"
      "pgtxn"
      (fn [strm event regex-match]
        (assoc strm
          :next_expected "HM report child exit to SD"
          )
       )
    )
    (process-state-ek1-mk2val
      1005 [1004] e
      #"\[report_process_failure\]: report_process_failure_with_retry for (\S+) is_crash (\d+) ip_pid (\S+):(\d+)"
      "HM report child exit to SD"
      "process_crash_relaunch"
      "pgtxn"
      (fn [strm event regex-match]
        (assoc strm
          :plugin_type "process_crash_relaunch"
          :next_expected "SD report_process_failure started"
          )
       )
    )
    (process-state-ek1-mk2val
      1006 [1005] e
      #"\[report_process_failure\]: failed pgname (\S+) is_crash (\d+) match_pid (\S+):(\d+) failure reason (\d+)"
      "SD report_process_failure started"
      "process_crash_relaunch"
      "pgtxn"
      (fn [strm event regex-match]
        (assoc strm
          :plugin_type "process_crash_relaunch"
          :next_expected "SD report_process_failure params"
          )
       )
    )
    (process-state-ek1-mk2val
      1007 [1006] e
      #"\[report_process_failure\]: service_name '(\S+)' ip_pid '(\S+):(\d+)' pgname '(\S+)'"
      "SD report_process_failure params"
      "process_crash_relaunch"
      "pgtxn"
      (fn [strm event regex-match]
        (assoc strm
          :plugin_type "process_crash_relaunch"
          :next_expected "SD process_death started"
          )
       )
    )
    (process-state-ek1-mk2val
      1008 [1007] e
      #"\[report_process_failure\]: service_name '(\S+)' ip_pid '(\S+):(\d+)' pgname '(\S+)' domain '(\S+)' vnf_name '(\S+)'"
      "SD report_process_failure params"
      "process_crash_relaunch"
      "pgtxn"
      (fn [strm event regex-match]
        (assoc strm
          :plugin_type "process_crash_relaunch"
          :domain_name (get regex-match 5)
          :vnf_name (str/replace (get regex-match 6) #"\\" "")
          :next_expected "SD process_death started"
          )
       )
    )
    (process-state-ek1-mk2val
      1009 [1007 1008] e
      #"process_death:  dying_pgname = (\S+) \(reason=(\d+)\) restart version (\S+)"
      "SD process_death started"
      "process_crash_relaunch"
      "pgtxn"
      (fn [strm event regex-match]
        (assoc strm
          :plugin_type "process_crash_relaunch"
          :next_expected "SD restart policy"
          )
       )
    )
    (process-state-ek1-mk2val
      1010 [1009] e
      #"process_death:  SD will relaunch pgname (\S+) with restartable policy for service_name=(\S+)"
      "SD restart policy"
      "process_crash_relaunch"
      "pgtxn"
      (fn [strm event regex-match]
        (assoc strm
          :plugin_type "process_crash_relaunch"
          :next_expected "SD restart policy"
          )
       )
    )
    (process-state-ek1-mk2val
      1011 [1010] e
      #"process_death:  process_death: pgname (\S+) local_path '(\S+)'"
      "process_death started"
      "process_crash_relaunch"
      "pgtxn"
      (fn [strm event regex-match]
        (assoc strm
          :plugin_type "process_crash_relaunch"
          :next_expected "process_death finished"
          )
       )
    )
    (process-state-ek1-mk2val
      1012 [1011] e
      #"\[report_process_failure\]: process_death service (\S+) done with status '(\S+)'"
      "process_death finished"
      "process_crash_relaunch"
      "pgtxn"
      (fn [strm event regex-match]
        (assoc strm
          :next_expected "SD new service ip_pid"
          )
       )
    )
    (process-state-ek1-mk2val
      1013 [1012] e
      #"\[service_has_recovered\]: success service (\S+) pgname (\S+) ip_pid (\S+)"
      "SD new service ip_pid"
      "process_crash_relaunch"
      "pgtxn"
      (fn [strm event regex-match]
        (assoc strm
          :new_ip_pid (remove-from-end (get regex-match 3) "}")
          :next_expected "SD report_process_failure finished"
          )
       )
    )
    (process-state-ek1-mk2val
      1014 [1013] e
      #"\[report_process_failure\]: relaunched service (\S+) successfully"
      "SD report_process_failure finished"
      "process_crash_relaunch"
      "pgtxn"
      (fn [strm event regex-match]
        (assoc strm
          :next_expected "HM report_process_failure started"
          )
       )
    )
    (process-state-ek1-mk2val
      1015 [1014] e
      #"\[report_process_failure\]: report_process_failure_with_retry for (\S+) DONE, status=(\S+)"
      "HM report_process_failure started"
      "process_crash_relaunch"
      "pgtxn"
      (fn [strm event regex-match]
        (assoc strm
          :next_expected "process child exit event finished"
          )
       )
    )
    (process-state-ek1-mk2val
      1016 [1015] e
      #"\[process_child_exit\]: pid (\d+) done"
      "process child exit event finished"
      "process_crash_relaunch"
      "pgtxn"
      (fn [strm event regex-match]
        (do
          (def concise-msg (apply str (now) " : ""Process '" (:exited_service strm) "' (pgname '" (:pgname strm)"', ip_pid '" (:ip_where_proc_exit strm) ":" (:pid_of_exited_proc strm)"') exited with reason " (:exit_type strm)" and got relaunched successfully with ip_pid '" (:new_ip_pid strm) "'. The recovery took "(get-time-taken (:startTime strm) (:time strm)) " seconds"
              )
            )
          )
        (file-write info-log-location [concise-msg "\n"])
        "DELETE"
        )
      )
    )
  )
(defn process_crash_relaunch_expired [& children]
  (fn [strm]
    (let [concise-msg (apply str (now) " : ""Process '" (:exited_service strm) "' (pgname '" (:pgname strm)"', ip_pid '" (:ip_where_proc_exit strm) ":" (:pid_of_exited_proc strm) "' domain '"(:domain_name strm) "' vnf_name '" (:vnf_name strm) "') exited with reason " (:exit_type strm)" and failed to restart.  Last good known state is '" (:description strm) "'. Next expected state is '"(:next_expected strm) "'.")]
      (file-write error-log-location [concise-msg "\n"])
      )
    )
  )
