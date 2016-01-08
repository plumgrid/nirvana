(include "../pg_utils.clj")

(ns plugins.process-crash-count
  "process crash count handlers"
  (:require [riemann.config :refer :all]
            [clojure.string :as str]
            [riemann.streams :refer :all]))

(defn process-crash-count []
  (fn [e]
    (def start-state (get start-states-map :process-crash-count))
    (def service "process_crash_count")
    (process-state-k1-l1-s
      start-state [] e
      ; [process_child_exit]: ippid 10.10.0.230:28689 Exited service pem_master pgname pem_master_b2144351
      #"\[process_child_exit\]: ippid (\S+):(\d+) Exited service (\S+) pgname (\S+).*"
      "exit message received"
      "pgname"
      4
      service
      (fn [strm event regex-match]
        (let [pgname (get regex-match 4) crash-threshold 3]
          ; First of all see whether this event has been expired earlier?
          (let [existed-service-index-entry (first (search-k1-v1-s "pgname" pgname service))]
            ; If this is NOT the first time,
            (if (not= existed-service-index-entry nil)
              (do
                ; See how many times it had been crashed earlier?
                (let [exit-count (:count existed-service-index-entry)]
                  ; If exit count is more than the accepted threshold
                  (if (= exit-count crash-threshold)
                    (do
                      ; Leave an error note.
                      (prn "Entry exist. count === " exit-count)
                      (let [concise-msg (apply str "ERROR: service with pgname " (get regex-match 4)
                                          " has been expired " (+ exit-count 1) " times in last x hours"
                                          )]
                        (file-write error-log-location [concise-msg "\n"])
                        )
                      ; And reset the count.
                      (assoc strm
                        :host (get regex-match 4) ; exited service name
                        :service service
                        :pgname (get regex-match 4)
                        :count 1
                        :ttl 100
                        )
                      )
                    (do
                      (prn "Entry exist. count = " exit-count)
                      (assoc strm
                        :host (get regex-match 4) ; TODO: Remove this.
                        :service service
                        :pgname (get regex-match 4)
                        :count (+ exit-count 1)
                        :ttl 100
                        )
                      )
                    )
                  )
                )
              ; If this is the first time.
              (do
                (prn "Entry NOT exist.")
                (assoc strm
                  :host (get regex-match 4) ; exited service name
                  :service service
                  :pgname (get regex-match 4)
                  :count 1
                  :ttl 100
                  )
                )
              )
            )
          )
        )
      )
    )
  )


(defn process-crash-count-expired [& children]
  (fn [strm]
    (prn "event is expired")
    (let [concise-msg (apply str "INFO: service with pgname " (:pgname strm)
               " has been expired " (:count strm) "times in last x hours"
                )]
      (prn concise-msg)
      (file-write info-log-location [concise-msg "\n"])
      )
    )
  )
