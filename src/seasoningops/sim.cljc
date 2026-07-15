(ns seasoningops.sim
  "Simulation driver for testing the instant-seasoning/soup-mix
  manufacturing operations actor end-to-end.

  For CLI: clojure -M:dev:run

  Example flow:
    1. Start with empty store
    2. Create a batch in :intake phase
    3. Propose a batch -> :package transition with processing parameters
    4. Governor validates parameters against facts
    5. If valid, audit fact is committed
    6. CLI prints audit trail")

(defn -main [& _args]
  (println "SeasoningOps simulation: not yet implemented.")
  (println "TODO: integrate langgraph-clj StateGraph when available."))
