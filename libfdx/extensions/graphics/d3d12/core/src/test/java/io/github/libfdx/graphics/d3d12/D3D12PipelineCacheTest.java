package io.github.libfdx.graphics.d3d12;

import io.github.libfdx.graphics.BlendState;
import io.github.libfdx.graphics.ColorTargetState;
import io.github.libfdx.graphics.CompareFunction;
import io.github.libfdx.graphics.CullMode;
import io.github.libfdx.graphics.DepthStencilState;
import io.github.libfdx.graphics.FrontFace;
import io.github.libfdx.graphics.MultisampleState;
import io.github.libfdx.graphics.PrimitiveState;
import io.github.libfdx.graphics.PrimitiveTopology;
import io.github.libfdx.graphics.RenderPipelineDescriptor;
import io.github.libfdx.graphics.TextureFormat;
import io.github.libfdx.graphics.VertexAttribute;
import io.github.libfdx.graphics.VertexFormat;
import io.github.libfdx.graphics.VertexLayout;
import io.github.libfdx.graphics.internal.ShaderRenderBindings;
import io.github.libfdx.graphics.shader.runtime.ShaderCacheKey;
import io.github.libfdx.graphics.shader.runtime.ShaderCacheLayer;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@EnabledOnOs(OS.WINDOWS)
class D3D12PipelineCacheTest {
    private static final ShaderCacheKey VERTEX = ShaderCacheKey.of(ShaderCacheLayer.DXIL, "vertex");
    private static final ShaderCacheKey FRAGMENT = ShaderCacheKey.of(ShaderCacheLayer.DXIL, "fragment");

    @Test void changedNativeStateAndBindingsCannotReuseAnotherPipeline() {
        List<Consumer<RenderPipelineDescriptor>> changes = List.of(
                state -> state.colorFormat(TextureFormat.BGRA8_UNORM),
                state -> state.primitiveTopology(PrimitiveTopology.LINE_LIST),
                state -> state.primitiveState(PrimitiveState.of(PrimitiveTopology.TRIANGLE_LIST, FrontFace.CLOCKWISE, CullMode.BACK)),
                state -> state.colorTargets(ColorTargetState.of(TextureFormat.RGBA8_UNORM, BlendState.alphaBlend(), 7)),
                state -> state.depthStencilState(DepthStencilState.builder(TextureFormat.DEPTH32_FLOAT)
                        .depthWriteEnabled(true).depthCompare(CompareFunction.LESS).depthBias(3, 2, 1).build()),
                state -> state.multisampleState(MultisampleState.of(4, 3, true)),
                state -> state.sampledTextureCount(1),
                state -> state.vertexLayouts(VertexLayout.of(16, VertexAttribute.of(1, VertexFormat.FLOAT32X3, 4))));
        ShaderCacheKey initial = key(base());
        for (var change : changes) {
            RenderPipelineDescriptor changed = base(); change.accept(changed);
            assertNotEquals(initial, key(changed));
        }
    }

    @Test void shaderCompilerAdapterAndRuntimeIdentitiesAreIndependentOfLabels() {
        RenderPipelineDescriptor state = base();
        var inputs = D3D12Device.VertexInputs.from(state.vertexLayouts());
        var bindings = D3D12Device.PipelineBindings.from(ShaderRenderBindings.from(state));
        ShaderCacheKey initial = key(state);
        assertEquals(initial, key(base().label("another application label")));
        assertNotEquals(initial, D3D12PipelineCache.key("changed-driver", "runtime", VERTEX, FRAGMENT, state, inputs, bindings));
        assertNotEquals(initial, D3D12PipelineCache.key("adapter", "updated-runtime", VERTEX, FRAGMENT, state, inputs, bindings));
        assertNotEquals(initial, D3D12PipelineCache.key("adapter", "runtime",
                ShaderCacheKey.of(ShaderCacheLayer.DXIL, "changed-source-or-options"), FRAGMENT, state, inputs, bindings));
    }

    private static RenderPipelineDescriptor base() {
        return new RenderPipelineDescriptor().colorFormat(TextureFormat.RGBA8_UNORM)
                .vertexLayouts(VertexLayout.of(12, VertexAttribute.of(0, VertexFormat.FLOAT32X3, 0)));
    }
    private static ShaderCacheKey key(RenderPipelineDescriptor state) {
        return D3D12PipelineCache.key("adapter", "runtime", VERTEX, FRAGMENT, state,
                D3D12Device.VertexInputs.from(state.vertexLayouts()),
                D3D12Device.PipelineBindings.from(ShaderRenderBindings.from(state)));
    }
}
