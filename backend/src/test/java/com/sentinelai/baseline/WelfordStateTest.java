package com.sentinelai.baseline;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WelfordStateTest {

    @Test
    void matchesReferenceMeanAndStd() {
        double[] xs = {2, 4, 4, 4, 5, 5, 7, 9};
        WelfordState w = new WelfordState();
        for (double x : xs) {
            w.observe(x);
        }
        // Reference: mean 5.0, sample std 2.138...
        assertThat(w.count()).isEqualTo(8);
        assertThat(w.mean()).isEqualTo(5.0);
        assertThat(w.std()).isCloseTo(2.138, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void zScoreFlagsOutliers() {
        WelfordState w = new WelfordState();
        for (int i = 0; i < 100; i++) {
            w.observe(13 + (i % 3 - 1)); // ~12-14, mean ~13
        }
        assertThat(Math.abs(w.zScore(3))).isGreaterThan(3.0);   // 3am is far off
        assertThat(Math.abs(w.zScore(13))).isLessThan(1.0);     // near baseline
    }
}
