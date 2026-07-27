package io.github.libfdx.runtime.core.shader;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Decoded, versioned target-interface metadata emitted by the native Tint bridge.
 *
 * <p>The binary transport uses the {@code FDXT} magic. It is separate from
 * canonical {@code FDXI} reflection because target writers can rename entry
 * points, renumber resources, combine sampler/texture pairs, or expand one
 * canonical resource to several target slots.</p>
 *
 * @author xpenatan
 */
public final class RuntimeShaderTargetInterface {
    private static final int VERSION = 1;

    private final RuntimeShaderEntryPointRemap[] entryPoints;
    private final RuntimeShaderBindingRemap[] bindings;

    private RuntimeShaderTargetInterface(RuntimeShaderEntryPointRemap[] entryPoints,
            RuntimeShaderBindingRemap[] bindings) {
        this.entryPoints = entryPoints.clone();
        this.bindings = bindings.clone();
    }

    /**
     * Creates target-interface metadata for a runtime compiler implementation.
     *
     * @param entryPoints the translated entry points
     * @param bindings the translated bindings
     * @return the target interface
     */
    public static RuntimeShaderTargetInterface of(RuntimeShaderEntryPointRemap[] entryPoints,
            RuntimeShaderBindingRemap[] bindings) {
        if (entryPoints == null || entryPoints.length == 0 || bindings == null) {
            throw new IllegalArgumentException("Runtime shader target interface is invalid");
        }
        for (RuntimeShaderEntryPointRemap entryPoint : entryPoints) {
            if (entryPoint == null) {
                throw new IllegalArgumentException("Runtime shader target entry point cannot be null");
            }
        }
        for (RuntimeShaderBindingRemap binding : bindings) {
            if (binding == null) {
                throw new IllegalArgumentException("Runtime shader target binding cannot be null");
            }
        }
        return new RuntimeShaderTargetInterface(entryPoints, bindings);
    }

    /**
     * Decodes an {@code FDXT} v1 payload.
     *
     * @param bytes the payload
     * @return the decoded target interface
     */
    public static RuntimeShaderTargetInterface fromBytes(byte[] bytes) {
        if (bytes == null || bytes.length < 12) {
            throw new IllegalArgumentException("FDXT payload is truncated");
        }
        Reader reader = new Reader(bytes);
        reader.requireMagic();
        int version = reader.u32("version");
        if (version != VERSION) {
            throw new IllegalArgumentException("Unsupported FDXT version " + version);
        }
        int entryCount = reader.count("entry-point count");
        RuntimeShaderEntryPointRemap[] entries = new RuntimeShaderEntryPointRemap[entryCount];
        for (int i = 0; i < entryCount; i++) {
            RuntimeShaderCompileStage stage = stage(reader.u32("entry-point stage"));
            String sourceName = reader.string("entry-point source name");
            String targetName = reader.string("entry-point target name");
            if (sourceName.length() == 0 || targetName.length() == 0) {
                throw new IllegalArgumentException("FDXT entry-point names cannot be empty");
            }
            entries[i] = RuntimeShaderEntryPointRemap.of(stage, sourceName, targetName);
        }
        int bindingCount = reader.count("binding-remap count");
        RuntimeShaderBindingRemap[] bindings = new RuntimeShaderBindingRemap[bindingCount];
        for (int i = 0; i < bindingCount; i++) {
            int sourceGroup = reader.u32("source group");
            int sourceBinding = reader.u32("source binding");
            RuntimeShaderBindingRemapKind kind = kind(reader.u32("binding-remap kind"));
            int targetCount = reader.count("target binding count");
            if (targetCount == 0) {
                throw new IllegalArgumentException("FDXT binding remap has no target slots");
            }
            RuntimeShaderTargetBinding[] targets = new RuntimeShaderTargetBinding[targetCount];
            for (int targetIndex = 0; targetIndex < targetCount; targetIndex++) {
                String namespace = reader.string("target namespace");
                int targetGroup = reader.u32("target group");
                int targetBinding = reader.u32("target binding");
                String role = reader.string("target role");
                String name = reader.string("target name");
                if (namespace.length() == 0 || role.length() == 0) {
                    throw new IllegalArgumentException("FDXT target namespace and role cannot be empty");
                }
                targets[targetIndex] = RuntimeShaderTargetBinding.of(namespace,
                        targetGroup, targetBinding, role, name);
            }
            bindings[i] = RuntimeShaderBindingRemap.of(sourceGroup, sourceBinding, kind, targets);
        }
        reader.requireEnd();
        return RuntimeShaderTargetInterface.of(entries, bindings);
    }

    public RuntimeShaderEntryPointRemap[] entryPoints() {
        return entryPoints.clone();
    }

    public RuntimeShaderBindingRemap[] bindings() {
        return bindings.clone();
    }

    private static RuntimeShaderCompileStage stage(int tag) {
        return switch (tag) {
            case 1 -> RuntimeShaderCompileStage.VERTEX;
            case 2 -> RuntimeShaderCompileStage.FRAGMENT;
            case 3 -> RuntimeShaderCompileStage.COMPUTE;
            default -> throw new IllegalArgumentException("Unknown FDXT entry-point stage " + tag);
        };
    }

    private static RuntimeShaderBindingRemapKind kind(int tag) {
        return switch (tag) {
            case 0 -> RuntimeShaderBindingRemapKind.DIRECT;
            case 1 -> RuntimeShaderBindingRemapKind.COMBINED_TEXTURE;
            case 2 -> RuntimeShaderBindingRemapKind.COMBINED_SAMPLER;
            default -> throw new IllegalArgumentException("Unknown FDXT binding-remap kind " + tag);
        };
    }

    private static final class Reader {
        private final ByteBuffer buffer;

        private Reader(byte[] bytes) {
            buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        }

        private void requireMagic() {
            require(4, "magic");
            if (buffer.get() != 'F' || buffer.get() != 'D'
                    || buffer.get() != 'X' || buffer.get() != 'T') {
                throw new IllegalArgumentException("Invalid FDXT magic");
            }
        }

        private int count(String label) {
            return u32(label);
        }

        private int u32(String label) {
            require(4, label);
            int value = buffer.getInt();
            if (value < 0) {
                throw new IllegalArgumentException("FDXT " + label + " exceeds the Java limit");
            }
            return value;
        }

        private String string(String label) {
            int length = count(label + " length");
            require(length, label);
            byte[] bytes = new byte[length];
            buffer.get(bytes);
            return new String(bytes, StandardCharsets.UTF_8);
        }

        private void require(int size, String label) {
            if (size < 0 || size > buffer.remaining()) {
                throw new IllegalArgumentException("FDXT " + label + " is truncated");
            }
        }

        private void requireEnd() {
            if (buffer.hasRemaining()) {
                throw new IllegalArgumentException("FDXT payload contains trailing data");
            }
        }
    }
}
