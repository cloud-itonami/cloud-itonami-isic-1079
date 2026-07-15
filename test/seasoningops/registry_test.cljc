(ns seasoningops.registry-test
  (:require [clojure.test :refer [deftest is testing]]
            [seasoningops.registry :as registry]))

;; ──────────────────────── Moisture Content (CCP1 analogue) ──────────────────────

(deftest moisture-content-exceeds-max-test
  (testing "moisture content at max returns false (no violation)"
    (is (false? (registry/moisture-content-exceeds-max? 5.0 5.0))))

  (testing "moisture content below max returns false"
    (is (false? (registry/moisture-content-exceeds-max? 3.0 5.0))))

  (testing "moisture content above max returns true (violation)"
    (is (true? (registry/moisture-content-exceeds-max? 6.5 5.0)))))

;; ──────────────────────── Blend Homogeneity (CCP2 analogue) ──────────────────────

(deftest blend-homogeneity-below-minimum-test
  (testing "blend homogeneity at min returns false (no violation)"
    (is (false? (registry/blend-homogeneity-below-minimum? 92.0 92.0))))

  (testing "blend homogeneity above min returns false"
    (is (false? (registry/blend-homogeneity-below-minimum? 98.0 92.0))))

  (testing "blend homogeneity below min returns true (violation)"
    (is (true? (registry/blend-homogeneity-below-minimum? 80.0 92.0)))))

;; ──────────────────────── Water Activity ──────────────────────

(deftest water-activity-exceeds-max-test
  (testing "water activity within max returns false (no violation)"
    (is (false? (registry/water-activity-exceeds-max? 0.40 0.60))))

  (testing "water activity at max returns false"
    (is (false? (registry/water-activity-exceeds-max? 0.60 0.60))))

  (testing "water activity exceeding max returns true (violation)"
    (is (true? (registry/water-activity-exceeds-max? 0.75 0.60)))))

;; ──────────────────────── Microbial Load ──────────────────────

(deftest microbial-load-exceeds-max-test
  (testing "microbial load within max returns false (no violation)"
    (is (false? (registry/microbial-load-exceeds-max? 500 10000))))

  (testing "microbial load at max returns false"
    (is (false? (registry/microbial-load-exceeds-max? 10000 10000))))

  (testing "microbial load exceeding max returns true (violation)"
    (is (true? (registry/microbial-load-exceeds-max? 50000 10000)))))

;; ──────────────────────── Shelf Life ──────────────────────

(deftest shelf-life-exceeded-test
  (testing "elapsed hours within max returns false (no violation)"
    (is (false? (registry/shelf-life-exceeded? 100.0 8760.0))))

  (testing "elapsed hours at max returns false"
    (is (false? (registry/shelf-life-exceeded? 8760.0 8760.0))))

  (testing "elapsed hours exceeding max returns true (violation)"
    (is (true? (registry/shelf-life-exceeded? 9000.0 8760.0)))))

;; ──────────────────────── Metal Detector Calibration ──────────────────────

(deftest metal-detector-calibration-overdue-test
  (testing "recent calibration returns false (no violation)"
    ;; Calibrated 6 hours ago (well within the 48-hour batch-line interval)
    (let [now #?(:clj (System/currentTimeMillis) :cljs (.now js/Date))
          six-hours-ago (- now (* 6 60 60 1000))]
      (is (false? (registry/metal-detector-calibration-overdue? six-hours-ago now)))))

  (testing "overdue calibration returns true (violation)"
    (let [now #?(:clj (System/currentTimeMillis) :cljs (.now js/Date))
          three-days-ago (- now (* 72 60 60 1000))]
      (is (true? (registry/metal-detector-calibration-overdue? three-days-ago now))))))

;; ──────────────────────── Weight Variance ──────────────────────

(deftest weight-variance-excessive-test
  (testing "variance within tolerance returns false (no violation)"
    (is (false? (registry/weight-variance-excessive? 3 5))))

  (testing "variance at tolerance returns false"
    (is (false? (registry/weight-variance-excessive? 5 5))))

  (testing "variance exceeding tolerance returns true (violation)"
    (is (true? (registry/weight-variance-excessive? 6 5)))))

;; ──────────────────────── Allergen Cross-Contact Labeling ──────────────────────

(deftest allergen-label-mismatch-test
  (testing "no cross-contact risk returns false (no risk) regardless of declaration"
    (is (false? (registry/allergen-label-mismatch? #{} #{}))))

  (testing "cross-contact risk fully covered by declaration returns false (no risk)"
    (is (false? (registry/allergen-label-mismatch? #{:wheat :soy} #{:wheat :soy}))))

  (testing "declaring more than the actual risk set is conservative and returns false"
    (is (false? (registry/allergen-label-mismatch? #{:wheat} #{:wheat :soy :milk}))))

  (testing "cross-contact risk not fully covered by declaration returns true (risk)"
    (is (true? (registry/allergen-label-mismatch? #{:wheat :soy} #{:wheat})))))

;; ──────────────────────── Foreign Material ──────────────────────

(deftest foreign-material-detected-test
  (testing "no detection returns false"
    (is (false? (registry/foreign-material-detected? false)))
    (is (false? (registry/foreign-material-detected? nil))))

  (testing "detection returns true"
    (is (true? (registry/foreign-material-detected? true)))))

;; ──────────────────────── Sanitation Score ──────────────────────

(deftest sanitation-score-insufficient-test
  (testing "score at minimum returns false (no violation)"
    (is (false? (registry/sanitation-score-insufficient? 75 75))))

  (testing "score above minimum returns false"
    (is (false? (registry/sanitation-score-insufficient? 85 75))))

  (testing "score below minimum returns true (violation)"
    (is (true? (registry/sanitation-score-insufficient? 74 75)))))

;; ──────────────────────── Packaging Seal Integrity ──────────────────────

(deftest packaging-seal-compromised-test
  (testing "no compromise returns false"
    (is (false? (registry/packaging-seal-compromised? false)))
    (is (false? (registry/packaging-seal-compromised? nil))))

  (testing "compromised seal returns true"
    (is (true? (registry/packaging-seal-compromised? true)))))
