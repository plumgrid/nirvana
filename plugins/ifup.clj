(include "../pg_utils.clj")

(ns plugins.ifup
  "ifup"
  (:require [riemann.config :refer :all]
            [clojure.string :as str]
            [riemann.streams :refer :all]))

(defn ifup []
  (fn [e]
    (def start-state (get start-states-map :ifup-state)) ; 350
    (def plugin-type "ifup")
    (process-state-ek1-mk2val
      start-state [] e
      ;RPCIF_P2P [0c135c91:855:6]<018:19:52:07.845676>[5]: EVENT 'ifup tap1 access_vm 1 00:00:00:00:00:01 ip 10.10.1.51' txn_id 0c135c91 pid 16506
      #"EVENT 'ifup (\S+) (\S+) (\S+) (\S+).* ip (\S+)' txn_id (\S+) pid (\d+)"
      "ifup started"
      "pgtxn"
      plugin-type
      (fn [strm event regex-match]
        (assoc strm
          :plugin_type plugin-type
          :name (get regex-match 1)
          :type (get regex-match 2)
          :mac (get regex-match 4)
          :ip (get regex-match 5)
          :pid (get regex-match 7)
          :next_expected "Starting ifup_internal" ; TODO: Come up with some way to get it automatically.
          :ttl 10
          )
        )
      )
    (process-state-ek1-mk2val
      351 [350] e
      ; PE_89819501 [78bd44dd:554:8]<004:04:12:44.881208>[47]: dp_pgname=PE_89819501: Starting ifup_internal [ifup tap1 access_vm 1 00:00:00:00:00:01]
      #"dp_pgname=(\S+): Starting ifup_internal \[ifup (\S+) .*\]"
      "starting ifup_internal"
      "pgtxn"
      plugin-type
      (fn [strm event regex-match]
        (assoc strm
          :edge (get regex-match 1)
          :next_expected "ifup plumgrid"
          )
        )
      )
    (process-state-ek1-mk2val
      352 [351] e
      ; PE_89819501 [78bd44dd:554:9]<004:04:12:44.881217>[48]: ifup_internal: dp_pgname=PE_89819501 port_id=1 access_vm 1 00:00:00:00:00:01
      #"ifup_internal: dp_pgname=(\S+) port_id=(\d+) .*"
      "ifup plumgrid"
      "pgtxn"
      plugin-type
      (fn [strm event regex-match]
        (assoc strm
          :next_expected "started ifup_internal in pem_helper"
          )
        )
      )
    (process-state-ek1-mk2val
      353 [352] e
      ; pem_helper_92b5bb51 [78bd44dd:554:8]<004:04:12:44.881609>[6]: [notify_ifup_internal] ifc_name: PE_89819501+localhost+tap1, pif_fiber:506
      #"\[notify_ifup_internal\] ifc_name: (\S+), pif_fiber:(\d+)"
      "started ifup_internal in pem_helper"
      "pgtxn"
      plugin-type
      (fn [strm event regex-match]
        (assoc strm
          :next_expected "print_ifc_event1"
          )
        )
      )
    (process-state-ek1-mk2val
      354 [353] e
      ; pem_helper_92b5bb51 [78bd44dd:433:8]<012:04:36:38.676509>[7]: print_ifc_event: pe[PE_64a7a021] port[0/1] ifc_type[ACCESS_VM]
      #"print_ifc_event: pe\[(\S+)\] port\[(\S+)\] ifc_type\[(\S+)\]"
      "print_ifc_event1"
      "pgtxn"
      plugin-type
      (fn [strm event regex-match]
        (assoc strm
          :next_expected "print_ifc_event2"
          )
        )
      )
    (process-state-ek1-mk2val
      355 [354] e
      ; pem_helper_92b5bb51 [78bd44dd:433:8]<012:04:36:38.676523>[8]: print_ifc_event: tag1[] tag2[] tag3[] device[tap1] label[] phy_mac[00:00:00:00:00:01] bundle[] vlan[] uuid[1] nic[0] context[]
      #"print_ifc_event: .* phy_mac\[(\S+)\] bundle\[\] .* uuid\[(\S+)\] nic\[(\d+)\] .*"
      "print_ifc_event2"
      "pgtxn"
      plugin-type
      (fn [strm event regex-match]
        (assoc strm
          :next_expected "proposed physical interface name"
          :uuid (get regex-match 2)
          )
        )
      )
    (process-state-ek1-mk2val
      356 [354] e
      ;pem_helper_92b5bb51 [78bd44dd:554:9]<004:04:12:44.881657>[9]: notify_ifup_internal_fpool: proposed physical interface name is 00:00:00:00:00:01+localhost+tap1
      #"notify_ifup_internal_fpool: proposed physical interface name is (\S+)"
      "proposed physical interface name"
      "pgtxn"
      plugin-type
      (fn [strm event regex-match]
        (assoc strm
          :next_expected "completed ifup_internal in pem_helper"
          )
        )
      )
    (process-state-ek1-mk2val
      357 [356] e
      ; pem_helper_92b5bb51 [78bd44dd:554:9]<004:04:12:44.911674>[30]: notify_ifup_internal_fpool: ifup for PE_89819501+localhost+tap1 name:IFC+ACCESS_VM+1+0 ended
      #"notify_ifup_internal_fpool: ifup for (\S+) name:(\S+) ended"
      "completed ifup_internal in pem_helper"
      "pgtxn"
      plugin-type
      (fn [strm event regex-match]
        (assoc strm
          :next_expected "ifup plumid"
          )
        )
      )
    (process-state-ek1-mk2val
      358 [357] e
      ; PE_3d8d4e31 [6e49b9b3:1419:9]<361:06:33:02.809626>[166]: ifup_internal: port_id=3 plum_id=2 ifc_cookie=00:00:00:00:00:02+localhost+tap2
      #"ifup_internal: port_id=(\d+) plum_id=(\d+) ifc_cookie=(\S+)"
      "ifup plumid"
      "pgtxn"
      plugin-type
      (fn [strm event regex-match]
        (assoc strm
          :next_expected "ifup latency measurment"
          )
        )
      )
    (process-state-ek1-mk2val
      359 [358] e
      ; PE_3d8d4e31 [6e49b9b3:1419:8]<361:06:33:02.809634>[167]: ifup_latency: 10029 name: tap2 type: access_vm
      #"ifup_latency: (\d+) name: (\S+) type: (\S+)"
      "ifup latency"
      "pgtxn"
      plugin-type
      (fn [strm event regex-match]
        (assoc strm
          :next_expected "ifup completion"
          )
        )
      )
    (process-state-ek1-mk2val
      360 [359] e
      ; PE_3d8d4e31 [6e49b9b3:1419:8]<361:06:33:02.809645>[168]: dp_pgname=PE_3d8d4e31: Completed ifup_internal [ifup tap2 access_vm DYN_2 00:00:00:00:00:02]
      #"dp_pgname=(\S+): Completed ifup_internal \[ifup (\S+) .*\]"
      "ifup completed"
      "pgtxn"
      plugin-type
      (fn [strm event regex-match]
        (do
          ; Interface (name:tap2, type:access_vm, mac:00:00:00:00:00:02) successfully attached to edge PE_3d8d4e31<HOST NAME> ip: 192.168.10.2
          (def concise-msg (apply str (now) ": Interface (name:" (:name strm) ", type:" (:type strm)
                                      ", mac:" (:mac strm) ", uuid:" (:uuid strm) ") successfully attached to edge " (:edge strm) " <"
                                      (:my_host strm) "> <" (:ip strm) "> (took "
                                      (get-time-taken (:startTime strm) (:time strm)) " seconds)."
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
    (let [concise-msg (apply str (now) ": Interface (name:" (:name strm) ", type:" (:type strm)
                        ", mac:" (:mac strm) ", uuid:" (:uuid strm) ") failed to attach to edge " (:edge strm) "<" (:my_host strm)
                        "> <" (:ip strm) "> Last good known state is '" (:description strm) "'. Next expected state is '" (:next_expected strm)
                        "'. Transaction id: " (:pgtxn strm) "."
                        )]
      (file-write error-log-location [concise-msg "\n"])
      )
    )
  )
