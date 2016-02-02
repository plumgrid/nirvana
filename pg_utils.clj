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

;;; Process the PG Message
;;; Possible potential primary keys for later searches
(defn process-raw-PG-msg[e searchMsg]
  (def pgmatch
    ;    (re-find #"(^[a-zA-Z0-9_]+) \[([0-9a-f]+):([0-9a-f]+):([0-9a-f]+)\]<([^>]+)>\[\D+\]: (.*)" msg)
    (re-find #"(^[a-zA-Z0-9_]+) \[([0-9a-f]+):([0-9a-f]+):([0-9a-f]+)\]<([^>]+)>\[(\d+)\]:(.*)" searchMsg)
    )
  (assoc e
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

; Search on basis of values of host and service
(defn search-host-service [host-value service-value]
  (->> (list 'and (list '= :host host-value)
         (list '= :service service-value)
         )
    (riemann.index/search (:index @riemann.config/core)))
  )

;(defn search-host-service-plugintype [host-value service-value plugintype-value]
;  (->> (list 'and (list '= :host host-value)
;         ('and (list '= :service service-value)
;               (list '= :plugin-type plugintype-value))
;         )
;    (riemann.index/search (:index @riemann.config/core)))
;  )

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
                                         :handler (:handler curr-event)
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

(defn add-index [myState entryStateList curr-event desc host-val service-val buildStateStream regex-match]
  ; Supply values which either does not change (follow fix pattern like pgstate) or default values.
  (prn "add: my state is: " myState)
  (let [new-dt (buildStateStream {
                                   :description desc
                                   :host host-val
                                   :service service-val
                                   :my_host (:host curr-event)
                                   :handler (:handler curr-event)
                                   :pgstate myState
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

;;;; Call this function if both keys, on basis of which you want to search, is part of your original event.
;;;; (search keys would be among fields defined under process-raw-PG-msg).
(defn process-state-ek1-ek2
  ([myState entryStateList curr-event searchRe desc ek1 ek2 buildStateStream]
    ; Search whether event message matches with regex?
    (let [regex-match (re-find searchRe (:pgmsg curr-event))]
      ; if matches
      (if (not= regex-match nil)
        ; and if the state is not the starting state
        (if (not= entryStateList (vector))
          (do
            ; then there must be some entry in index. Search for that entry, And update the index.
            (let [search-val1 ((keyword ek1) curr-event) search-val2 ((keyword ek2) curr-event)]
              (update-index myState entryStateList curr-event desc buildStateStream regex-match (search-host-service search-val1 search-val2))
              )
            )
          ; This is a case when the state is starting state
          (do
            (let [host-val ((keyword ek1) curr-event) service-val ((keyword ek2) curr-event)]
              (add-index myState entryStateList curr-event desc host-val service-val buildStateStream regex-match)
              )
            )
          ))))
  ([myState entryStateList curr-event searchRe desc search-key service]
    (process-state-ek1-ek2 myState entryStateList curr-event searchRe desc search-key service (fn [strm curr-event regexOut] strm))
    )
  )

;;;; Call this function if you have two keys on basis of which you search.
;;;; One key is part of original event (You will get value of it from original event),
;;;; while other key is your own defined key (you have to supply its value on your own).
(defn process-state-ek1-mk2val
  ([myState entryStateList curr-event searchRe desc ek1 mk2val buildStateStream]
    ; Search whether event message matches with regex?
    (let [regex-match (re-find searchRe (:pgmsg curr-event))]
      ; if matches
      (if (not= regex-match nil)
        ; and if the state is not the starting state
        (if (not= entryStateList (vector))
          (do
            ; then there must be some entry in index. Search for that entry, And update the index.
            (let [search-val1 ((keyword ek1) curr-event)]
              (update-index myState entryStateList curr-event desc buildStateStream regex-match (search-host-service search-val1 mk2val))
              )
            )
          ; This is a case when the state is starting state
          (do
            (let [host-val ((keyword ek1) curr-event)]
              (add-index myState entryStateList curr-event desc host-val mk2val buildStateStream regex-match)
              )
            )
          ))))
  ([myState entryStateList curr-event searchRe desc ek1 mk2val]
    (process-state-ek1-mk2val myState entryStateList curr-event searchRe desc ek1 mk2val (fn [strm curr-event regexOut] strm))
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
    (process-state-k1-l1-s myState entryStateList curr-event searchRe desc search-key regex-loc service (fn [strm curr-event regexOut] strm))
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

  (if (= nil (get tmp_lst_next_expected 0))
    (do
      (conj! tmp_lst_next_expected "CM")
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

;;;; Will remove the last character from string e.g 172.16.1.1} will be converted to 172.16.1.1
(defn remove-from-end [s end]
  (if (.endsWith s end)
      (.substring s 0 (- (count s)
                         (count end)))
    s))

;;;; Convert the Hex IP to dotted IP. e.g 0a161b1a will be converted to 10.22.27.26.
(defn convert-hexip-to-dottedip [hexip]
  (let [A (Integer/parseInt (clojure.string/join (take 2 hexip)) 16)
        B (Integer/parseInt (clojure.string/join (take 2 (drop 2 hexip))) 16)
        C (Integer/parseInt (clojure.string/join (take 2 (drop 4 hexip))) 16)
        D (Integer/parseInt (clojure.string/join (take 2 (drop 6 hexip))) 16)]
    (let [v [A B C D]]
      (str/join "." v)
      )
    )
  )

;;;; Get time.
(defn now [] (new java.util.Date))
