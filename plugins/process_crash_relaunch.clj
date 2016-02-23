;Copyright 2015 PLUMgrid Inc.

;Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.
;You may obtain a copy of the License at:

;http://www.apache.org/licenses/LICENSE-2.0

;Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS"
;BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific
;language governing permissions and limitations under the License.

(include "../pg_utils.clj")
(include "./start_states_consts.clj")

(ns plugins.process-crash-relaunch
  "process crash handlers"
  (:require [riemann.config :refer :all]
            [clojure.string :as str]
            [riemann.streams :refer :all]))

(defn process-crash-relaunch []
  (fn [e]
    (def start-state (get start-states-map :process-crash-relaunch-start-state))
    (def plugin-type "process_crash_relaunch")
    (process-state-ek1-mk2val
      start-state [] e
      ; RPCIF_system_manager [5cc63441:2139:6]<013:04:48:55.752040>[45]: EVENT 'report_process_failure' txn_id 5cc63441 pid 22605
      #"EVENT 'report_process_failure' txn_id (\S+) pid (\d+)"
      "process child exit event started"
      "pgtxn"
      plugin-type
      (fn [strm event regex-match]
        (assoc strm
          :plugin_type plugin-type
          :next_expected "child exit event params"
          :ttl 10
          )
        )
      )
    (process-state-ek1-mk2val
      2001 [2000] e
      ; system_manager_4d1f1bb1 [5cc63441:2139:8]<013:04:48:55.752076>[127]: [process_child_exit]: ippid 10.22.27.26:22877 Exited service pem_master pgname pem_master_7719e2f1
      #"\[process_child_exit\]: ippid (\S+):(\d+) Exited service (\S+) pgname (\S+)"
      "child exit event params"
      "pgtxn"
      plugin-type
      (fn [strm event regex-match]
        (assoc strm
          :ip_where_proc_exit (get regex-match 1)
          :pid_of_exited_proc (get regex-match 2)
          :exited_service (get regex-match 3)
          :pgname (remove-from-end (get regex-match 4) "}")
          :next_expected "child exit type"
          )
        )
      )
    (process-state-ek1-mk2val
      2002 [2001] e
      ; system_manager_4d1f1bb1 [5cc63441:2139:9]<013:04:48:55.752827>[128]: [process_child_exit]: SIGCHILD child 22877 returned raw status 0x6 and exit type=[WIFSIGNALED: Terminated by signal 6]
      #"\[process_child_exit\]: SIGCHILD child (\d+) returned raw status (\S+) and exit type=\[(\S+): .*"
      "child exit type"
      "pgtxn"
      plugin-type
      (fn [strm event regex-match]
        (assoc strm
          :raw_status (get regex-match 2)
          :exit_type (get regex-match 3)
          :next_expected "SM report child exit to HM"
          )
        )
      )
    ; TODO: Does not match.
    (process-state-ek1-mk2val
      2003 [2002] e
      ; system_manager_4d1f1bb1 [5cc63441:2139:9]<013:04:48:55.753169>[130]: [22877]: Reporting process failure process_child_exit ; pgname pem_master_7719e2f1
      #"Reporting process failure process_child_exit ; pgname (\S+)"
      "SM report child exit to HM"
      "pgtxn"
      plugin-type
      (fn [strm event regex-match]
        (assoc strm
          :next_expected "HM report_process_failure started"
          )
        )
      )
    (process-state-ek1-mk2val
      2004 [2003] e
      ; HealthMonitorProcessFailure [5cc63441:2139:9]<013:04:48:55.753183>[1]: [report_process_failure]: pgname pem_master_7719e2f1 fail_from_exit 1 is_crash 1 sm_exit 0 ip_pid 10.22.27.26:22877
      #"\[report_process_failure\]: pgname (\S+) fail_from_exit (\d+) is_crash (\d+) sm_exit (\d+) ip_pid (\S+):(\d+)"
      "HM report_process_failure started"
      "pgtxn"
      plugin-type
      (fn [strm event regex-match]
        (assoc strm
          :next_expected "HM report child exit to SD"
          )
        )
      )
    (process-state-ek1-mk2val
      2005 [2004] e
      ; HealthMonitorProcessFailure [5cc63441:2139:9]<013:04:48:55.755540>[8]: [report_process_failure]: report_process_failure_with_retry for pem_master_7719e2f1 is_crash 1 ip_pid 10.22.27.26:22877
      #"\[report_process_failure\]: report_process_failure_with_retry for (\S+) is_crash (\d+) ip_pid (\S+):(\d+)"
      "HM report child exit to SD"
      "pgtxn"
      plugin-type
      (fn [strm event regex-match]
        (assoc strm
          :next_expected "SD report_process_failure started"
          )
        )
      )
    (process-state-ek1-mk2val
      2006 [2005] e
      ; service_directory_19b4c701 [5cc63441:687:9]<013:04:48:55.756187>[305]: [report_process_failure]: failed pgname pem_master_7719e2f1 is_crash 1 match_pid 10.22.27.26:22877 failure reason 2
      #"\[report_process_failure\]: failed pgname (\S+) is_crash (\d+) match_pid (\S+):(\d+) failure reason (\d+)"
      "SD report_process_failure started"
      "pgtxn"
      plugin-type
      (fn [strm event regex-match]
        (assoc strm
          :next_expected "SD report_process_failure params"
          )
        )
      )
    ; TODO: It matches twice.
    (process-state-ek1-mk2val
      2007 [2006] e
      ; service_directory_19b4c701 [5cc63441:687:9]<013:04:48:55.756513>[306]: [report_process_failure]: service_name 'pem_master' ip_pid '10.22.27.26:22877' pgname 'pem_master_7719e2f1'
      #"\[report_process_failure\]: service_name '(\S+)' ip_pid '(\S+):(\d+)' pgname '(\S+)'"
      "SD report_process_failure params"
      "pgtxn"
      plugin-type
      (fn [strm event regex-match]
        (assoc strm
          :next_expected "SD process_death started"
          )
        )
      )
    (process-state-ek1-mk2val
      2008 [2006] e
      ; service_directory_c1a1c361 [0d373005:907:9]<013:04:51:49.305411>[652]: [report_process_failure]: service_name 'bridge' ip_pid '10.22.27.26:23069' pgname 'bridge_a615e671' domain 'Demo' vnf_name '/0/connectivity/domain/Demo/ne/Bridge
      #"\[report_process_failure\]: service_name '(\S+)' ip_pid '(\S+):(\d+)' pgname '(\S+)' domain '(\S+)' vnf_name '(\S+)'"
      "SD report_process_failure params"
      "pgtxn"
      plugin-type
      (fn [strm event regex-match]
        (assoc strm
          :domain_name (get regex-match 5)
          :vnf_name (str/replace (get regex-match 6) #"\\" "")
          :next_expected "SD process_death started"
          )
        )
      )
    (process-state-ek1-mk2val
      2009 [2007 2008] e
      ; service_directory_19b4c701 [5cc63441:687:8]<013:04:48:55.756528>[307]: process_death:  dying_pgname = pem_master_7719e2f1 (reason=2) restart version 9.45.0
      #"process_death:  dying_pgname = (\S+) \(reason=(\d+)\) restart version (\S+)"
      "SD process_death started"
      "pgtxn"
      plugin-type
      (fn [strm event regex-match]
        (assoc strm
          :next_expected "SD restart policy"
          )
        )
      )
    (process-state-ek1-mk2val
      2010 [2009] e
      ; service_directory_19b4c701 [5cc63441:687:9]<013:04:48:55.756736>[308]: process_death: SD will relaunch pgname pem_master_7719e2f1 with restartable policy for service_name=pem_master
      #"process_death:  SD will relaunch pgname (\S+) with restartable policy for service_name=(\S+)"
      "SD restart policy"
      "pgtxn"
      plugin-type
      (fn [strm event regex-match]
        (assoc strm
          :next_expected "SD restart policy"
          )
        )
      )
    (process-state-ek1-mk2val
      2011 [2010] e
      ; service_directory_19b4c701 [5cc63441:687:9]<013:04:48:55.756753>[309]: process_death: pgname pem_master_7719e2f1 local_path '/service/pem_master_7719e2f1'
      #"process_death:  process_death: pgname (\S+) local_path '(\S+)'"
      "process_death started"
      "pgtxn"
      plugin-type
      (fn [strm event regex-match]
        (assoc strm
          :next_expected "process_death finished"
          )
        )
      )
    (process-state-ek1-mk2val
      2012 [2011] e
      ; service_directory_19b4c701 [5cc63441:687:9]<013:04:48:55.756934>[312]: [report_process_failure]: process_death service pem_master_7719e2f1 done with status 'ok'
      #"\[report_process_failure\]: process_death service (\S+) done with status '(\S+)'"
      "process_death finished"
      "pgtxn"
      plugin-type
      (fn [strm event regex-match]
        (assoc strm
          :next_expected "SD new service ip_pid"
          )
        )
      )
    (process-state-ek1-mk2val
      2013 [2012] e
      ; service_directory_19b4c701 [5cc63441:687:9]<013:04:48:56.858516>[363]: [service_has_recovered]: success service pem_master pgname pem_master_7719e2f1 ip_pid 10.22.27.26:23807
      #"\[service_has_recovered\]: success service (\S+) pgname (\S+) ip_pid (\S+)"
      "SD new service ip_pid"
      "pgtxn"
      plugin-type
      (fn [strm event regex-match]
        (assoc strm
          :new_ip_pid (remove-from-end (get regex-match 3) "}")
          :next_expected "SD report_process_failure finished"
          )
        )
      )
    (process-state-ek1-mk2val
      2014 [2013] e
      ; service_directory_19b4c701 [5cc63441:687:9]<013:04:48:56.858563>[364]: [report_process_failure]: relaunched service pem_master_7719e2f1 successfully
      #"\[report_process_failure\]: relaunched service (\S+) successfully"
      "SD report_process_failure finished"
      "pgtxn"
      plugin-type
      (fn [strm event regex-match]
        (assoc strm
          :next_expected "HM report_process_failure started"
          )
        )
      )
    ; TODO: It is not being matched.
    (process-state-ek1-mk2val
      2015 [2014] e
      ; HealthMonitorProcessFailure [5cc63441:2139:9]<013:04:48:56.858824>[9]: [report_process_failure]: report_process_failure_with_retry for pem_master_7719e2f1 DONE, status=ok
      #"\[report_process_failure\]: report_process_failure_with_retry for (\S+) DONE, status=(\S+)"
      "HM report_process_failure started"
      "pgtxn"
      plugin-type
      (fn [strm event regex-match]
        (assoc strm
          :next_expected "process child exit event finished"
          )
        )
      )

   (process-state-ek1-mk2val
      2016 [2015] e
      ; system_manager_4d1f1bb1 [5cc63441:2139:9]<013:04:48:56.859552>[143]: [process_child_exit]: pid 22877 done
      #"\[process_child_exit\]: pid (\d+) done"
      "process child exit event finished"
      "pgtxn"
      plugin-type
      (fn [strm event regex-match]
        (do
          ; Process ‘bridge’ (pgname 'bridge_a615e671',ip_pid '10.22.27.26:23069' domain 'Demo' vnf_name '/0/connectivity/domain/Demo/ne/Bridge' ) exited with reason WIFSIGNALED and got relaunched successfully with ip_pid: 10.22.27.26:23807
          ; TODO: If demo & vnf_name is empty then it is operator message.
          (let [exited-process (:exited_service strm)]
            (if (or (= exited-process "rest_gateway") (= exited-process "cdb") (= exited-process "health_monitor")
                    (= exited-process "tenant_manager") (= exited-process "analytics_manager")
                    (= exited-process "CM") (= exited-process "vmw_agent") (= exited-process "metadata")
                    (= exited-process "pem_master"))
              (def concise-msg (apply str (now) ": Process '" (:exited_service strm) "' (pgname '" (:pgname strm)
                                 "', ip_pid '" (:ip_where_proc_exit strm) ":" (:pid_of_exited_proc strm)
                                 "') exited with reason " (:exit_type strm)
                                 " and got relaunched successfully with ip_pid '" (:new_ip_pid strm) "'. The recovery took "
                                 (get-time-taken (:startTime strm) (:time strm)) " seconds"
                                 )
                )
              (def concise-msg (apply str (now) ": Process '" (:exited_service strm) "' (pgname '" (:pgname strm)
                                 "', ip_pid '" (:ip_where_proc_exit strm) ":" (:pid_of_exited_proc strm) "' domain '"
                                 (:domain_name strm) "' vnf_name '" (:vnf_name strm) "') exited with reason " (:exit_type strm)
                                 " and got relaunched successfully with ip_pid '" (:new_ip_pid strm) "'. The recovery took "
                                 (get-time-taken (:startTime strm) (:time strm)) " seconds"
                                 )
                )
              )
            )
          )
        (def raw-status (:raw_status strm))
        (if (not= raw-status "0")
          (file-write info-log-location [concise-msg "\n"])
          )
        "DELETE"
        )
      )
    )
  )


(defn process-crash-relaunch-expired [& children]
  (fn [strm]
    (prn "event is expired")
    ; Process ‘bridge’ (pgname 'bridge_a615e671',ip_pid '10.22.27.26:23069' domain 'Demo' vnf_name '/0/connectivity/domain/Demo/ne/Bridge' ) exited with reason WIFSIGNALED and failed to restart.  Last good known state is “”. Next expected state “”
    (let [concise-msg (apply str (now) ": Process '" (:exited_service strm) "' (pgname '" (:pgname strm)
                        "', ip_pid '" (:ip_where_proc_exit strm) ":" (:pid_of_exited_proc strm) "' domain '"
                        (:domain_name strm) "' vnf_name '" (:vnf_name strm) "') exited with reason " (:exit_type strm)
                        " and failed to restart.  Last good known state is '" (:description strm) "'. Next expected state is '"
                        (:next_expected strm) "'."
                        ) exit-status (:exit_status strm)]
      (if (not= exit-status "0")
        (file-write error-log-location [concise-msg "\n"])
        )
      )
    )
  )
