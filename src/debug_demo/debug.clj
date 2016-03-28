(ns debug-demo.debug
  (:require [clojure.core.async :refer [thread]])
  (:import com.sun.jdi.Bootstrap
           com.sun.jdi.request.EventRequest
           com.sun.jdi.request.BreakpointRequest
           com.sun.jdi.request.StepRequest
           com.sun.jdi.BooleanValue
           com.sun.jdi.StringReference
           com.sun.jdi.LongValue
           com.sun.tools.jdi.LongValueImpl
           com.sun.tools.jdi.ObjectReferenceImpl))

(defn get-thread-with-name
 "Returns the ThreadReference with the given name"
 [vm name]
 (some (fn [thread-ref] 
         (when (= name (.name thread-ref)) thread-ref)) 
       (.allThreads vm)))

(defn get-frame
 "Get the frame at the given stack position for the named thread"
 [vm thread-name stack-pos]
 (let [thread-ref (get-thread-with-name vm thread-name)]
   (.frame thread-ref stack-pos)))
   
(defn list-frames
 "Returns a list of frames for the thread with the given thread."
 [vm name]
 (let [tr (get-thread-with-name vm name)]
  (.frames tr)))
  
(defn ref-type-has-src-path?
 "Returns true if the given reference type has a src file matching the given path."
 [ref-type src-path]
 (try 
    (when-let [src-paths (.sourcePaths ref-type "Clojure")]
      (some (fn [path] (.endsWith src-path path)) 
            src-paths))
    (catch Exception e)))

(defn ref-type-matching-location
  "Returns the matching line location for the reference type, or nil if none exists."
  [ref-type line]
  (let [locs (.allLineLocations ref-type)]
    (some (fn [loc] (when (= (.lineNumber loc "Clojure") line) loc)) 
          locs)))
          
(defn printable-variable
 "Get the printable value for a local variables Value."
 [value]
 (println "TYPE: " (type value))
 (cond
  (instance? BooleanValue value) (.value value)
  
  (instance? StringReference value) (.value value)
  
  (instance? LongValue value) (.value value)
  
  ;; TODO - Figure out a better way to print objects
  (instance? ObjectReferenceImpl value) (str value)
  
  :else (str value)))
 
(defn print-locals
  "Print the local variables and their values for the given stack frame.
  This method is not robust and converts all locals to strings to print them out.
  A real API should interrogate the local to determine its type and handle it 
  accordingly."
  [frame]
  (doseq [local (.visibleVariables frame)]
   (println (.name local) " = " (printable-variable (.getValue frame local)))))
   
; (defn set-value
;  "Set a value for a local variable."
;  [vm, frame variable-name value]
;  ;; TODO - Right now this only works for long values.
;  (if-let [variable (.visibleVariableByName frame variable-name)]
;   (.setValue frame variable (LongValueImpl. vm value))
;   (println "No such variable: " variable-name)))

(defn find-loc-for-src-line
  "Find the ref-type that matches the given src file path and line."
  [vm src-path line]
  (let [ref-types (.allClasses vm)]
    (some (fn [ref-type]
            (when (ref-type-has-src-path? ref-type src-path)
              (do
                (println "Ref type has src path.....")
                (ref-type-matching-location ref-type line))))
          ref-types)))
          
(defn set-breakpoint
 "Set a breakpoint"
 [vm src-path line]
 (when-let [loc (find-loc-for-src-line vm src-path line)]
   (let [_ (println "Found location...............")
         _ (println loc)
         evt-req-mgr (.eventRequestManager vm)
         breq (.createBreakpointRequest evt-req-mgr loc)]
      ; (.setSuspendPolicy breq com.sun.jdi.request.BreakpointRequest/SUSPEND_EVENT_THREAD)
      (.setSuspendPolicy breq com.sun.jdi.request.BreakpointRequest/SUSPEND_ALL)
      (.enable breq))
   loc))
   
(defn- step
 "Step into or over called functions. Depth must be either StepRequest.STEP_INTO or
 StepRequest.STEP_OVER"
  [vm thread-name depth]
  (let [evt-req-mgr (.eventRequestManager vm)
        thread-ref (get-thread-with-name vm thread-name)
        step-req (.createStepRequest evt-req-mgr thread-ref StepRequest/STEP_LINE depth)]
   (.addCountFilter step-req 1) ;; one step only
   (.setSuspendPolicy step-req com.sun.jdi.request.EventRequest/SUSPEND_EVENT_THREAD)
   (.enable step-req)
   (.resume vm)))

(defn step-into
  "Step into called functions"
  [vm thread-name]
  (step vm thread-name StepRequest/STEP_INTO))
    
(defn step-over
  "Step over called functions"
  [vm thread-name]
  (step vm thread-name StepRequest/STEP_OVER))
   
(defn continue
 "Resume execution of a paused VM."
 [vm]
 (.resume vm))
           
(defn listen-for-events
  "List for events on the event queue and handle them."
  [evt-queue evt-req-mgr]
  (println "Listening for events....")
  (loop [evt-set (.remove evt-queue)]
    (println "Got an event............")
    (let [events (iterator-seq (.eventIterator evt-set))]
      (doseq [evt events
               :let [evt-req (.request evt)]]
        (cond 
          (instance? BreakpointRequest evt-req)
          (let [tr (.thread evt)
                line (-> evt-req .location .lineNumber)]
            (println "Thread: " (.name tr))
            (println "Breakpoint hit at line " line))
            
          (instance? StepRequest evt-req)
          (let [tr (.thread evt)
                frame (.frame tr 0)
                loc (.location frame)
                src (.sourceName loc)]
            (println "At location " (.lineNumber loc))
            (println "File: " src)
            ;; Need to remove a step request or we won't be able to make another one.
            (.deleteEventRequest evt-req-mgr evt-req))
          
          :default
          (println "Unknown event"))))
    (recur (.remove evt-queue))))
           
(defn setup-debugger
 "Intialize the debugger."
 []
 (let [vm-manager (com.sun.jdi.Bootstrap/virtualMachineManager)
       attachingConnectors (.attachingConnectors vm-manager)
       connector (some (fn [ac]
                          (when (= "dt_socket")
                                (-> ac .transport .name)
                            ac))
                       attachingConnectors)
       params-map (when connector (.defaultArguments connector))
       port-arg (when params-map (get params-map "port"))
       _ (when port-arg (.setValue port-arg 8030))]
   (when-let [vm (when port-arg (.attach connector params-map))]
     (println "Attached to process " (.name vm))
     (let [evt-req-mgr (.eventRequestManager vm)
           evt-queue (.eventQueue vm)]
       (thread (listen-for-events evt-queue evt-req-mgr)))
     vm)))