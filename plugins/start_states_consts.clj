(def start-states-map {
                        :director-bootup-stage1-state 50
                        :director-bootup-stage2-state 100
                        :director-exit-state 150
                        :edge-bootup-state 200
                        :edge-reconnect-state 250
                        :resource-load-state 300
                        :ifup-state 350
                        :topo-load-via-cdb-state 400
                        :topo-load-via-api-state 450
                        :process-crash-relaunch-start-state 2000
                        }
  )

(def error-log-location "/opt/pg/log/plumgrid.error.log")
(def warn-log-location "/opt/pg/log/plumgrid.warn.log")
(def info-log-location "/opt/pg/log/plumgrid.info.log")
