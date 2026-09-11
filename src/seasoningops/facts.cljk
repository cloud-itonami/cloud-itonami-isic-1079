(ns seasoningops.facts
  "Reference facts for instant-seasoning/soup-mix manufacturing (dry-mix
  blending and packaging lines -- ISIC 1079 'other food products n.e.c.',
  illustrated here by instant dashi/soup-stock powder, cream-based soup
  mix, dry spice blend, and bouillon granules): product-type processing
  parameters (moisture-content/blend-homogeneity/water-activity/
  microbial-load/shelf-life windows), jurisdiction evidence-checklist
  requirements. This namespace contains pure lookup functions for
  regulatory/food-safety compliance checks -- the Governor calls these to
  independently validate proposals; the advisor's confidence is never
  sufficient on its own."
  (:require [clojure.set :as set]))

(def product-types
  "Valid instant-seasoning/soup-mix product categories and their safe
  dry-mix blending/packaging processing windows. `moisture-content-max-
  percent` is the maximum total moisture content (%, wet basis) the
  finished mix may carry -- excess moisture in a low-moisture product
  enables microbial growth and hygroscopic caking/clumping (FDA Draft
  Guidance for Industry: Control of Salmonella in Low-Moisture Foods;
  Codex Alimentarius CXC 68-2013 Code of Hygienic Practice for
  Low-Moisture Foods, both name moisture control as a primary control for
  this product category). `blend-homogeneity-min-percent` is the minimum
  blend-uniformity score (a mixing-validation metric, e.g. coefficient-
  of-variation-derived uniformity index) the batch must reach after
  mixing -- inadequate homogeneity risks pockets of under/over-dosed
  active ingredients (salt, preservatives, flavor actives) and uneven
  allergen dispersal across a mixed-menu blending line, this is this
  actor's CCP2 analogue to a cook-line's chill-time check. `water-
  activity-max` is the maximum allowable water activity (Aw) -- for
  shelf-stable low-moisture product this is a defining safety parameter
  distinct from raw moisture content (bound vs. free/available water);
  most instant-seasoning/soup-mix product stays well below Aw 0.6, the
  threshold below which most bacterial pathogens cannot grow.
  `microbial-load-max-cfu-per-g` is the maximum allowable aerobic-plate-
  count (or equivalent pathogen-indicator) load in colony-forming units
  per gram -- dried spices/seasonings are a recognised Salmonella
  contamination vector (multiple FDA/CDC multistate outbreak
  investigations have traced back to spice-blend and bouillon-type
  products) so this actor treats a failed microbial test as a hard,
  un-overridable hold. `max-shelf-life-hours` is the maximum time from
  production to use-by the product may be held before its safety/quality
  margin is exhausted; unlike ISIC 1075's refrigerated ready-meals, this
  product category is shelf-stable at ambient temperature so there is no
  cold-chain window to track -- moisture-barrier packaging integrity is
  what protects the shelf-life claim instead."
  {:seasoning/instant-dashi-powder
   {:id :seasoning/instant-dashi-powder
    :name "インスタントだしパウダー(かつお/昆布ベース)"
    :moisture-content-max-percent 5.0
    :blend-homogeneity-min-percent 92.0
    :water-activity-max 0.60
    :microbial-load-max-cfu-per-g 10000
    :max-shelf-life-hours 8760.0}

   :seasoning/western-soup-mix-cream
   {:id :seasoning/western-soup-mix-cream
    :name "西洋風クリームスープミックス(コーンポタージュ等)"
    :moisture-content-max-percent 4.0
    :blend-homogeneity-min-percent 90.0
    :water-activity-max 0.55
    :microbial-load-max-cfu-per-g 10000
    :max-shelf-life-hours 10950.0}

   :seasoning/dry-spice-blend-curry
   {:id :seasoning/dry-spice-blend-curry
    :name "ドライスパイスブレンド(カレー粉等)"
    ;; Whole/ground spice particles carry more inherent moisture and
    ;; blend less uniformly than fine powders, so this product type's
    ;; reference windows are looser on moisture/homogeneity than the
    ;; powder-based product types above.
    :moisture-content-max-percent 8.0
    :blend-homogeneity-min-percent 88.0
    :water-activity-max 0.65
    :microbial-load-max-cfu-per-g 100000
    :max-shelf-life-hours 17520.0}

   :seasoning/bouillon-granules
   {:id :seasoning/bouillon-granules
    :name "ブイヨン/コンソメ顆粒"
    :moisture-content-max-percent 3.0
    :blend-homogeneity-min-percent 90.0
    :water-activity-max 0.50
    :microbial-load-max-cfu-per-g 10000
    :max-shelf-life-hours 13140.0}})

(defn product-type-by-id [id]
  (get product-types id))

(def jurisdictions
  "Instant-seasoning/soup-mix manufacturing jurisdictions and their
  evidence-checklist requirements."
  {:jp/mhlw
   {:id :jp/mhlw
    :name "日本 (食品衛生法・HACCP制度化・厚生労働省)"
    :required-evidence
    [:raw-material-intake-record
     :mixing-blend-record
     :moisture-content-test
     :water-activity-test
     :microbial-test
     :allergen-declaration
     :weight-check
     :packaging-seal-check]}

   :us/fda
   {:id :us/fda
    :name "United States (FDA Food Code / Low-Moisture Foods Guidance / HACCP)"
    :required-evidence
    [:raw-material-intake-record
     :mixing-blend-record
     :moisture-content-test
     :water-activity-test
     :microbial-test
     :allergen-declaration
     :weight-check
     :packaging-seal-check]}

   :eu/efsa
   {:id :eu/efsa
    :name "European Union (Regulation (EC) 852/2004 & 853/2004 hygiene package)"
    :required-evidence
    [:raw-material-intake-record
     :mixing-blend-record
     :moisture-content-test
     :water-activity-test
     :microbial-test
     :allergen-declaration
     :weight-check
     :packaging-seal-check]}})

(defn jurisdiction-by-id [id]
  (get jurisdictions id))

(defn required-evidence-satisfied?
  "Verify that every item in the jurisdiction's `:required-evidence` list
  is present in `evidence`. `jurisdiction` may be a resolved jurisdiction
  map (as returned by `jurisdiction-by-id`) or a raw jurisdiction id --
  both call conventions are in use (tests pass a resolved map; the
  Governor passes the raw id straight off batch metadata)."
  [jurisdiction evidence]
  (let [j (if (map? jurisdiction) jurisdiction (jurisdiction-by-id jurisdiction))]
    (if-not j
      false
      (set/subset? (set (:required-evidence j)) (set evidence)))))

(defn moisture-content-within-max?
  "Positive-sense convenience predicate: does `percent` stay at or below
  `product`'s maximum allowable moisture content?"
  [percent product]
  (boolean
   (and (some? product)
        (<= percent (:moisture-content-max-percent product)))))

(defn blend-homogeneity-meets-minimum?
  "Positive-sense convenience predicate: does `percent` meet or exceed
  `product`'s minimum required blend-homogeneity/uniformity score?"
  [percent product]
  (boolean
   (and (some? product)
        (>= percent (:blend-homogeneity-min-percent product)))))

(defn water-activity-within-max?
  "Positive-sense convenience predicate: does `aw` stay at or below
  `product`'s maximum allowable water activity?"
  [aw product]
  (boolean
   (and (some? product)
        (<= aw (:water-activity-max product)))))

(defn microbial-load-within-max?
  "Positive-sense convenience predicate: does `cfu-per-g` stay at or
  below `product`'s maximum allowable microbial load?"
  [cfu-per-g product]
  (boolean
   (and (some? product)
        (<= cfu-per-g (:microbial-load-max-cfu-per-g product)))))

(defn shelf-life-within-max?
  "Positive-sense convenience predicate: has the batch stayed at or
  below `product`'s maximum shelf-life hours since production?"
  [hours-elapsed product]
  (boolean
   (and (some? product)
        (<= hours-elapsed (:max-shelf-life-hours product)))))
