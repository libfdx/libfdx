package io.github.libfdx.graphics.gl;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class GLShaderCoordinatesTest {
    @Test void translatedPositionUsesCameraCoordinatesRegardlessOfBuiltinFieldName() {
        for (String field : new String[] {"position", "clip", "clipPosition_2"}) {
            String value = "v_1." + field;
            String source = "gl_Position = vec4(" + value + ".x, -(" + value + ".y), ((2.0f * "
                    + value + ".z) - " + value + ".w), " + value + ".w);";
            assertEquals("gl_Position = " + value + ";", GLGraphicsDevice.normalizeGlslSource(source));
        }
    }

    @Test void conversionAcceptsTranslatorWhitespaceAndFloatLiteralVariants() {
        assertEquals("gl_Position = out_2.clip;", GLGraphicsDevice.normalizeGlslSource(
                "gl_Position=vec4( out_2.clip.x, - (out_2.clip.y), ((2.0 * out_2.clip.z) - out_2.clip.w), out_2.clip.w );"));
    }

    @Test void ordinaryGlslAndUnrelatedExpressionsArePreserved() {
        for (String source : new String[] {
                "gl_Position = viewProjection * vec4(position, 1.0);",
                "gl_Position = vec4(v.clip.x, -(v.clip.y), ((2.0f * v.other.z) - v.clip.w), v.clip.w);"
        }) assertEquals(source, GLGraphicsDevice.normalizeGlslSource(source));
    }
}
