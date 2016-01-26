;Copyright 2015 PLUMgrid Inc.

;Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.
;You may obtain a copy of the License at:

;http://www.apache.org/licenses/LICENSE-2.0

;Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS"
;BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific
;language governing permissions and limitations under the License.

(require '[clojure.string :as str])
(require '[clojure.java.io :as io])


; health status
(def dhtvalues {:green "ok" :yellow "warn" :red "critical"})

(defn check-tags [tag taglist]
  (some?
    (some #{tag} taglist)
    )
  )

(defn parse-int [s]
  (Integer/parseInt (re-find #"\A-?\d+" s)))

; Process the PG Message
(defn process-raw-PG-msg[msg searchMsg]
  (def pgmatch
    ;    (re-find #"(^[a-zA-Z0-9_]+) \[([0-9a-f]+):([0-9a-f]+):([0-9a-f]+)\]<([^>]+)>\[\D+\]: (.*)" msg)
    (re-find #"(^[a-zA-Z0-9_]+) \[([0-9a-f]+):([0-9a-f]+):([0-9a-f]+)\]<([^>]+)>\[(\d+)\]:(.*)" searchMsg)
    )
  (assoc msg
    ;:service (get pgmatch 1)
    :handler (get pgmatch 1)
    :pgtxn (get pgmatch 2)
    :pgfiber (get pgmatch 3)
    :pgseverity (get pgmatch 4)
    :pgtime (get pgmatch 5)
    :pglineno (get pgmatch 6)
    :pgmsg (get pgmatch 7)
    :metric 1
    )
  )

(defn create-file-if-not-exists [file-path]
  (let [directory (.getParent (clojure.java.io/file file-path))]
    (if (not (.exists (io/file directory)))
      (do
        (prn "Directory does not exist.")
        (.mkdir (io/file directory))
        )
      )
    )
    (if (not (.exists (io/file file-path)))
      (do
        (prn "File does not exist.")
        (.createNewFile (io/file file-path))
        )
      )
  )

(defn file-write [file-path lines]
  (create-file-if-not-exists file-path)
  (with-open [wtr (io/writer file-path :append true)]
    (doseq [line lines] (.write wtr line))))


(defn gen-warning [expStateList, event, msg]
  (if (= (.indexOf expStateList (:pgstate event)) -1)
    (let [st2 (apply str "WARN::" (:startTime event) " " msg " Out of order sequence detected. Request for " (:requestFor event) " recevied state ["
                (:pgstate event) "] instead of " (str/join "," expStateList))]
      (prn st2)
      (file-write "./log/plumgrid.warn.log" [st2 "\n"])
      )
    )
  )

; Search on basis 1 key value pair and service_name.
(defn search-k1-v1-s [search-key search-value service]
  (->> (list 'and (list '= (keyword search-key) search-value)
         (list '= :service service)
         )
    (riemann.index/search (:index @riemann.config/core)))
  )

(defn update-index [myState entryStateList curr-event desc buildStateStream regex-match events]
  (clojure.string/join "\n"
    (map
    (fn [e]
      (gen-warning entryStateList e (str/join "" ["L" myState]))
      (prn "my state is: " myState)
      (let [new-dt (buildStateStream (assoc e
                                         :PreviousState (:pgstate e)
                                         :pgstate myState
                                         :description desc
                                         :pgtxn (:pgtxn curr-event)
                                         :time (:time curr-event)
                                         ) curr-event regex-match)]
        (prn "new-dt=" new-dt)
        (if (= new-dt "DELETE")
          (riemann.index/delete (:index @riemann.config/core) e)
          ((index) new-dt)
          )
        )
      )
    events)
    )
  )

(defn add-index [myState entryStateList curr-event desc buildStateStream regex-match]
  ; Supply values which either does not change (follow fix pattern like pgstate) or default values.
  (prn "add: my state is: " myState)
  (let [new-dt (buildStateStream {
                                   :handler (:handler curr-event)
                                   :pgstate myState
                                   :description desc
                                   :pgtxn (:pgtxn curr-event)
                                   :startTime (:time curr-event)
                                   :time (:time curr-event)
                                   :metric 1
                                   ;:ttl (:ttl curr-event) ; Default value
                                   } curr-event regex-match)]
    ; TODO: We need to see here whether new-dt is NIL?
    (prn "add: new-dt=" new-dt)
    ((index) new-dt)
    )
  )

; Call this function when
; 1) key, on basis of which we will search, is part of event e.g pgtxn.
(defn process-state-k1-s
  ([myState entryStateList curr-event searchRe desc search-key service buildStateStream]
    ; Search whether event message matches with regex?
    (let [regex-match (re-find searchRe (:pgmsg curr-event))]
      ; if matches
      (if (not= regex-match nil)
        ; and if the state is not the starting state
        (if (not= entryStateList (vector))
          (do
            ; then there must be some entry in index. Search for that entry, And update the index.
            (let [search-value ((keyword search-key) curr-event)]
              (update-index myState entryStateList curr-event desc buildStateStream regex-match (search-k1-v1-s search-key search-value service))
              )
            )
          ; This is a case when the state is starting state
          (add-index myState entryStateList curr-event desc buildStateStream regex-match)
          ))))
  ([myState entryStateList curr-event searchRe desc search-key service]
    (process-state-k1-s myState entryStateList curr-event searchRe desc search-key service (fn [strm curr-event regexOut] strm))
    )
  )

; Call this function
;   - if you donot want to index (You just want to call the supplied function).
(defn process-state-no-index
  ([myState entryStateList curr-event searchRe buildStateStream]
    ; Search whether event message matches with regex?
    (let [regex-match (re-find searchRe (:pgmsg curr-event))]
      ; if matches
      (if (not= regex-match nil)
        ; and if the state is not the starting state
        (buildStateStream curr-event regex-match)
        )
      )
    )
  )

; Call this function when
; 1) key, on basis of which we will search, is part of regex search result.
(defn process-state-k1-l1-s
  ([myState entryStateList curr-event searchRe desc search-key regex-loc service buildStateStream]
    ; Search whether event message matches with regex?
    (let [regex-match (re-find searchRe (:pgmsg curr-event))]
      ; if matches
      (if (not= regex-match nil)
        ; and if the state is not the starting state
        (if (not= entryStateList (vector))
          (do
            ; then there must be some entry in index. Search for that entry, And update the index.
            (let [search-value (get regex-match regex-loc)]
              (update-index myState entryStateList curr-event desc buildStateStream regex-match (search-k1-v1-s search-key search-value service))
              )
            )
          ; This is a case when the state is starting state
          (add-index myState entryStateList curr-event desc buildStateStream regex-match)
          ))))
  ([myState entryStateList curr-event searchRe desc search-key regex-loc service]
    (process-state-k1-s myState entryStateList curr-event searchRe desc search-key regex-loc service (fn [strm curr-event regexOut] strm))
    )
  )

(defn convert-pg-time [time]
  (if (not= time nil)
    (let [tm (str/split time #"[\:\.]")]
      (+ (*
           (+ (*
                (+ (*
                     (+ (*
                          (Integer/parseInt (tm 0)) 24)
                       (Integer/parseInt (tm 1)))
                     60)
                  (Integer/parseInt (tm 2))
                  )
                60)
             (Integer/parseInt (tm 3))
             )
           1000000
           )
        (Integer/parseInt (tm 4))
        )
      )
    )
  )

(defn get-time-taken [startTime time]
  (float (- time startTime))
  )

;;;; For director-bootup-stage2 plugin. In case when it stucks at some place.

;;; After rest_gateay and cdb is up in serial fashion, one of the following service will come up in any order
;;; *) health_monitor *) tenant_manager *) tunnel_service *) analytics_manager
;;; In case, systes stucks at any place, we need to tell in nirvana log that what services have been sucessfully up and
;;; what are the next expected services?
;;; Following function will try to figure out that which services have already been up and which one are expected next.

(defn get-next-expected-operator-service [strm event-service]
  (def tmp_lst_next_expected (transient []))
  ;; See whether health_monitor is already up. If NOT, add it to set of next expected services.
  (if (and (not= (:health_monitor strm) "active") (not= event-service "health_monitor"))
    (do
      (conj! tmp_lst_next_expected "health_monitor")
      )
    )
  ;; See whether tenant_manager is already up. If NOT, add it to set of next expected services.
  (if (and (not= (:tenant_manager strm) "active") (not= event-service "tenant_manager"))
    (do
      (conj! tmp_lst_next_expected "tenant_manager")
      )
    )
  ;; See whether tunnel_service is already up. If NOT, add it to set of next expected services.
  (if (and (not= (:tunnel_service strm) "active") (not= event-service "tunnel_service"))
    (do
      (conj! tmp_lst_next_expected "tunnel_service")
      )
    )
  ;; See whether analytics_manager is already up. If NOT, add it to set of next expected services.
  (if (and (not= (:analytics_manager strm) "active") (not= event-service "analytics_manager"))
    (do
      (conj! tmp_lst_next_expected "analytics_manager")
      )
    )
  (persistent! tmp_lst_next_expected)
  )

(defn str-to-regex [c]
  (re-pattern (java.util.regex.Pattern/quote (str c))
    )
  )

(defn get-substr-after-nth-occurance-of-char [string c n]
  (let [pgpath (str/split string (str-to-regex c))]
      (get pgpath n)
    )
  )

; /0/connectivity/domain/Demo
(defn get-substr-after-last-char [string c]
  (let [pgpath (str/split string (str-to-regex c))]
    (let [length (count pgpath)]
      (get pgpath (- length 1))
      )
    )
  )
