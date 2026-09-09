package io.github.libfdx.tests.graphics;

import io.github.libfdx.testsupport.graphics.FramebufferCapture;

import io.github.libfdx.Fdx;
import io.github.libfdx.application.Application;
import io.github.libfdx.application.ApplicationAdapter;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.Logger;
import io.github.libfdx.display.Display;
import io.github.libfdx.graphics.Buffer;
import io.github.libfdx.graphics.BufferDescriptor;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.GraphicsFrame;
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.RenderPass;
import io.github.libfdx.graphics.RenderPassDescriptor;
import io.github.libfdx.graphics.RenderPipeline;
import io.github.libfdx.graphics.RenderPipelineDescriptor;
import io.github.libfdx.graphics.shader.ShaderModule;
import io.github.libfdx.graphics.shader.ShaderModuleDescriptor;
import io.github.libfdx.graphics.StoreOp;
import io.github.libfdx.graphics.VertexAttribute;
import io.github.libfdx.graphics.VertexFormat;
import io.github.libfdx.graphics.VertexLayout;
import io.github.libfdx.testsupport.TestFpsLogger;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Renders a deterministic WGSL sculpture scene with ray-marched surfaces, shadows and tiled ground.
 *
 * @author xpenatan
 */
public final class ShaderSceneTest extends ApplicationAdapter {
    private static final int FLOATS_PER_VERTEX = 4;
    private static final int BYTES_PER_VERTEX = FLOATS_PER_VERTEX * 4;
    private static final int VERTEX_COUNT = 6;
    private static final VertexLayout VERTEX_LAYOUT = VertexLayout.of(BYTES_PER_VERTEX,
            VertexAttribute.of(0, VertexFormat.FLOAT32X2, 0),
            VertexAttribute.of(1, VertexFormat.FLOAT32X2, 8));
    private static final float[] QUAD_VERTICES = {
            -1.0f, -1.0f, 0.0f, 0.0f,
            1.0f, -1.0f, 1.0f, 0.0f,
            1.0f, 1.0f, 1.0f, 1.0f,
            -1.0f, -1.0f, 0.0f, 0.0f,
            1.0f, 1.0f, 1.0f, 1.0f,
            -1.0f, 1.0f, 0.0f, 1.0f
    };
    private static final LoadOp CLEAR = LoadOp.clear(0.02f, 0.025f, 0.035f, 1.0f);
    private static final StoreOp STORE = StoreOp.store();
    private static final String SHADER_SOURCE = """
            struct VertexInput {
                @location(0) position : vec2<f32>,
                @location(1) uv : vec2<f32>,
            };

            struct VertexOutput {
                @builtin(position) position : vec4<f32>,
                @location(0) uv : vec2<f32>,
            };

            @vertex
            fn vertexMain(input : VertexInput) -> VertexOutput {
                var output : VertexOutput;
                output.position = vec4<f32>(input.position, 0.0, 1.0);
                output.uv = input.uv;
                return output;
            }

            // Signed distances keep silhouettes sharp at the actual framebuffer resolution.
            fn box(p : vec3<f32>, size : vec3<f32>) -> f32 {
                let q = abs(p) - size;
                return length(max(q, vec3<f32>(0.0))) + min(max(q.x, max(q.y, q.z)), 0.0);
            }

            fn scene(p : vec3<f32>) -> vec2<f32> {
                var hit = vec2<f32>(p.y, 0.0);
                let plinth = box(p - vec3<f32>(0.0, 0.18, 0.0), vec3<f32>(2.4, 0.18, 1.25)) - 0.04;
                if (plinth < hit.x) { hit = vec2<f32>(plinth, 1.0); }
                let pedestal = box(p - vec3<f32>(-0.85, 0.54, 0.0), vec3<f32>(0.88, 0.18, 0.77)) - 0.035;
                if (pedestal < hit.x) { hit = vec2<f32>(pedestal, 1.0); }
                let q = p - vec3<f32>(-0.85, 1.785, 0.0);
                let torus = length(vec2<f32>(length(q.xy) - 0.78, q.z)) - 0.25;
                if (torus < hit.x) { hit = vec2<f32>(torus, 2.0); }
                let sphere = length(p - vec3<f32>(1.15, 1.05, 0.1)) - 0.65;
                if (sphere < hit.x) { hit = vec2<f32>(sphere, 3.0); }
                return hit;
            }

            fn normalAt(p : vec3<f32>) -> vec3<f32> {
                let e = vec2<f32>(0.001, 0.0);
                return normalize(vec3<f32>(
                    scene(p + e.xyy).x - scene(p - e.xyy).x,
                    scene(p + e.yxy).x - scene(p - e.yxy).x,
                    scene(p + e.yyx).x - scene(p - e.yyx).x));
            }

            fn shadowAt(p : vec3<f32>, light : vec3<f32>) -> f32 {
                var visibility = 1.0;
                var distance = 0.03;
                for (var i = 0; i < 40; i = i + 1) {
                    let h = scene(p + light * distance).x;
                    visibility = min(visibility, 12.0 * h / distance);
                    distance = distance + clamp(h, 0.025, 0.3);
                    if (h < 0.001 || distance > 9.0) { break; }
                }
                return clamp(visibility, 0.0, 1.0);
            }

            @fragment
            fn fragmentMain(input : VertexOutput) -> @location(0) vec4<f32> {
                let eye = vec3<f32>(4.2, 3.1, 7.4);
                let forward = normalize(vec3<f32>(0.0, 1.1, 0.0) - eye);
                let right = normalize(cross(forward, vec3<f32>(0.0, 1.0, 0.0)));
                let up = cross(right, forward);
                let ray = normalize(forward * 2.6 + right * input.uv.x + up * input.uv.y);
                // Evaluate derivatives before divergent ray marching or material branches.
                let ground = eye + ray * (-eye.y / min(ray.y, -0.001));
                let groundFootprint = max(fwidth(ground.xz * 0.7), vec2<f32>(0.001));
                var distance = 0.0;
                var material = 0.0;
                var found = false;
                for (var i = 0; i < 112; i = i + 1) {
                    let hit = scene(eye + ray * distance);
                    if (hit.x < 0.0001 * max(1.0, distance)) {
                        material = hit.y;
                        found = true;
                        break;
                    }
                    distance = distance + hit.x;
                    if (distance > 45.0) { break; }
                }
                let sky = mix(vec3<f32>(0.16, 0.23, 0.32), vec3<f32>(0.035, 0.065, 0.12), max(ray.y, 0.0));
                var color = sky;
                if (found) {
                    let p = eye + ray * distance;
                    let n = normalAt(p);
                    let light = normalize(vec3<f32>(-3.0, 6.0, 4.0));
                    var base = vec3<f32>(0.32, 0.38, 0.43);
                    var gloss = 32.0;
                    var specular = vec3<f32>(0.16);
                    if (material < 0.5) {
                        // Derivative-filtered grout stays legible without distant grid shimmer.
                        let grid = abs(fract(p.xz * 0.7 - vec2<f32>(0.5)) - vec2<f32>(0.5));
                        let aa = groundFootprint;
                        let lines = vec2<f32>(1.0) - smoothstep(vec2<f32>(0.014), vec2<f32>(0.014) + aa, grid);
                        base = mix(vec3<f32>(0.24, 0.29, 0.34), vec3<f32>(0.11, 0.15, 0.19), max(lines.x, lines.y));
                    } else if (material < 1.5) {
                        base = vec3<f32>(0.58, 0.63, 0.65);
                    } else if (material < 2.5) {
                        base = vec3<f32>(0.015, 0.40, 0.37);
                        gloss = 90.0;
                        specular = vec3<f32>(0.65);
                    } else {
                        base = vec3<f32>(0.72, 0.22, 0.075);
                        gloss = 64.0;
                        specular = vec3<f32>(0.95, 0.58, 0.3);
                    }
                    var occlusion = 0.0;
                    for (var i = 1; i <= 4; i = i + 1) {
                        let h = 0.12 * f32(i);
                        occlusion = occlusion + max(0.0, h - scene(p + n * h).x) / f32(i);
                    }
                    let ambient = clamp(1.0 - occlusion * 1.5, 0.25, 1.0);
                    let shadow = shadowAt(p + n * 0.015, light);
                    let diffuse = max(dot(n, light), 0.0) * shadow;
                    let halfVector = normalize(light - ray);
                    let highlight = pow(max(dot(n, halfVector), 0.0), gloss) * shadow;
                    let rim = pow(1.0 - max(dot(n, -ray), 0.0), 4.0);
                    color = base * (vec3<f32>(0.22, 0.31, 0.42) * ambient + vec3<f32>(1.35, 1.22, 1.02) * diffuse)
                        + specular * highlight + vec3<f32>(0.12, 0.22, 0.3) * rim * ambient;
                    color = mix(color, sky, 1.0 - exp(-distance * distance * 0.0005));
                }
                color = color / (vec3<f32>(1.0) + color);
                return vec4<f32>(pow(color, vec3<f32>(1.0 / 2.2)), 1.0);
            }
            """;

    private final long exitAfterFrames;
    private final RenderPassDescriptor passDescriptor = new RenderPassDescriptor()
            .label("shader scene pass")
            .colorLoadOp(CLEAR)
            .colorStoreOp(STORE);
    private Application application;
    private Display display;
    private GraphicsContext graphics;
    private Logger logger;
    private TestFpsLogger fpsLogger;
    private ShaderModule shaderModule;
    private RenderPipeline pipeline;
    private Buffer vertexBuffer;
    private final ByteBuffer quadData = ByteBuffer.allocateDirect(VERTEX_COUNT * BYTES_PER_VERTEX)
            .order(ByteOrder.nativeOrder());
    private int quadWidth;
    private int quadHeight;
    private String capturePath;
    private long captureFrame;
    private boolean created;
    private boolean captured;
    private long renderedFrames;

    /**
     * Creates a procedural shader scene test.
     *
     * @param exitAfterFrames the exit after frames
     */
    public ShaderSceneTest(long exitAfterFrames) {
        this.exitAfterFrames = exitAfterFrames;
    }

    /**
     * Initializes the application with the libFDX runtime root.
     *
     * @param fdx the libFDX runtime root
     */
    @Override
    public void create(Fdx fdx) {
        application = fdx.app();
        display = fdx.displays().main();
        graphics = fdx.graphics().main();
        logger = fdx.logger();
        fpsLogger = TestFpsLogger.create(logger, "ShaderSceneTest");
        vertexBuffer = graphics.device().createBuffer(BufferDescriptor.vertex("shader scene quad vertices",
                VERTEX_COUNT * BYTES_PER_VERTEX));
        updateQuad();
        shaderModule = graphics.device().createShaderModule(ShaderModuleDescriptor
                .wgsl("procedural shader scene", SHADER_SOURCE));
        pipeline = graphics.device().createRenderPipeline(RenderPipelineDescriptor
                .shader(shaderModule, graphics.surfaceFormat())
                .label("procedural shader scene")
                .vertexLayout(VERTEX_LAYOUT)
                .depthWriteEnabled(false));
        capturePath = System.getProperty("libfdx.test.capture", "");
        captureFrame = Long.parseLong(System.getProperty("libfdx.test.captureFrame", "2"));
        created = true;
        logger.info("ShaderSceneTest created WGSL procedural shader scene for provider "
                + graphics.providerId().value());
    }

    /**
     * Renders the current content.
     */
    @Override
    public void render() {
        updateQuad();
        GraphicsFrame frame = graphics.currentFrame();
        passDescriptor.colorAttachment(frame.colorAttachment());
        RenderPass pass = frame.commandEncoder().beginRenderPass(passDescriptor);
        pass.setPipeline(pipeline);
        pass.setVertexBuffer(vertexBuffer);
        pass.draw(VERTEX_COUNT, 1, 0, 0);
        pass.end();

        if (capturePath != null && capturePath.length() > 0 && !captured && renderedFrames >= captureFrame) {
            captureFrame(capturePath);
            captured = true;
        }
        renderedFrames++;
        fpsLogger.frame(application.deltaTime(), renderedFrames);
        if (exitAfterFrames > 0L && renderedFrames >= exitAfterFrames) {
            application.requestExit();
        }
    }

    /**
     * Releases resources held by this instance.
     */
    @Override
    public void dispose() {
        if (pipeline != null) {
            pipeline.dispose();
            pipeline = null;
        }
        if (shaderModule != null) {
            shaderModule.dispose();
            shaderModule = null;
        }
        if (vertexBuffer != null) {
            vertexBuffer.dispose();
            vertexBuffer = null;
        }
        if (!created) {
            throw new FdxException("ShaderSceneTest did not create graphics resources");
        }
        if (exitAfterFrames > 0L && renderedFrames < exitAfterFrames) {
            throw new FdxException("ShaderSceneTest rendered " + renderedFrames + " of "
                    + exitAfterFrames + " required frames");
        }
        if (capturePath != null && capturePath.length() > 0 && !captured) {
            throw new FdxException("ShaderSceneTest did not capture framebuffer to " + capturePath);
        }
        logger.info("ShaderSceneTest rendered " + renderedFrames + " frames");
    }

    private int framebufferWidth() {
        int width = display.framebufferWidth() > 0 ? display.framebufferWidth() : display.width();
        return width > 0 ? width : 640;
    }

    private int framebufferHeight() {
        int height = display.framebufferHeight() > 0 ? display.framebufferHeight() : display.height();
        return height > 0 ? height : 480;
    }

    private void captureFrame(String path) {
        try {
            ByteBuffer pixels = FramebufferCapture.readPixelsRgba8(graphics);
            FramebufferCapture.writePpm(path, framebufferWidth(), framebufferHeight(), pixels);
            logger.info("ShaderSceneTest captured framebuffer to " + path);
        } catch (Exception e) {
            throw new FdxException("Could not capture ShaderSceneTest framebuffer", e);
        }
    }

    private void updateQuad() {
        int width = framebufferWidth();
        int height = framebufferHeight();
        if (width == quadWidth && height == quadHeight) {
            return;
        }
        quadWidth = width;
        quadHeight = height;
        // Preserve vertical field of view in landscape, and scene framing in portrait.
        float xScale = Math.max(1.0f, (float) width / height);
        float yScale = Math.max(1.0f, (float) height / width);
        quadData.clear();
        for (int i = 0; i < QUAD_VERTICES.length; i += FLOATS_PER_VERTEX) {
            quadData.putFloat(QUAD_VERTICES[i]);
            quadData.putFloat(QUAD_VERTICES[i + 1]);
            quadData.putFloat((QUAD_VERTICES[i + 2] * 2.0f - 1.0f) * xScale);
            quadData.putFloat((QUAD_VERTICES[i + 3] * 2.0f - 1.0f) * yScale);
        }
        quadData.flip();
        graphics.device().writeBuffer(vertexBuffer, quadData);
    }
}
