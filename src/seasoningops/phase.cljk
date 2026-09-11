(ns seasoningops.phase
  "Phase machine: the states an instant-seasoning/soup-mix production
  batch transits through.

  State machine:
    :intake -> :weigh -> :mix -> :package -> :inspect -> :audit -> :archived

  `:intake` is raw-material receiving (spices, salt, starch, flavor
  bases, etc.); `:weigh` is precise per-formula batch weighing of each
  raw-material component; `:mix` is the blending/mixing step that
  produces the finished dry mix -- never directly controlled by this
  actor, mixing-line control remains exclusive to plant staff;
  `:package` is filling/sealing the finished mix into moisture-barrier
  pouches/sachets/canisters -- never directly controlled by this actor,
  packaging-line control remains exclusive to plant staff; `:inspect` is
  metal-detector, fill-weight, and seal-integrity inspection; `:audit` is
  compliance audit; `:archived` is the terminal state.

  Each transition can accept a proposal and yield an audit fact.")

(def all-phases
  "All valid phases in the instant-seasoning/soup-mix production
  workflow."
  [:intake :weigh :mix :package :inspect :audit :archived])

(def phase-sequence
  "Ordered phases representing normal batch progression."
  [:intake :weigh :mix :package :inspect :audit :archived])

(defn valid-phase?
  "Check if a phase is valid."
  [phase]
  (contains? (set all-phases) phase))

(defn- index-of
  "Portable (Clojure/ClojureScript) index lookup -- `.indexOf` is a
  JVM-only `java.util.List` method that ClojureScript's PersistentVector
  does not implement, so it is avoided here even though `phase-sequence`
  is a plain vector. Returns -1 when `x` is not found, matching
  `java.util.List/indexOf`'s contract."
  [coll x]
  (or (first (keep-indexed (fn [i v] (when (= v x) i)) coll)) -1))

(defn can-transition?
  "Check if a transition from one phase to another is valid
  (must be forward-only in the sequence, no backtracking). Always returns a
  boolean (never nil), including when either phase is invalid."
  [from-phase to-phase]
  (boolean
   (and (valid-phase? from-phase) (valid-phase? to-phase)
        (let [from-idx (index-of phase-sequence from-phase)
              to-idx (index-of phase-sequence to-phase)]
          (and (>= from-idx 0) (>= to-idx 0) (< from-idx to-idx))))))
