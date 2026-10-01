package io.github.libfdx.benchmark.graphics;

import io.github.libfdx.core.FdxException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ResourceLock(Resources.SYSTEM_PROPERTIES)
final class SpriteBatchStressBenchmarkTest {
    @Test
    void libfdxCpuWorkloadDefaultsTo8191SpritesAndAllowsBatchOverflow() {
        withSpritesProperty(null, () -> assertEquals(8191,
                new SpriteBatchStressBenchmark(0L, null, true).spriteCount()));
        withSpritesProperty("9000", () -> assertEquals(9000,
                new SpriteBatchStressBenchmark(0L, null, true).spriteCount()));
    }

    @Test
    void missingPropertyUsesCoreDefaultAndPreallocatesWithoutGraphics() {
        withSpritesProperty(null, () -> {
            SpriteBatchStressBenchmark benchmark = new SpriteBatchStressBenchmark(0L, null);
            assertEquals(SpriteBatchStressBenchmark.DEFAULT_SPRITE_COUNT, benchmark.spriteCount());
        });
    }

    @Test
    void configuredCountIsAllocatedOnceAtConstruction() {
        withSpritesProperty(" 17 ", () -> {
            SpriteBatchStressBenchmark benchmark = new SpriteBatchStressBenchmark(0L, null);
            assertEquals(17, benchmark.spriteCount());
            System.setProperty(SpriteBatchStressBenchmark.SPRITES_PROPERTY, "34");
            assertEquals(17, benchmark.spriteCount());
            assertEquals(34, new SpriteBatchStressBenchmark(0L, null).spriteCount());
        });
    }

    @Test
    void acceptsPositiveCountsBeyondTheInitialBatchCapacity() {
        assertEquals(1, SpriteBatchStressBenchmark.parseSpriteCount("1"));
        assertEquals(500000, SpriteBatchStressBenchmark.parseSpriteCount("500000"));
    }

    @Test
    void rejectsMalformedNonpositiveAndOverflowingCountsBeforeAllocation() {
        for (String value : new String[] {"", " ", "abc", "1.5", "0", "-1", "6000000",
                "2147483647", "2147483648"}) {
            withSpritesProperty(value, () -> {
                FdxException error = assertThrows(FdxException.class,
                        () -> new SpriteBatchStressBenchmark(0L, null));
                assertTrue(error.getMessage().contains(SpriteBatchStressBenchmark.SPRITES_PROPERTY));
            });
        }
    }

    private static void withSpritesProperty(String value, Runnable check) {
        String previous = System.getProperty(SpriteBatchStressBenchmark.SPRITES_PROPERTY);
        try {
            if (value == null) {
                System.clearProperty(SpriteBatchStressBenchmark.SPRITES_PROPERTY);
            } else {
                System.setProperty(SpriteBatchStressBenchmark.SPRITES_PROPERTY, value);
            }
            check.run();
        } finally {
            if (previous == null) {
                System.clearProperty(SpriteBatchStressBenchmark.SPRITES_PROPERTY);
            } else {
                System.setProperty(SpriteBatchStressBenchmark.SPRITES_PROPERTY, previous);
            }
        }
    }
}
