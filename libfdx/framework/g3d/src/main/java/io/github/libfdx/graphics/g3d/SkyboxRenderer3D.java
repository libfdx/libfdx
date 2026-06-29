package io.github.libfdx.graphics.g3d;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.Buffer;
import io.github.libfdx.graphics.BufferDescriptor;
import io.github.libfdx.graphics.camera.Camera;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.GraphicsFrame;
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.PrimitiveTopology;
import io.github.libfdx.graphics.RenderPass;
import io.github.libfdx.graphics.RenderPassDescriptor;
import io.github.libfdx.graphics.RenderPipeline;
import io.github.libfdx.graphics.RenderPipelineDescriptor;
import io.github.libfdx.graphics.ShaderModule;
import io.github.libfdx.graphics.ShaderModuleDescriptor;
import io.github.libfdx.graphics.StoreOp;
import io.github.libfdx.graphics.VertexAttribute;
import io.github.libfdx.graphics.VertexFormat;
import io.github.libfdx.graphics.VertexLayout;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Renders a WGSL-authored procedural sky background for 3D scenes.
 *
 * @author xpenatan
 */
public final class SkyboxRenderer3D implements Disposable {
    private static final int FLOATS_PER_VERTEX = 26;
    private static final int BYTES_PER_VERTEX = FLOATS_PER_VERTEX * 4;
    private static final int VERTEX_COUNT = 6;
    private static final VertexLayout VERTEX_LAYOUT = VertexLayout.of(BYTES_PER_VERTEX,
            VertexAttribute.of(0, VertexFormat.FLOAT32X2, 0),
            VertexAttribute.of(1, VertexFormat.FLOAT32X4, 8),
            VertexAttribute.of(2, VertexFormat.FLOAT32X4, 24),
            VertexAttribute.of(3, VertexFormat.FLOAT32X4, 40),
            VertexAttribute.of(4, VertexFormat.FLOAT32X4, 56),
            VertexAttribute.of(5, VertexFormat.FLOAT32X4, 72),
            VertexAttribute.of(6, VertexFormat.FLOAT32X4, 88));
    private static final String SOURCE = """
            struct VertexInput {
                @location(0) clipPosition : vec2f,
                @location(1) worldDirection : vec4f,
                @location(2) zenithColor : vec4f,
                @location(3) horizonColor : vec4f,
                @location(4) nadirColor : vec4f,
                @location(5) sunColor : vec4f,
                @location(6) params : vec4f,
            };

            struct VertexOutput {
                @builtin(position) position : vec4f,
                @location(0) worldDirection : vec3f,
                @location(1) zenithColor : vec4f,
                @location(2) horizonColor : vec4f,
                @location(3) nadirColor : vec4f,
                @location(4) sunColor : vec4f,
                @location(5) params : vec4f,
            };

            @vertex
            fn vertexMain(input : VertexInput) -> VertexOutput {
                var output : VertexOutput;
                output.position = vec4f(input.clipPosition, 1.0, 1.0);
                output.worldDirection = input.worldDirection.xyz;
                output.zenithColor = input.zenithColor;
                output.horizonColor = input.horizonColor;
                output.nadirColor = input.nadirColor;
                output.sunColor = input.sunColor;
                output.params = input.params;
                return output;
            }

            @fragment
            fn fragmentMain(input : VertexOutput) -> @location(0) vec4f {
                let ray = normalize(input.worldDirection);
                let sunDirection = normalize(input.params.xyz);
                let sunRadius = max(input.params.w * 0.24, 0.001);
                let below = smoothstep(-0.58, 0.02, ray.y);
                let above = smoothstep(0.02, 0.82, ray.y);
                let horizonHaze = 1.0 - smoothstep(0.0, 0.38, abs(ray.y));
                var color = mix(input.nadirColor, input.horizonColor, below);
                color = mix(color, input.zenithColor, above);
                color = mix(color, input.horizonColor, horizonHaze * 0.42);
                let sunDot = dot(ray, sunDirection);
                let disc = smoothstep(cos(sunRadius), cos(sunRadius * 0.35), sunDot);
                let glow = smoothstep(cos(sunRadius * 5.2), cos(sunRadius), sunDot);
                var rgb = mix(color.rgb, input.sunColor.rgb, disc * input.sunColor.a);
                rgb = rgb + input.sunColor.rgb * glow * input.sunColor.a * 0.20;
                return vec4f(rgb, 1.0);
            }
            """;
    private static final float[] CLIP_UV = {
            -1.0f, -1.0f, 0.0f, 0.0f,
            1.0f, -1.0f, 1.0f, 0.0f,
            1.0f, 1.0f, 1.0f, 1.0f,
            -1.0f, -1.0f, 0.0f, 0.0f,
            1.0f, 1.0f, 1.0f, 1.0f,
            -1.0f, 1.0f, 0.0f, 1.0f
    };

    private final GraphicsContext graphics;
    private final boolean heapUploadBuffers;
    private final ShaderModule shader;
    private final RenderPipeline pipeline;
    private final RenderPassDescriptor renderPassDescriptor =
            new RenderPassDescriptor().label("skybox renderer 3d pass");
    private final float[] vertices = new float[VERTEX_COUNT * FLOATS_PER_VERTEX];
    private final float[] zenithColor = { 0.06f, 0.18f, 0.42f, 1.0f };
    private final float[] horizonColor = { 0.78f, 0.56f, 0.34f, 1.0f };
    private final float[] nadirColor = { 0.018f, 0.025f, 0.04f, 1.0f };
    private final float[] sunColor = { 1.0f, 0.84f, 0.54f, 0.75f };
    private Buffer vertexBuffer;
    private ByteBuffer uploadBuffer;
    private FloatBuffer uploadFloats;
    private RenderPass pass;
    private boolean ownsPass;
    private boolean drawing;
    private boolean disposed;
    private float sunDirectionX = 0.25f;
    private float sunDirectionY = 0.82f;
    private float sunDirectionZ = -0.52f;
    private float sunSize = 0.105f;

    /**
     * Creates a 3D skybox renderer.
     *
     * @param graphics the graphics context
     */
    public SkyboxRenderer3D(GraphicsContext graphics) {
        if (graphics == null) {
            throw new FdxException("GraphicsContext cannot be null");
        }
        this.graphics = graphics;
        heapUploadBuffers = usesHeapUploadBuffers(graphics);
        shader = graphics.device().createShaderModule(ShaderModuleDescriptor.wgsl(
                "skybox renderer 3d", SOURCE));
        pipeline = graphics.device().createRenderPipeline(RenderPipelineDescriptor
                .shader(shader, graphics.surfaceFormat())
                .label("skybox renderer 3d")
                .primitiveTopology(PrimitiveTopology.TRIANGLE_LIST)
                .vertexLayout(VERTEX_LAYOUT)
                .depthTestEnabled(true)
                .depthWriteEnabled(false));
        vertexBuffer = graphics.device().createBuffer(BufferDescriptor.vertex(
                "skybox renderer 3d vertices", VERTEX_COUNT * BYTES_PER_VERTEX));
        uploadBuffer = newUploadBuffer(VERTEX_COUNT * BYTES_PER_VERTEX);
        uploadFloats = uploadBuffer.asFloatBuffer();
    }

    /**
     * Begins the operation.
     */
    public void begin() {
        begin(LoadOp.clear(0.0f, 0.0f, 0.0f, 1.0f));
    }

    /**
     * Begins the operation.
     *
     * @param loadOp the load operation for the color attachment
     */
    public void begin(LoadOp loadOp) {
        ensureNotDisposed();
        GraphicsFrame frame = graphics.currentFrame();
        pass = frame.commandEncoder().beginRenderPass(renderPassDescriptor
                .colorAttachment(frame.colorAttachment())
                .colorLoadOp(loadOp != null ? loadOp : LoadOp.load())
                .colorStoreOp(StoreOp.store())
                .depthClear(1.0f));
        ownsPass = true;
        drawing = true;
    }

    /**
     * Begins the operation.
     *
     * @param pass the render pass
     */
    public void begin(RenderPass pass) {
        ensureNotDisposed();
        if (pass == null) {
            throw new FdxException("RenderPass cannot be null");
        }
        this.pass = pass;
        ownsPass = false;
        drawing = true;
    }

    /**
     * Sets the upper sky color and returns this renderer.
     *
     * @param red the red component
     * @param green the green component
     * @param blue the blue component
     * @return this renderer for chaining
     */
    public SkyboxRenderer3D zenithColor(float red, float green, float blue) {
        setColor(zenithColor, red, green, blue, 1.0f);
        return this;
    }

    /**
     * Sets the horizon sky color and returns this renderer.
     *
     * @param red the red component
     * @param green the green component
     * @param blue the blue component
     * @return this renderer for chaining
     */
    public SkyboxRenderer3D horizonColor(float red, float green, float blue) {
        setColor(horizonColor, red, green, blue, 1.0f);
        return this;
    }

    /**
     * Sets the lower sky color and returns this renderer.
     *
     * @param red the red component
     * @param green the green component
     * @param blue the blue component
     * @return this renderer for chaining
     */
    public SkyboxRenderer3D nadirColor(float red, float green, float blue) {
        setColor(nadirColor, red, green, blue, 1.0f);
        return this;
    }

    /**
     * Sets the sun color and intensity and returns this renderer.
     *
     * @param red the red component
     * @param green the green component
     * @param blue the blue component
     * @param intensity the sun intensity from 0 to 1
     * @return this renderer for chaining
     */
    public SkyboxRenderer3D sunColor(float red, float green, float blue, float intensity) {
        validateFinite(intensity, "Skybox sun intensity");
        setColor(sunColor, red, green, blue, clamp(intensity, 0.0f, 1.0f));
        return this;
    }

    /**
     * Sets the sun position from normalized sky coordinates and returns this renderer.
     *
     * @param x the normalized x coordinate
     * @param y the normalized y coordinate
     * @return this renderer for chaining
     */
    public SkyboxRenderer3D sunPosition(float x, float y) {
        validateFinite(x, "Skybox sun x");
        validateFinite(y, "Skybox sun y");
        float azimuth = (clamp(x, 0.0f, 1.0f) - 0.5f) * ((float)Math.PI * 2.0f);
        float elevation = (clamp(y, 0.0f, 1.0f) - 0.5f) * (float)Math.PI;
        float horizontal = (float)Math.cos(elevation);
        sunDirection((float)Math.sin(azimuth) * horizontal, (float)Math.sin(elevation),
                -(float)Math.cos(azimuth) * horizontal);
        return this;
    }

    /**
     * Sets the world-space sun direction and returns this renderer.
     *
     * @param x the x direction
     * @param y the y direction
     * @param z the z direction
     * @return this renderer for chaining
     */
    public SkyboxRenderer3D sunDirection(float x, float y, float z) {
        validateFinite(x, "Skybox sun direction x");
        validateFinite(y, "Skybox sun direction y");
        validateFinite(z, "Skybox sun direction z");
        float len = (float)Math.sqrt(x * x + y * y + z * z);
        if (len <= 0.0f) {
            throw new FdxException("Skybox sun direction cannot be zero length");
        }
        float invLen = 1.0f / len;
        sunDirectionX = x * invLen;
        sunDirectionY = y * invLen;
        sunDirectionZ = z * invLen;
        return this;
    }

    /**
     * Sets the sun size and returns this renderer.
     *
     * @param size the normalized sun radius
     * @return this renderer for chaining
     */
    public SkyboxRenderer3D sunSize(float size) {
        validateFinite(size, "Skybox sun size");
        if (size <= 0.0f) {
            throw new FdxException("Skybox sun size must be greater than zero");
        }
        sunSize = size;
        return this;
    }

    /**
     * Draws the skybox background using the current camera orientation.
     *
     * @param camera the active camera
     */
    public void draw(Camera camera) {
        ensureDrawing();
        if (camera == null) {
            throw new FdxException("Camera cannot be null");
        }
        writeVertices(camera);
        uploadBuffer.clear();
        uploadFloats.clear();
        uploadFloats.put(vertices, 0, vertices.length);
        uploadBuffer.limit(vertices.length * 4);
        uploadBuffer.position(0);
        graphics.device().writeBuffer(vertexBuffer, uploadBuffer);
        uploadBuffer.clear();
        pass.setPipeline(pipeline);
        pass.setVertexBuffer(vertexBuffer);
        pass.draw(VERTEX_COUNT, 1, 0, 0);
    }

    /**
     * Ends the operation.
     */
    public void end() {
        ensureDrawing();
        drawing = false;
        if (ownsPass) {
            pass.end();
        }
        pass = null;
        ownsPass = false;
    }

    private void writeVertices(Camera camera) {
        float forwardX = camera.direction().x();
        float forwardY = camera.direction().y();
        float forwardZ = camera.direction().z();
        float upX = camera.up().x();
        float upY = camera.up().y();
        float upZ = camera.up().z();
        float rightX = forwardY * upZ - forwardZ * upY;
        float rightY = forwardZ * upX - forwardX * upZ;
        float rightZ = forwardX * upY - forwardY * upX;
        float rightLength = (float)Math.sqrt(rightX * rightX + rightY * rightY + rightZ * rightZ);
        if (rightLength <= 0.000001f) {
            rightX = 1.0f;
            rightY = 0.0f;
            rightZ = 0.0f;
            rightLength = 1.0f;
        }
        float invRightLength = 1.0f / rightLength;
        rightX *= invRightLength;
        rightY *= invRightLength;
        rightZ *= invRightLength;
        float trueUpX = rightY * forwardZ - rightZ * forwardY;
        float trueUpY = rightZ * forwardX - rightX * forwardZ;
        float trueUpZ = rightX * forwardY - rightY * forwardX;
        float verticalScale = (float)Math.tan(Math.toRadians(camera.fieldOfView()) * 0.5f);
        float horizontalScale = verticalScale * Math.max(camera.viewportWidth() / camera.viewportHeight(), 0.0001f);
        int in = 0;
        int out = 0;
        for (int vertex = 0; vertex < VERTEX_COUNT; vertex++) {
            float clipX = CLIP_UV[in++];
            float clipY = CLIP_UV[in++];
            in += 2;
            float rayX = forwardX + rightX * clipX * horizontalScale + trueUpX * clipY * verticalScale;
            float rayY = forwardY + rightY * clipX * horizontalScale + trueUpY * clipY * verticalScale;
            float rayZ = forwardZ + rightZ * clipX * horizontalScale + trueUpZ * clipY * verticalScale;
            float rayLength = (float)Math.sqrt(rayX * rayX + rayY * rayY + rayZ * rayZ);
            float invRayLength = rayLength > 0.000001f ? 1.0f / rayLength : 1.0f;
            vertices[out++] = clipX;
            vertices[out++] = clipY;
            vertices[out++] = rayX * invRayLength;
            vertices[out++] = rayY * invRayLength;
            vertices[out++] = rayZ * invRayLength;
            vertices[out++] = 0.0f;
            out = appendColor(vertices, out, zenithColor);
            out = appendColor(vertices, out, horizonColor);
            out = appendColor(vertices, out, nadirColor);
            out = appendColor(vertices, out, sunColor);
            vertices[out++] = sunDirectionX;
            vertices[out++] = sunDirectionY;
            vertices[out++] = sunDirectionZ;
            vertices[out++] = sunSize;
        }
    }

    private static int appendColor(float[] values, int index, float[] color) {
        values[index++] = color[0];
        values[index++] = color[1];
        values[index++] = color[2];
        values[index++] = color[3];
        return index;
    }

    private void setColor(float[] target, float red, float green, float blue, float alpha) {
        validateFinite(red, "Skybox color red");
        validateFinite(green, "Skybox color green");
        validateFinite(blue, "Skybox color blue");
        validateFinite(alpha, "Skybox color alpha");
        target[0] = clamp(red, 0.0f, 1.0f);
        target[1] = clamp(green, 0.0f, 1.0f);
        target[2] = clamp(blue, 0.0f, 1.0f);
        target[3] = clamp(alpha, 0.0f, 1.0f);
    }

    private ByteBuffer newUploadBuffer(int byteCount) {
        ByteBuffer buffer = heapUploadBuffers ? ByteBuffer.allocate(byteCount) : ByteBuffer.allocateDirect(byteCount);
        return buffer.order(ByteOrder.nativeOrder());
    }

    private static boolean usesHeapUploadBuffers(GraphicsContext graphics) {
        return "psp".equals(graphics.providerId().value());
    }

    private static void validateFinite(float value, String label) {
        if (!Float.isFinite(value)) {
            throw new FdxException(label + " must be finite");
        }
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private void ensureDrawing() {
        ensureNotDisposed();
        if (!drawing || pass == null) {
            throw new FdxException("SkyboxRenderer3D.begin() must be called before drawing");
        }
    }

    private void ensureNotDisposed() {
        if (disposed) {
            throw new FdxException("SkyboxRenderer3D has been disposed");
        }
    }

    /**
     * Releases resources held by this instance.
     */
    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        if (vertexBuffer != null) {
            vertexBuffer.dispose();
            vertexBuffer = null;
        }
        pipeline.dispose();
        shader.dispose();
    }

    /**
     * Returns whether this instance has already been disposed.
     *
     * @return true if disposed is enabled or true; false otherwise
     */
    @Override
    public boolean isDisposed() {
        return disposed;
    }
}
