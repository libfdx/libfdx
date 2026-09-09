package io.github.libfdx.graphics.d3d12;

import io.github.libfdx.graphics.BlendFactor;
import io.github.libfdx.graphics.BlendOperation;
import io.github.libfdx.graphics.BlendState;
import io.github.libfdx.graphics.ColorTargetState;
import io.github.libfdx.graphics.CullMode;
import io.github.libfdx.graphics.DepthStencilState;
import io.github.libfdx.graphics.FrontFace;
import io.github.libfdx.graphics.RenderPipelineDescriptor;
import java.lang.foreign.MemorySegment;

/** Writes immutable common pipeline state into a D3D12 graphics PSO descriptor. */
final class D3D12PipelineState {
    private D3D12PipelineState() { }

    static void write(MemorySegment target, RenderPipelineDescriptor state) {
        ColorTargetState[] colors = state.colorTargets();
        long offset = D3D12Ffm.OFF_PIPELINE_BLEND;
        target.set(D3D12Ffm.INT, offset, state.multisampleState().alphaToCoverageEnabled() ? 1 : 0);
        target.set(D3D12Ffm.INT, offset + 4, colors.length > 1 ? 1 : 0);
        for (int i = 0; i < colors.length; i++) {
            ColorTargetState color = colors[i];
            BlendState blend = color.blend();
            offset = D3D12Ffm.OFF_PIPELINE_BLEND + 8L + 40L * i;
            target.set(D3D12Ffm.INT, offset, blend != null ? 1 : 0);
            target.set(D3D12Ffm.BYTE, offset + 36, (byte) color.writeMask());
            target.set(D3D12Ffm.INT, offset + 8, blend == null ? 2 : factor(blend.color().sourceFactor(), false));
            target.set(D3D12Ffm.INT, offset + 12, blend == null ? 1 : factor(blend.color().destinationFactor(), false));
            target.set(D3D12Ffm.INT, offset + 16, blend == null ? 1 : operation(blend.color().operation()));
            target.set(D3D12Ffm.INT, offset + 20, blend == null ? 2 : factor(blend.alpha().sourceFactor(), true));
            target.set(D3D12Ffm.INT, offset + 24, blend == null ? 1 : factor(blend.alpha().destinationFactor(), true));
            target.set(D3D12Ffm.INT, offset + 28, blend == null ? 1 : operation(blend.alpha().operation()));
            target.set(D3D12Ffm.INT, offset + 32, D3D12Ffm.D3D12_LOGIC_OP_NOOP);
        }
        target.set(D3D12Ffm.INT, D3D12Ffm.OFF_PIPELINE_SAMPLE_MASK, state.multisampleState().mask());
        offset = D3D12Ffm.OFF_PIPELINE_RASTERIZER;
        CullMode cull = state.primitiveState().cullMode();
        target.set(D3D12Ffm.INT, offset + 4, cull == CullMode.NONE ? 1 : cull == CullMode.FRONT ? 2 : 3);
        target.set(D3D12Ffm.INT, offset + 8,
                state.primitiveState().frontFace() == FrontFace.COUNTER_CLOCKWISE ? 1 : 0);
        DepthStencilState depth = state.depthStencilState();
        if (depth != null) {
            target.set(D3D12Ffm.INT, offset + 12, depth.depthBias());
            target.set(D3D12Ffm.FLOAT, offset + 16, depth.depthBiasClamp());
            target.set(D3D12Ffm.FLOAT, offset + 20, depth.depthBiasSlopeScale());
            target.set(D3D12Ffm.INT, D3D12Ffm.OFF_PIPELINE_DEPTH_STENCIL + 8L,
                    depth.depthCompare().ordinal() + 1);
        }
        // The provider exposes D32 only; stencil states cannot occur on an accepted target.
    }

    static int operation(BlendOperation operation) {
        return switch (operation) {
            case ADD -> 1;
            case SUBTRACT -> 2;
            case REVERSE_SUBTRACT -> 3;
            case MIN -> 4;
            case MAX -> 5;
        };
    }

    static int factor(BlendFactor factor, boolean alpha) {
        return switch (factor) {
            case ZERO -> 1;
            case ONE -> 2;
            case SOURCE -> alpha ? 5 : 3;
            case ONE_MINUS_SOURCE -> alpha ? 6 : 4;
            case SOURCE_ALPHA -> 5;
            case ONE_MINUS_SOURCE_ALPHA -> 6;
            case DESTINATION -> alpha ? 7 : 9;
            case ONE_MINUS_DESTINATION -> alpha ? 8 : 10;
            case DESTINATION_ALPHA -> 7;
            case ONE_MINUS_DESTINATION_ALPHA -> 8;
            case SOURCE_ALPHA_SATURATED -> alpha ? 2 : 11;
            case CONSTANT -> 14;
            case ONE_MINUS_CONSTANT -> 15;
        };
    }
}
