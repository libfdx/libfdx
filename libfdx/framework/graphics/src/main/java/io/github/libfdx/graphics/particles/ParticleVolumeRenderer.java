package io.github.libfdx.graphics.particles;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.*;
import io.github.libfdx.graphics.Buffer;
import io.github.libfdx.graphics.shader.ShaderModule;
import io.github.libfdx.graphics.shader.ShaderModuleDescriptor;
import io.github.libfdx.graphics.shader.runtime.ShaderParameterBlock;
import io.github.libfdx.math.Matrix4;
import io.github.libfdx.math.Vector3;
import io.github.libfdx.math.ClipDepthRange;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Ray-integrates a particle density volume in world space, with emission and absorption.
 * Owns its GPU resources and borrows the CPU volume. Application-thread only. Uses a 2D
 * slice atlas internally for provider portability; no particle is rendered as a sprite.
 * Render after opaque geometry. Depth tests the first significant medium sample; embedded
 * opaque objects require separate volume bounds (this renderer does not sample scene depth).
 * Solids linked through ParticleSolidRenderer integrate this same medium up to their surface.
 * Keeps the most recent borrowed pass identity and camera for that composition until the next
 * draw or disposal; it never ends or disposes the pass.
 */
public final class ParticleVolumeRenderer implements Disposable {
    private static final int STRIDE = 46 * 4;
    private static final VertexLayout LAYOUT = VertexLayout.of(STRIDE,
            VertexAttribute.of(0, VertexFormat.FLOAT32X2, 0),
            VertexAttribute.of(1, VertexFormat.FLOAT32X3, 8),
            VertexAttribute.of(2, VertexFormat.FLOAT32X3, 20),
            VertexAttribute.of(3, VertexFormat.FLOAT32X3, 32),
            VertexAttribute.of(4, VertexFormat.FLOAT32X3, 44),
            VertexAttribute.of(5, VertexFormat.FLOAT32X4, 56),
            VertexAttribute.of(6, VertexFormat.FLOAT32X4, 72),
            VertexAttribute.of(7, VertexFormat.FLOAT32X4, 88),
            VertexAttribute.of(8, VertexFormat.FLOAT32X4, 104),
            VertexAttribute.of(9, VertexFormat.FLOAT32X4, 120),
            VertexAttribute.of(10, VertexFormat.FLOAT32X3, 136),
            VertexAttribute.of(11, VertexFormat.FLOAT32X3, 148),
            VertexAttribute.of(12, VertexFormat.FLOAT32X3, 160),
            VertexAttribute.of(13, VertexFormat.FLOAT32X3, 172));
    private static final float[] CORNERS = {-1,-1, 1,-1, 1,1, -1,-1, 1,1, -1,1};
    private final GraphicsContext graphics;
    private final ParticleVolume volume;
    private final ParticleVolume secondary;
    private final Vector3 near = new Vector3(), far = new Vector3();
    private final ByteBuffer upload = ByteBuffer.allocateDirect(6 * STRIDE).order(ByteOrder.nativeOrder());
    private ShaderModule shader;
    private RenderPipeline pipeline;
    private Buffer vertices;
    private Texture texture, secondaryTexture;
    private boolean disposed;
    private int steps = 96;
    private float density = 5;
    private final float[] projectionValues = new float[16];
    private final float[] inverseValues = new float[16];
    private RenderPass preparedPass;
    private ClipDepthRange preparedDepthRange;
    private float preparedTime;
    private final float[] flamePalette = {1, 0.48f, 0.025f, 1, 0.96f, 0.68f};

    /** Sets warm and hot flame RGB colors in [0,1]; supports custom magical/chemical color palettes. */
    public ParticleVolumeRenderer flameColors(float r, float g, float b, float hotR, float hotG, float hotB) {
        validateColor(r); validateColor(g); validateColor(b);
        validateColor(hotR); validateColor(hotG); validateColor(hotB);
        flamePalette[0]=r; flamePalette[1]=g; flamePalette[2]=b;
        flamePalette[3]=hotR; flamePalette[4]=hotG; flamePalette[5]=hotB; return this;
    }

    private static void validateColor(float value) {
        if (!Float.isFinite(value) || value < 0 || value > 1) throw new FdxException("Volume color must be in [0,1]");
    }

    public ParticleVolumeRenderer(GraphicsContext graphics, ParticleVolume volume) {
        this(graphics, volume, null);
    }

    /**
     * Borrows two independently resolved grids and integrates their media together along each ray.
     * Foreground smoke attenuates background emission, including where the grids interpenetrate.
     * Grid argument order does not determine visibility. Null secondary selects one grid.
     * Deposit any number of emitters into these grids before drawing once.
     */
    public ParticleVolumeRenderer(GraphicsContext graphics, ParticleVolume volume, ParticleVolume secondary) {
        if (graphics == null || volume == null) throw new FdxException("Graphics and volume are required");
        this.graphics = graphics; this.volume = volume; this.secondary = secondary;
        try {
            String constants = "const GRID = vec3f(" + volume.nx + ".0," + volume.ny + ".0," + volume.nz
                    + ".0); const TILES = vec2f(" + volume.columns + ".0," + volume.rows + ".0);\n";
            shader = graphics.device().createShaderModule(ShaderModuleDescriptor.wgsl("particle volume", constants + secondarySource(secondary) + ParticleMediumShader.SOURCE + SOURCE));
            pipeline = graphics.device().createRenderPipeline(RenderPipelineDescriptor.shader(shader, graphics.surfaceFormat())
                    .label("particle volume integration").vertexLayout(LAYOUT).sampledTextureCount(secondary == null ? 1 : 2)
                    .depthTestEnabled(true).depthWriteEnabled(false));
            vertices = graphics.device().createBuffer(BufferDescriptor.vertex("particle volume rays", 6 * STRIDE));
            texture = graphics.device().createTexture(TextureDescriptor.rgba8("particle density atlas",
                    volume.nx * volume.columns, volume.ny * volume.rows));
            if (secondary != null) secondaryTexture = graphics.device().createTexture(TextureDescriptor.rgba8(
                    "secondary particle density atlas", secondary.nx * secondary.columns, secondary.ny * secondary.rows));
        } catch (RuntimeException | Error failure) { dispose(); throw failure; }
    }

    /** Ray samples per intersected pixel, 32–192. Higher values resolve thinner features at greater GPU cost. */
    public ParticleVolumeRenderer steps(int value) {
        if (value < 32 || value > 192) throw new FdxException("Volume steps must be 32–192");
        steps = value; return this;
    }

    /** Positive optical density per world unit. */
    public ParticleVolumeRenderer density(float value) {
        if (!Float.isFinite(value) || value <= 0) throw new FdxException("Invalid optical density");
        density = value; return this;
    }

    /**
     * Uploads current deposits and draws into a borrowed active pass. Matrices must describe
     * the same finite near/far camera, in the supplied depth range. No Java allocation per draw.
     * Both orthographic 2D views and perspective 3D views use the same medium integration.
     */
    public void draw(RenderPass pass, Matrix4 viewProjection, Matrix4 inverseViewProjection,
            ClipDepthRange depthRange, float timeSeconds) {
        if (disposed) throw new FdxException("Particle volume renderer is disposed");
        if (pass == null || viewProjection == null || inverseViewProjection == null || depthRange == null
                || !Float.isFinite(timeSeconds)) throw new FdxException("Invalid particle volume draw");
        inverseViewProjection.copyValues(inverseValues, 0);
        preparedDepthRange = depthRange;
        preparedTime = timeSeconds;
        preparedPass = null;
        graphics.device().writeTexture(texture, volume.pack());
        if (secondary != null) graphics.device().writeTexture(secondaryTexture, secondary.pack());
        upload.clear();
        viewProjection.copyValues(projectionValues, 0);
        for (int i = 0; i < 6; i++) {
            float x = CORNERS[i * 2], y = CORNERS[i * 2 + 1];
            near.set(x, y, depthRange.nearPlaneDepth());
            far.set(x, y, depthRange.farPlaneDepth());
            inverseViewProjection.transformProjective(near, near);
            inverseViewProjection.transformProjective(far, far);
            upload.putFloat(x).putFloat(y);
            put(near); put(far);
            upload.putFloat(volume.minX).putFloat(volume.minY).putFloat(volume.minZ);
            upload.putFloat(volume.width).putFloat(volume.height).putFloat(volume.depth);
            upload.putFloat(steps).putFloat(timeSeconds).putFloat(depthRange.isZeroToOne() ? 0 : 1).putFloat(density);
            for (float value : projectionValues) upload.putFloat(value);
            for (float value : flamePalette) upload.putFloat(value);
            ParticleVolume other = secondary == null ? volume : secondary;
            upload.putFloat(other.minX).putFloat(other.minY).putFloat(other.minZ);
            upload.putFloat(other.width).putFloat(other.height).putFloat(other.depth);
        }
        upload.flip(); graphics.device().writeBuffer(vertices, upload);
        pass.setPipeline(pipeline); pass.setVertexBuffer(vertices); pass.setTexture(0, texture);
        if (secondaryTexture != null) pass.setTexture(1, secondaryTexture);
        pass.draw(6, 1, 0, 0);
        preparedPass = pass;
    }

    private void put(Vector3 value) { upload.putFloat(value.x()).putFloat(value.y()).putFloat(value.z()); }
    String solidSource(GraphicsContext context) {
        if (disposed || context != graphics) throw new FdxException("Solid particles require the same live graphics context as their medium");
        return "const GRID = vec3f(" + volume.nx + ".0," + volume.ny + ".0," + volume.nz
                + ".0); const TILES = vec2f(" + volume.columns + ".0," + volume.rows + ".0);\n"
                + secondarySource(secondary) + ParticleMediumShader.SOURCE + SOLID_SOURCE;
    }
    int textureCount() { return secondary == null ? 1 : 2; }
    void requireSolidCamera(RenderPass pass, float[] projection, ClipDepthRange range) {
        if (disposed || preparedPass != pass || preparedDepthRange != range)
            throw new FdxException("Draw the particle medium first in the same pass and depth range");
        for (int i=0;i<16;i++) if (projection[i] != projectionValues[i])
            throw new FdxException("Solid particles must use their medium's camera");
    }
    void bindSolids(RenderPass pass, ShaderParameterBlock block) {
        if (disposed || preparedPass != pass) throw new FdxException("Draw the particle medium before its solids in the same pass");
        ParticleVolume other = secondary == null ? volume : secondary;
        pass.setTexture(0, texture);
        if (secondaryTexture != null) pass.setTexture(1, secondaryTexture);
        var layout = block.layout();
        block.setFloatMatrix(layout.requireHandle("inverseViewProjection"), inverseValues, 0);
        block.setFloat4(layout.requireHandle("depths"), preparedDepthRange.nearPlaneDepth(), preparedDepthRange.farPlaneDepth(), 0, 0);
        block.setFloat4(layout.requireHandle("low"), volume.minX, volume.minY, volume.minZ, 0);
        block.setFloat4(layout.requireHandle("extent"), volume.width, volume.height, volume.depth, 0);
        block.setFloat4(layout.requireHandle("params"), steps, preparedTime, 0, density);
        block.setFloat4(layout.requireHandle("warm"), flamePalette[0], flamePalette[1], flamePalette[2], 0);
        block.setFloat4(layout.requireHandle("hot"), flamePalette[3], flamePalette[4], flamePalette[5], 0);
        block.setFloat4(layout.requireHandle("secondLow"), other.minX, other.minY, other.minZ, 0);
        block.setFloat4(layout.requireHandle("secondExtent"), other.width, other.height, other.depth, 0);
        pass.setParameterBlock(1, 0, block);
    }
    @Override public boolean isDisposed() { return disposed; }
    @Override public void dispose() {
        if (disposed) return;
        disposed = true;
        preparedPass = null;
        try { try { if (secondaryTexture != null) secondaryTexture.dispose(); }
        finally { if (texture != null) texture.dispose(); } }
        finally { try { if (vertices != null) vertices.dispose(); }
        finally { try { if (pipeline != null) pipeline.dispose(); }
        finally { if (shader != null) shader.dispose(); } } }
    }

    private static String secondarySource(ParticleVolume secondary) {
        if (secondary == null) return "fn sampleSecondary(p : vec3f) -> vec4f { return vec4f(0.0); }\n";
        return "const GRID2 = vec3f(" + secondary.nx + ".0," + secondary.ny + ".0," + secondary.nz
                + ".0); const TILES2 = vec2f(" + secondary.columns + ".0," + secondary.rows + ".0);\n"
                + """
                @group(0) @binding(2) var field2 : texture_2d<f32>;
                @group(0) @binding(3) var fieldSampler2 : sampler;
                fn secondaryLayer(p : vec2f, z : f32) -> vec4f {
                    let tile = vec2f(z % TILES2.x, floor(z / TILES2.x));
                    let uv = (tile * GRID2.xy + clamp(p * GRID2.xy, vec2f(0.5), GRID2.xy - 0.5)) / (GRID2.xy * TILES2);
                    return textureSampleLevel(field2, fieldSampler2, uv, 0.0);
                }
                fn sampleSecondary(p : vec3f) -> vec4f {
                    if (any(p < vec3f(0.0)) || any(p > vec3f(1.0))) { return vec4f(0.0); }
                    let z = clamp(p.z * GRID2.z - 0.5, 0.0, GRID2.z - 1.0);
                    return mix(secondaryLayer(p.xy, floor(z)),
                        secondaryLayer(p.xy, min(floor(z) + 1.0, GRID2.z - 1.0)), fract(z));
                }
                """;
    }

    private static final String SOLID_SOURCE = """
        struct Uniforms {
            inverseViewProjection : mat4x4f, depths : vec4f,
            low : vec4f, extent : vec4f, params : vec4f, warm : vec4f, hot : vec4f,
            secondLow : vec4f, secondExtent : vec4f,
        };
        @group(1) @binding(0) var<uniform> uniforms : Uniforms;
        struct V { @location(0) clip : vec4f, @location(1) color : vec4f, @location(2) world : vec3f };
        struct O { @builtin(position) position : vec4f, @location(0) color : vec4f,
                   @location(1) world : vec3f, @location(2) projected : vec4f };
        @vertex fn vertexMain(v : V) -> O {
            var o : O; o.position=v.clip; o.projected=v.clip; o.color=v.color; o.world=v.world; return o;
        }
        @fragment fn fragmentMain(v : O, @builtin(front_facing) inward : bool) -> @location(0) vec4f {
            if (inward) { discard; }
            let ndc = v.projected.xy / v.projected.w;
            let a = uniforms.inverseViewProjection * vec4f(ndc,uniforms.depths.x,1.0);
            let b = uniforms.inverseViewProjection * vec4f(ndc,uniforms.depths.y,1.0);
            let start = a.xyz/a.w; let end = b.xyz/b.w;
            let distance = dot(v.world-start,normalize(end-start));
            let medium = Medium(uniforms.low.xyz,uniforms.extent.xyz,uniforms.params,
                uniforms.warm.xyz,uniforms.hot.xyz,uniforms.secondLow.xyz,uniforms.secondExtent.xyz);
            let front = integrateMedium(start,end,distance,medium);
            // Preserve foreground radiance while replacing what lies behind this surface.
            return vec4f(clamp(front.color + front.transmittance*v.color.rgb,vec3f(0.0),vec3f(1.0)),v.color.a);
        }
        """;
    private static final String SOURCE = """
        struct In {
            @location(0) clip : vec2f, @location(1) start : vec3f, @location(2) end : vec3f,
            @location(3) low : vec3f, @location(4) extent : vec3f, @location(5) params : vec4f,
            @location(6) c0 : vec4f, @location(7) c1 : vec4f, @location(8) c2 : vec4f, @location(9) c3 : vec4f,
            @location(10) warm : vec3f, @location(11) hot : vec3f,
            @location(12) secondLow : vec3f, @location(13) secondExtent : vec3f,
        };
        struct Out {
            @builtin(position) position : vec4f,
            @location(0) start : vec3f, @location(1) end : vec3f,
            @location(2) low : vec3f, @location(3) extent : vec3f, @location(4) params : vec4f,
            @location(5) c0 : vec4f, @location(6) c1 : vec4f, @location(7) c2 : vec4f, @location(8) c3 : vec4f,
            @location(9) warm : vec3f, @location(10) hot : vec3f,
            @location(11) secondLow : vec3f, @location(12) secondExtent : vec3f,
        };
        @vertex fn vertexMain(v : In) -> Out {
            var o : Out;
            o.position = vec4f(v.clip, 0.0, 1.0); o.start = v.start; o.end = v.end;
            o.low = v.low; o.extent = v.extent; o.params = v.params;
            o.c0 = v.c0; o.c1 = v.c1; o.c2 = v.c2; o.c3 = v.c3; o.warm = v.warm; o.hot = v.hot;
            o.secondLow = v.secondLow; o.secondExtent = v.secondExtent; return o;
        }
        struct Fragment { @location(0) color : vec4f, @builtin(frag_depth) depth : f32 };
        @fragment fn fragmentMain(v : Out) -> Fragment {
            let medium = Medium(v.low,v.extent,v.params,v.warm,v.hot,v.secondLow,v.secondExtent);
            let result = integrateMedium(v.start,v.end,length(v.end-v.start),medium);
            let first = result.first;
            let ray = normalize(v.end-v.start);
            let radiance = result.color;
            let transmittance = result.transmittance;
            if (first < 0.0) { discard; }
            let opacity = 1.0 - transmittance;
            let clip = mat4x4f(v.c0,v.c1,v.c2,v.c3) * vec4f(v.start + ray * first,1.0);
            var o : Fragment;
            o.depth = clamp(mix(clip.z/clip.w,clip.z/clip.w*0.5+0.5,v.params.z),0.0,1.0);
            o.color = vec4f(clamp(radiance / max(opacity,0.001),vec3f(0.0),vec3f(1.0)),opacity);
            return o;
        }
        """;
}
