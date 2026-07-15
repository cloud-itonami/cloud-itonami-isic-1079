(ns seasoningops.phase-test
  (:require [clojure.test :refer [deftest is testing]]
            [seasoningops.phase :as phase]))

;; ──────────────────────── Phase Validity ──────────────────────

(deftest valid-phase-test
  (testing "intake is valid"
    (is (true? (phase/valid-phase? :intake))))

  (testing "mix is valid"
    (is (true? (phase/valid-phase? :mix))))

  (testing "archived is valid"
    (is (true? (phase/valid-phase? :archived))))

  (testing "invalid phase returns false"
    (is (false? (phase/valid-phase? :invalid)))))

;; ──────────────────────── Phase Transitions ──────────────────────

(deftest can-transition-test
  (testing "intake -> weigh is valid (forward progression)"
    (is (true? (phase/can-transition? :intake :weigh))))

  (testing "intake -> mix is valid (skip weigh)"
    (is (true? (phase/can-transition? :intake :mix))))

  (testing "mix -> intake is invalid (backward)"
    (is (false? (phase/can-transition? :mix :intake))))

  (testing "mix -> archived is valid (forward to end)"
    (is (true? (phase/can-transition? :mix :archived))))

  (testing "archived -> intake is invalid (backward from end)"
    (is (false? (phase/can-transition? :archived :intake))))

  (testing "same phase is invalid"
    (is (false? (phase/can-transition? :mix :mix))))

  (testing "invalid phases return false"
    (is (false? (phase/can-transition? :invalid :mix)))
    (is (false? (phase/can-transition? :mix :invalid)))))
