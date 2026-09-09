package io.github.libfdx.graphics.particles;

import io.github.libfdx.core.FdxException;
import java.nio.ByteBuffer;

/**
 * Reusable world-space density grid. Particles deposit spherical kernels, never camera-facing quads.
 * CPU-only, caller-owned and not thread-safe. Clear, deposit particles, then render each frame.
 */
public final class ParticleVolume {
    public enum Medium { FIRE, SMOKE, EMBER, SNOW }
    final int nx, ny, nz, columns, rows;
    final float[] field;
    final ByteBuffer pixels;
    private final int[] occupied;
    private int occupiedCount;
    float minX, minY, minZ, width = 1, height = 1, depth = 1;

    /** Resolution per axis, each 8-128. Storage is allocated once. */
    public ParticleVolume(int xCells, int yCells, int zCells) {
        if (xCells < 8 || yCells < 8 || zCells < 8 || xCells > 128 || yCells > 128 || zCells > 128)
            throw new FdxException("Particle volume resolution must be 8-128 per axis");
        nx = xCells; ny = yCells; nz = zCells;
        columns = Math.min(8, nz); rows = (nz + columns - 1) / columns;
        field = new float[nx * ny * nz * 4];
        occupied = new int[nx * ny * nz];
        pixels = ByteBuffer.allocateDirect(nx * columns * ny * rows * 4);
    }

    /** Sets the world-space minimum corner and positive extents; clears previous deposits. */
    public ParticleVolume bounds(float x, float y, float z, float width, float height, float depth) {
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)
                || !Float.isFinite(width) || !Float.isFinite(height) || !Float.isFinite(depth)
                || width <= 0 || height <= 0 || depth <= 0) throw new FdxException("Invalid volume bounds");
        minX = x; minY = y; minZ = z; this.width = width; this.height = height; this.depth = depth;
        clear(); return this;
    }

    public void clear() {
        for (int entry = 0; entry < occupiedCount; entry++) {
            int cell = occupied[entry], i = cell * 4;
            field[i] = 0; field[i + 1] = 0; field[i + 2] = 0; field[i + 3] = 0;
            pixels.putInt(atlasOffset(cell), 0);
        }
        occupiedCount = 0;
    }

    /**
     * Adds a spherical particle. Radius is in world units, density is nonnegative, heat is [0,1].
     * Overlapping particles blend in the medium before integration. Out-of-bounds support is clipped.
     * Sub-voxel particles are widened to one cell so they do not flicker between samples.
     */
    public void add(float x, float y, float z, float radius, float density, float heat, Medium medium) {
        if (medium == null || !Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)
                || !Float.isFinite(radius) || radius <= 0 || !Float.isFinite(density) || density < 0
                || !Float.isFinite(heat) || heat < 0 || heat > 1) throw new FdxException("Invalid volume particle");
        if (density == 0) return;
        float cell = Math.max(width / nx, Math.max(height / ny, depth / nz));
        radius = Math.max(radius, cell);
        int x0 = Math.max(0, (int)Math.floor((x - radius - minX) * nx / width));
        int y0 = Math.max(0, (int)Math.floor((y - radius - minY) * ny / height));
        int z0 = Math.max(0, (int)Math.floor((z - radius - minZ) * nz / depth));
        int x1 = Math.min(nx - 1, (int)Math.ceil((x + radius - minX) * nx / width));
        int y1 = Math.min(ny - 1, (int)Math.ceil((y + radius - minY) * ny / height));
        int z1 = Math.min(nz - 1, (int)Math.ceil((z + radius - minZ) * nz / depth));
        float invRadius2 = 1 / (radius * radius);
        for (int iz = z0; iz <= z1; iz++) for (int iy = y0; iy <= y1; iy++) for (int ix = x0; ix <= x1; ix++) {
            float dx = minX + (ix + 0.5f) * width / nx - x;
            float dy = minY + (iy + 0.5f) * height / ny - y;
            float dz = minZ + (iz + 0.5f) * depth / nz - z;
            float kernel = Math.max(0, 1 - (dx * dx + dy * dy + dz * dz) * invRadius2);
            float mass = kernel * kernel * density;
            if (mass <= 0) continue;
            int i = ((iz * ny + iy) * nx + ix) * 4;
            if (field[i] == 0 && field[i + 2] == 0 && field[i + 3] == 0) occupied[occupiedCount++] = i / 4;
            switch (medium) {
                case FIRE, EMBER -> { field[i] += mass; field[i + 1] += mass * heat; }
                case SMOKE -> field[i + 2] += mass;
                case SNOW -> field[i + 3] += mass;
            }
        }
    }

    ByteBuffer pack() {
        for (int entry = 0; entry < occupiedCount; entry++) {
            int cell = occupied[entry], i = cell * 4;
            int rgba = (quantize(field[i] / 8) & 255) << 24;
            rgba |= (quantize(field[i] > 0 ? field[i + 1] / field[i] : 0) & 255) << 16;
            rgba |= (quantize(field[i + 2] / 8) & 255) << 8;
            rgba |= quantize(field[i + 3] / 8) & 255;
            pixels.putInt(atlasOffset(cell), rgba);
        }
        pixels.clear(); return pixels;
    }

    private int atlasOffset(int cell) {
        int x = cell % nx, y = (cell / nx) % ny, z = cell / (nx * ny);
        return (((z / columns) * ny + y) * (nx * columns) + (z % columns) * nx + x) * 4;
    }

    private static byte quantize(float value) { return (byte)Math.round(Math.min(1, value) * 255); }
}
