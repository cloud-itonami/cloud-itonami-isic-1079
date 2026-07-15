(ns seasoningops.governor-test
  (:require [clojure.test :refer [deftest is testing]]
            [seasoningops.governor :as governor]))

(def ^:private now-ms #?(:clj (System/currentTimeMillis) :cljs (.now js/Date)))
(def ^:private six-hours-ago (- now-ms (* 6 60 60 1000)))
(def ^:private three-days-ago (- now-ms (* 72 60 60 1000)))

(def ^:private clean-batch
  {:product-type :seasoning/instant-dashi-powder
   :jurisdiction :us/fda
   :moisture-content-percent 3.5
   :blend-homogeneity-percent 95.0
   :water-activity 0.45
   :microbial-load-cfu-per-g 500
   :shelf-life-hours-elapsed 100.0
   :foreign-material-detected? false
   :metal-detector-last-calibration-date six-hours-ago
   :weight-variance-grams 2
   :declared-allergens #{}
   :cross-contact-risk #{}
   :sanitation-score 85
   :packaging-seal-compromised? false
   :evidence-checklist [:raw-material-intake-record :mixing-blend-record :moisture-content-test :water-activity-test
                        :microbial-test :allergen-declaration :weight-check :packaging-seal-check]})

;; ──────────────────────── Batch Registration (generalized) ──────────────────────

(deftest batch-not-registered-violation-test
  (testing "log-production-batch against an unregistered batch is a hard violation"
    (let [req {:op :log-production-batch :subject "batch-ghost"}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :us/fda} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop {})]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :batch-not-registered) (:violations result)))))

  (testing "schedule-maintenance against an unregistered batch is also a hard violation"
    (let [req {:op :schedule-maintenance :subject "batch-ghost"}
          prop {:cites [] :value {} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop {})]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :batch-not-registered) (:violations result)))))

  (testing "a registered batch does not trigger this rule"
    (let [batch-id "batch-001"
          store {:batches {batch-id clean-batch}}
          req {:op :schedule-maintenance :subject batch-id}
          prop {:cites [] :value {} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (not (some #(= (:rule %) :batch-not-registered) (:violations result)))))))

;; ──────────────────────── Hard Violations ──────────────────────

(deftest spec-basis-violation-test
  (testing "proposal with no jurisdiction citation is a hard violation"
    (let [batch-id "batch-001"
          store {:batches {batch-id clean-batch}}
          req {:op :log-production-batch :subject batch-id}
          prop {:cites [] :value {:jurisdiction nil}}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :no-spec-basis) (:violations result)))))

  (testing "proposal with proper citation passes spec basis check"
    (let [batch-id "batch-001"
          store {:batches {batch-id clean-batch}}
          req {:op :log-production-batch :subject batch-id}
          prop {:cites [{:spec "FDA-Low-Moisture-Foods-Guidance"}] :value {:jurisdiction :us/fda}}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (false? (:hard? result))))))

;; ──────────────────────── Moisture Content Violations (CCP1) ──────────────────────

(deftest moisture-content-violation-test
  (testing "batch with moisture content above the product's maximum triggers hard violation"
    (let [batch-id "batch-001"
          store {:batches {batch-id (assoc clean-batch :moisture-content-percent 6.5)}}
          req {:op :log-production-batch :subject batch-id}
          prop {:cites [{:spec "FDA-Low-Moisture-Foods-Guidance"}] :value {:jurisdiction :us/fda} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :moisture-content-exceeds-max) (:violations result)))))

  (testing "batch with moisture content at/below maximum passes"
    (let [batch-id "batch-001"
          store {:batches {batch-id clean-batch}}
          req {:op :log-production-batch :subject batch-id}
          prop {:cites [{:spec "FDA-Low-Moisture-Foods-Guidance"}] :value {:jurisdiction :us/fda} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (false? (:hard? result))))))

;; ──────────────────────── Blend Homogeneity Violations (CCP2) ──────────────────────

(deftest blend-homogeneity-violation-test
  (testing "batch with blend homogeneity below the product's minimum triggers hard violation"
    (let [batch-id "batch-001"
          store {:batches {batch-id (assoc clean-batch :blend-homogeneity-percent 80.0)}}
          req {:op :log-production-batch :subject batch-id}
          prop {:cites [{:spec "FDA-Low-Moisture-Foods-Guidance"}] :value {:jurisdiction :us/fda} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :blend-homogeneity-below-minimum) (:violations result))))))

;; ──────────────────────── Shelf Life Violations ──────────────────────

(deftest shelf-life-violation-test
  (testing "batch with elapsed time exceeding the product's shelf life triggers hard violation"
    (let [batch-id "batch-001"
          store {:batches {batch-id (assoc clean-batch :shelf-life-hours-elapsed 9000.0)}}
          req {:op :log-production-batch :subject batch-id}
          prop {:cites [{:spec "FDA-Low-Moisture-Foods-Guidance"}] :value {:jurisdiction :us/fda} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :shelf-life-exceeded) (:violations result))))))

;; ──────────────────────── Water Activity Violations ──────────────────────

(deftest water-activity-violation-test
  (testing "batch with water activity exceeding the product's maximum triggers hard violation"
    (let [batch-id "batch-001"
          store {:batches {batch-id (assoc clean-batch :water-activity 0.75)}}
          req {:op :log-production-batch :subject batch-id}
          prop {:cites [{:spec "FDA-Low-Moisture-Foods-Guidance"}] :value {:jurisdiction :us/fda} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :water-activity-exceeds-max) (:violations result))))))

;; ──────────────────────── Microbial Load Violations ──────────────────────

(deftest microbial-load-violation-test
  (testing "batch with microbial load exceeding the product's maximum triggers hard violation"
    (let [batch-id "batch-001"
          store {:batches {batch-id (assoc clean-batch :microbial-load-cfu-per-g 50000)}}
          req {:op :log-production-batch :subject batch-id}
          prop {:cites [{:spec "FDA-Low-Moisture-Foods-Guidance"}] :value {:jurisdiction :us/fda} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :microbial-load-exceeds-max) (:violations result))))))

;; ──────────────────────── Foreign Material Violations ──────────────────────

(deftest foreign-material-violation-test
  (testing "batch with detected foreign material triggers hard violation"
    (let [batch-id "batch-001"
          store {:batches {batch-id (assoc clean-batch :foreign-material-detected? true)}}
          req {:op :log-production-batch :subject batch-id}
          prop {:cites [{:spec "FDA-Low-Moisture-Foods-Guidance"}] :value {:jurisdiction :us/fda} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :foreign-material-detected) (:violations result))))))

;; ──────────────────────── Metal Detector Calibration Violations ──────────────────────

(deftest metal-detector-calibration-violation-test
  (testing "batch with overdue metal-detector calibration triggers hard violation"
    (let [batch-id "batch-001"
          store {:batches {batch-id (assoc clean-batch :metal-detector-last-calibration-date three-days-ago)}}
          req {:op :log-production-batch :subject batch-id}
          prop {:cites [{:spec "FDA-Low-Moisture-Foods-Guidance"}] :value {:jurisdiction :us/fda} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :metal-detector-calibration-overdue) (:violations result))))))

;; ──────────────────────── Weight Variance Violations ──────────────────────

(deftest weight-variance-violation-test
  (testing "batch with excessive weight variance triggers hard violation"
    (let [batch-id "batch-001"
          store {:batches {batch-id (assoc clean-batch :weight-variance-grams 8)}}
          req {:op :log-production-batch :subject batch-id}
          prop {:cites [{:spec "FDA-Low-Moisture-Foods-Guidance"}] :value {:jurisdiction :us/fda} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :weight-variance-excessive) (:violations result))))))

;; ──────────────────────── Allergen Cross-Contact Violations ──────────────────────

(deftest allergen-label-mismatch-violation-test
  (testing "cross-contact risk without a matching declaration triggers hard violation"
    (let [batch-id "batch-001"
          store {:batches {batch-id (assoc clean-batch :cross-contact-risk #{:wheat :soy} :declared-allergens #{})}}
          req {:op :log-production-batch :subject batch-id}
          prop {:cites [{:spec "FDA-Low-Moisture-Foods-Guidance"}] :value {:jurisdiction :us/fda} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :allergen-label-mismatch) (:violations result)))))

  (testing "cross-contact risk WITH a complete declaration passes"
    (let [batch-id "batch-002"
          store {:batches {batch-id (assoc clean-batch :cross-contact-risk #{:wheat :soy} :declared-allergens #{:wheat :soy})}}
          req {:op :log-production-batch :subject batch-id}
          prop {:cites [{:spec "FDA-Low-Moisture-Foods-Guidance"}] :value {:jurisdiction :us/fda} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (not (some #(= (:rule %) :allergen-label-mismatch) (:violations result)))))))

;; ──────────────────────── Sanitation Score Violations ──────────────────────

(deftest sanitation-score-violation-test
  (testing "batch with insufficient sanitation score triggers hard violation"
    (let [batch-id "batch-001"
          store {:batches {batch-id (assoc clean-batch :sanitation-score 60)}}
          req {:op :log-production-batch :subject batch-id}
          prop {:cites [{:spec "FDA-Low-Moisture-Foods-Guidance"}] :value {:jurisdiction :us/fda} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :sanitation-score-insufficient) (:violations result))))))

;; ──────────────────────── Packaging Seal Violations ──────────────────────

(deftest packaging-seal-violation-test
  (testing "batch with a compromised packaging seal triggers hard violation"
    (let [batch-id "batch-001"
          store {:batches {batch-id (assoc clean-batch :packaging-seal-compromised? true)}}
          req {:op :log-production-batch :subject batch-id}
          prop {:cites [{:spec "FDA-Low-Moisture-Foods-Guidance"}] :value {:jurisdiction :us/fda} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :packaging-seal-compromised) (:violations result))))))

;; ──────────────────────── Food-Safety Flag Violations ──────────────────────

(deftest food-safety-flag-unresolved-violation-test
  (testing "batch with an unresolved food-safety flag triggers hard violation"
    (let [batch-id "batch-001"
          store {:batches {batch-id (assoc clean-batch
                                            :safety-concern-raised? true
                                            :safety-concern-resolved? false)}}
          req {:op :log-production-batch :subject batch-id}
          prop {:cites [{:spec "FDA-Low-Moisture-Foods-Guidance"}] :value {:jurisdiction :us/fda} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :food-safety-flag-unresolved) (:violations result)))))

  (testing "batch with a resolved food-safety flag does not trigger this rule"
    (let [batch-id "batch-002"
          store {:batches {batch-id (assoc clean-batch
                                            :safety-concern-raised? true
                                            :safety-concern-resolved? true)}}
          req {:op :log-production-batch :subject batch-id}
          prop {:cites [{:spec "FDA-Low-Moisture-Foods-Guidance"}] :value {:jurisdiction :us/fda} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (not (some #(= (:rule %) :food-safety-flag-unresolved) (:violations result)))))))

;; ──────────────────────── Escalation (Low Confidence) ──────────────────────

(deftest low-confidence-escalation-test
  (testing "low confidence proposal escalates even when hard checks pass"
    (let [batch-id "batch-001"
          store {:batches {batch-id clean-batch}}
          req {:op :schedule-maintenance :subject batch-id}
          prop {:cites [{:spec "Equipment-Manual"}] :value {:jurisdiction :us/fda} :confidence 0.5}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (false? (:ok? result)))
      (is (true? (:escalate? result)))
      (is (false? (:hard? result))))))

;; ──────────────────────── High Stakes Escalation ──────────────────────

(deftest high-stakes-escalation-test
  (testing "log-production-batch escalates even when all checks pass"
    (let [batch-id "batch-001"
          store {:batches {batch-id clean-batch}}
          req {:op :log-production-batch :subject batch-id}
          prop {:cites [{:spec "FDA-Low-Moisture-Foods-Guidance"}] :value {:jurisdiction :us/fda} :confidence 0.95}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (false? (:ok? result)))
      (is (true? (:escalate? result)))
      (is (false? (:hard? result))))))

;; ──────────────────────── Already Processed Violation ──────────────────────

(deftest already-processed-violation-test
  (testing "batch already processed triggers hard violation"
    (let [batch-id "batch-001"
          store {:batches {batch-id
                           {:product-type :seasoning/instant-dashi-powder
                            :processed? true}}}
          req {:op :log-production-batch :subject batch-id}
          prop {:cites [{:spec "FDA-Low-Moisture-Foods-Guidance"}] :value {:jurisdiction :us/fda} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :already-processed) (:violations result))))))

;; ──────────────────────── Already Shipment Finalized Violation ──────────────────────

(deftest already-shipment-finalized-violation-test
  (testing "batch with a shipment already finalized triggers hard violation"
    (let [batch-id "batch-001"
          store {:batches {batch-id
                           {:product-type :seasoning/instant-dashi-powder
                            :shipment-finalized? true}}}
          req {:op :coordinate-shipment :subject batch-id}
          prop {:cites [{:spec "Shipment-Manual"}] :value {:jurisdiction :us/fda} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :already-shipment-finalized) (:violations result))))))
