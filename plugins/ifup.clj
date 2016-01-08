(include "../pg_utils.clj")

(ns plugins.ifup
  "ifup"
  (:require [riemann.config :refer :all]
            [clojure.string :as str]
            [riemann.streams :refer :all]))

(defn ifup []
  (fn [e]
    (def start-state (get start-states-map :ifup-state)) ; 350
    (def service "ifup")
    (process-state-k1-s
      start-state [] e
      ; RPCIF_P2P [6e49b9b3:1419:6]<361:06:33:02.799578>[21]: EVENT 'ifup tap2 access_vm DYN_2 00:00:00:00:00:02' txn_id 6e49b9b3
      #"EVENT 'ifup (\S+) (\S+) (\S+) (\S+)' txn_id (\S+)"
      "ifup started"
      "pgtxn"
      service
      (fn [strm event regex-match]
        (assoc strm
          :host event
          :service service
          :name (get regex-match 1)
          :type (get regex-match 2)
          :mac (get regex-match 4)
          :next_expected "Starting ifup_internal" ; TODO: Come up with some way to get it automatically.
          :ttl 10
          )
        )
      )
    (process-state-k1-s
      351 [350] e
      ; PE_3d8d4e31 [6e49b9b3:1419:8]<361:06:33:02.799597>[162]: dp_pgname=PE_3d8d4e31: Starting ifup_internal [ifup tap2 access_vm DYN_2 00:00:00:00:00:02]
      #"dp_pgname=(\S+): Starting ifup_internal \[ifup (\S+) .*\]"
      "starting ifup_internal"
      "pgtxn"
      service
      (fn [strm event regex-match]
        (assoc strm
          :edge (get regex-match 1)
          :next_expected "ifup internal"
          )
        )
      )
    (process-state-k1-s
      352 [351] e
      ; PE_3d8d4e31 [6e49b9b3:1419:9]<361:06:33:02.799607>[163]: ifup_internal: dp_pgname=PE_3d8d4e31 port_id=2 access_vm DYN_2 00:00:00:00:00:02
      #"ifup_internal: dp_pgname=(\S+) port_id=(\d+) .*"
      "ifup_internal started"
      "pgtxn"
      service
      (fn [strm event regex-match]
        (assoc strm
          :next_expected "ifup internal -- 2"
          )
        )
      )
      (process-state-k1-s
        353 [352] e
        ; PE_3d8d4e31 [6e49b9b3:1419:9]<361:06:33:02.809626>[166]: ifup_internal: port_id=3 plum_id=2 ifc_cookie=00:00:00:00:00:02+localhost+tap2
        #"ifup_internal: port_id=(\d+) plum_id=(\d+) ifc_cookie=(\S+)"
        "ifup plumid"
        "pgtxn"
        service
        (fn [strm event regex-match]
          (assoc strm
            :next_expected "ifup latency measurment"
            )
          )
        )
    (process-state-k1-s
      354 [353] e
      ; PE_3d8d4e31 [6e49b9b3:1419:8]<361:06:33:02.809634>[167]: ifup_latency: 10029 name: tap2 type: access_vm
      #"ifup_latency: (\d+) name: (\S+) type: (\S+)"
      "ifup latency"
      "pgtxn"
      service
      (fn [strm event regex-match]
        (assoc strm
          :next_expected "ifup completion"
          )
        )
      )
    (process-state-k1-s
      355 [354] e
      ; PE_3d8d4e31 [6e49b9b3:1419:8]<361:06:33:02.809645>[168]: dp_pgname=PE_3d8d4e31: Completed ifup_internal [ifup tap2 access_vm DYN_2 00:00:00:00:00:02]
      #"dp_pgname=(\S+): Completed ifup_internal \[ifup (\S+) .*\]"
      "ifup completed"
      "pgtxn"
      service
      (fn [strm event regex-match]
        (do
          ; Interface (name:tap2, type:access_vm, mac:00:00:00:00:00:02) successfully attached to edge PE_3d8d4e31<HOST NAME> ip: 192.168.10.2
          (def concise-msg (apply str "INFO::" (:startTime strm) " Interface (name:" (:name strm) ", type:" (:type strm)
                                      ", mac:" (:mac strm) ") successfully attached to edge " (:edge strm) "<" (:host strm) "> ip: nil. It took "
                                      (get-time-taken (:startTime strm) (:time strm)) " seconds."
                             )
            )
          )
        (file-write info-log-location [concise-msg "\n"])
        "DELETE"
        )
      )
    )
  )


(defn ifup-expired [& children]
  (fn [strm]
    ; Interface (name: tap2, type: access_vm, mac: 00:00:00:00:00:02) failed to attach to edge PE_3d8d4e31<HOST NAME>
    ; ip: 192.168.10.2. Last good known state is “”. Next expected state “” Transaction id: 6e49b9b3
    (let [concise-msg (apply str "ERROR::" (:startTime strm) " Interface (name:" (:name strm) ", type:" (:type strm)
                        ", mac:" (:mac strm) ") failed to attach to edge " (:edge strm) "<" (:host strm)
                        "> ip: nil. Last good known state is '" (:description strm) "'. Next expected state is '" (:next_expected strm)
                        "'. Transaction id: " (:pgtxn strm) "."
                        )]
      (file-write error-log-location [concise-msg "\n"])
      )
    )
  )
