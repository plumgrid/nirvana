(include "../pg_utils.clj")

(ns plugins.resource-load
  "Edge Reconnect"
  (:require [riemann.config :refer :all]
            [clojure.string :as str]
            [riemann.streams :refer :all]))

(defn resource-load []
  (fn [e]
    (def start-state (get start-states-map :resource-load-state)) ; 300
    (def service "resource_load")
    (process-state-no-index
      start-state [] e
      ; system_manager_c5f58e51 [044cf721:1:8]<352:05:17:27.067906>[32]: IP: 172.16.1.5 CPU load: 96.21 Mem load: 13.72 FDs: 25 Avg load: 0.32/0.29/0.25 (inst. 4) Free disk: 81137MB
      #"IP: (\S+) CPU load: (\S+) Mem load: (\S+) FDs: .* Avg load: .* \(inst. .*\) Free disk: (\S+)"
      (fn [event regex-match]
        (let [ip (get regex-match 1) cpu-load (parse-int (get regex-match 2)) mem-load (parse-int (get regex-match 3))]
          (if (and (> cpu-load 90) (<= cpu-load 97))
            (do
              (def concise-msg (apply str "WARN::" (:time event) " CPU load of director node " (:host event)
                                 " IP:" ip " is at " cpu-load "."
                                 )
                )
              (file-write warn-log-location [concise-msg "\n"])
              )
            )
          (if (> cpu-load 97)
            (do
              (def concise-msg (apply str "ERROR::" (:time event) " CPU load of director node " (:host event)
                                 " IP:" ip " is at " cpu-load "."
                                 )
                )
              (file-write error-log-location [concise-msg "\n"])
              )
            )
          (if (and (> mem-load 90) (<= mem-load 97))
            (do
              (def concise-msg (apply str "WARN::" (:time event) " Memory load of director node " (:host event)
                                 " IP:" ip " is at " mem-load "."
                                 )
                )
              (file-write warn-log-location [concise-msg "\n"])
              )
            )
          (if (> mem-load 97)
            (do
              (def concise-msg (apply str "ERROR::" (:time event) " Memory load of director node " (:host event)
                                 " IP:" ip " is at " mem-load "."
                                 )
                )
              (file-write error-log-location [concise-msg "\n"])
              )
            )
          )
        )
      )
    )
  )
