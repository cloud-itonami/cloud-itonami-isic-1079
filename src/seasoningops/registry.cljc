(ns seasoningops.registry
  "Pure validation functions for instant-seasoning/soup-mix (dry-mix
  blending and packaging) manufacturing production parameters. These are
  called by the Governor to independently verify physical/operational
  critical-control-point constraints -- the advisor's confidence is NOT
  sufficient to override these checks.

  All functions here are pure arithmetic/set/boolean predicates with no
  host-clock or I/O calls, so this namespace stays trivially portable
  across Clojure/ClojureScript. Callers that need the current time (see
  `metal-detector-calibration-overdue?`) obtain it themselves via a
  `:clj`/`:cljs` reader-conditional at the call site (see
  `seasoningops.governor`)."
  (:require [clojure.set :as set]))

(defn moisture-content-exceeds-max?
  "Independently verify that the batch's actual finished-mix moisture
  content does not exceed the product's maximum allowable level (CCP1
  analogue). Above the product's maximum indicates the mix carries more
  free/bound water than the low-moisture safety margin allows -- a hard
  food-safety hazard (microbial growth / hygroscopic caking), never
  overridable by advisor confidence."
  [actual-percent max-percent]
  (> actual-percent max-percent))

(defn blend-homogeneity-below-minimum?
  "Independently verify that the batch's actual blend-homogeneity/
  uniformity score did not fall below the product's minimum required
  level (CCP2 analogue). Below minimum indicates the mixing step may not
  have achieved uniform dispersal of active ingredients (salt,
  preservatives, flavor actives) or allergenic ingredients across the
  batch."
  [actual-percent min-percent]
  (< actual-percent min-percent))

(defn water-activity-exceeds-max?
  "Independently verify that the batch's actual water activity (Aw) does
  not exceed the product's maximum allowable level. For a shelf-stable
  low-moisture product, Aw (available/free water) is the parameter that
  actually governs microbial-growth risk, distinct from raw moisture
  content."
  [actual-aw max-aw]
  (> actual-aw max-aw))

(defn microbial-load-exceeds-max?
  "Independently verify that the batch's actual microbial-load test
  result (aerobic plate count or equivalent pathogen-indicator CFU/g)
  does not exceed the product's maximum allowable level. Dried spices/
  seasonings are a recognised Salmonella contamination vector; a failed
  microbial test is a hard, un-overridable hold regardless of advisor
  confidence."
  [actual-cfu-per-g max-cfu-per-g]
  (> actual-cfu-per-g max-cfu-per-g))

(defn shelf-life-exceeded?
  "Independently verify that the batch's elapsed time since production
  has not exceeded the product's maximum shelf-life hours. Exceeding
  shelf life is a use-by-date violation -- the batch's safety/quality
  margin is exhausted."
  [actual-hours-elapsed max-hours]
  (> actual-hours-elapsed max-hours))

(defn metal-detector-calibration-overdue?
  "Independently verify that the metal-detection inspection equipment
  (catches tramp metal fragments introduced during raw-material intake,
  mixing, or filling before the finished product ships) was calibrated
  within the last 48 hours. Dry-mix blending/packaging lines run lower-
  throughput, batch-based production than a continuous prepared-meal
  cook line, so this actor's reference recalibration interval is longer
  (48h) than ISIC 1075's shift-based (24h) interval.
  `last-calibration-epoch-ms` and `now-epoch-ms` are both epoch
  milliseconds -- callers obtain `now` via a `:clj`/`:cljs`
  reader-conditional, keeping this namespace free of any host-clock
  call."
  [last-calibration-epoch-ms now-epoch-ms]
  (> (- now-epoch-ms last-calibration-epoch-ms)
     (* 48 60 60 1000)))

(defn weight-variance-excessive?
  "Independently verify that a batch's finished-product fill-weight
  variance (drift from target net weight per sachet/pouch/canister, in
  grams) does not exceed the maximum tolerance. Excessive variance
  indicates the filling line is out of calibration or the fill weight
  was measured incorrectly -- also a labeled-net-quantity compliance
  concern."
  [actual-variance-grams max-variance-grams]
  (> actual-variance-grams max-variance-grams))

(defn allergen-label-mismatch?
  "True when the batch's actual cross-contact allergen risk set (e.g.
  wheat, soy, milk, egg, sesame -- from shared blending/filling equipment
  on a mixed-product-line dry-mix facility) is not fully covered by
  `declared-allergens` (mislabeling / under-declaration risk -- a genuine
  food-safety hazard for allergic consumers). Declaring MORE than the
  actual risk set is conservative and never a risk."
  [cross-contact-risk declared-allergens]
  (boolean
   (seq (set/difference (set cross-contact-risk) (set declared-allergens)))))

(defn foreign-material-detected?
  "Independently verify a batch's foreign-material-detection result
  (metal or other dense-fragment contamination caught by metal-detector
  inspection during/after mixing and packaging). Any detection is a
  genuine physical hazard -- this predicate simply coerces the raw fact
  to a boolean so the Governor's check functions stay uniform in shape
  with every other independently-verified physical constraint in this
  namespace."
  [actual-detected?]
  (boolean actual-detected?))

(defn sanitation-score-insufficient?
  "Independently verify that the plant's pre-production sanitation/
  pest-control/cross-contamination-control score meets the minimum
  required. Score is 0-100, assessed by a third-party auditor against
  food-safety sanitation standards -- a significant concern for
  mixed-product dry-mix lines handling multiple raw ingredients and
  allergens on shared blending/filling equipment."
  [actual-score min-score-required]
  (< actual-score min-score-required))

(defn packaging-seal-compromised?
  "Independently verify a batch's packaging seal-integrity inspection
  result. Instant-seasoning/soup-mix shelf-stability relies entirely on
  intact moisture-barrier packaging (foil pouch/sachet or lined canister)
  to keep the finished mix below its safe water-activity ceiling for the
  claimed shelf life -- a compromised seal lets ambient moisture in and
  undermines the shelf-life calculation entirely. This predicate simply
  coerces the raw fact to a boolean so the Governor's check functions
  stay uniform in shape."
  [actual-compromised?]
  (boolean actual-compromised?))
