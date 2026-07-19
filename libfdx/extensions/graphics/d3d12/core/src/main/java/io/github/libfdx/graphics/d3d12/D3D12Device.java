package io.github.libfdx.graphics.d3d12;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.Buffer;
import io.github.libfdx.graphics.BufferDescriptor;
import io.github.libfdx.graphics.GraphicsDevice;
import io.github.libfdx.graphics.RenderPipeline;
import io.github.libfdx.graphics.RenderPipelineDescriptor;
import io.github.libfdx.graphics.ShaderBinding;
import io.github.libfdx.graphics.ShaderBindingType;
import io.github.libfdx.graphics.ShaderLanguage;
import io.github.libfdx.graphics.ShaderModule;
import io.github.libfdx.graphics.ShaderModuleDescriptor;
import io.github.libfdx.graphics.ShaderModuleDescriptors;
import io.github.libfdx.graphics.ShaderReflection;
import io.github.libfdx.graphics.ShaderTarget;
import io.github.libfdx.graphics.Texture;
import io.github.libfdx.graphics.TextureDescriptor;
import io.github.libfdx.graphics.VertexAttribute;
import io.github.libfdx.graphics.VertexLayout;

import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.util.ArrayList;

final class D3D12Device implements GraphicsDevice {
    private final D3D12Context context;

    D3D12Device(D3D12Context context) {
        this.context = context;
    }

    @Override
    public Buffer createBuffer(BufferDescriptor descriptor) {
        if (descriptor == null) {
            throw new FdxException("BufferDescriptor cannot be null");
        }
        context.requireUsable("create a buffer");
        long handle = D3D12Native.createBuffer(context.nativeHandle(), descriptor.size(), descriptor.usage().ordinal());
        return new D3D12Buffer(context, handle, descriptor.size(), descriptor.usage());
    }

    @Override
    public void writeBuffer(Buffer buffer, ByteBuffer data) {
        if (data == null) {
            throw new FdxException("Buffer data cannot be null");
        }
        D3D12Buffer target = context.requireBuffer(buffer, "Buffer");
        if (data.remaining() > target.size()) {
            throw new FdxException("Buffer write exceeds the destination size");
        }
        int size = data.remaining();
        MemorySegment source = target.uploadSource(data, size);
        D3D12Native.writeBuffer(context.nativeHandle(), target.nativeHandle(), source, size);
    }

    @Override
    public Texture createTexture(TextureDescriptor descriptor) {
        if (descriptor == null) {
            throw new FdxException("TextureDescriptor cannot be null");
        }
        context.requireUsable("create a texture");
        long handle = D3D12Native.createTexture(context.nativeHandle(), descriptor.width(), descriptor.height(),
                descriptor.format().ordinal(), descriptor.usage().ordinal(), descriptor.filter().ordinal(),
                descriptor.wrapS().ordinal(), descriptor.wrapT().ordinal());
        return new D3D12Texture(context, handle, descriptor.width(), descriptor.height(), descriptor.format(),
                descriptor.usage(), descriptor.filter(), descriptor.wrapS(), descriptor.wrapT());
    }

    @Override
    public void writeTexture(Texture texture, ByteBuffer data) {
        if (data == null) {
            throw new FdxException("Texture data cannot be null");
        }
        D3D12Texture target = context.requireTexture(texture, "Texture");
        int requiredBytes = target.width() * target.height() * 4;
        if (data.remaining() < requiredBytes) {
            throw new FdxException("RGBA8 texture write requires " + requiredBytes + " bytes");
        }
        MemorySegment source = target.uploadSource(data, requiredBytes);
        D3D12Native.writeTexture(context.nativeHandle(), target.nativeHandle(), source, requiredBytes);
    }

    @Override
    public ShaderModule createShaderModule(ShaderModuleDescriptor descriptor) {
        if (descriptor == null) {
            throw new FdxException("ShaderModuleDescriptor cannot be null");
        }
        context.requireUsable("create a shader module");
        ShaderModuleDescriptor ready = ShaderModuleDescriptors.requireTarget(descriptor,
                ShaderTarget.DIRECTX_HLSL, "Direct3D 12");
        if (!ready.hasSource(ShaderLanguage.HLSL)) {
            throw new FdxException("Direct3D 12 requires HLSL shader modules");
        }
        long handle = D3D12Native.createShader(context.nativeHandle(), ready.hlslVertexSource(),
                ready.hlslFragmentSource(), ready.vertexEntryPoint(), ready.fragmentEntryPoint(), ready.label());
        return new D3D12Shader(context, handle);
    }

    @Override
    public RenderPipeline createRenderPipeline(RenderPipelineDescriptor descriptor) {
        if (descriptor == null) {
            throw new FdxException("RenderPipelineDescriptor cannot be null");
        }
        context.requireUsable("create a render pipeline");
        D3D12Shader shader = context.requireShader(descriptor.shaderModule(), "Render pipeline shader module");
        PipelineBindings bindings = PipelineBindings.from(descriptor.shaderReflection(),
                descriptor.sampledTextureCount());
        VertexInputs inputs = VertexInputs.from(descriptor.vertexLayouts());
        long handle = D3D12Native.createPipeline(context.nativeHandle(), shader.nativeHandle(),
                descriptor.colorFormat().ordinal(), descriptor.primitiveTopology().ordinal(),
                descriptor.depthTestEnabled(), descriptor.depthWriteEnabled(), descriptor.sampledTextureCount(),
                bindings.uniformGroup, bindings.uniformBinding,
                inputs.layoutStrides, inputs.layoutStepModes,
                inputs.locations, inputs.formats, inputs.offsets, inputs.slots,
                bindings.textureGroups, bindings.textureBindings,
                bindings.samplerGroups, bindings.samplerBindings);
        return new D3D12Pipeline(context, handle, descriptor.sampledTextureCount(), bindings.uniformGroup >= 0);
    }

    @Override
    public ProviderId providerId() {
        return D3D12Provider.ID;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T as() {
        return (T)this;
    }

    private static final class VertexInputs {
        final int[] layoutStrides;
        final int[] layoutStepModes;
        final int[] locations;
        final int[] formats;
        final int[] offsets;
        final int[] slots;

        private VertexInputs(int[] layoutStrides, int[] layoutStepModes, int[] locations,
                int[] formats, int[] offsets, int[] slots) {
            this.layoutStrides = layoutStrides;
            this.layoutStepModes = layoutStepModes;
            this.locations = locations;
            this.formats = formats;
            this.offsets = offsets;
            this.slots = slots;
        }

        static VertexInputs from(VertexLayout[] layouts) {
            int attributeCount = 0;
            for (int i = 0; i < layouts.length; i++) {
                attributeCount += layouts[i].attributeCount();
            }
            int[] strides = new int[layouts.length];
            int[] stepModes = new int[layouts.length];
            int[] locations = new int[attributeCount];
            int[] formats = new int[attributeCount];
            int[] offsets = new int[attributeCount];
            int[] slots = new int[attributeCount];
            int index = 0;
            for (int slot = 0; slot < layouts.length; slot++) {
                VertexLayout layout = layouts[slot];
                strides[slot] = layout.arrayStride();
                stepModes[slot] = layout.stepMode().ordinal();
                for (int attributeIndex = 0; attributeIndex < layout.attributeCount(); attributeIndex++) {
                    VertexAttribute attribute = layout.attribute(attributeIndex);
                    locations[index] = attribute.location();
                    formats[index] = attribute.format().ordinal();
                    offsets[index] = attribute.offset();
                    slots[index] = slot;
                    index++;
                }
            }
            return new VertexInputs(strides, stepModes, locations, formats, offsets, slots);
        }
    }

    private static final class PipelineBindings {
        final int uniformGroup;
        final int uniformBinding;
        final int[] textureGroups;
        final int[] textureBindings;
        final int[] samplerGroups;
        final int[] samplerBindings;

        private PipelineBindings(int uniformGroup, int uniformBinding, int[] textureGroups, int[] textureBindings,
                int[] samplerGroups, int[] samplerBindings) {
            this.uniformGroup = uniformGroup;
            this.uniformBinding = uniformBinding;
            this.textureGroups = textureGroups;
            this.textureBindings = textureBindings;
            this.samplerGroups = samplerGroups;
            this.samplerBindings = samplerBindings;
        }

        static PipelineBindings from(ShaderReflection reflection, int sampledTextureCount) {
            ArrayList<Integer> textureGroups = new ArrayList<Integer>();
            ArrayList<Integer> textureBindings = new ArrayList<Integer>();
            ArrayList<Integer> samplerGroups = new ArrayList<Integer>();
            ArrayList<Integer> samplerBindings = new ArrayList<Integer>();
            int uniformGroup = -1;
            int uniformBinding = -1;
            ShaderBinding[] bindings = reflection != null ? reflection.bindings() : new ShaderBinding[0];
            for (int i = 0; i < bindings.length; i++) {
                ShaderBinding binding = bindings[i];
                if (binding.type() == ShaderBindingType.UNIFORM_BUFFER && "uniforms".equals(binding.name())) {
                    if (uniformGroup >= 0) {
                        throw new FdxException("Direct3D 12 supports one reflected uniforms buffer per pipeline");
                    }
                    uniformGroup = binding.group();
                    uniformBinding = binding.binding();
                } else if (binding.type() == ShaderBindingType.TEXTURE) {
                    textureGroups.add(binding.group());
                    textureBindings.add(binding.binding());
                } else if (binding.type() == ShaderBindingType.SAMPLER) {
                    samplerGroups.add(binding.group());
                    samplerBindings.add(binding.binding());
                } else if (binding.type() == ShaderBindingType.STORAGE_BUFFER
                        || binding.type() == ShaderBindingType.STORAGE_TEXTURE) {
                    throw new FdxException("Direct3D 12 storage bindings are not supported by the graphics contract");
                }
            }
            if (sampledTextureCount > 0 && textureGroups.isEmpty() && samplerGroups.isEmpty()) {
                for (int slot = 0; slot < sampledTextureCount; slot++) {
                    textureGroups.add(0);
                    textureBindings.add(slot * 2);
                    samplerGroups.add(0);
                    samplerBindings.add(slot * 2 + 1);
                }
            }
            if (textureGroups.size() != sampledTextureCount || samplerGroups.size() != sampledTextureCount) {
                throw new FdxException("Direct3D 12 pipeline declares " + sampledTextureCount
                        + " sampled textures but reflection contains " + textureGroups.size()
                        + " textures and " + samplerGroups.size() + " samplers");
            }
            return new PipelineBindings(uniformGroup, uniformBinding,
                    ints(textureGroups), ints(textureBindings), ints(samplerGroups), ints(samplerBindings));
        }

        private static int[] ints(ArrayList<Integer> values) {
            int[] result = new int[values.size()];
            for (int i = 0; i < values.size(); i++) {
                result[i] = values.get(i);
            }
            return result;
        }
    }
}
