package io.github.libfdx.maps;

import io.github.libfdx.core.FdxException;

/**
 * Immutable repeating sequence of atlas-local tile images. Durations use milliseconds.
 * Arrays are copied at construction; sampling an absolute clock allocates no storage.
 * Frames refer to images, so referencing an animated tile does not recurse.
 */
public final class TileAnimation {
    private final int[] tiles;
    private final long[] ends;
    private final long duration;

    public TileAnimation(int[] localIds, int[] durationsMillis) {
        if (localIds == null || durationsMillis == null || localIds.length == 0
                || localIds.length != durationsMillis.length || localIds.length > 65536) {
            throw new FdxException("Animation requires 1..65536 matching tile IDs and durations");
        }
        tiles = localIds.clone();
        ends = new long[tiles.length];
        long total = 0;
        for (int i = 0; i < tiles.length; i++) {
            if (tiles[i] < 0 || durationsMillis[i] <= 0) {
                throw new FdxException("Animation tile IDs must be nonnegative and durations positive");
            }
            ends[i] = total += durationsMillis[i];
        }
        duration = total;
    }

    public int frameCount() { return tiles.length; }
    public int localId(int frame) { return tiles[frame]; }
    public int durationMillis(int frame) { return (int)(ends[frame] - (frame == 0 ? 0 : ends[frame - 1])); }
    public long totalDurationMillis() { return duration; }

    /** Returns a local image ID. Negative clocks wrap backwards; boundaries select the next frame. */
    public int tileAt(long elapsedMillis) {
        long position = Math.floorMod(elapsedMillis, duration);
        int low = 0, high = ends.length - 1;
        while (low < high) {
            int middle = low + (high - low) / 2;
            if (position < ends[middle]) { high = middle; }
            else { low = middle + 1; }
        }
        return tiles[low];
    }
}
