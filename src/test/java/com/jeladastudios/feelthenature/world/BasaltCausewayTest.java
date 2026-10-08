package com.jeladastudios.feelthenature.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A basalt coast's columns: no field of spires on a plain, stepping down into the sea, upright in a cliff and leaning
 * on its foot, all round the patch's middle and none away from the sea.
 */
class BasaltCausewayTest {

    private static final int N = 32, SEA = 63;

    /** A straight coast along x = {@code shore}: land to the west at {@code land}, the sea floor to the east. */
    private static int[] coast(boolean[] sea, int shore, int land, int floor) {
        int[] g = new int[N * N];
        for (int z = 0; z < N; z++) {
            for (int x = 0; x < N; x++) {
                sea[z * N + x] = x >= shore;
                g[z * N + x] = x >= shore ? floor : land;
            }
        }
        return g;
    }

    @Test
    void aLowPlainGetsNoSpires() {
        boolean[] sea = new boolean[N * N];
        int[] g = coast(sea, 16, SEA, 58);
        for (long seed = 0; seed < 40; seed++) {
            var plan = BasaltCauseway.plan(N, g, sea, SEA, 15, 16, 8, seed);
            for (int i = 0; i < N * N; i++) {
                int t = plan.top()[i];
                if (t == BasaltCauseway.NONE) continue;
                if (!sea[i]) assertTrue(t <= g[i] + BasaltCauseway.PLATFORM, "a spire of " + (t - g[i]) + " on the plain at " + i);
                else assertTrue(t <= SEA + 3, "a column standing " + (t - SEA + 1) + " over the sea at " + i);
            }
        }
    }

    @Test
    void stepsDownIntoTheSea() {
        boolean[] sea = new boolean[N * N];
        int[] g = coast(sea, 16, SEA, 50);
        double[] mean = new double[BasaltCauseway.SEA_REACH + 2];
        int[] count = new int[mean.length];
        for (long seed = 0; seed < 60; seed++) {
            var plan = BasaltCauseway.plan(N, g, sea, SEA, 15, 16, 9, seed);
            for (int z = 12; z <= 20; z++) {
                for (int x = 16; x < N; x++) {
                    int t = plan.top()[z * N + x], d = x - 15;
                    if (d > BasaltCauseway.SEA_REACH) {
                        assertEquals(BasaltCauseway.NONE, t, "a column " + d + " blocks out to sea");
                        continue;
                    }
                    if (t == BasaltCauseway.NONE) continue;
                    mean[d] += t;
                    count[d]++;
                }
            }
        }
        // At the shore they come up to the water; further out lower, the outer ones under it.
        assertTrue(mean[1] / count[1] >= SEA - 1, "at the shore: " + mean[1] / count[1]);
        double last = Double.MAX_VALUE;
        for (int d = 1; d <= BasaltCauseway.SEA_REACH; d++) {
            double m = mean[d] / count[d];
            assertTrue(m <= last + 0.3, "higher at " + d + " out than nearer the shore");
            last = m;
        }
        assertTrue(last < SEA - 2, "the outer columns not drowned: " + last);
    }

    @Test
    void aCliffStandsInColumnsAndLeansOnItsFoot() {
        // A cliff at x = 13 down to a beach at the sea's level, the sea from x = 16.
        boolean[] sea = new boolean[N * N];
        int[] g = coast(sea, 16, SEA, 52);
        for (int z = 0; z < N; z++) for (int x = 0; x < 14; x++) g[z * N + x] = SEA + 12;
        boolean face = false, leaning = false;
        for (long seed = 0; seed < 20; seed++) {
            var plan = BasaltCauseway.plan(N, g, sea, SEA, 15, 16, 9, seed);
            for (int z = 0; z < N; z++) {
                int edge = z * N + 13, foot = z * N + 14;
                if (plan.faceFrom()[edge] != BasaltCauseway.NONE) {
                    face = true;
                    assertTrue(plan.faceFrom()[edge] <= SEA + 1, "the face stops short of its foot");
                }
                int t = plan.top()[foot];
                if (t != BasaltCauseway.NONE && t > g[foot] + BasaltCauseway.PLATFORM) {
                    leaning = true;
                    assertTrue(t < g[edge], "a column at the foot over the cliff's top");
                }
            }
        }
        assertTrue(face, "the cliff's face never turned to columns");
        assertTrue(leaning, "no column leaning on the cliff's foot");
    }

    @Test
    void nothingAwayFromThePatch() {
        boolean[] sea = new boolean[N * N];
        int[] g = coast(sea, 16, SEA, 50);
        var plan = BasaltCauseway.plan(N, g, sea, SEA, 15, 16, 5, 7L);
        for (int z = 0; z < N; z++) {
            for (int x = 0; x < N; x++) {
                double r = Math.hypot(x - 15, z - 16);
                if (r <= 5 + 1.5) continue;
                assertEquals(BasaltCauseway.NONE, plan.top()[z * N + x], "a column " + r + " from the middle");
                assertEquals(BasaltCauseway.NONE, plan.faceFrom()[z * N + x]);
            }
        }
    }

    @Test
    void noSeaNoColumns() {
        boolean[] sea = new boolean[N * N];
        int[] g = new int[N * N];
        java.util.Arrays.fill(g, SEA + 4);
        var plan = BasaltCauseway.plan(N, g, sea, SEA, 15, 16, 8, 3L);
        for (int i = 0; i < N * N; i++) assertEquals(BasaltCauseway.NONE, plan.top()[i]);
    }
}
