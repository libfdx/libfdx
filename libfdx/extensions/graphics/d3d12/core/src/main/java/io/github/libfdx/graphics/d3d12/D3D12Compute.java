package io.github.libfdx.graphics.d3d12;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.ComputePass;
import io.github.libfdx.graphics.ComputePipeline;
import io.github.libfdx.graphics.ComputePipelineDescriptor;
import io.github.libfdx.graphics.shader.ShaderLanguage;
import io.github.libfdx.graphics.shader.ShaderModule;
import io.github.libfdx.graphics.shader.ShaderModuleDescriptor;
import io.github.libfdx.graphics.shader.reflection.ShaderReflection;
import io.github.libfdx.graphics.shader.reflection.ShaderResourceAccess;
import io.github.libfdx.graphics.shader.reflection.ShaderResourceLayout;
import io.github.libfdx.graphics.shader.runtime.ShaderResourceSet;
import io.github.libfdx.graphics.shader.runtime.ShaderResourceValueKind;
import io.github.libfdx.graphics.shader.target.ShaderArtifactStage;
import java.util.Arrays;

final class D3D12Compute {
    private D3D12Compute() { }

    static final class Module implements ShaderModule {
        final D3D12Context context;
        final ShaderModuleDescriptor descriptor;
        private boolean disposed;
        Module(D3D12Context context, ShaderModuleDescriptor descriptor) { this.context = context; this.descriptor = descriptor; }
        @Override public ShaderLanguage language() { return ShaderLanguage.HLSL; }
        @Override public ShaderReflection reflection() { return descriptor.reflection(); }
        @Override public ProviderId providerId() { return context.providerId(); }
        @Override @SuppressWarnings("unchecked") public <T> T as() { return (T) this; }
        @Override public boolean isDisposed() { return disposed; }
        @Override public void dispose() { disposed = true; }
    }

    static final class Pipeline extends D3D12Resource implements ComputePipeline {
        final D3D12Context context;
        final ShaderResourceLayout layout;
        final long handle;
        Pipeline(D3D12Context context, Module module, ComputePipelineDescriptor descriptor) {
            super(context, createNative(context, module, descriptor));
            this.context = context;
            layout = descriptor.resourceLayout();
            handle = nativeHandle();
        }
        private static long createNative(D3D12Context context, Module module, ComputePipelineDescriptor descriptor) {
            var layout = descriptor.resourceLayout();
            var artifact = module.descriptor.targetArtifact();
            int count = layout.bindingCount();
            int[] types = new int[count], bindings = new int[count], groups = new int[count];
            for (int i = 0; i < count; i++) {
                var binding = layout.binding(i);
                var remap = artifact.translatedInterface().findBinding(ShaderArtifactStage.COMPUTE,
                        descriptor.entryPoint(), binding.group(), binding.binding());
                if (remap == null || remap.targetCount() != 1) throw new FdxException("Invalid D3D12 compute binding remap");
                bindings[i] = remap.target(0).binding(); groups[i] = remap.target(0).group();
                types[i] = switch (binding.resourceKind()) {
                    case STORAGE_BUFFER -> binding.access() == ShaderResourceAccess.READ ? 1 : 0;
                    case UNIFORM_BUFFER -> 2;
                    case STORAGE_TEXTURE -> 3;
                    default -> throw new FdxException("Unsupported D3D12 compute binding: " + binding.resourceKind());
                };
            }
            String entry = null;
            for (var remap : artifact.translatedInterface().entryPoints()) {
                if (remap.sourceName().equals(descriptor.entryPoint())) entry = remap.targetName();
            }
            var stage = entry == null ? null : artifact.find(ShaderArtifactStage.COMPUTE, entry);
            if (stage == null) throw new FdxException("Missing D3D12 compute entry point " + descriptor.entryPoint());
            return D3D12Native.createComputePipeline(context.nativeHandle(), stage.text(), entry, types, bindings, groups);
        }
        @Override public ProviderId providerId() { return context.providerId(); }
        @Override @SuppressWarnings("unchecked") public <T> T as() { return (T) this; }
        @Override public boolean isDisposed() { return resourceDisposed(); }
        @Override public void dispose() { disposeResource(); }
        @Override void destroyNative(long contextHandle, long resourceHandle) { D3D12Native.destroyComputePipeline(contextHandle, resourceHandle); }
    }

    static final class Pass implements ComputePass {
        final D3D12Context context;
        final D3D12CommandEncoder owner;
        final ShaderResourceSet[] sets = new ShaderResourceSet[2];
        final long[] resources = new long[64], offsets = new long[64];
        Pipeline pipeline;
        boolean ended = true;
        Pass(D3D12Context context, D3D12CommandEncoder owner) { this.context = context; this.owner = owner; }
        void begin() { ended = false; pipeline = null; Arrays.fill(sets, null); }
        void requireOpen() {
            context.requireFrame("record compute commands");
            if (ended) throw new FdxException("D3D12 compute pass has ended");
        }
        @Override public void setPipeline(ComputePipeline value) {
            requireOpen();
            if (!(value instanceof Pipeline candidate) || candidate.context != context || candidate.isDisposed()) throw new FdxException("Invalid D3D12 compute pipeline or device");
            pipeline = candidate; Arrays.fill(sets, null);
        }
        @Override public void setResourceSet(ShaderResourceSet value) {
            requireOpen();
            if (pipeline == null || value == null || value.group() >= sets.length
                    || !pipeline.layout.physicalHash().equals(value.layout().physicalHash())) throw new FdxException("Mismatched D3D12 compute resources");
            sets[value.group()] = value;
        }
        @Override public void dispatch(int x, int y, int z) {
            requireOpen(); validateDispatch(x, y, z, context.device().capabilities().limits());
            if (pipeline == null || pipeline.isDisposed()) throw new FdxException("No live D3D12 compute pipeline");
            for (int i = 0; i < pipeline.layout.bindingCount(); i++) {
                var binding = pipeline.layout.binding(i);
                var set = sets[binding.group()];
                if (set == null) throw new FdxException("Missing D3D12 compute resource group");
                var value = set.find(binding.binding());
                if (value.kind() == ShaderResourceValueKind.BUFFER) {
                    resources[i] = context.requireBuffer(value.buffer(), "Compute buffer").nativeHandle(); offsets[i] = value.offset();
                } else if (value.kind() == ShaderResourceValueKind.TEXTURE) {
                    resources[i] = context.requireTexture(value.texture(), "Compute texture").nativeHandle(); offsets[i] = 0;
                } else throw new FdxException("D3D12 compute requires explicit buffer or texture resources");
            }
            D3D12Native.dispatchCompute(context.nativeHandle(), pipeline.handle, resources, offsets, x, y, z);
        }
        @Override public void end() {
            if (ended) return;
            ended = true; pipeline = null; Arrays.fill(sets, null); owner.ended();
        }
        @Override public ProviderId providerId() { return context.providerId(); }
        @Override @SuppressWarnings("unchecked") public <T> T as() { return (T) this; }
    }
}
