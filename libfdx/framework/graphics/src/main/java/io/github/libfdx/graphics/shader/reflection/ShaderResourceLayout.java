package io.github.libfdx.graphics.shader.reflection;

import io.github.libfdx.graphics.GraphicsCapabilities;
import io.github.libfdx.graphics.GraphicsFeature;
import io.github.libfdx.graphics.GraphicsLimits;
import io.github.libfdx.graphics.shader.ShaderStage;
import io.github.libfdx.graphics.shader.target.ShaderArtifactStage;
import io.github.libfdx.graphics.shader.target.ShaderEntryPointSelection;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.internal.PortableSha256;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Immutable resource layout selected from a complete shader interface.
 */
public final class ShaderResourceLayout {
    private static final AtomicLong NEXT_IDENTITY = new AtomicLong(1);

    private final long identity;
    private final ShaderReflection reflection;
    private final ShaderBinding[] bindings;
    private final ShaderEntryPointSelection[] entryPoints;
    private final String physicalHash;

    private ShaderResourceLayout(ShaderReflection reflection,
            ShaderEntryPointSelection[] entryPoints, boolean allBindings) {
        if (reflection == null || !reflection.complete()) {
            throw new FdxException("A shader resource layout requires complete reflection");
        }
        this.reflection = reflection;
        this.entryPoints = entryPoints != null
                ? entryPoints.clone() : new ShaderEntryPointSelection[0];
        requireEntryPoints();
        bindings = selectBindings(reflection, this.entryPoints, allBindings);
        identity = nextIdentity();
        physicalHash = computeHash();
    }

    /**
     * Creates a layout containing every reflected resource.
     *
     * @param reflection complete shader reflection
     * @return the resource layout
     */
    public static ShaderResourceLayout all(ShaderReflection reflection) {
        return new ShaderResourceLayout(reflection, null, true);
    }

    /**
     * Creates a layout for one linked vertex/fragment program.
     *
     * @param reflection complete shader reflection
     * @param vertexEntryPoint vertex entry point
     * @param fragmentEntryPoint fragment entry point
     * @return the resource layout
     */
    public static ShaderResourceLayout render(ShaderReflection reflection,
            String vertexEntryPoint, String fragmentEntryPoint) {
        return new ShaderResourceLayout(reflection, new ShaderEntryPointSelection[] {
                ShaderEntryPointSelection.of(ShaderArtifactStage.VERTEX, vertexEntryPoint),
                ShaderEntryPointSelection.of(ShaderArtifactStage.FRAGMENT, fragmentEntryPoint)
        }, false);
    }

    /**
     * Creates a layout for one compute entry point.
     *
     * @param reflection complete shader reflection
     * @param computeEntryPoint compute entry point
     * @return the resource layout
     */
    public static ShaderResourceLayout compute(ShaderReflection reflection,
            String computeEntryPoint) {
        return new ShaderResourceLayout(reflection, new ShaderEntryPointSelection[] {
                ShaderEntryPointSelection.of(ShaderArtifactStage.COMPUTE, computeEntryPoint)
        }, false);
    }

    public long identity() {
        return identity;
    }

    public ShaderReflection reflection() {
        return reflection;
    }

    public ShaderBinding[] bindings() {
        return bindings.clone();
    }

    public int bindingCount() {
        return bindings.length;
    }

    public ShaderBinding binding(int index) {
        return bindings[index];
    }

    public ShaderEntryPointSelection[] entryPoints() {
        return entryPoints.clone();
    }

    public String physicalHash() {
        return physicalHash;
    }

    public ShaderBinding find(int group, int binding) {
        for (ShaderBinding candidate : bindings) {
            if (candidate.group() == group && candidate.binding() == binding) {
                return candidate;
            }
        }
        return null;
    }

    public ShaderBinding require(int group, int binding) {
        ShaderBinding result = find(group, binding);
        if (result == null) {
            throw new FdxException("Shader resource layout has no binding "
                    + group + ':' + binding);
        }
        return result;
    }

    public int groupBindingCount(int group) {
        int count = 0;
        for (ShaderBinding binding : bindings) {
            if (binding.group() == group) {
                count++;
            }
        }
        return count;
    }

    /**
     * Returns the number of bindings with the requested resource kind.
     *
     * @param kind resource kind
     * @return binding count
     */
    public int bindingCount(ShaderResourceKind kind) {
        if (kind == null) {
            return 0;
        }
        int count = 0;
        for (ShaderBinding binding : bindings) {
            if (binding.resourceKind() == kind) {
                count++;
            }
        }
        return count;
    }

    /**
     * Returns one binding of a resource kind in canonical group/binding order.
     *
     * @param kind resource kind
     * @param index zero-based index among bindings of that kind
     * @return binding
     */
    public ShaderBinding binding(ShaderResourceKind kind, int index) {
        if (kind == null || index < 0) {
            throw new FdxException("Shader resource kind/index is invalid");
        }
        int cursor = 0;
        for (ShaderBinding binding : bindings) {
            if (binding.resourceKind() == kind && cursor++ == index) {
                return binding;
            }
        }
        throw new FdxException("Shader resource layout has no " + kind
                + " binding at index " + index);
    }

    /**
     * Returns the canonical slot among bindings of one kind.
     *
     * @param kind resource kind
     * @param group bind group
     * @param binding binding index
     * @return zero-based slot, or {@code -1}
     */
    public int bindingIndex(ShaderResourceKind kind, int group, int binding) {
        if (kind == null) {
            return -1;
        }
        int cursor = 0;
        for (ShaderBinding candidate : bindings) {
            if (candidate.resourceKind() == kind) {
                if (candidate.group() == group && candidate.binding() == binding) {
                    return cursor;
                }
                cursor++;
            }
        }
        return -1;
    }

    /**
     * Validates this layout against provider features and limits.
     *
     * @param capabilities device capabilities
     */
    public void validate(GraphicsCapabilities capabilities) {
        if (capabilities == null) {
            throw new FdxException("Graphics capabilities cannot be null");
        }
        capabilities.require(reflection.profile());
        GraphicsLimits limits = capabilities.limits();
        int uniformCount = 0;
        int storageBufferCount = 0;
        int sampledTextureCount = 0;
        int samplerCount = 0;
        int storageTextureCount = 0;
        for (ShaderBinding binding : bindings) {
            if (binding.group() >= limits.maxBindGroups()
                    || binding.binding() >= limits.maxBindingsPerGroup()) {
                throw new FdxException("Shader binding exceeds provider group/binding limits: "
                        + binding.group() + ':' + binding.binding());
            }
            switch (binding.resourceKind()) {
                case UNIFORM_BUFFER -> {
                    uniformCount++;
                    if (binding.minimumBindingSize() > limits.maxUniformBufferBindingSize()) {
                        throw new FdxException("Uniform buffer exceeds provider binding-size limit: "
                                + binding.name());
                    }
                }
                case STORAGE_BUFFER -> {
                    capabilities.require(GraphicsFeature.STORAGE_BUFFERS);
                    storageBufferCount++;
                    if (binding.minimumBindingSize() > limits.maxStorageBufferBindingSize()) {
                        throw new FdxException("Storage buffer exceeds provider binding-size limit: "
                                + binding.name());
                    }
                }
                case SAMPLER -> samplerCount++;
                case SAMPLED_TEXTURE, MULTISAMPLED_TEXTURE, DEPTH_TEXTURE,
                        DEPTH_MULTISAMPLED_TEXTURE, EXTERNAL_TEXTURE -> sampledTextureCount++;
                case STORAGE_TEXTURE -> {
                    capabilities.require(GraphicsFeature.STORAGE_TEXTURES);
                    storageTextureCount++;
                }
                case TEXEL_BUFFER, INPUT_ATTACHMENT ->
                        throw new FdxException("Provider-neutral resource sets do not yet expose "
                                + binding.resourceKind() + ": " + binding.name());
                case UNKNOWN -> throw new FdxException("Complete resource layout contains an unknown binding");
            }
        }
        requireLimit(uniformCount, limits.maxUniformBuffersPerStage(), "uniform buffers");
        requireLimit(storageBufferCount, limits.maxStorageBuffersPerStage(), "storage buffers");
        requireLimit(sampledTextureCount, limits.maxSampledTexturesPerStage(), "sampled textures");
        requireLimit(samplerCount, limits.maxSamplersPerStage(), "samplers");
        requireLimit(storageTextureCount, limits.maxStorageTexturesPerStage(), "storage textures");
    }

    private void requireEntryPoints() {
        for (int i = 0; i < entryPoints.length; i++) {
            ShaderEntryPointSelection selection = entryPoints[i];
            if (selection == null) {
                throw new FdxException("Shader resource layout entry point cannot be null");
            }
            if (i > 0 && entryPoints[i - 1].compareTo(selection) >= 0) {
                throw new FdxException("Shader resource layout entry points must be unique and ordered");
            }
            reflection.requireEntryPoint(stage(selection.stage()), selection.entryPoint());
        }
    }

    private static ShaderBinding[] selectBindings(ShaderReflection reflection,
            ShaderEntryPointSelection[] entries, boolean allBindings) {
        ShaderBinding[] reflected = reflection.bindings();
        if (allBindings) {
            return reflected;
        }
        ShaderBinding[] scratch = new ShaderBinding[reflected.length];
        int count = 0;
        for (ShaderBinding binding : reflected) {
            if (isUsed(reflection, entries, binding.group(), binding.binding())) {
                scratch[count++] = binding;
            }
        }
        ShaderBinding[] result = new ShaderBinding[count];
        System.arraycopy(scratch, 0, result, 0, count);
        return result;
    }

    private static boolean isUsed(ShaderReflection reflection,
            ShaderEntryPointSelection[] entries, int group, int binding) {
        for (ShaderEntryPointSelection selection : entries) {
            ShaderEntryPoint entry = reflection.requireEntryPoint(
                    stage(selection.stage()), selection.entryPoint());
            for (ShaderResourceUse use : entry.resources()) {
                if (use.group() == group && use.binding() == binding) {
                    return true;
                }
            }
        }
        return false;
    }

    private String computeHash() {
        PortableSha256 digest = new PortableSha256()
                .updateSizedUtf8("fdx-shader-resource-layout-v1")
                .updateSizedUtf8(reflection.fullHash())
                .updateInt(entryPoints.length);
        for (ShaderEntryPointSelection entryPoint : entryPoints) {
            digest.updateSizedUtf8(entryPoint.stage().name())
                    .updateSizedUtf8(entryPoint.entryPoint());
        }
        digest.updateInt(bindings.length);
        for (ShaderBinding binding : bindings) {
            digest.updateInt(binding.group()).updateInt(binding.binding())
                    .updateSizedUtf8(binding.stableId())
                    .updateSizedUtf8(binding.resourceKind().name());
        }
        return digest.digestHex();
    }

    private static ShaderStage stage(ShaderArtifactStage stage) {
        return switch (stage) {
            case VERTEX -> ShaderStage.VERTEX;
            case FRAGMENT -> ShaderStage.FRAGMENT;
            case COMPUTE -> ShaderStage.COMPUTE;
            case MODULE -> throw new FdxException("A resource layout entry point cannot be a module");
        };
    }

    private static void requireLimit(int actual, int limit, String label) {
        if (actual > limit) {
            throw new FdxException("Shader resource layout requires " + actual + ' ' + label
                    + ", provider limit is " + limit);
        }
    }

    private static long nextIdentity() {
        long identity = NEXT_IDENTITY.getAndIncrement();
        if (identity <= 0) {
            throw new FdxException("Shader resource layout identity space is exhausted");
        }
        return identity;
    }
}
