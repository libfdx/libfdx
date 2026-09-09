package io.github.libfdx.graphics.g3d;

import com.sun.management.ThreadMXBean;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.math.Matrix4;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;

import static io.github.libfdx.graphics.g3d.AnimationSampler.Interpolation.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

final class AnimationSamplerTest {
    @Test void stepBoundariesClampAndInputArraysAreCopied() {
        float[] times = {1, 2, 4};
        float[] values = {1, 2, 3, 4, 5, 6, 7, 8, 9};
        var sampler = new AnimationSampler(false, STEP, times, values);
        times[1] = 99;
        values[0] = 99;
        sampler.times()[1] = 99;
        sampler.values()[0] = 99;
        float[] out = {-1, -1, -1, -1, -1};
        for (float time : new float[] {-100, 0, 1, Math.nextDown(2f)}) {
            sampler.sample(time, out, 1);
            assertArrayEquals(new float[] {-1, 1, 2, 3, -1}, out);
        }
        sampler.sample(2, out, 1);
        assertArrayEquals(new float[] {-1, 4, 5, 6, -1}, out);
        sampler.sample(100, out, 1);
        assertArrayEquals(new float[] {-1, 7, 8, 9, -1}, out);
    }

    @Test void nonuniformCubicIntervalsReproduceAnAnalyticPolynomialWithPerSecondTangents() {
        float[] times = {1, 3, 6};
        float[] values = new float[times.length * 9];
        for (int i = 0; i < times.length; i++) {
            float t = times[i];
            float[] key = {2*t, 2, 0, t*t, 2*t+1, -3, 2*t, 2, 0};
            System.arraycopy(key, 0, values, i * 9, 9);
        }
        var sampler = new AnimationSampler(false, CUBICSPLINE, times, values);
        float[] out = new float[3];
        for (int i = 0; i <= 140; i++) {
            float t = i / 20f;
            sampler.sample(t, out, 0);
            float clamped = Math.max(1, Math.min(6, t));
            assertArrayEquals(new float[] {clamped*clamped, 2*clamped+1, -3}, out, .00001f);
        }
    }

    @Test void quaternionLinearUsesShortestArcWhileCubicPreservesAuthoredSignsAndNormalizes() {
        float[] times = {0, 2};
        float[] antipodal = {0, 0, 0, 1, 0, 0, 0, -1};
        float[] out = new float[4];
        new AnimationSampler(true, LINEAR, times, antipodal).sample(1, out, 0);
        assertArrayEquals(new float[] {0, 0, 0, 1}, out);
        float s = (float)Math.sqrt(.75);
        new AnimationSampler(true, LINEAR, times, new float[] {0, 0, 0, 1, 0, 0, -s, -.5f}).sample(1, out, 0);
        assertArrayEquals(new float[] {0, 0, .5f, s}, out, .00001f);
        float[] cubic = {0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0,
                        0, 0, 0, 0, 0, 0, -s, -.5f, 0, 0, 0, 0};
        new AnimationSampler(true, CUBICSPLINE, times, cubic).sample(1, out, 0);
        assertArrayEquals(new float[] {0, 0, -s, .5f}, out, .00001f);

        // With nonzero tangents the midpoint is the corresponding cubic Bezier midpoint:
        // (P0 + 3*(P0+dt*M0/3) + 3*(P1-dt*M1/3) + P1)/8, then normalized.
        cubic[8] = .4f;
        cubic[13] = .8f;
        new AnimationSampler(true, CUBICSPLINE, times, cubic).sample(1, out, 0);
        double[] expected = {.1, -.2, -s/2, .25};
        double norm = 0;
        for (double v : expected) norm += v*v;
        for (int c = 0; c < 4; c++) assertEquals(expected[c]/Math.sqrt(norm), out[c], .00001);

        cubic[8] = cubic[13] = cubic[18] = 0;
        cubic[19] = -1;
        var zeroCrossing = new AnimationSampler(true, CUBICSPLINE, times, cubic);
        assertThrows(FdxException.class, () -> zeroCrossing.sample(1, out, 0));
    }

    @Test void mixedIndependentTimelinesKeepStepDiscontinuitiesAndDefaultComponents() {
        var translation = new AnimationSampler(false, LINEAR, new float[] {1, 3}, new float[] {0, 2, 4, 8, 6, 0});
        var scale = new AnimationSampler(false, STEP, new float[] {0, 2, 5}, new float[] {1, 1, 1, 2, 3, 4, 5, 6, 7});
        var channel = AnimationClip.sampledTransform("node", AnimationClip.keyframe(0, 99, 99, 99), translation, null, scale);
        assertTrue(channel.hasSamplers());
        assertSame(translation, channel.translationSampler());
        assertNull(channel.rotationSampler());
        assertThrows(FdxException.class, channel::keyframes);
        Matrix4 out = new Matrix4();
        assertArrayEquals(new Matrix4().setToTrs(2, 3, 3, 0, 0, 0, 1, 1, 1, 1).values(), channel.sample(1.5f, out).values());
        assertArrayEquals(new Matrix4().setToTrs(4, 4, 2, 0, 0, 0, 1, 2, 3, 4).values(), channel.sample(2, out).values());
        var defaults = AnimationClip.sampledTransform("node", AnimationClip.keyframe(9, 1, 2, 3), null, null, null);
        assertArrayEquals(new Matrix4().setToTranslation(1, 2, 3).values(), defaults.sample(5, out).values());
    }

    @Test void malformedTracksAndOutputsFailExplicitly() {
        for (float[] times : new float[][] {{}, {-1}, {Float.NaN}, {Float.POSITIVE_INFINITY}, {1, 1}, {2, 1}}) {
            assertThrows(FdxException.class, () -> new AnimationSampler(false, LINEAR, times, new float[times.length*3]));
        }
        assertThrows(FdxException.class, () -> new AnimationSampler(false, CUBICSPLINE, new float[] {0}, new float[9]));
        assertThrows(FdxException.class, () -> new AnimationSampler(false, STEP, new float[] {0}, new float[4]));
        assertThrows(FdxException.class, () -> new AnimationSampler(true, LINEAR, new float[] {0}, new float[4]));
        assertThrows(FdxException.class, () -> new AnimationSampler(false, LINEAR, new float[] {0}, new float[] {0, 0, Float.NaN}));
        var sampler = new AnimationSampler(false, LINEAR, new float[] {0}, new float[3]);
        assertThrows(FdxException.class, () -> sampler.sample(Float.NaN, new float[3], 0));
        assertThrows(FdxException.class, () -> sampler.sample(0, new float[3], 1));
        assertThrows(FdxException.class, () -> sampler.sample(0, new float[3], Integer.MAX_VALUE));
        assertThrows(FdxException.class, () -> AnimationClip.sampledTransform("node", AnimationClip.keyframe(0, Float.NaN, 0, 0), null, null, null));
        assertThrows(FdxException.class, () -> AnimationClip.sampledTransform("node", AnimationClip.keyframe(0, 0, 0, 0), null, sampler, null));
    }

    @Test void repeatedIndependentSamplingAllocatesNoStorage() {
        assumeTrue(ManagementFactory.getThreadMXBean() instanceof ThreadMXBean);
        var bean = (ThreadMXBean)ManagementFactory.getThreadMXBean();
        assumeTrue(bean.isThreadAllocatedMemorySupported());
        bean.setThreadAllocatedMemoryEnabled(true);
        var translation = new AnimationSampler(false, CUBICSPLINE, new float[] {0, 2},
                new float[] {0, 0, 0, 0, 0, 0, 1, 2, 3, 1, 2, 3, 2, 4, 6, 0, 0, 0});
        var rotation = new AnimationSampler(true, LINEAR, new float[] {0, 1}, new float[] {0, 0, 0, 1, 0, 0, 1, 0});
        var channel = AnimationClip.sampledTransform("node", AnimationClip.keyframe(0, 0, 0, 0), translation, rotation, null);
        Matrix4 out = new Matrix4();
        for (int i = 0; i < 16000; i++) channel.sample(i%200 / 100f, out);
        long id = Thread.currentThread().threadId(), before = bean.getThreadAllocatedBytes(id);
        for (int i = 0; i < 2000; i++) channel.sample(i%200 / 100f, out);
        long allocated = bean.getThreadAllocatedBytes(id)-before;
        assertTrue(allocated <= 512, "Animation sampling allocated " + allocated + " bytes");
        assertEquals(1.99f, out.values()[12], .00001f);
    }
}
