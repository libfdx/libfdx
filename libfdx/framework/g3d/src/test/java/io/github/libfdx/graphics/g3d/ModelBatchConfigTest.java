package io.github.libfdx.graphics.g3d;

import io.github.libfdx.graphics.shader.runtime.ShaderProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

final class ModelBatchConfigTest {
    @Test
    void commonAndDomainSpecificProviderSettersAreMutuallyExclusive() {
        ShaderProvider common = new ShaderProvider() {
        };
        ShaderProvider3D domain = new ShaderProvider3D() {
            @Override
            public Shader3D shader(Renderable3D renderable,
                    RenderContext3D context) {
                return null;
            }
        };
        ModelBatchConfig config = new ModelBatchConfig()
                .shaderProvider(domain);
        assertSame(domain, config.shaderProvider());
        assertNull(config.commonShaderProvider());

        config.shaderProvider(common);
        assertNull(config.shaderProvider());
        assertSame(common, config.commonShaderProvider());

        config.shaderProvider(domain);
        assertSame(domain, config.shaderProvider());
        assertNull(config.commonShaderProvider());
    }

    @Test
    void nullCallRetainsTheSourceCompatibleNarrowOverload() {
        ModelBatchConfig config = new ModelBatchConfig()
                .shaderProvider((ShaderProvider) new ShaderProvider() {
                });
        config.shaderProvider(null);
        assertNull(config.shaderProvider());
        assertNull(config.commonShaderProvider());
    }
}
