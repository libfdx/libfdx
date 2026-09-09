package io.github.libfdx.testsupport.graphics;

import io.github.libfdx.core.FdxException;
import java.nio.ByteBuffer;

/** Builds and checks distinct runtime programs for the multiple-shader compilation scenario. */
public final class MultipleShadersFixture {
    private MultipleShadersFixture() { }

    /** Checks each usable program's shaded geometry rather than treating a submitted draw or
     * a colored clear tile as visual success. Input is the standard bottom-up RGBA8 readback. */
    public static void verifyPixels(ByteBuffer pixels, int width, int height, float gridHeight,
            int count, int columns, int rows, int failedIndex) {
        if (pixels == null || pixels.limit() < (long) width * height * 4) {
            throw new FdxException("MultipleShadersTest has incomplete pixel readback");
        }
        for (int tile = 0; tile < count; tile++) {
            if (tile == failedIndex) continue;
            int left = (tile % columns) * width / columns;
            int right = (tile % columns + 1) * width / columns;
            int bottom = (int) (gridHeight * (rows - tile / columns - 1) / rows);
            int top = (int) (gridHeight * (rows - tile / columns) / rows);
            int xInset = Math.max(2, (right - left) / 10), yInset = Math.max(2, (top - bottom) / 10);
            int[] low = {255, 255, 255}, high = {0, 0, 0};
            for (int y = bottom + yInset; y < top - yInset; y++) {
                for (int x = left + xInset; x < right - xInset; x++) {
                    int offset = (y * width + x) * 4;
                    for (int channel = 0; channel < 3; channel++) {
                        int value = pixels.get(offset + channel) & 255;
                        low[channel] = Math.min(low[channel], value);
                        high[channel] = Math.max(high[channel], value);
                    }
                }
            }
            if (Math.max(high[0] - low[0], Math.max(high[1] - low[1], high[2] - low[2])) < 24) {
                throw new FdxException("MultipleShadersTest shader " + (tile + 1)
                        + " is ready but its tile contains no shaded geometry");
            }
        }
    }

    public static String source(int index, int seed) {
        // Changes affect geometry and lighting, rather than just a label or comment.
        float variant = (index + 1) * 0.037f + Math.floorMod(seed, 100_003) * 0.000017f;
        return SOURCE.replace("$MODE$", Integer.toString(index % 4))
                .replace("$VARIANT$", Float.toString(variant));
    }

    private static final String SOURCE = """
            const MODE : i32 = $MODE$;
            const VARIANT : f32 = $VARIANT$;
            struct VertexInput {
                @location(0) position : vec2f,
                @location(1) uv : vec2f,
            };
            struct VertexOutput {
                @builtin(position) position : vec4f,
                @location(0) uv : vec2f,
            };
            @vertex fn vertexMain(input : VertexInput) -> VertexOutput {
                var output : VertexOutput;
                output.position = vec4f(input.position, 0.0, 1.0);
                output.uv = input.uv;
                return output;
            }
            fn surface(p : vec3f) -> f32 {
                let angle = VARIANT * 0.7;
                let q = vec3f(cos(angle) * p.x - sin(angle) * p.z,
                        p.y, sin(angle) * p.x + cos(angle) * p.z);
                if (MODE == 0) {
                    return length(q) - 0.64
                            + 0.045 * sin(q.x * 13.0 + VARIANT) * sin(q.y * 13.0) * sin(q.z * 13.0);
                }
                if (MODE == 1) {
                    return length(vec2f(length(q.xy) - 0.48, q.z)) - 0.19;
                }
                if (MODE == 2) {
                    let d = abs(q) - vec3f(0.43, 0.50, 0.40);
                    return length(max(d, vec3f(0.0))) + min(max(d.x, max(d.y, d.z)), 0.0) - 0.09;
                }
                let a = length(q - vec3f(0.24, 0.12, 0.0)) - 0.43;
                let b = length(q + vec3f(0.26, 0.14, 0.0)) - 0.43;
                let h = max(0.22 - abs(a - b), 0.0) / 0.22;
                return min(a, b) - h * h * 0.055;
            }
            fn normalAt(p : vec3f) -> vec3f {
                let e = vec2f(0.002, 0.0);
                return normalize(vec3f(surface(p + e.xyy) - surface(p - e.xyy),
                        surface(p + e.yxy) - surface(p - e.yxy),
                        surface(p + e.yyx) - surface(p - e.yyx)));
            }
            fn visibility(p : vec3f, light : vec3f) -> f32 {
                var result = 1.0;
                var distance = 0.025;
                for (var i = 0; i < 16; i++) {
                    let h = surface(p + light * distance);
                    result = min(result, 10.0 * h / distance);
                    distance += clamp(h, 0.02, 0.15);
                    if (h < 0.001 || distance > 2.0) { break; }
                }
                return clamp(result, 0.0, 1.0);
            }
            @fragment fn fragmentMain(input : VertexOutput) -> @location(0) vec4f {
                let eye = vec3f(0.0, 0.0, 2.6);
                let ray = normalize(vec3f(input.uv * 0.9, -2.0));
                var distance = 0.0;
                var found = false;
                for (var i = 0; i < 56; i++) {
                    let h = surface(eye + ray * distance);
                    if (h < 0.001) { found = true; break; }
                    distance += max(h * 0.7, 0.002);
                    if (distance > 5.0) { break; }
                }
                let palette = 0.5 + 0.5 * cos(vec3f(0.2, 2.3, 4.4) + VARIANT * 2.7);
                var color = vec3f(0.025, 0.04, 0.065) + palette * 0.035;
                if (found) {
                    let p = eye + ray * distance;
                    let n = normalAt(p);
                    let light = normalize(vec3f(-0.7 + VARIANT * 0.08, 0.85, 1.4));
                    let diffuse = max(dot(n, light), 0.0);
                    let halfVector = normalize(light - ray);
                    let specular = pow(max(dot(n, halfVector), 0.0), 24.0 + VARIANT * 3.0);
                    color = palette * (0.18 + diffuse * visibility(p + n * 0.006, light) * 0.82)
                            + vec3f(specular * 0.45);
                    color += palette * pow(1.0 - max(dot(n, -ray), 0.0), 3.0) * 0.25;
                }
                return vec4f(pow(max(color, vec3f(0.0)), vec3f(0.454545)), 1.0);
            }
            """;
}
