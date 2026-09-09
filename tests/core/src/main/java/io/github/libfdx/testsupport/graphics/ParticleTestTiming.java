package io.github.libfdx.testsupport.graphics;

/** Elapsed-time simulation and bounded frame-time histogram for particle scenarios. */
public final class ParticleTestTiming {
    private final int[] histogram = new int[2001];
    private int frames, samples;
    private double total;
    private final float captureDelta = Float.parseFloat(System.getProperty("libfdx.test.particleDelta", "0"));
    public float advance(float elapsed) {
        if (!Float.isFinite(elapsed) || elapsed < 0) return 0;
        if (++frames > 60) {
            histogram[Math.min(2000, (int)(elapsed * 10000))]++;
            total += elapsed; samples++;
        }
        return Math.min(0.1f, captureDelta > 0 ? captureDelta : elapsed);
    }
    public String report() {
        return "Particle frame timing after 60 warmup frames: samples=" + samples
                + ", meanMs=" + (samples > 0 ? total * 1000 / samples : 0)
                + ", p95Ms=" + percentile(0.95f) + ", p99Ms=" + percentile(0.99f);
    }
    private float percentile(float percentile) {
        if (samples == 0) return 0;
        int target = (int)Math.ceil(samples * percentile), sum = 0;
        for (int i = 0; i < histogram.length; i++) {
            sum += histogram[i]; if (sum >= target) return (i + 1) / 10f;
        }
        return 200;
    }
}
