(include "./start_states_consts.clj")
(ns plugins.topo_load_via_cdb
  "topo_load_via_cdb"
  (:require [riemann.config :refer :all]
            [clojure.string :as str]
            [riemann.streams :refer :all]))
(defn topo_load_via_cdb []
  (fn [e]
    (process-state-ek1-mk2val
      400 [] e
      #"Requesting cdb to post config for :(\S+)"
      "CM request CDB to post config"
      "topo_load_via_cdb"
      "pgtxn"
      (fn [strm event regex-match]
        (assoc strm
          :plugin_type "topo_load_via_cdb"
          :domain_name (get regex-match 1)
          )
       )
    )
    (process-state-ek1-mk2val
      401 [400] e
      #"Dom0::domain_update_cb: Method (\d+) for domain key=(\S+)"
      "CM received domain update callback"
      "topo_load_via_cdb"
      "pgtxn"
      (fn [strm event regex-match]
        (assoc strm
          :next_expected "request config post"
          )
       )
    )
    (process-state-ek1-mk2val
      402 [401] e
      #"request_config_post: Completed processing config request for (\S+) in (\d+) usec with status (\d+)"
      "CDB completed post config"
      "topo_load_via_cdb"
      "pgtxn"
      (fn [strm event regex-match]
        (assoc strm
          :next_expected "processing config request complete"
          )
       )
    )
    (process-state-ek1-mk2val
      403 [402] e
      #"request_config_post returned with status 0"
      "Load Topology completed"
      "topo_load_via_cdb"
      "pgtxn"
      (fn [strm event regex-match]
        (do
          (def concise-msg (apply str (now) " : "": VND '" (:domain_name strm) "' created successfully (took"(get-time-taken (:startTime strm) (:time strm)) " secs) via CDB."
              )
            )
          )
        (file-write info-log-location [concise-msg "\n"])
        "DELETE"
        )
      )
    )
  )
(defn topo_load_via_cdb_expired [& children]
  (fn [strm]
    (let [concise-msg (apply str (now) " : "": VND '" (:domain_name strm) "' creation failed. Last good known state is '" (:description strm) "'. Next expected state is '" (:next_expected strm)"'. Transaction id: " (:pgtxn strm) ".")]
      (file-write error-log-location [concise-msg "\n"])
      )
    )
  )
