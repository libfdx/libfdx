package io.github.libfdx.graphics.particles;

/** One optical model for full-volume rendering and the medium in front of solid surfaces. */
final class ParticleMediumShader {
    private ParticleMediumShader() {}
    static final String SOURCE = """
        @group(0) @binding(0) var field : texture_2d<f32>;
        @group(0) @binding(1) var fieldSampler : sampler;
        struct Medium {
            low : vec3f, extent : vec3f, params : vec4f,
            warm : vec3f, hot : vec3f, secondLow : vec3f, secondExtent : vec3f,
        };
        fn layer(p : vec2f, z : f32) -> vec4f {
            let tile = vec2f(z % TILES.x, floor(z / TILES.x));
            let uv = (tile * GRID.xy + clamp(p * GRID.xy, vec2f(0.5), GRID.xy - 0.5)) / (GRID.xy * TILES);
            return textureSampleLevel(field, fieldSampler, uv, 0.0);
        }
        fn sampleField(p : vec3f) -> vec4f {
            if (any(p < vec3f(0.0)) || any(p > vec3f(1.0))) { return vec4f(0.0); }
            let z = clamp(p.z * GRID.z - 0.5, 0.0, GRID.z - 1.0);
            return mix(layer(p.xy, floor(z)), layer(p.xy, min(floor(z) + 1.0, GRID.z - 1.0)), fract(z));
        }
        fn hash(p : vec3f) -> f32 {
            var q = fract(p * 0.1031);
            q += dot(q, q.yzx + 33.33);
            return fract((q.x + q.y) * q.z);
        }
        fn noise(p : vec3f) -> f32 {
            let c = floor(p); let f = fract(p); let u = f*f*(3.0-2.0*f);
            return mix(mix(mix(hash(c),hash(c+vec3f(1,0,0)),u.x),
                           mix(hash(c+vec3f(0,1,0)),hash(c+vec3f(1,1,0)),u.x),u.y),
                       mix(mix(hash(c+vec3f(0,0,1)),hash(c+vec3f(1,0,1)),u.x),
                           mix(hash(c+vec3f(0,1,1)),hash(c+vec3f(1,1,1)),u.x),u.y),u.z);
        }
        struct MediumResult { color : vec3f, transmittance : f32, first : f32 };
        fn integrateMedium(start : vec3f, end : vec3f, limit : f32, v : Medium) -> MediumResult {
            let ray = normalize(end - start);
            let safeRay = select(vec3f(-1.0), vec3f(1.0), ray >= vec3f(0.0)) * max(abs(ray), vec3f(0.000001));
            let boxLow = min(v.low, v.secondLow);
            let boxHigh = max(v.low + v.extent, v.secondLow + v.secondExtent);
            let a = (boxLow - start) / safeRay; let b = (boxHigh - start) / safeRay;
            let lo = min(a,b); let hi = max(a,b);
            let enter = max(0.0, max(lo.x,max(lo.y,lo.z)));
            let leave = min(length(end - start), min(hi.x,min(hi.y,hi.z)));
            if (leave <= enter || limit <= enter) { return MediumResult(vec3f(0.0), 1.0, -1.0); }
            let stepSize = (leave - enter) / v.params.x;
            var transmittance = 1.0; var radiance = vec3f(0.0);
            var first = -1.0;
            for (var i = 0; i < 192; i++) {
                if (f32(i) >= v.params.x || transmittance < 0.015) { break; }
                let stepStart = enter + f32(i) * stepSize;
                if (stepStart >= limit) { break; }
                let sampleLength = min(stepSize, limit - stepStart);
                let t = stepStart + 0.5 * sampleLength;
                let p = start + ray * t;
                let primary = sampleField((p - v.low) / v.extent);
                let secondary = sampleSecondary((p - v.secondLow) / v.secondExtent);
                let fireDensity = primary.r + secondary.r;
                // Heat is mass-weighted; adding normalized heat channels would heat empty smoke cells.
                let heatDensity = (primary.g * primary.r + secondary.g * secondary.r) / max(fireDensity, 0.000001);
                let f = vec4f(fireDensity, heatDensity, primary.b + secondary.b, primary.a + secondary.a);
                if (f.r + f.b + f.a < 0.003) { continue; }
                let advected = p * 18.0 + vec3f(0.13,-7.0,0.21) * v.params.y;
                let n = noise(advected) * 0.7 + noise(advected * 2.07 + 3.2) * 0.3;
                let breakup = smoothstep(0.36,0.58,n);
                let fire = max(0.0, f.r * 8.0 - 0.04) * breakup;
                let smoke = f.b * 8.0 * (0.35 + 0.9 * n);
                let snow = f.a * 8.0;
                let mass = fire + smoke + snow;
                if (mass < 0.008) { continue; }
                if (first < 0.0) { first = t; }
                let heat = clamp(f.g * 0.65 + fire * 0.12, 0.0, 1.0);
                let hot = mix(v.warm * vec3f(1.0,0.1354,0.08), v.warm, smoothstep(0.3,0.72,heat));
                let flameColor = mix(hot,v.hot,smoothstep(0.72,1.0,heat));
                let smokeLight = 0.12 + 0.3 * n;
                let source = (flameColor * fire * 1.25 + vec3f(smokeLight) * smoke
                              + vec3f(0.8,0.88,1.0) * snow) / max(mass,0.0001);
                let alpha = 1.0 - exp(-mass * sampleLength * v.params.w);
                radiance += transmittance * alpha * source;
                transmittance *= 1.0 - alpha;
            }
            return MediumResult(radiance, transmittance, first);
        }
        """;
}
