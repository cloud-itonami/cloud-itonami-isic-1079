(ns seasoningops.facts-test
  (:require [clojure.test :refer [deftest is testing]]
            [seasoningops.facts :as facts]))

;; ──────────────────────── Product Type Lookups ──────────────────────

(deftest product-type-by-id-test
  (testing "instant dashi powder product type exists"
    (let [p (facts/product-type-by-id :seasoning/instant-dashi-powder)]
      (is (some? p))
      (is (= (:id p) :seasoning/instant-dashi-powder))
      (is (= (:moisture-content-max-percent p) 5.0))
      (is (= (:blend-homogeneity-min-percent p) 92.0))))

  (testing "western soup mix cream product type exists"
    (let [p (facts/product-type-by-id :seasoning/western-soup-mix-cream)]
      (is (some? p))
      (is (= (:max-shelf-life-hours p) 10950.0))
      (is (= (:water-activity-max p) 0.55))))

  (testing "dry spice blend curry product type exists"
    (let [p (facts/product-type-by-id :seasoning/dry-spice-blend-curry)]
      (is (some? p))
      (is (= (:moisture-content-max-percent p) 8.0))
      (is (= (:microbial-load-max-cfu-per-g p) 100000))
      (is (= (:max-shelf-life-hours p) 17520.0))))

  (testing "bouillon granules product type exists"
    (let [p (facts/product-type-by-id :seasoning/bouillon-granules)]
      (is (some? p))
      (is (= (:water-activity-max p) 0.50))
      (is (= (:blend-homogeneity-min-percent p) 90.0))))

  (testing "nonexistent product type returns nil"
    (is (nil? (facts/product-type-by-id :seasoning/nonexistent)))))

;; ──────────────────────── Jurisdiction Lookups ──────────────────────

(deftest jurisdiction-by-id-test
  (testing "JP MHLW jurisdiction exists"
    (let [j (facts/jurisdiction-by-id :jp/mhlw)]
      (is (some? j))
      (is (some #{:mixing-blend-record} (:required-evidence j)))))

  (testing "US FDA jurisdiction exists"
    (let [j (facts/jurisdiction-by-id :us/fda)]
      (is (some? j))
      (is (some #{:allergen-declaration} (:required-evidence j)))))

  (testing "EU EFSA jurisdiction exists"
    (let [j (facts/jurisdiction-by-id :eu/efsa)]
      (is (some? j))
      (is (some #{:packaging-seal-check} (:required-evidence j)))))

  (testing "nonexistent jurisdiction returns nil"
    (is (nil? (facts/jurisdiction-by-id :xx/unknown)))))

;; ──────────────────────── Evidence Completeness ──────────────────────

(deftest required-evidence-satisfied-test
  (testing "complete evidence checklist passes"
    (let [j (facts/jurisdiction-by-id :us/fda)
          evidence [:raw-material-intake-record :mixing-blend-record :moisture-content-test :water-activity-test
                    :microbial-test :allergen-declaration :weight-check :packaging-seal-check]]
      (is (true? (facts/required-evidence-satisfied? j evidence)))))

  (testing "incomplete evidence fails"
    (let [j (facts/jurisdiction-by-id :us/fda)
          evidence [:raw-material-intake-record :mixing-blend-record]]
      (is (false? (facts/required-evidence-satisfied? j evidence)))))

  (testing "accepts a raw jurisdiction id in place of a resolved map"
    (let [evidence [:raw-material-intake-record :mixing-blend-record :moisture-content-test :water-activity-test
                    :microbial-test :allergen-declaration :weight-check :packaging-seal-check]]
      (is (true? (facts/required-evidence-satisfied? :us/fda evidence))))))

;; ──────────────────────── Processing Safety Predicates ──────────────────────

(deftest moisture-content-within-max-test
  (testing "moisture content at or below max passes"
    (let [p (facts/product-type-by-id :seasoning/instant-dashi-powder)]
      (is (true? (facts/moisture-content-within-max? 5.0 p)))
      (is (true? (facts/moisture-content-within-max? 3.0 p)))))

  (testing "moisture content above max fails"
    (let [p (facts/product-type-by-id :seasoning/instant-dashi-powder)]
      (is (false? (facts/moisture-content-within-max? 6.5 p))))))

(deftest blend-homogeneity-meets-minimum-test
  (testing "blend homogeneity at or above min passes"
    (let [p (facts/product-type-by-id :seasoning/instant-dashi-powder)]
      (is (true? (facts/blend-homogeneity-meets-minimum? 92.0 p)))
      (is (true? (facts/blend-homogeneity-meets-minimum? 98.0 p)))))

  (testing "blend homogeneity below min fails"
    (let [p (facts/product-type-by-id :seasoning/instant-dashi-powder)]
      (is (false? (facts/blend-homogeneity-meets-minimum? 80.0 p))))))

(deftest water-activity-within-max-test
  (testing "water activity at or below max passes"
    (let [p (facts/product-type-by-id :seasoning/instant-dashi-powder)]
      (is (true? (facts/water-activity-within-max? 0.60 p)))
      (is (true? (facts/water-activity-within-max? 0.40 p)))))

  (testing "water activity above max fails"
    (let [p (facts/product-type-by-id :seasoning/instant-dashi-powder)]
      (is (false? (facts/water-activity-within-max? 0.75 p))))))

(deftest microbial-load-within-max-test
  (testing "microbial load at or below max passes"
    (let [p (facts/product-type-by-id :seasoning/instant-dashi-powder)]
      (is (true? (facts/microbial-load-within-max? 10000 p)))
      (is (true? (facts/microbial-load-within-max? 500 p)))))

  (testing "microbial load above max fails"
    (let [p (facts/product-type-by-id :seasoning/instant-dashi-powder)]
      (is (false? (facts/microbial-load-within-max? 50000 p))))))

(deftest shelf-life-within-max-test
  (testing "elapsed hours at or below max passes"
    (let [p (facts/product-type-by-id :seasoning/instant-dashi-powder)]
      (is (true? (facts/shelf-life-within-max? 8760.0 p)))
      (is (true? (facts/shelf-life-within-max? 100.0 p)))))

  (testing "elapsed hours above max fails"
    (let [p (facts/product-type-by-id :seasoning/instant-dashi-powder)]
      (is (false? (facts/shelf-life-within-max? 9000.0 p))))))
