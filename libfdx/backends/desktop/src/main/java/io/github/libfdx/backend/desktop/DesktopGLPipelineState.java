package io.github.libfdx.backend.desktop;

import io.github.libfdx.graphics.*;
import org.lwjgl.opengl.*;

/** Desktop GL implementation of the portable immutable pipeline state. */
final class DesktopGLPipelineState {
    private DesktopGLPipelineState() { }

    static void apply(PrimitiveState primitive, ColorTargetState color, DepthStencilState depth,
            MultisampleState samples) {
        enable(GL11.GL_CULL_FACE, primitive.cullMode() != CullMode.NONE);
        GL11.glCullFace(primitive.cullMode() == CullMode.FRONT ? GL11.GL_FRONT : GL11.GL_BACK);
        GL11.glFrontFace(primitive.frontFace() == FrontFace.CLOCKWISE ? GL11.GL_CW : GL11.GL_CCW);
        int mask = color.writeMask();
        GL11.glColorMask((mask & ColorWriteMask.RED) != 0, (mask & ColorWriteMask.GREEN) != 0,
                (mask & ColorWriteMask.BLUE) != 0, (mask & ColorWriteMask.ALPHA) != 0);
        BlendState blend = color.blend();
        enable(GL11.GL_BLEND, blend != null);
        if (blend != null) {
            GL14.glBlendFuncSeparate(factor(blend.color().sourceFactor()), factor(blend.color().destinationFactor()),
                    factor(blend.alpha().sourceFactor()), factor(blend.alpha().destinationFactor()));
            GL20.glBlendEquationSeparate(operation(blend.color().operation()), operation(blend.alpha().operation()));
            GL14.glBlendColor(0, 0, 0, 0);
        }
        enable(GL11.GL_DEPTH_TEST, depth != null);
        GL11.glDepthMask(depth != null && depth.depthWriteEnabled());
        if (depth != null) GL11.glDepthFunc(compare(depth.depthCompare()));
        boolean stencil = depth != null && depth.format() == TextureFormat.DEPTH24_STENCIL8;
        enable(GL11.GL_STENCIL_TEST, stencil);
        if (stencil) {
            GL11.glStencilMask(depth.stencilWriteMask());
            stencil(GL11.GL_FRONT, depth.stencilFront(), depth.stencilReadMask());
            stencil(GL11.GL_BACK, depth.stencilBack(), depth.stencilReadMask());
        }
        boolean bias = depth != null && (depth.depthBias() != 0 || depth.depthBiasSlopeScale() != 0);
        enable(GL11.GL_POLYGON_OFFSET_FILL, bias);
        if (bias) {
            var caps = GL.getCapabilities();
            if (caps.OpenGL46) GL46.glPolygonOffsetClamp(depth.depthBiasSlopeScale(), depth.depthBias(), depth.depthBiasClamp());
            else if (caps.GL_ARB_polygon_offset_clamp) ARBPolygonOffsetClamp.glPolygonOffsetClamp(depth.depthBiasSlopeScale(), depth.depthBias(), depth.depthBiasClamp());
            else EXTPolygonOffsetClamp.glPolygonOffsetClampEXT(depth.depthBiasSlopeScale(), depth.depthBias(), depth.depthBiasClamp());
        }
        enable(GL13.GL_SAMPLE_ALPHA_TO_COVERAGE, samples.alphaToCoverageEnabled());
        enable(GL32.GL_SAMPLE_MASK, samples.mask() != -1);
        GL32.glSampleMaski(0, samples.mask());
    }

    private static void enable(int capability, boolean enabled) {
        if (enabled) GL11.glEnable(capability); else GL11.glDisable(capability);
    }

    private static void stencil(int face, StencilFaceState state, int mask) {
        GL20.glStencilFuncSeparate(face, compare(state.compare()), 0, mask);
        GL20.glStencilOpSeparate(face, stencilOp(state.fail()), stencilOp(state.depthFail()), stencilOp(state.pass()));
    }

    static int compare(CompareFunction value) {
        return switch (value) {
            case NEVER -> GL11.GL_NEVER;
            case LESS -> GL11.GL_LESS;
            case EQUAL -> GL11.GL_EQUAL;
            case LESS_EQUAL -> GL11.GL_LEQUAL;
            case GREATER -> GL11.GL_GREATER;
            case NOT_EQUAL -> GL11.GL_NOTEQUAL;
            case GREATER_EQUAL -> GL11.GL_GEQUAL;
            case ALWAYS -> GL11.GL_ALWAYS;
        };
    }

    private static int stencilOp(StencilOperation value) {
        return switch (value) {
            case KEEP -> GL11.GL_KEEP;
            case ZERO -> GL11.GL_ZERO;
            case REPLACE -> GL11.GL_REPLACE;
            case INVERT -> GL11.GL_INVERT;
            case INCREMENT_CLAMP -> GL11.GL_INCR;
            case DECREMENT_CLAMP -> GL11.GL_DECR;
            case INCREMENT_WRAP -> GL14.GL_INCR_WRAP;
            case DECREMENT_WRAP -> GL14.GL_DECR_WRAP;
        };
    }

    static int operation(BlendOperation value) {
        return switch (value) {
            case ADD -> GL14.GL_FUNC_ADD;
            case SUBTRACT -> GL14.GL_FUNC_SUBTRACT;
            case REVERSE_SUBTRACT -> GL14.GL_FUNC_REVERSE_SUBTRACT;
            case MIN -> GL14.GL_MIN;
            case MAX -> GL14.GL_MAX;
        };
    }

    static int factor(BlendFactor value) {
        return switch (value) {
            case ZERO -> GL11.GL_ZERO;
            case ONE -> GL11.GL_ONE;
            case SOURCE -> GL11.GL_SRC_COLOR;
            case ONE_MINUS_SOURCE -> GL11.GL_ONE_MINUS_SRC_COLOR;
            case SOURCE_ALPHA -> GL11.GL_SRC_ALPHA;
            case ONE_MINUS_SOURCE_ALPHA -> GL11.GL_ONE_MINUS_SRC_ALPHA;
            case DESTINATION -> GL11.GL_DST_COLOR;
            case ONE_MINUS_DESTINATION -> GL11.GL_ONE_MINUS_DST_COLOR;
            case DESTINATION_ALPHA -> GL11.GL_DST_ALPHA;
            case ONE_MINUS_DESTINATION_ALPHA -> GL11.GL_ONE_MINUS_DST_ALPHA;
            case SOURCE_ALPHA_SATURATED -> GL11.GL_SRC_ALPHA_SATURATE;
            case CONSTANT -> GL14.GL_CONSTANT_COLOR;
            case ONE_MINUS_CONSTANT -> GL14.GL_ONE_MINUS_CONSTANT_COLOR;
        };
    }
}
