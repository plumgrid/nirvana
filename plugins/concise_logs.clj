;Copyright 2015 PLUMgrid Inc.

;Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.
;You may obtain a copy of the License at:

;http://www.apache.org/licenses/LICENSE-2.0

;Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS"
;BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific
;language governing permissions and limitations under the License.

(include "../pg_utils.clj")
(require '[plugins.process-crash-relaunch :as PC]) ; TODO: Change it to PCR
(require '[plugins.process-crash-count :as PCC])
(require '[plugins.director-bootup-stage1 :as DBS1])
(require '[plugins.director-bootup-stage2 :as DBS2])
(require '[plugins.director-exit :as DE])
(require '[plugins.edge-bootup :as EB])
(require '[plugins.edge-reconnect :as ERC])
(require '[plugins.resource-load :as RL])
(require '[plugins.ifup :as IFU])
(require '[plugins.topo-load-via-cdb :as TLVCDB])


; Take a raw stream and create a PG stream message out of it
(defn pg-stream [& children]
  (fn [e]
    (if (and (check-tags "NRV" (:tags e)) (not= nil (:description e))) ; REVIEW: Do we really need NRV tag ???
      ; "2016-01-04T23:16:43.021Z 127.0.0.1 {2016-01-04T23:16:43.019273+00:00,tahir-ahmed-b-1-bld-master, service_directory_2fa5b111 [10c044ae:1:9]<352:05:17:26.955312>[17]: [init]: rest_gateway is active}
      (let [search-msg (get-substr-after-nth-occurance-of-char (:description e) ", " 1)] ; Get PG log message out of syslog message
        (let [new-event (process-raw-PG-msg e search-msg)]
          (do
            (if (some? (:handler new-event)) (call-rescue new-event children))
            )
          )
        )
      )
    )
  )

(let [index (index)]
  (streams
    (where (not (expired? event))
      (default :ttl 10
        (pg-stream
          (where (re-find #"service_directory_.*|system_manager_.*|HealthMonitorProcessFailure" (:handler event))
            (where (re-find #"\[process_child_exit\]: .*|\[report_process_failure\]: .*|activate_service: .*" (:pgmsg event))
              (PC/process-crash-relaunch)
              )
            (where (re-find #"\[process_child_exit\]: .*" (:pgmsg event))
              (PCC/process-crash-count)
              )
            (where (re-find #"\[init\]:.*|\[operator\(\)\]:.*" (:pgmsg event))
              (DBS2/director-bootup-stage2)
              )
            (where (re-find #".* CPU load: .* Mem load: .*" (:pgmsg event))
              (RL/resource-load)
              )
            )
          (where (re-find #"SMLite_.*" (:handler event))
            (DBS1/director-bootup-stage1)
            (where (re-find #"\[child_exit_monitor\]: .*" (:pgmsg event))
              (DE/director-exit)
              )
            )
          (where (re-find #"SELite_.*" (:handler event))
            (where (re-find #"\[SELite\]: .*|\[nos_monitor\]: .*|\[launch_sem\]: .*|\[rx_sm_msg_handler\]: .*" (:pgmsg event))
              (EB/edge-bootup)
              )
            (where (re-find #"\[nos_monitor\]: .*|\[handle_nos_reconnect\]: .*|\[child_exit_monitor\]: .*|\[launch_sem\]: .*|\[rx_sm_msg_handler\]: .*" (:pgmsg event))
              (ERC/edge-reconnect)
              )
            )
          (where (re-find #"RPCIF_.*|PE_.*" (:handler event))
            (where (re-find #"EVENT .*|dp_pgname.*|ifup_internal: .*|ifup_latency: .*|dp_pgname .*" (:pgmsg event))
              (IFU/ifup)
              )
            )
          (where (re-find #"RPCIF_.*|ConnectivityManager.*|cdb_.*" (:handler event))
            (where (re-find #"EVENT .*|Requesting cdb.*|Dom0::.*|request_config_post.*" (:pgmsg event))
              (TLVCDB/topo-load-via-cdb)
              )
            )
          )
        )
      )
    (expired
      #(prn "******* Expired1> ******** " %)
      (where (re-find #"process_crash_relaunch" (:service event))
        (PC/process-crash-relaunch-expired)
        )
      (where (re-find #"process_crash_count" (:service event))
        (PCC/process-crash-count-expired)
        )
      (where (re-find #"director_bootup_stage1" (:service event))
        (DBS1/director-bootup-stage1-expired)
        )
      (where (re-find #"director_bootup_stage2" (:service event))
        (DBS2/director-bootup-stage2-expired)
        )
      (where (re-find #"director_exit" (:service event))
        (DE/director-exit-expired)
        )
      (where (re-find #"edge_bootup" (:service event))
        (EB/edge-bootup-expired)
        )
      (where (re-find #"edge_reconnect" (:service event))
        (ERC/edge-reconnect-expired)
        )
      (where (re-find #"ifup" (:service event))
        (IFU/ifup-expired)
        )
      )
    )
  )
