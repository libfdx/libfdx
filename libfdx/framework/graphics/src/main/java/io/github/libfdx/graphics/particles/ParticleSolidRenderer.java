package io.github.libfdx.graphics.particles;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.*;
import io.github.libfdx.graphics.Buffer;
import io.github.libfdx.graphics.shader.ShaderModule;
import io.github.libfdx.graphics.shader.ShaderModuleDescriptor;
import io.github.libfdx.graphics.shader.runtime.ShaderParameterBlock;
import io.github.libfdx.math.Matrix4;
import io.github.libfdx.math.ClipDepthRange;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Batches world-space octahedra for small sparks, snow and debris. Owns GPU storage;
 * thread-confined. Sorts all submissions by center depth, back to front, across GPU
 * upload chunks. Alpha blends outward faces without writing scene depth. Intersecting
 * translucent solids still have the usual center-sorting limitation.
 */
public final class ParticleSolidRenderer implements Disposable {
    private static final float[] POINTS = {1,0,0, -1,0,0, 0,1,0, 0,-1,0, 0,0,1, 0,0,-1};
    private static final int[] FACES = {2,0,4, 2,4,1, 2,1,5, 2,5,0, 3,4,0, 3,1,4, 3,5,1, 3,0,5};
    private final GraphicsContext graphics;
    private final ParticleVolumeRenderer medium;
    private final ByteBuffer upload;
    private float[] particles, depths;
    private int[] order;
    private int count;
    private boolean reversedDepth;
    private Buffer buffer;
    private ShaderModule shader;
    private RenderPipeline pipeline;
    private ShaderParameterBlock mediumParameters;
    private RenderPass pass;
    private final float[] projection = new float[16];
    private boolean disposed;
    public ParticleSolidRenderer(GraphicsContext graphics, int capacity) {
        this(graphics, capacity, null);
    }
    /**
     * Borrows an optional medium renderer. Draw that medium first in the same pass,
     * with the same camera, then submit all solid emitters in one begin/end scope.
     * Every solid color is composited through the medium up to its actual surface.
     * Keep that medium's grids and settings unchanged between its draw and this end.
     * Null selects standalone solids. Dispose this renderer before its borrowed medium.
     */
    public ParticleSolidRenderer(GraphicsContext graphics, int capacity, ParticleVolumeRenderer medium) {
        if (graphics == null || capacity < 1 || capacity > 100000) throw new FdxException("Invalid solid particle capacity");
        this.graphics = graphics;
        this.medium = medium;
        particles = new float[capacity * 8]; depths = new float[capacity]; order = new int[capacity];
        upload = ByteBuffer.allocateDirect(capacity * 24 * 44).order(ByteOrder.nativeOrder());
        try {
            shader = graphics.device().createShaderModule(ShaderModuleDescriptor.wgsl("solid particles", medium != null ? medium.solidSource(graphics) : """
                struct V { @location(0) clip : vec4f, @location(1) color : vec4f };
                struct O { @builtin(position) clip : vec4f, @location(0) color : vec4f };
                @vertex fn vertexMain(v : V) -> O { var o : O; o.clip=v.clip; o.color=v.color; return o; }
                @fragment fn fragmentMain(v : O, @builtin(front_facing) inward : bool) -> @location(0) vec4f {
                    if (inward) { discard; } return v.color;
                }
                """));
            if (medium != null) {
                for (var binding : shader.reflection().bindings()) {
                    if (binding.group() == 1 && binding.binding() == 0) {
                        mediumParameters = ShaderParameterBlock.allocate(binding.bufferLayout());
                    }
                }
                if (mediumParameters == null) throw new FdxException("Particle medium uniform layout is missing");
            }
            pipeline = graphics.device().createRenderPipeline(RenderPipelineDescriptor.shader(shader, graphics.surfaceFormat())
                    .vertexLayout(VertexLayout.of(44, VertexAttribute.of(0, VertexFormat.FLOAT32X4, 0),
                            VertexAttribute.of(1, VertexFormat.FLOAT32X4, 16), VertexAttribute.of(2, VertexFormat.FLOAT32X3, 32)))
                    .sampledTextureCount(medium == null ? 0 : medium.textureCount())
                    .depthTestEnabled(true).depthWriteEnabled(false));
            buffer = graphics.device().createBuffer(BufferDescriptor.vertex("solid particle vertices", upload.capacity()));
        } catch (RuntimeException | Error e) { dispose(); throw e; }
    }
    /**
     * Borrows the active pass and copies the camera matrix. Its depth range must
     * match the active graphics device, as for other world-space geometry.
     */
    public void begin(RenderPass pass, Matrix4 viewProjection, ClipDepthRange depthRange) {
        if (disposed || this.pass != null || pass == null || viewProjection == null || depthRange == null) throw new FdxException("Invalid solid particle scope");
        viewProjection.copyValues(projection, 0);
        if (medium != null) medium.requireSolidCamera(pass, projection, depthRange);
        this.pass = pass;
        reversedDepth = depthRange.isReversed(); count = 0; upload.clear();
    }
    /** Queues a world-space solid. Storage grows only when the previous high-water mark is exceeded. */
    public void add(float x, float y, float z, float radius, float r, float g, float b, float alpha) {
        if (pass == null) throw new FdxException("Solid particle renderer has not begun");
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z) || !Float.isFinite(radius) || radius <= 0
                || !Float.isFinite(r) || !Float.isFinite(g) || !Float.isFinite(b) || !Float.isFinite(alpha))
            throw new FdxException("Invalid solid particle");
        if (count == order.length) {
            int capacity = Math.max(count + 1, count * 2);
            particles = java.util.Arrays.copyOf(particles, capacity * 8);
            depths = java.util.Arrays.copyOf(depths, capacity); order = java.util.Arrays.copyOf(order, capacity);
        }
        int at = count * 8;
        particles[at]=x; particles[at+1]=y; particles[at+2]=z; particles[at+3]=radius;
        particles[at+4]=r; particles[at+5]=g; particles[at+6]=b; particles[at+7]=alpha;
        float[] m = projection;
        float w = m[3]*x+m[7]*y+m[11]*z+m[15];
        depths[count] = (m[2]*x+m[6]*y+m[10]*z+m[14]) / Math.max(w, 0.000001f) * (reversedDepth ? -1 : 1);
        order[count] = count; count++;
    }
    private void append(int index) {
        if (upload.remaining() < 24 * 44) flush();
        int at = index * 8;
        float x=particles[at], y=particles[at+1], z=particles[at+2], radius=particles[at+3];
        float r=particles[at+4], g=particles[at+5], b=particles[at+6], alpha=particles[at+7];
        float[] m = projection;
        for (int i = 0; i < FACES.length; i++) {
            int p = FACES[i] * 3;
            float px = x + POINTS[p] * radius, py = y + POINTS[p + 1] * radius, pz = z + POINTS[p + 2] * radius;
            upload.putFloat(m[0]*px+m[4]*py+m[8]*pz+m[12]);
            upload.putFloat(m[1]*px+m[5]*py+m[9]*pz+m[13]);
            float clipZ = m[2]*px+m[6]*py+m[10]*pz+m[14];
            float clipW = m[3]*px+m[7]*py+m[11]*pz+m[15];
            // Preserve camera-native depth, exactly as the surrounding scene does.
            upload.putFloat(clipZ);
            upload.putFloat(clipW);
            float light = 0.7f + 0.1f * ((i / 3) % 4);
            upload.putFloat(r * light).putFloat(g * light).putFloat(b * light).putFloat(alpha);
            upload.putFloat(px).putFloat(py).putFloat(pz);
        }
    }
    private void flush() {
        if (upload.position() == 0) return;
        int count = upload.position() / 44;
        upload.flip(); graphics.device().writeBuffer(buffer, upload);
        pass.setPipeline(pipeline); pass.setVertexBuffer(buffer);
        if (medium != null) medium.bindSolids(pass, mediumParameters);
        pass.draw(count, 1, 0, 0); upload.clear();
    }
    private void sort(int low, int high) {
        int i=low, j=high; float pivot=depths[order[(low+high) >>> 1]];
        while (i <= j) {
            while (depths[order[i]] > pivot) i++;
            while (depths[order[j]] < pivot) j--;
            if (i <= j) { int swap=order[i]; order[i++]=order[j]; order[j--]=swap; }
        }
        if (low < j) sort(low,j);
        if (i < high) sort(i,high);
    }
    public void end() {
        if (pass == null) throw new FdxException("Solid particle renderer has not begun");
        try {
            if (count > 1) sort(0, count - 1);
            for (int i=0;i<count;i++) append(order[i]);
            flush();
        } finally { pass = null; count = 0; }
    }
    @Override
    public boolean isDisposed() { return disposed; }
    @Override
    public void dispose() {
        if (disposed) return;
        disposed = true; pass = null;
        try { if (buffer != null) buffer.dispose(); }
        finally { try { if (pipeline != null) pipeline.dispose(); } finally { if (shader != null) shader.dispose(); } }
    }
}
