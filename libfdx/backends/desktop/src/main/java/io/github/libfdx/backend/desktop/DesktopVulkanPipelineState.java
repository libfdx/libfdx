package io.github.libfdx.backend.desktop;

import io.github.libfdx.graphics.BlendFactor;
import io.github.libfdx.graphics.BlendOperation;
import io.github.libfdx.graphics.BlendState;
import io.github.libfdx.graphics.CullMode;
import io.github.libfdx.graphics.DepthStencilState;
import io.github.libfdx.graphics.FrontFace;
import io.github.libfdx.graphics.RenderPipelineDescriptor;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkPipelineColorBlendAttachmentState;
import org.lwjgl.vulkan.VkPipelineMultisampleStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineRasterizationStateCreateInfo;
import static org.lwjgl.vulkan.VK10.*;

/** Maps accepted common pipeline state without changing descriptor defaults. */
final class DesktopVulkanPipelineState {
    private DesktopVulkanPipelineState() { }

    static void apply(RenderPipelineDescriptor state,
            VkPipelineRasterizationStateCreateInfo rasterizer,
            VkPipelineMultisampleStateCreateInfo multisampling,
            VkPipelineColorBlendAttachmentState color, int colorIndex, MemoryStack stack) {
        CullMode cull = state.primitiveState().cullMode();
        rasterizer.cullMode(cull == CullMode.NONE ? VK_CULL_MODE_NONE
                        : cull == CullMode.FRONT ? VK_CULL_MODE_FRONT_BIT : VK_CULL_MODE_BACK_BIT)
                .frontFace(state.primitiveState().frontFace() == FrontFace.COUNTER_CLOCKWISE
                        ? VK_FRONT_FACE_COUNTER_CLOCKWISE : VK_FRONT_FACE_CLOCKWISE);
        DepthStencilState depth = state.depthStencilState();
        if (depth != null) {
            rasterizer.depthBiasEnable(depth.depthBias() != 0 || depth.depthBiasSlopeScale() != 0)
                    .depthBiasConstantFactor(depth.depthBias())
                    .depthBiasSlopeFactor(depth.depthBiasSlopeScale())
                    .depthBiasClamp(depth.depthBiasClamp());
        }
        multisampling.pSampleMask(stack.ints(state.multisampleState().mask()))
                .alphaToCoverageEnable(state.multisampleState().alphaToCoverageEnabled());
        color.colorWriteMask(state.colorTargets()[colorIndex].writeMask());
        BlendState blend = state.colorTargets()[colorIndex].blend();
        color.blendEnable(blend != null);
        if (blend != null) {
            color.srcColorBlendFactor(factor(blend.color().sourceFactor(), false))
                    .dstColorBlendFactor(factor(blend.color().destinationFactor(), false))
                    .colorBlendOp(operation(blend.color().operation()))
                    .srcAlphaBlendFactor(factor(blend.alpha().sourceFactor(), true))
                    .dstAlphaBlendFactor(factor(blend.alpha().destinationFactor(), true))
                    .alphaBlendOp(operation(blend.alpha().operation()));
        }
    }

    static int operation(BlendOperation value) {
        return switch (value) {
            case ADD -> VK_BLEND_OP_ADD;
            case SUBTRACT -> VK_BLEND_OP_SUBTRACT;
            case REVERSE_SUBTRACT -> VK_BLEND_OP_REVERSE_SUBTRACT;
            case MIN -> VK_BLEND_OP_MIN;
            case MAX -> VK_BLEND_OP_MAX;
        };
    }

    static int factor(BlendFactor value, boolean alpha) {
        return switch (value) {
            case ZERO -> VK_BLEND_FACTOR_ZERO;
            case ONE -> VK_BLEND_FACTOR_ONE;
            case SOURCE -> VK_BLEND_FACTOR_SRC_COLOR;
            case ONE_MINUS_SOURCE -> VK_BLEND_FACTOR_ONE_MINUS_SRC_COLOR;
            case SOURCE_ALPHA -> VK_BLEND_FACTOR_SRC_ALPHA;
            case ONE_MINUS_SOURCE_ALPHA -> VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA;
            case DESTINATION -> VK_BLEND_FACTOR_DST_COLOR;
            case ONE_MINUS_DESTINATION -> VK_BLEND_FACTOR_ONE_MINUS_DST_COLOR;
            case DESTINATION_ALPHA -> VK_BLEND_FACTOR_DST_ALPHA;
            case ONE_MINUS_DESTINATION_ALPHA -> VK_BLEND_FACTOR_ONE_MINUS_DST_ALPHA;
            case SOURCE_ALPHA_SATURATED -> VK_BLEND_FACTOR_SRC_ALPHA_SATURATE;
            case CONSTANT -> alpha ? VK_BLEND_FACTOR_CONSTANT_ALPHA : VK_BLEND_FACTOR_CONSTANT_COLOR;
            case ONE_MINUS_CONSTANT -> alpha ? VK_BLEND_FACTOR_ONE_MINUS_CONSTANT_ALPHA : VK_BLEND_FACTOR_ONE_MINUS_CONSTANT_COLOR;
        };
    }
}
