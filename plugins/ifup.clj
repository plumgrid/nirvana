(include "./start_states_consts.clj")
(ns plugins.ifup
  "ifup"
  (:require [riemann.config :refer :all]
            [clojure.string :as str]
            [riemann.streams :refer :all]))
(defn ifup []
  (fn [e]
    (process-state-ek1-mk2val
      360 [] e
      #"EVENT 'ifup (\S+) (\S+) (\S+) (\S+) ip (\S+)' txn_id (\S+) pid (\d+)"
      "ifup started"
      "pgtxn"
      "ifup"
      (fn [strm event regex-match]
        (assoc strm
          :plugin_type "ifup"
          :name (get regex-match 1)
          :type (get regex-match 2)
          :mac (get regex-match 4)
          :ip (get regex-match 5)
          :pid (get regex-match 7)
          :next_expected "Starting ifup_internal"
          :ttl 10
          )
       )
    )
    (process-state-ek1-mk2val
      361 [360] e
      #"dp_pgname=(\S+): Starting ifup_internal \[ifup (\S+) .*\]"
      "starting ifup_internal"
      "pgtxn"
      "ifup"
      (fn [strm event regex-match]
        (assoc strm
          :edge (get regex-match 1)
          :next_expected "ifup plumgrid"
          )
       )
    )
    (process-state-ek1-mk2val
      362 [361] e
      #"ifup_internal: dp_pgname=(\S+) port_id=(\d+) .*"
      "ifup plumgrid"
      "pgtxn"
      "ifup"
      (fn [strm event regex-match]
        (assoc strm
          :next_expected "started ifup_internal in pem_helper"
          )
       )
    )
    (process-state-ek1-mk2val
      363 [362] e
      #"\[notify_ifup_internal\] ifc_name: (\S+), pif_fiber:(\d+)"
      "started ifup_internal in pem_helper"
      "pgtxn"
      "ifup"
      (fn [strm event regex-match]
        (assoc strm
          :next_expected "print_ifc_event1"
          )
       )
    )
    (process-state-ek1-mk2val
      364 [363] e
      #"print_ifc_event: pe\[(\S+)\] port\[(\S+)\] ifc_type\[(\S+)\]"
      "print_ifc_event1"
      "pgtxn"
      "ifup"
      (fn [strm event regex-match]
        (assoc strm
          :next_expected "print_ifc_event2"
          )
       )
    )
    (process-state-ek1-mk2val
      365 [364] e
      #"print_ifc_event: .* phy_mac\[(\S+)\] bundle\[\] .* uuid\[(\S+)\] nic\[(\d+)\] .*"
      "print_ifc_event2"
      "pgtxn"
      "ifup"
      (fn [strm event regex-match]
        (assoc strm
          :next_expected "proposed physical interface name"
          :uuid (get regex-match 2)
          )
       )
    )
    (process-state-ek1-mk2val
      366 [365] e
      #"notify_ifup_internal_fpool: proposed physical interface name is (\S+)"
      "proposed physical interface name"
      "pgtxn"
      "ifup"
      (fn [strm event regex-match]
        (assoc strm
          :next_expected "completed ifup_internal in pem_helper"
          )
       )
    )
    (process-state-ek1-mk2val
      367 [366] e
      #"notify_ifup_internal_fpool: ifup for (\S+) name:(\S+) ended"
      "completed ifup_internal in pem_helper"
      "pgtxn"
      "ifup"
      (fn [strm event regex-match]
        (assoc strm
          :next_expected "ifup plumid"
          )
       )
    )
    (process-state-ek1-mk2val
      368 [367] e
      #"ifup_internal: port_id=(\d+) plum_id=(\d+) ifc_cookie=(\S+)"
      "ifup plumid"
      "pgtxn"
      "ifup"
      (fn [strm event regex-match]
        (assoc strm
          :next_expected "ifup latency measurment"
          )
       )
    )
    (process-state-ek1-mk2val
      369 [367 368] e
      #"ifup_latency: (\d+) name: (\S+) type: (\S+)"
      "ifup latency"
      "pgtxn"
      "ifup"
      (fn [strm event regex-match]
        (assoc strm
          :next_expected "ifup completion"
          )
       )
    )
    (process-state-ek1-mk2val
      370 [369] e
      #"dp_pgname=(\S+): Completed ifup_internal \[ifup (\S+) .*\]"
      "ifup completed"
      "pgtxn"
      "ifup"
      (fn [strm event regex-match]
        (do
          (def concise-msg (apply str (now) " : ""Interface (name:" (:name strm) ", type:" (:type strm) ", mac:" (:mac strm) ", uuid:" (:uuid strm) ") successfully attached to edge " (:edge strm) " <" (:my_host strm) "> <" (:ip strm) "> (took " (get-time-taken (:startTime strm) (:time strm)) " seconds)"
              )
            )
          )
        (file-write info-log-location [concise-msg "\n"])
        "DELETE"
        )
      )
    )
  )
(defn ifup_expired [& children]
  (fn [strm]
    (let [concise-msg (apply str (now) " : ""Interface (name:" (:name strm) ", type:" (:type strm) ", mac:" (:mac strm) ", uuid:" (:uuid strm) ") failed to attach to edge " (:edge strm) "<" (:my_host strm) "> <" (:ip strm) "> Last good known state is '" (:description strm) "'. Next expected state is '" (:next_expected strm) "'. Transaction id: " (:pgtxn strm) ".")]
      (file-write error-log-location [concise-msg "\n"])
      )
    )
  )
