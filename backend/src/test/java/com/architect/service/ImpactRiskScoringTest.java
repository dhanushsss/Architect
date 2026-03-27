package com.architect.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ImpactRiskScoringTest {

    @Test
    void rawScore_matchesFormula() {
        // min(2*2,6) + min(3*0.3,2.5) + min(2*0.5,1.5) = 4 + 0.9 + 1 = 5.9
        assertEquals(5.9, ImpactRiskScoring.rawScore(2, 3, 2), 0.001);
        assertEquals(0.0, ImpactRiskScoring.rawScore(0, 0, 0), 0.001);
    }

    @Test
    void labels_highMediumLow() {
        assertEquals("HIGH", ImpactRiskScoring.label(6.0));
        assertEquals("MEDIUM", ImpactRiskScoring.label(5.9));
        assertEquals("MEDIUM", ImpactRiskScoring.label(3.0));
        assertEquals("LOW", ImpactRiskScoring.label(2.9));
    }

    @Test
    void verdicts_blockedReviewSafe() {
        assertEquals("BLOCKED", ImpactRiskScoring.verdict(7.0));
        assertEquals("REVIEW REQUIRED", ImpactRiskScoring.verdict(6.0));
        assertEquals("REVIEW REQUIRED", ImpactRiskScoring.verdict(4.0));
        assertEquals("SAFE TO MERGE", ImpactRiskScoring.verdict(3.0));
    }

    @Test
    void coreTrustThreshold_examples() {
        // No callers / tiny blast radius => LOW + SAFE TO MERGE.
        double low = ImpactRiskScoring.rawScore(0, 0, 1);
        assertEquals("LOW", ImpactRiskScoring.label(low));
        assertEquals("SAFE TO MERGE", ImpactRiskScoring.verdict(low));

        // Callers exist with moderate fan-out => MEDIUM + REVIEW REQUIRED.
        double medium = ImpactRiskScoring.rawScore(1, 5, 1);
        assertEquals("MEDIUM", ImpactRiskScoring.label(medium));
        assertEquals("REVIEW REQUIRED", ImpactRiskScoring.verdict(medium));

        // Wide direct impact => HIGH + BLOCKED.
        double high = ImpactRiskScoring.rawScore(3, 10, 3);
        assertEquals("HIGH", ImpactRiskScoring.label(high));
        assertEquals("BLOCKED", ImpactRiskScoring.verdict(high));
    }
}
