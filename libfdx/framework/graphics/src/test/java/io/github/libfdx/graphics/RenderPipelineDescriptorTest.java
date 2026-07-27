package io.github.libfdx.graphics;

import io.github.libfdx.graphics.shader.ShaderLanguage;
import io.github.libfdx.graphics.shader.ShaderModule;
import io.github.libfdx.graphics.shader.ShaderProfile;
import io.github.libfdx.graphics.shader.ShaderStage;
import io.github.libfdx.graphics.shader.reflection.ShaderAttribute;
import io.github.libfdx.graphics.shader.reflection.ShaderBinding;
import io.github.libfdx.graphics.shader.reflection.ShaderBindingType;
import io.github.libfdx.graphics.shader.reflection.ShaderEntryPoint;
import io.github.libfdx.graphics.shader.reflection.ShaderReflection;
import io.github.libfdx.graphics.shader.reflection.ShaderReflectionDecoderTest;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.ProviderId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RenderPipelineDescriptorTest {
    @Test
    void inheritsModuleReflectionAndDerivesSampledTextureCount() {
        ShaderReflection reflection = ShaderReflection.fromRuntime(ShaderReflectionDecoderTest.runtimeFixture());
        RenderPipelineDescriptor descriptor = RenderPipelineDescriptor.shader(
                        new FakeShaderModule(reflection), TextureFormat.RGBA8_UNORM)
                .vertexEntryPoint("vs_main")
                .fragmentEntryPoint("fs_main");

        assertFalse(descriptor.hasExplicitShaderReflection());
        assertFalse(descriptor.hasExplicitSampledTextureCount());
        assertSame(reflection, descriptor.shaderReflection());
        assertEquals(1, descriptor.sampledTextureCount());
    }

    @Test
    void completeOverrideMustPhysicallyMatchModuleAndExplicitCountMustMatch() {
        ShaderReflection reflection = ShaderReflection.fromRuntime(ShaderReflectionDecoderTest.runtimeFixture());
        ShaderReflection stale = ShaderReflection.complete(ShaderProfile.PORTABLE_WEBGPU,
                new ShaderEntryPoint[] {
                        ShaderEntryPoint.builder("vs_main", ShaderStage.VERTEX).build(),
                        ShaderEntryPoint.builder("fs_main", ShaderStage.FRAGMENT).build()
                }, new ShaderBinding[0], new String[0]);
        RenderPipelineDescriptor descriptor = RenderPipelineDescriptor.shader(
                        new FakeShaderModule(reflection), TextureFormat.RGBA8_UNORM)
                .vertexEntryPoint("vs_main")
                .fragmentEntryPoint("fs_main")
                .shaderReflection(stale);

        assertTrue(descriptor.hasExplicitShaderReflection());
        assertThrows(FdxException.class, descriptor::shaderReflection);

        RenderPipelineDescriptor wrongCount = RenderPipelineDescriptor.shader(
                        new FakeShaderModule(reflection), TextureFormat.RGBA8_UNORM)
                .vertexEntryPoint("vs_main")
                .fragmentEntryPoint("fs_main")
                .sampledTextureCount(2);
        assertThrows(FdxException.class, wrongCount::sampledTextureCount);
    }

    @Test
    void incompleteOverrideIsValidatedAsSubsetAndExplicitEmptyRemainsEmpty() {
        ShaderReflection reflection = ShaderReflection.fromRuntime(ShaderReflectionDecoderTest.runtimeFixture());
        ShaderReflection coarse = ShaderReflection.of(new ShaderBinding[] {
                ShaderBinding.of(1, 0, "color_texture", ShaderBindingType.TEXTURE)
        }, new ShaderAttribute[0]);
        RenderPipelineDescriptor subset = RenderPipelineDescriptor.shader(
                        new FakeShaderModule(reflection), TextureFormat.RGBA8_UNORM)
                .vertexEntryPoint("vs_main")
                .fragmentEntryPoint("fs_main")
                .shaderReflection(coarse);
        assertSame(reflection, subset.shaderReflection());

        RenderPipelineDescriptor explicitlyEmpty = RenderPipelineDescriptor.shader(
                        new FakeShaderModule(reflection), TextureFormat.RGBA8_UNORM)
                .vertexEntryPoint("vs_main")
                .fragmentEntryPoint("fs_main")
                .shaderReflection(null);
        assertSame(ShaderReflection.empty(), explicitlyEmpty.shaderReflection());
        assertEquals(0, explicitlyEmpty.sampledTextureCount());
    }

    private static final class FakeShaderModule implements ShaderModule {
        private final ShaderReflection reflection;
        private boolean disposed;

        private FakeShaderModule(ShaderReflection reflection) {
            this.reflection = reflection;
        }

        @Override
        public ShaderLanguage language() {
            return ShaderLanguage.WGSL;
        }

        @Override
        public ShaderReflection reflection() {
            return reflection;
        }

        @Override
        public ProviderId providerId() {
            return ProviderId.of("test");
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T as() {
            return (T) this;
        }

        @Override
        public void dispose() {
            disposed = true;
        }

        @Override
        public boolean isDisposed() {
            return disposed;
        }
    }
}
