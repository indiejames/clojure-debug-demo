(ns debug-demo.core)

(defn bar
 [num]
 (* num num))

(defn foo
  "I don't do a whole lot."
  [^long x]
  (println x "Hello, World!")
  (let [y 4
        z 10
        w (bar x)]
   (println "y = " y)
   (println "z = " z)
   (println "w = " w)))
