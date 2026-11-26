package io.github.libfdx.graphics.shadergraph.document;

import io.github.libfdx.graphics.shadergraph.model.ShaderGraphLiteral;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

final class ShaderGraphPreviewPlaybackTest {
    @Test
    void advancesAtConstantSpeedAndWrapsForwardAcrossMultipleCycles() {
        var playback = new ShaderGraphPreviewPlayback(new ShaderGraphPreviewAnimation("amount", -2, 4));
        var first = playback.advance(0);
        assertEquals(ShaderGraphLiteral.f32(-2), first);
        for (int cycle = 0; cycle < 3; cycle++) {
            for (int quarter = 1; quarter < 24; quarter++) {
                var value = playback.advance(0.25f);
                assertEquals(ShaderGraphLiteral.f32(-2 + quarter * 0.25f), value);
                assertSame(value, playback.advance(0));
            }
            assertSame(first, playback.advance(0.25f));
        }
        assertEquals(ShaderGraphLiteral.f32(1), playback.advance(15));
        playback.reset();
        assertSame(first, playback.advance(0));
    }

}
