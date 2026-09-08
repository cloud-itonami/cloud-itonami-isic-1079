(ns seasoningops.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Drives THIS repo's real actor stack -- `seasoningops.operation/run-operation`
  -> `seasoningops.governor/check` -> `seasoningops.store` -- through a
  scenario and renders the resulting store + audit ledger deterministically.
  Every disposition, every violation rule, every spec limit shown on the page
  is read back out of real governor/store/facts output. Nothing on the page is
  a hand-typed row.

  Two things about this repo shape the design, both measured before writing
  (do not `fix` them here, they are findings, not bugs in this file):

  1. `langgraph` is NOT on this repo's classpath. `deps.edn` declares
     `:deps {}` and puts langgraph/langchain under `:dev :override-deps`,
     and `:override-deps` only rewrites coordinates for deps that are already
     in the tree -- it never adds one. Verified: `clojure -M:dev -e
     \"(require 'langgraph.graph)\"` throws FileNotFoundException. This repo
     also ships no StateGraph: `seasoningops.advisor` is an empty skeleton and
     `seasoningops.sim/-main` prints \"not yet implemented\". So the real
     stack here is the pure-function driver `operation/run-operation`, and
     that is what this renderer drives. Building a graph inside the renderer
     would mean the renderer inventing an actor topology the repo does not
     have.

  2. This repo ships NO seed data (`seasoningops.store` has no `seed-db` /
     `demo-data`). The batch fixtures below are therefore authored HERE, and
     the page says so. What is NOT authored here: every product spec limit,
     every jurisdiction evidence requirement, every gate posture and every
     verdict on the page is read at render time out of `seasoningops.facts`
     and `seasoningops.governor`, so a limit can never drift from the code.

  Determinism: the page carries no timestamps. Metal-detector calibration
  dates are the one clock-derived input (the Governor calls
  `System/currentTimeMillis` internally via
  `metal-detector-calibration-overdue?`), so fixtures express calibration as
  an AGE IN HOURS and the epoch value is computed from `now` and never
  printed. A 6-hour-old calibration is inside the 48h window and a 72-hour-old
  one is outside it at any wall-clock time, so both the verdicts and the page
  bytes are stable across runs.

  Usage: `clojure -M:dev:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [kotoba.lang.text :as str]
            [seasoningops.facts :as facts]
            [seasoningops.governor :as governor]
            [seasoningops.operation :as operation]
            [seasoningops.store :as store]))

;; ─────────────────────────── scenario fixtures ───────────────────────────

(def ^:private actor-context
  "The context `operation/run-operation` requires: an actor id and the fact
  constructor used when the Governor refuses."
  {:actor-id "seasoningops-advisor-1"
   :hold-fact-fn governor/hold-fact})

(def ^:private approver
  "The human who signs off escalated proposals in this scenario. Whether this
  identity survives into the committed store record is MEASURED at render
  time (see `attribution-report`) rather than asserted."
  "plant-operator-1")

(def ^:private hour-ms (* 60 60 1000))

(def ^:private clean-actuals
  "Per-product processing actuals that sit inside every window declared by
  `facts/product-types` for that product. Authored here (this repo ships no
  seed data); the LIMITS they are compared against are never authored here."
  {:seasoning/instant-dashi-powder
   {:moisture-content-percent 3.4 :blend-homogeneity-percent 95.5
    :water-activity 0.44 :microbial-load-cfu-per-g 620
    :shelf-life-hours-elapsed 240.0}
   :seasoning/western-soup-mix-cream
   {:moisture-content-percent 3.1 :blend-homogeneity-percent 93.8
    :water-activity 0.41 :microbial-load-cfu-per-g 480
    :shelf-life-hours-elapsed 310.0}
   :seasoning/dry-spice-blend-curry
   {:moisture-content-percent 6.4 :blend-homogeneity-percent 91.2
    :water-activity 0.52 :microbial-load-cfu-per-g 18000
    :shelf-life-hours-elapsed 900.0}
   :seasoning/bouillon-granules
   {:moisture-content-percent 2.2 :blend-homogeneity-percent 94.1
    :water-activity 0.38 :microbial-load-cfu-per-g 350
    :shelf-life-hours-elapsed 420.0}})

(defn- clean-batch
  "A batch record with every independently-verified parameter inside spec.
  The evidence checklist is DERIVED from the jurisdiction's own
  `:required-evidence` in `facts/jurisdictions`, so it can never drift from
  what `governor/check` demands."
  [product-type jurisdiction now]
  (merge {:product-type product-type
          :jurisdiction jurisdiction
          :foreign-material-detected? false
          :packaging-seal-compromised? false
          :metal-detector-last-calibration-date (- now (* 6 hour-ms))
          :calibration-age-hours 6
          :weight-variance-grams 2
          :cross-contact-risk #{}
          :declared-allergens #{}
          :sanitation-score 88
          :safety-concern-raised? false
          :safety-concern-resolved? false
          :evidence-checklist
          (vec (:required-evidence (facts/jurisdiction-by-id jurisdiction)))}
         (get clean-actuals product-type)))

(def ^:private fixtures
  "One fixture per Governor hard rule that is a property OF A BATCH, plus two
  clean batches. `:defect` is the single field pushed out of spec; everything
  else stays clean so each refusal isolates to exactly one rule. `:intent` is
  the scenario's own note, printed as-is."
  [{:id "SB-1001" :product-type :seasoning/instant-dashi-powder
    :jurisdiction :jp/mhlw :line "blend-line-A"
    :intent "clean -- runs the full lifecycle and both double-commit guards"}
   {:id "SB-1002" :product-type :seasoning/western-soup-mix-cream
    :jurisdiction :us/fda :line "blend-line-A"
    :defect {:moisture-content-percent 5.6}
    :intent "finished-mix moisture above the product window (CCP1 analogue)"}
   {:id "SB-1003" :product-type :seasoning/bouillon-granules
    :jurisdiction :eu/efsa :line "blend-line-B"
    :defect {:blend-homogeneity-percent 84.0}
    :intent "post-mixing blend uniformity below the product window (CCP2 analogue)"}
   {:id "SB-1004" :product-type :seasoning/dry-spice-blend-curry
    :jurisdiction :jp/mhlw :line "blend-line-B"
    :defect {:shelf-life-hours-elapsed 18600.0}
    :intent "held past the product's use-by window"}
   {:id "SB-1005" :product-type :seasoning/instant-dashi-powder
    :jurisdiction :us/fda :line "blend-line-A"
    :defect {:water-activity 0.71}
    :intent "water activity above the product window (free water, not raw moisture)"}
   {:id "SB-1006" :product-type :seasoning/dry-spice-blend-curry
    :jurisdiction :jp/mhlw :line "blend-line-C"
    :defect {:microbial-load-cfu-per-g 260000}
    :intent "aerobic plate count above the product window"}
   {:id "SB-1007" :product-type :seasoning/western-soup-mix-cream
    :jurisdiction :eu/efsa :line "fill-line-1"
    :defect {:foreign-material-detected? true}
    :intent "metal-detector inspection flagged tramp metal on this batch"}
   {:id "SB-1008" :product-type :seasoning/bouillon-granules
    :jurisdiction :jp/mhlw :line "fill-line-1"
    :defect {:calibration-age-hours 72}
    :intent "metal-detector calibration older than the 48h reference interval"}
   {:id "SB-1009" :product-type :seasoning/instant-dashi-powder
    :jurisdiction :us/fda :line "fill-line-2"
    :defect {:weight-variance-grams 9}
    :intent "fill-weight drift beyond the 5g tolerance"}
   {:id "SB-1010" :product-type :seasoning/dry-spice-blend-curry
    :jurisdiction :jp/mhlw :line "blend-line-C"
    :defect {:cross-contact-risk #{:wheat :soy} :declared-allergens #{:wheat}}
    :intent "soy cross-contact present on shared equipment but not declared"}
   {:id "SB-1011" :product-type :seasoning/western-soup-mix-cream
    :jurisdiction :eu/efsa :line "blend-line-B"
    :defect {:sanitation-score 61}
    :intent "plant sanitation score below the minimum of 75"}
   {:id "SB-1012" :product-type :seasoning/bouillon-granules
    :jurisdiction :jp/mhlw :line "fill-line-2"
    :defect {:packaging-seal-compromised? true}
    :intent "moisture-barrier seal integrity inspection failed"}
   {:id "SB-1013" :product-type :seasoning/instant-dashi-powder
    :jurisdiction :us/fda :line "blend-line-A"
    :defect {:safety-concern-raised? true :safety-concern-resolved? false}
    :intent "open food-safety concern, never resolved"}
   {:id "SB-1014" :product-type :seasoning/dry-spice-blend-curry
    :jurisdiction :jp/mhlw :line "blend-line-C"
    :evidence-drop #{:microbial-test}
    :intent "microbial test missing from the jurisdiction's evidence checklist"}
   {:id "SB-1015" :product-type :seasoning/bouillon-granules
    :jurisdiction :jp/mhlw :line "blend-line-B"
    :intent "clean -- exercises the proposal-shape and confidence gates"}])

(def ^:private unregistered-batch-id
  "Never seeded into the store -- proves the `:batch-not-registered` guard."
  "SB-9999")

(defn- seed-store
  "Build the initial store value. `now` is threaded in so calibration epochs
  are derived, not hard-coded."
  [now]
  {:batches
   (reduce (fn [acc {:keys [id product-type jurisdiction line defect evidence-drop]}]
             (let [base (assoc (clean-batch product-type jurisdiction now) :line line)
                   b (merge base defect)
                   b (if-let [age (:calibration-age-hours defect)]
                       (assoc b :metal-detector-last-calibration-date
                              (- now (* age hour-ms)))
                       b)
                   b (if evidence-drop
                       (update b :evidence-checklist
                               #(vec (remove evidence-drop %)))
                       b)]
               (assoc acc id b)))
           {}
           fixtures)
   :facts []})

;; ────────────────────────────── the scenario ─────────────────────────────

(def ^:private jp-cite
  [{:spec "食品衛生法 / HACCP制度化 (厚生労働省)"}])
(def ^:private us-cite
  [{:spec "FDA Draft Guidance: Control of Salmonella in Low-Moisture Foods"}])
(def ^:private eu-cite
  [{:spec "Regulation (EC) 852/2004 hygiene package"}])
(def ^:private codex-cite
  [{:spec "Codex Alimentarius CXC 68-2013 (Low-Moisture Foods)"}])

(def ^:private scenario
  "Ordered scenario steps. Each is fed verbatim to `operation/run-operation`;
  nothing here pre-judges the outcome -- the disposition column on the page is
  whatever the Governor actually returned."
  [{:op :schedule-maintenance :subject "SB-1015" :cites codex-cite :confidence 0.91
    :note "routine line maintenance, clean and confident"}
   {:op :log-production-batch :subject "SB-1001" :cites jp-cite :confidence 0.88
    :note "register the finished batch into production records"}
   {:op :coordinate-shipment :subject "SB-1001" :cites jp-cite :confidence 0.86
    :note "release the registered batch to outbound shipment"}
   {:op :log-production-batch :subject "SB-1001" :cites jp-cite :confidence 0.88
    :note "re-attempt the same registration (double-commit guard)"}
   {:op :coordinate-shipment :subject "SB-1001" :cites jp-cite :confidence 0.86
    :note "re-attempt the same shipment (double-commit guard)"}
   {:op :log-production-batch :subject "SB-1002" :cites us-cite :confidence 0.90}
   {:op :log-production-batch :subject "SB-1003" :cites eu-cite :confidence 0.90}
   {:op :log-production-batch :subject "SB-1004" :cites jp-cite :confidence 0.90}
   {:op :log-production-batch :subject "SB-1005" :cites us-cite :confidence 0.90}
   {:op :log-production-batch :subject "SB-1006" :cites jp-cite :confidence 0.90}
   {:op :log-production-batch :subject "SB-1007" :cites eu-cite :confidence 0.90}
   {:op :log-production-batch :subject "SB-1008" :cites jp-cite :confidence 0.90}
   {:op :log-production-batch :subject "SB-1009" :cites us-cite :confidence 0.90}
   {:op :log-production-batch :subject "SB-1010" :cites jp-cite :confidence 0.90}
   {:op :log-production-batch :subject "SB-1011" :cites eu-cite :confidence 0.90}
   {:op :log-production-batch :subject "SB-1012" :cites jp-cite :confidence 0.90}
   {:op :log-production-batch :subject "SB-1013" :cites us-cite :confidence 0.90}
   {:op :log-production-batch :subject "SB-1014" :cites jp-cite :confidence 0.90}
   {:op :schedule-maintenance :subject unregistered-batch-id :cites codex-cite :confidence 0.90
    :note "batch never registered in the plant record"}
   {:op :schedule-maintenance :subject "SB-1015" :cites codex-cite :confidence 0.90
    :effect :commit
    :note "proposal claims direct write authority instead of :propose"}
   {:op :control-mixing-line :subject "SB-1015" :cites codex-cite :confidence 0.95
    :note "operating the blending line directly -- outside the closed allowlist"}
   {:op :flag-food-safety-concern :subject "SB-1015" :cites [] :confidence 0.93
    :note "no jurisdiction citation, so requirements cannot be verified"}
   {:op :flag-food-safety-concern :subject "SB-1015" :cites jp-cite :confidence 0.93
    :note "properly cited concern -- always escalates, never auto-resolved"}
   {:op :schedule-maintenance :subject "SB-1015" :cites codex-cite :confidence 0.42
    :note "advisor confidence below the floor"}])

(defn- commit-effect
  "Apply the state change a committed op implies. `:schedule-maintenance` and
  `:flag-food-safety-concern` change no batch state in this store."
  [st op subject]
  (case op
    :log-production-batch (store/log-batch st subject (store/production-batch st subject))
    :coordinate-shipment (store/finalize-shipment st subject)
    st))

(defn run-scenario!
  "Drive every scenario step through `operation/run-operation` and thread the
  store. Returns `{:store st :steps [...]}` where each step carries the REAL
  verdict map the Governor returned.

  The commit / sign-off bookkeeping lives here rather than in the shipped
  code because the shipped code does not provide it: `run-operation` returns
  `{:ok? true :facts []}` on the pass path -- no commit fact, and no updated
  store -- so writing the batch and appending a commit fact is the caller's
  job. Likewise there is no approval-fact constructor anywhere in the repo,
  so the `:operator-signoff` fact below is authored by this driver. Both
  points are disclosed on the rendered page."
  []
  (let [now (System/currentTimeMillis)]
    (loop [st (seed-store now)
           [step & more] scenario
           acc []]
      (if (nil? step)
        {:store st :steps acc}
        (let [{:keys [op subject cites confidence effect]} step
              request {:op op :subject subject}
              proposal {:cites cites
                        :value {:jurisdiction (:jurisdiction (store/production-batch st subject))}
                        :effect (or effect :propose)
                        :confidence confidence}
              result (operation/run-operation request actor-context proposal st governor/check)
              verdict (or (:verdict result)
                          (governor/check request actor-context proposal st))
              hard? (boolean (:hard? verdict))
              escalated? (and (not (:ok? result)) (not hard?))
              disposition (cond (:ok? result) :auto-commit
                                hard? :hard-hold
                                :else :escalated)
              st (reduce store/append-fact st (:facts result))
              st (cond
                   (:ok? result)
                   (-> st
                       (store/append-fact {:t :auto-commit :op op :subject subject
                                           :disposition :commit
                                           :confidence (:confidence verdict)})
                       (commit-effect op subject))

                   escalated?
                   (-> st
                       (store/append-fact {:t :operator-signoff :op op :subject subject
                                           :disposition :commit
                                           :by approver
                                           :confidence (:confidence verdict)})
                       (commit-effect op subject))

                   :else st)]
          (recur st more
                 (conj acc (assoc step
                                  :disposition disposition
                                  :verdict verdict
                                  :basis (mapv :rule (:violations verdict))))))))))

;; ─────────────────────── render-time measurements ────────────────────────

(def ^:private approver-key-candidates
  "Keys any reasonable store or fact vocabulary might use to record WHO
  approved a proposal. Scanned at render time so the disclosure below
  self-corrects if approver custody is ever added to the store."
  #{:approved-by :approver :approval :signed-off-by :operator :operator-id
    :by :human :sign-off :signoff})

(defn- keys-matching [m] (filter approver-key-candidates (keys m)))

(defn- attribution-report
  "MEASURE -- do not assume -- whether this repo's store keeps the identity of
  the human who approved an escalated proposal. Scans the committed batch
  records and the audit ledger separately, because the honest answer here is
  different for each."
  [db steps]
  (let [records (vals (:batches db))
        ledger (store/audit-trail db)
        record-keys (into (sorted-set) (mapcat keys-matching records))
        ledger-keys (into (sorted-set) (mapcat keys-matching ledger))
        signoffs (filter #(= :operator-signoff (:t %)) ledger)
        committed-after-signoff
        (into (sorted-set)
              (for [s steps
                    :when (and (= :escalated (:disposition s))
                               (contains? #{:log-production-batch :coordinate-shipment}
                                          (:op s)))]
                (:subject s)))]
    {:record-keys record-keys
     :ledger-keys ledger-keys
     :signoff-count (count signoffs)
     :committed-after-signoff committed-after-signoff
     :verdict (cond
                (seq record-keys) :record-retains-approver
                (seq ledger-keys) :ledger-only
                :else :no-attribution-anywhere)}))

(defn- hold-classification
  "The shipped `governor/hold-fact` writes BOTH a permanent refusal and a
  soft escalation as `:t :governor-hold` with `:disposition :hold`, and drops
  the verdict's `:hard?` / `:escalate?` flags. Classify from what actually
  survives into the fact -- a non-empty `:basis` -- rather than from the
  verdict, so the page reports what an auditor reading the ledger alone can
  actually tell apart."
  [ledger]
  (let [holds (filter #(= :governor-hold (:t %)) ledger)
        {hard true soft false} (group-by #(boolean (seq (:basis %))) holds)]
    {:hard (vec hard)
     :soft (vec soft)
     :rules (into (sorted-set) (mapcat :basis holds))}))

;; ────────────────────────────── HTML helpers ─────────────────────────────

(defn- esc
  "Escape once, at the leaf. Callers must pass RAW text here and must never
  pass the result of `esc` back through `esc`."
  [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")))

(defn- kw-name [k] (if (keyword? k) (subs (str k) 1) (str k)))

(defn- fmt-set
  "Render a set in sorted order -- unsorted set iteration is not stable enough
  to put in a byte-identical page."
  [s]
  (if (empty? s) "—" (str/join ", " (sort (map kw-name s)))))

(defn- row [& cells] (str "        <tr>" (str/join cells) "</tr>"))
(defn- td [v] (str "<td>" (esc v) "</td>"))
(defn- code-td [v] (str "<td><code>" (esc v) "</code></td>"))
(defn- raw-td [markup] (str "<td>" markup "</td>"))

(defn- pill [class label] (str "<span class=\"pill " class "\">" (esc label) "</span>"))

(defn- disposition-cell [d]
  (case d
    :auto-commit (pill "ok" "auto-commit")
    :escalated (pill "warn" "escalated → human sign-off")
    :hard-hold (pill "bad" "HARD hold")))

;; ───────────────────────────────── sections ──────────────────────────────

(defn- batches-section [db steps]
  (let [batches (sort-by key (:batches db))
        intents (into {} (map (juxt :id :intent) fixtures))
        first-hold (fn [id]
                     (->> steps
                          (filter #(and (= (:subject %) id) (= :hard-hold (:disposition %))))
                          first))]
    (str
     "  <section class=\"card\">\n"
     "    <h2>Production batches <span class=\"count\">" (count batches) "</span></h2>\n"
     "    <p class=\"muted\">Live store state after the scenario ran. <code>logged</code> / <code>shipped</code> are read back out of <code>seasoningops.store</code>, not asserted by the scenario.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Batch</th><th>Product type</th><th>Line</th><th>Jurisdiction</th><th>Logged</th><th>Shipped</th><th>First refusal</th><th>Scenario intent</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n"
               (for [[id b] batches]
                 (row (code-td id)
                      (td (:name (facts/product-type-by-id (:product-type b))))
                      (code-td (:line b))
                      (code-td (kw-name (:jurisdiction b)))
                      (raw-td (if (store/batch-already-processed? db id)
                                (pill "ok" "yes") (pill "muted" "no")))
                      (raw-td (if (store/batch-shipment-finalized? db id)
                                (pill "ok" "yes") (pill "muted" "no")))
                      (raw-td (if-let [s (first-hold id)]
                                (str "<code>" (esc (kw-name (first (:basis s)))) "</code>")
                                (pill "muted" "none")))
                      (td (get intents id)))))
     "\n      </tbody>\n    </table>\n  </section>\n")))

(defn- product-spec-section []
  (let [ps (sort-by key facts/product-types)]
    (str
     "  <section class=\"card\">\n"
     "    <h2>Product specification windows <span class=\"count\">" (count ps) "</span></h2>\n"
     "    <p class=\"muted\">Read at render time from <code>seasoningops.facts/product-types</code> — these are the limits the Governor checks each batch against, so this table cannot drift from the code.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Product type</th><th>Moisture max %</th><th>Blend homogeneity min %</th><th>Water activity max</th><th>Microbial max CFU/g</th><th>Shelf life max h</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n"
               (for [[id p] ps]
                 (row (str "<td><code>" (esc (kw-name id)) "</code><br><span class=\"sub\">" (esc (:name p)) "</span></td>")
                      (td (:moisture-content-max-percent p))
                      (td (:blend-homogeneity-min-percent p))
                      (td (:water-activity-max p))
                      (td (:microbial-load-max-cfu-per-g p))
                      (td (:max-shelf-life-hours p)))))
     "\n      </tbody>\n    </table>\n  </section>\n")))

(defn- jurisdiction-section []
  (let [js (sort-by key facts/jurisdictions)]
    (str
     "  <section class=\"card\">\n"
     "    <h2>Jurisdiction evidence requirements <span class=\"count\">" (count js) "</span></h2>\n"
     "    <p class=\"muted\">Read from <code>seasoningops.facts/jurisdictions</code>. Each batch fixture's evidence checklist is generated from its own jurisdiction's list, so a missing document is a real gap against a real requirement.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Jurisdiction</th><th>Authority</th><th>Required evidence</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n"
               (for [[id j] js]
                 (row (code-td (kw-name id))
                      (td (:name j))
                      (raw-td (str/join " " (for [e (:required-evidence j)]
                                              (str "<code>" (esc (kw-name e)) "</code>")))))))
     "\n      </tbody>\n    </table>\n  </section>\n")))

(defn- action-gate-section []
  (let [ops (sort (map kw-name governor/allowed-ops))
        gate (fn [op-kw]
               (cond
                 (contains? governor/high-stakes op-kw)
                 [(pill "warn" "ALWAYS human sign-off")
                  "real actuation event — never auto-commits at any confidence"]
                 (contains? governor/always-escalate-ops op-kw)
                 [(pill "warn" "ALWAYS human sign-off")
                  "food-safety concern — never auto-resolved by advisor confidence"]
                 :else
                 [(pill "ok" "auto-commit when clean")
                  (str "commits only when the Governor finds no violation and confidence ≥ "
                       governor/confidence-floor)]))]
    (str
     "  <section class=\"card\">\n"
     "    <h2>Action gate <span class=\"count\">" (inc (count ops)) "</span></h2>\n"
     "    <p class=\"muted\">Derived at render time from <code>governor/allowed-ops</code>, <code>governor/high-stakes</code>, <code>governor/always-escalate-ops</code> and <code>governor/confidence-floor</code> — not a hand-maintained copy of them.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Op</th><th>Gate</th><th>Why</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n"
               (concat
                (for [o ops
                      :let [[p why] (gate (keyword o))]]
                  (row (code-td (str ":" o)) (raw-td p) (td why)))
                [(row (raw-td "<code>anything else</code>")
                      (raw-td (pill "bad" "refused unconditionally"))
                      (td "outside the closed allowlist — mixing/packaging line control and food-safety certification are not this actor's authority"))]))
     "\n      </tbody>\n    </table>\n  </section>\n")))

(defn- scenario-section [steps]
  (str
   "  <section class=\"card\">\n"
   "    <h2>Scenario run <span class=\"count\">" (count steps) "</span></h2>\n"
   "    <p class=\"muted\">Every row is one call to <code>operation/run-operation</code>. The disposition and the rules are whatever the Governor returned — the scenario does not declare an expected outcome anywhere.</p>\n"
   "    <table>\n"
   "      <thead><tr><th>#</th><th>Op</th><th>Batch</th><th>Conf.</th><th>Disposition</th><th>Governor basis</th><th>Note</th></tr></thead>\n"
   "      <tbody>\n"
   (str/join "\n"
             (map-indexed
              (fn [i {:keys [op subject confidence disposition basis note]}]
                (row (td (inc i))
                     (code-td (str ":" (kw-name op)))
                     (code-td subject)
                     (td confidence)
                     (raw-td (disposition-cell disposition))
                     (raw-td (if (seq basis)
                               (str/join " " (for [r basis]
                                               (str "<code class=\"bad-code\">" (esc (kw-name r)) "</code>")))
                               "<span class=\"sub\">—</span>"))
                     (td (or note ""))))
              steps))
   "\n      </tbody>\n    </table>\n  </section>\n"))

(defn- refusal-detail-section [steps]
  (let [holds (filter #(= :hard-hold (:disposition %)) steps)]
    (str
     "  <section class=\"card\">\n"
     "    <h2>Hard refusals in detail <span class=\"count\">" (count holds) "</span></h2>\n"
     "    <p class=\"muted\">The Governor's own <code>:detail</code> strings, verbatim. These are produced by <code>governor/check</code> at run time and interpolate the batch's real measured value against the real product limit.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Rule</th><th>Batch</th><th>Governor detail</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n"
               (for [s holds
                     v (:violations (:verdict s))]
                 (row (raw-td (str "<code class=\"bad-code\">" (esc (kw-name (:rule v))) "</code>"))
                      (code-td (:subject s))
                      (td (:detail v)))))
     "\n      </tbody>\n    </table>\n  </section>\n")))

(defn- ledger-section [db]
  (let [ledger (store/audit-trail db)]
    (str
     "  <section class=\"card\">\n"
     "    <h2>Audit ledger <span class=\"count\">" (count ledger) "</span></h2>\n"
     "    <p class=\"muted\">Append-only decision log for this run, read back from <code>store/audit-trail</code>.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>#</th><th>Fact</th><th>Op</th><th>Batch</th><th>Disposition</th><th>Basis</th><th>Approver on fact</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n"
               (map-indexed
                (fn [i f]
                  (row (td (inc i))
                       (code-td (kw-name (:t f)))
                       (code-td (str ":" (kw-name (:op f))))
                       (code-td (:subject f))
                       (code-td (kw-name (:disposition f)))
                       (raw-td (if (seq (:basis f))
                                 (str/join " " (for [r (:basis f)]
                                                 (str "<code class=\"bad-code\">" (esc (kw-name r)) "</code>")))
                                 "<span class=\"sub\">—</span>"))
                       (raw-td (if-let [k (first (keys-matching f))]
                                 (str "<code>" (esc (kw-name k)) "</code> = " (esc (get f k)))
                                 "<span class=\"sub\">—</span>"))))
                ledger))
     "\n      </tbody>\n    </table>\n  </section>\n")))

(defn- custody-section [db steps]
  (let [{:keys [record-keys ledger-keys signoff-count committed-after-signoff verdict]}
        (attribution-report db steps)
        {:keys [hard soft rules]} (hold-classification (store/audit-trail db))]
    (str
     "  <section class=\"card warnbox\">\n"
     "    <h2>What this store does and does not remember</h2>\n"
     "    <p class=\"muted\">Both statements below are produced by scanning the committed records and the ledger at render time, not written into the page. If approver custody is ever added to <code>seasoningops.store</code>, this section changes on the next build without anyone editing it.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Question</th><th>Measured answer</th></tr></thead>\n"
     "      <tbody>\n"
     (row (td "Escalated proposals a human signed off in this run")
          (td signoff-count))
     "\n"
     (row (td "Batches committed to the store only after that sign-off")
          (raw-td (str/join " " (for [b committed-after-signoff]
                                  (str "<code>" (esc b) "</code>")))))
     "\n"
     (row (td "Approver-identity keys found on any committed batch record")
          (raw-td (if (seq record-keys)
                    (str/join " " (for [k record-keys] (str "<code>" (esc (kw-name k)) "</code>")))
                    (pill "bad" "none"))))
     "\n"
     (row (td "Approver-identity keys found anywhere in the audit ledger")
          (raw-td (if (seq ledger-keys)
                    (str/join " " (for [k ledger-keys] (str "<code>" (esc (kw-name k)) "</code>")))
                    (pill "bad" "none"))))
     "\n      </tbody>\n    </table>\n"
     "    <p class=\"note\">"
     (case verdict
       :record-retains-approver
       (str "The committed batch record itself retains the approver, so &ldquo;who released this batch?&rdquo; is answerable from the store alone.")
       :ledger-only
       (str "The audit ledger carries the approval fact, but the committed batch record does not. "
            "<code>store/log-batch</code> and <code>store/finalize-shipment</code> take no approver argument — "
            "they persist batch metadata and a one-way flag, nothing about who authorised the change. "
            "So &ldquo;who released this batch?&rdquo; is answerable only by joining the ledger back to the record; "
            "the record on its own cannot distinguish a human-approved commit from an unapproved one. "
            "Stating this rather than quietly omitting the approver is the point: a reader must be able to tell "
            "&ldquo;nobody approved&rdquo; from &ldquo;the store did not keep it&rdquo;.")
       :no-attribution-anywhere
       (str "Neither the committed record nor the ledger retains an approver identity anywhere in this run."))
     "</p>\n"
     "    <p class=\"note\">Second finding, also measured: the shipped <code>governor/hold-fact</code> writes a "
     "permanent refusal and a soft escalation under the same <code>:t :governor-hold</code> / "
     "<code>:disposition :hold</code>, and does not carry the verdict's <code>:hard?</code> or "
     "<code>:escalate?</code> flags into the fact. This run produced <strong>" (count hard) "</strong> "
     "hard and <strong>" (count soft) "</strong> soft holds; reading the ledger alone, the only thing "
     "separating them is that a soft hold's <code>:basis</code> is empty. That is an implicit discriminator, "
     "not a recorded one. Left as found — changing it is a governance decision with its own contract tests.</p>\n"
     "    <p class=\"note\">Third: <code>operation/run-operation</code> returns <code>{:ok? true :facts []}</code> "
     "on the pass path — no commit fact and no updated store — so the <code>:auto-commit</code> and "
     "<code>:operator-signoff</code> facts above are written by the scenario driver in "
     "<code>seasoningops.render-html</code>, not by the shipped actor. The <code>:governor-hold</code> facts "
     "are the shipped actor's own output. Distinct hard rules exercised in this run: <strong>"
     (count rules) "</strong>.</p>\n"
     "  </section>\n")))

;; ─────────────────────────────────  page  ────────────────────────────────

(def ^:private page-css
  "Hand-written, dependency-free CSS. jp-go-dds is deliberately NOT used here:
  it is not on this repo's classpath and adding it would put a resolvable
  dependency between this page and a network/sibling checkout, which this
  build does not need. Keeping it inline keeps the build offline and the
  output byte-stable."
  (str/join
   "\n"
   [":root{--ink:#1a1a1c;--sub:#5c6068;--line:#d8dce3;--bg:#f4f6f9;--card:#fff;"
    "--ok:#0a6d3c;--ok-bg:#e6f4ec;--warn:#8a5300;--warn-bg:#fdf1de;--bad:#a01b28;--bad-bg:#fdeaec;--accent:#0b3d91;}"
    "*{box-sizing:border-box;}"
    "body{margin:0;background:var(--bg);color:var(--ink);"
    "font-family:system-ui,-apple-system,'Hiragino Sans','Noto Sans JP',sans-serif;"
    "font-size:14px;line-height:1.6;}"
    "header.bar{background:var(--accent);color:#fff;padding:20px 28px;}"
    "header.bar h1{margin:0 0 6px;font-size:19px;letter-spacing:.01em;}"
    "header.bar .badge{display:inline-block;font-size:12px;opacity:.92;}"
    "main{padding:22px 28px 48px;max-width:1500px;}"
    "section.card{background:var(--card);border:1px solid var(--line);border-radius:8px;"
    "padding:18px 20px;margin:0 0 20px;}"
    "section.warnbox{border-left:4px solid var(--warn);}"
    "h2{margin:0 0 4px;font-size:15px;}"
    ".count{display:inline-block;margin-left:8px;padding:1px 8px;border-radius:10px;"
    "background:var(--bg);color:var(--sub);font-size:12px;font-weight:400;}"
    ".muted{color:var(--sub);font-size:12.5px;margin:0 0 12px;}"
    ".sub{color:var(--sub);font-size:12px;}"
    ".note{border-top:1px solid var(--line);padding-top:10px;margin:12px 0 0;font-size:12.5px;color:var(--ink);}"
    "table{border-collapse:collapse;width:100%;}"
    "th,td{border-bottom:1px solid var(--line);padding:7px 9px;text-align:left;vertical-align:top;}"
    "th{background:var(--bg);color:var(--sub);font-size:11.5px;text-transform:uppercase;"
    "letter-spacing:.04em;font-weight:600;white-space:nowrap;}"
    "tbody tr:hover{background:#fafbfd;}"
    "code{font-family:ui-monospace,SFMono-Regular,Menlo,monospace;font-size:12px;"
    "background:var(--bg);padding:1px 5px;border-radius:3px;}"
    "code.bad-code{background:var(--bad-bg);color:var(--bad);}"
    ".pill{display:inline-block;padding:1px 9px;border-radius:11px;font-size:11.5px;"
    "font-weight:600;white-space:nowrap;}"
    ".pill.ok{background:var(--ok-bg);color:var(--ok);}"
    ".pill.warn{background:var(--warn-bg);color:var(--warn);}"
    ".pill.bad{background:var(--bad-bg);color:var(--bad);}"
    ".pill.muted{background:var(--bg);color:var(--sub);}"]))

(defn render
  "Render the whole console from the scenario result."
  [{:keys [store steps]}]
  (let [db store]
    (str
     "<!DOCTYPE html>\n"
     "<html lang=\"en\"><head><meta charset=\"utf-8\">\n"
     "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n"
     "<title>cloud-itonami-isic-1079 · seasoningops operator console</title>\n"
     "<style>" page-css "</style></head><body>\n"
     "<header class=\"bar\">\n"
     "  <h1>Other food products n.e.c. — instant seasoning &amp; soup mix (ISIC 1079) · Operator Console</h1>\n"
     "  <span class=\"badge\">read-only build-time sample · governor-gated · batch logging and shipment always human-approved · mixing and packaging line control is never this actor's authority</span>\n"
     "</header>\n"
     "<main>\n"
     "  <section class=\"card\">\n"
     "    <p class=\"muted\" style=\"margin:0\">Generated by <code>clojure -M:dev:render-html</code> "
     "(<code>seasoningops.render-html</code>), which drives the real "
     "<code>operation</code> → <code>governor</code> → <code>store</code> stack. "
     "No timestamps, no network, byte-identical across reruns. "
     "This repo ships no seed data, so the batch fixtures are authored in the renderer and labelled as such; "
     "every spec limit, evidence requirement, gate posture and verdict on this page is read out of "
     "<code>seasoningops.facts</code> and <code>seasoningops.governor</code> at render time.</p>\n"
     "  </section>\n"
     (batches-section db steps)
     (action-gate-section)
     (scenario-section steps)
     (refusal-detail-section steps)
     (product-spec-section)
     (jurisdiction-section)
     (ledger-section db)
     (custody-section db steps)
     "</main>\n</body></html>\n")))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        {:keys [store steps] :as result} (run-scenario!)
        ledger (store/audit-trail store)
        {:keys [hard rules]} (hold-classification ledger)]
    ;; Build-time invariant, not a convention: a console that shows no hard
    ;; refusal is not evidence that this actor is governed. Refuse to write it.
    (when (zero? (count hard))
      (throw (ex-info (str "refusing to write " out
                           ": the scenario produced zero HARD :governor-hold facts, "
                           "so the page would not demonstrate a single un-overridable refusal")
                      {:ledger-facts (count ledger)
                       :hard-holds 0
                       :steps (count steps)})))
    (let [f (java.io.File. ^String out)]
      (when-let [p (.getParentFile f)] (.mkdirs p)))
    (spit out (render result))
    (println "wrote" out
             (str "(" (count steps) " scenario steps, "
                  (count ledger) " ledger facts, "
                  (count hard) " hard holds over "
                  (count rules) " distinct rules)"))))
