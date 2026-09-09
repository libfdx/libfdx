package io.github.libfdx.tests.graphics;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.testsupport.graphics.MultipleShadersFixture;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MultipleShadersPixelsTest {
    @Test void aDifferentFlatColorInEveryTileDoesNotProveShadersRendered() {
        ByteBuffer pixels = ByteBuffer.allocate(64 * 32 * 4);
        for (int y = 0; y < 32; y++) for (int x = 0; x < 64; x++) {
            pixels.putInt((y * 64 + x) * 4, x < 32 ? 0x402040ff : 0x705050ff);
        }
        assertThrows(FdxException.class, () -> MultipleShadersFixture.verifyPixels(pixels, 64, 32, 32, 2, 2, 1, -1));
    }

    @Test void oneRenderedTileCannotHideAnotherMissingTile() {
        ByteBuffer pixels = ByteBuffer.allocate(64 * 32 * 4);
        for (int y = 8; y < 24; y++) for (int x = 8; x < 24; x++) pixels.putInt((y * 64 + x) * 4, -1);
        var failure = assertThrows(FdxException.class,
                () -> MultipleShadersFixture.verifyPixels(pixels, 64, 32, 32, 2, 2, 1, -1));
        assertTrue(failure.getMessage().contains("shader 2"));
        assertDoesNotThrow(() -> MultipleShadersFixture.verifyPixels(pixels, 64, 32, 32, 2, 2, 1, 1));
    }
}
