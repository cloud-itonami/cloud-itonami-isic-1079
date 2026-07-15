# cloud-itonami-isic-1079: Other Food Products n.e.c. Manufacturing Coordination Actor

**ISIC Rev. 5 1079** — Manufacture of Other Food Products n.e.c.

ISIC 1079 is a residual ("not elsewhere classified") food-manufacturing
category, covering product lines such as instant/prepared seasonings,
soup mixes, yeast, egg products, and honey processing that do not fit
any of the more specific ISIC 10xx food subclasses. This actor picks one
concrete product line as its illustration: **instant-seasoning / soup-mix
manufacturing** (e.g. instant dashi/soup-stock powder, cream-based soup
mix, dry spice blends, bouillon/consommé granules).

A distributed actor for autonomous, compliant coordination of dry-mix
seasoning/soup-mix plant operations: raw-material intake → precise
per-formula weighing → mixing/blending → packaging (moisture-barrier
pouch/sachet/canister filling and sealing) → metal-detector and
seal-integrity inspection → compliance audit → finished-product
logistics. Sealed LLM advisor; independent Governor enforcement;
append-only audit ledger. **Not equipment control.** Mixing-line and
packaging-line operation and food-safety certification authority remain
exclusive to licensed dry-mix-plant staff and regulators.

## Scope

This actor coordinates **plant-operations workflow** for instant-
seasoning/soup-mix manufacturing (instant dashi/soup-stock powder,
cream-based soup mix, dry spice blends, bouillon/consommé granules, and
comparable dry-mix "other food products n.e.c." lines):

- Production batch logging (mixing/packaging batch, output-quality data)
- Equipment maintenance scheduling (mixing/blending lines, filling/
  sealing lines, metal detectors)
- Food-safety concern escalation (allergen cross-contact, microbial
  contamination)
- Finished-product shipment coordination

**Out of scope:**
- Direct mixing-line/packaging-line equipment control (plant staff exclusive)
- Food-safety certification authority (human inspector/regulator only)
- Regulatory interpretation (proposals cite jurisdiction specifications; the Governor enforces only published requirements)

## Design

### Governor (Independent Compliance Layer)

The Governor is the separation-of-powers enforcement. It never trusts the advisor's confidence for anything safety- or compliance-relevant, and it always wins over the advisor.

- **Hard HOLD** (un-overridable):
  - Operation outside the closed allowlist (`:op-not-allowed`) — includes any proposal that would touch mixing-line/packaging-line-equipment control or food-safety certification
  - Proposal asserting an `:effect` other than `:propose` (`:effect-not-propose`)
  - Plant/batch record not independently verified/registered before any proposal is made against it (`:batch-not-registered`) — applies to every proposal op, not only shipment coordination
  - No jurisdiction citation (`:no-spec-basis`) — can't verify requirements without one
  - Evidence checklist incomplete (`:evidence-incomplete`)
  - Finished-mix moisture content exceeds the product's maximum allowable level — this actor's CCP1 analogue (`:moisture-content-exceeds-max`)
  - Post-mixing blend-homogeneity/uniformity score falls below the product's minimum required level — this actor's CCP2 analogue (`:blend-homogeneity-below-minimum`)
  - Shelf life exceeded — elapsed time since production beyond the product's maximum shelf-life hours (`:shelf-life-exceeded`)
  - Water activity exceeds the product's maximum allowable level (`:water-activity-exceeds-max`)
  - Microbial-load test exceeds the product's maximum allowable level (`:microbial-load-exceeds-max`)
  - Foreign material detected on the batch's own inspection — metal fragments (`:foreign-material-detected`)
  - Metal-detector calibration overdue (`:metal-detector-calibration-overdue`)
  - Finished-product weight variance excessive (`:weight-variance-excessive`)
  - Allergen cross-contact mismatch — a cross-contact risk (wheat/soy/milk/egg/sesame/etc.) not fully covered by the declared-allergens label (`:allergen-label-mismatch`)
  - Plant sanitation/cross-contamination-control score insufficient (`:sanitation-score-insufficient`)
  - Packaging seal (moisture-barrier pouch/sachet/canister) compromised (`:packaging-seal-compromised`)
  - Unresolved food-safety flag (`:food-safety-flag-unresolved`)
  - Batch already processed / shipment already finalized (double-commit guards)
- **Escalate** (human sign-off always required):
  - `:log-production-batch` / `:coordinate-shipment` — real actuation events, always require plant-operator sign-off even when the Governor is otherwise clean
  - `:flag-food-safety-concern` — a food-safety concern (allergen cross-contact, microbial contamination) is never auto-resolved by advisor confidence alone
  - Low advisor confidence (below `governor/confidence-floor`, 0.6)
- **Commit** (advisor proposal approved; Governor clean; not a mandatory-escalation op):
  - Routine, low-stakes proposals only — in this actor's current allowlist that is effectively `:schedule-maintenance` when clean

### Operations (Proposals)

Closed allowlist — the advisor may **only** ever propose these four operation types, all `:effect :propose`:

- **`:log-production-batch`** — Log mixing/packaging batch, output-quality data into production records (always requires human sign-off)
- **`:schedule-maintenance`** — Propose mixing/packaging-line-equipment maintenance for blending lines, filling/sealing lines, metal detectors (routine, low risk)
- **`:flag-food-safety-concern`** — Surface a food-safety concern (e.g. allergen cross-contact, microbial contamination); always escalates
- **`:coordinate-shipment`** — Coordinate outbound instant-seasoning/soup-mix shipment (always requires human sign-off)

Any proposal for an operation outside this allowlist — most importantly anything that would amount to direct mixing-line/packaging-line-equipment control, or food-safety certification — is refused unconditionally by the Governor (`:op-not-allowed`), regardless of advisor confidence.

## Testing

```bash
# Run full test suite
clojure -M:test

# Check code quality
clojure -M:lint

# Run demo simulation
clojure -M:run
```

## Standalone Use

This repo is **forkable outside the workspace**. If cloning standalone (not in the kotoba-lang monorepo), override `:local/root` paths in `deps.edn`:

```clojure
{:deps {io.github.kotoba-lang/langchain {:git/url "https://github.com/kotoba-lang/langchain" :git/tag "v0.1.0"}
        io.github.kotoba-lang/langgraph {:git/url "https://github.com/kotoba-lang/langgraph" :git/tag "v0.1.0"}}}
```

## License

AGPL-3.0-or-later. Forking/contribution welcome; see `CONTRIBUTING.md`.

## Security

Report security issues to the issue tracker or private disclosure; see `SECURITY.md`.

---

Part of **cloud-itonami**: autonomous actor fleet for regulated industries. See [github.com/cloud-itonami](https://github.com/cloud-itonami).
