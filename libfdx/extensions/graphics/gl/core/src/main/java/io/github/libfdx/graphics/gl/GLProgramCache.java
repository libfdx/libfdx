package io.github.libfdx.graphics.gl;

import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.graphics.shader.ShaderModuleDescriptor;
import io.github.libfdx.graphics.shader.runtime.ShaderArtifactCache;
import io.github.libfdx.graphics.shader.runtime.ShaderCacheKey;
import io.github.libfdx.graphics.shader.runtime.ShaderCacheLayer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Binary storage is asynchronous; native import/export is explicitly loading-only. */
final class GLProgramCache {
    private static final int HEADER_BYTES = 8;
    private static final int SCHEMA = 1;
    private final ShaderArtifactCache artifacts;
    private final String identity;
    private final GLApi gl;

    record Entry(ShaderCacheKey key, GLProgramBinary binary) { }

    GLProgramCache(ShaderArtifactCache artifacts, String identity, GLApi gl) {
        this.artifacts = artifacts; this.identity = identity; this.gl = gl;
    }

    /** Called on a source worker (or an explicit loading continuation), never during drawing. */
    FdxFuture<Entry> read(ShaderModuleDescriptor source) {
        ShaderCacheKey key = ShaderCacheKey.of(ShaderCacheLayer.DRIVER_PIPELINE,
                "gl-linked-program:1", identity, source.vertexEntryPoint(), source.fragmentEntryPoint(),
                GLGraphicsDevice.normalizeGlslSource(source.glslVertexSource()),
                GLGraphicsDevice.normalizeGlslSource(source.glslFragmentSource()));
        FdxFuture<Entry> result = FdxFuture.pending();
        artifacts.readAsync(key).onSuccess(payload -> {
            GLProgramBinary binary = null;
            if (payload != null) {
                if (payload.length > HEADER_BYTES && payload.length <= ShaderArtifactCache.MAX_PAYLOAD_BYTES) {
                    ByteBuffer data = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);
                    if (data.getInt() == SCHEMA) {
                        int format = data.getInt();
                        byte[] bytes = new byte[data.remaining()]; data.get(bytes);
                        binary = new GLProgramBinary(format, bytes);
                    }
                }
                if (binary == null) artifacts.rejected(key);
            }
            result.complete(new Entry(key, binary));
        }).onFailure(result::completeExceptionally);
        return result;
    }

    void rejected(Entry entry) { artifacts.rejected(entry.key()); }
    void restoring(Entry entry) { artifacts.pipelineInvoked(entry.key(), true); }
    void compiling(Entry entry) {
        artifacts.compilerInvoked(entry.key());
        artifacts.pipelineInvoked(entry.key(), false);
    }

    /** Owner-context loading call. A driver may block here; no runtime polling may call this. */
    void save(int program, Entry entry) {
        try {
            GLProgramBinary binary = gl.exportProgramBinary(program, ShaderArtifactCache.MAX_PAYLOAD_BYTES - HEADER_BYTES);
            if (binary == null || binary.bytes() == null || binary.bytes().length == 0
                    || binary.bytes().length > ShaderArtifactCache.MAX_PAYLOAD_BYTES - HEADER_BYTES) return;
            byte[] payload = ByteBuffer.allocate(HEADER_BYTES + binary.bytes().length).order(ByteOrder.LITTLE_ENDIAN)
                    .putInt(SCHEMA).putInt(binary.format()).put(binary.bytes()).array();
            artifacts.writeAsync(entry.key(), payload);
        } catch (RuntimeException unavailable) {
            // Optional persistence cannot invalidate a successfully linked program.
        }
    }
}
