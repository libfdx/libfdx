package io.github.libfdx.graphics.g3d;

/** Shared affine skin transform for the built-in visible and shadow passes. */
final class SkinningShader3D {
    static final int MAX_BONES=64;
    static final String FUNCTION="""
            fn skinTransform(joints : vec4f, sourceWeights : vec4f) -> mat4x4<f32> {
                let identity = mat4x4<f32>(vec4f(1.0, 0.0, 0.0, 0.0), vec4f(0.0, 1.0, 0.0, 0.0),
                        vec4f(0.0, 0.0, 1.0, 0.0), vec4f(0.0, 0.0, 0.0, 1.0));
                let positive = max(sourceWeights, vec4f(0.0));
                let largest = max(max(positive.x, positive.y), max(positive.z, positive.w));
                if (uniforms.skinningParams.x <= 0.0 || largest <= 0.0) { return identity; }
                let scaled = positive / largest;
                let weights = scaled / (scaled.x + scaled.y + scaled.z + scaled.w);
                let last = i32(uniforms.skinningParams.x) - 1;
                let joint = clamp(vec4i(joints), vec4i(0), vec4i(last));
                var matrix = uniforms.boneMatrices[joint.x] * weights.x
                        + uniforms.boneMatrices[joint.y] * weights.y
                        + uniforms.boneMatrices[joint.z] * weights.z
                        + uniforms.boneMatrices[joint.w] * weights.w;
                // Linear skinning uses affine palettes. Preserve w exactly across float weight sums.
                matrix[0].w = 0.0; matrix[1].w = 0.0; matrix[2].w = 0.0; matrix[3].w = 1.0;
                return matrix;
            }
            """;
    private SkinningShader3D() { }
}
