package com.jeladastudios.feelthenature.world;

import java.util.ArrayDeque;

/**
 * Where the columns of a basalt coast stand, and how high, apart from the game so it can be tested on its own.
 *
 * <p>Columnar basalt is a thick lava flow cooled slowly into prisms. Where the sea has cut into it (the Giant's
 * Causeway, Staffa, Reynisfjara) the columns stand upright in the cliff's face, as a broken staircase at its foot,
 * and step down into the sea, the outer ones under water. Behind the shore the plateau stays what it was. So here:</p>
 * <ul>
 *   <li>in the sea, within six blocks of the shore, columns topping out at the water or up to two blocks over it at
 *   the shore (now and then a stack two more), lower the further out, the outer ones drowned;</li>
 *   <li>on the shore, within a few blocks of the water, a low platform: the ground's top turned to column ends, the
 *   columns of broken height, nowhere more than three blocks over the ground;</li>
 *   <li>in a cliff's face, its blocks turned to upright columns down to the foot, the soil on top left;</li>
 *   <li>at a cliff's foot, columns of broken height leaning on the face.</li>
 * </ul>
 * <p>All of it in one patch round a point on the shore, its edge ragged; columns two blocks across, every other row
 * shifted by one, as near a honeycomb as blocks go.</p>
 */
public final class BasaltCauseway {

    private BasaltCauseway() {}

    /** No column here. */
    public static final int NONE = Integer.MIN_VALUE;

    /** How far out into the sea the columns go, and how far in from it the platform reaches, in blocks. */
    static final int SEA_REACH = 6, SHORE_REACH = 4;

    /** How far over the ground the shore's platform rises at most, in blocks. */
    static final int PLATFORM = 3;

    /** How much higher a neighbour has to stand for a column to be at the foot of a cliff, or in its face. */
    static final int CLIFF_STEP = 3;

    /**
     * The columns of one patch over a square of ground, {@code n} by {@code n} columns indexed {@code z * n + x}.
     *
     * @param ground   the top of the solid ground in each column, the sea floor under the sea
     * @param sea      whether the sea stands over each column
     * @param seaLevel the sea's level: its top water is a block under it
     * @param cx       the patch's middle, a column on the shore
     * @param radius   the patch's reach from its middle, in blocks
     * @param seed     what makes each patch's columns its own
     * @return for each column the top of its column ({@link #NONE} for none) in {@code top}, and where a cliff's face
     *         is turned to columns the lowest block of it in {@code faceFrom} ({@link #NONE} elsewhere)
     */
    public static Result plan(int n, int[] ground, boolean[] sea, int seaLevel, int cx, int cz, int radius, long seed) {
        int[] fromSea = distance(n, sea, false), fromLand = distance(n, sea, true);
        int[] top = new int[n * n], faceFrom = new int[n * n];
        java.util.Arrays.fill(top, NONE);
        java.util.Arrays.fill(faceFrom, NONE);
        for (int z = 0; z < n; z++) {
            for (int x = 0; x < n; x++) {
                int i = z * n + x;
                // A ragged edge: each cell reaches a block more or less.
                long cell = cell(x, z, seed);
                double reach = radius + (int) (cell >>> 60 & 3) - 1.5;
                if ((x - cx) * (x - cx) + (z - cz) * (z - cz) > reach * reach) continue;
                int j = (int) (cell >>> 40 & 0xFFFF);
                int g = ground[i];
                if (sea[i]) {
                    int d = fromLand[i];
                    if (d > SEA_REACH) continue;
                    // Now and then one standing two blocks taller than its neighbours, near the shore: a stack.
                    int stack = d <= 3 && (j >> 8) % 9 == 0 ? 2 : 0;
                    int t = seaLevel - 1 - (d - 1) * 2 / 3 + j % 3 + stack;
                    if (t > g) top[i] = t;
                    continue;
                }
                int d = fromSea[i];
                int high = highestNear(n, ground, sea, x, z, 2);
                int low = lowestNear(n, ground, sea, x, z, 1);
                if (g - Math.max(low, seaLevel - 1) >= CLIFF_STEP) {
                    // In the face of a cliff, standing over its foot or the water: its blocks turned to columns down
                    // to the foot (a low bank over the sea is the shore's, below).
                    faceFrom[i] = Math.max(low + 1, seaLevel - SEA_REACH);
                } else if (high - g > CLIFF_STEP) {
                    // At its foot: columns of broken height leaning on the face.
                    top[i] = Math.min(high - 1 - j % 4, g + 8);
                } else if (d <= SHORE_REACH) {
                    // The shore's platform: the ground's top a column's end, here and there a block or two over it.
                    int step = seaLevel + (d - 1) * 2 / 3 + j % 4;
                    top[i] = Math.max(g, Math.min(step, g + PLATFORM));
                }
            }
        }
        return new Result(top, faceFrom);
    }

    public record Result(int[] top, int[] faceFrom) {}

    /** A column's cell's hash: two blocks across, every other row shifted by one. */
    static long cell(int x, int z, long seed) {
        int cz = Math.floorDiv(z, 2);
        int cx = Math.floorDiv(x + (cz & 1), 2);
        long h = seed ^ (cx * 0x9E3779B97F4A7C15L) ^ (cz * 0xC2B2AE3D27D4EB4FL);
        h = (h ^ (h >>> 31)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        return h ^ (h >>> 33);
    }

    /** Steps from each column to the nearest one of the other kind (land to sea, or sea to land) up to 16. */
    static int[] distance(int n, boolean[] sea, boolean fromLand) {
        int[] d = new int[n * n];
        java.util.Arrays.fill(d, 16);
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int i = 0; i < n * n; i++) {
            if (sea[i] != fromLand) {
                d[i] = 0;
                queue.add(i);
            }
        }
        while (!queue.isEmpty()) {
            int i = queue.poll(), x = i % n, z = i / n;
            if (d[i] >= 15) continue;
            int[][] ways = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] w : ways) {
                int nx = x + w[0], nz = z + w[1];
                if (nx < 0 || nz < 0 || nx >= n || nz >= n) continue;
                int k = nz * n + nx;
                if (d[k] > d[i] + 1) {
                    d[k] = d[i] + 1;
                    queue.add(k);
                }
            }
        }
        return d;
    }

    private static int highestNear(int n, int[] ground, boolean[] sea, int x, int z, int r) {
        int best = Integer.MIN_VALUE;
        for (int dz = -r; dz <= r; dz++) {
            for (int dx = -r; dx <= r; dx++) {
                int nx = x + dx, nz = z + dz;
                if (nx < 0 || nz < 0 || nx >= n || nz >= n || sea[nz * n + nx]) continue;
                best = Math.max(best, ground[nz * n + nx]);
            }
        }
        return best;
    }

    private static int lowestNear(int n, int[] ground, boolean[] sea, int x, int z, int r) {
        int best = Integer.MAX_VALUE;
        for (int dz = -r; dz <= r; dz++) {
            for (int dx = -r; dx <= r; dx++) {
                if (Math.abs(dx) + Math.abs(dz) != 1) continue;
                int nx = x + dx, nz = z + dz;
                if (nx < 0 || nz < 0 || nx >= n || nz >= n) continue;
                best = Math.min(best, ground[nz * n + nx]);
            }
        }
        return best;
    }
}
