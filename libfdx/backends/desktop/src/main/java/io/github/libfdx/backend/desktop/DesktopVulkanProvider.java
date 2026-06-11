package io.github.libfdx.backend.desktop;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.Buffer;
import io.github.libfdx.graphics.BufferDescriptor;
import io.github.libfdx.graphics.BufferUsage;
import io.github.libfdx.graphics.CommandEncoder;
import io.github.libfdx.graphics.FrameBuffer;
import io.github.libfdx.graphics.GraphicsAttachment;
import io.github.libfdx.graphics.GraphicsAttachmentProvider;
import io.github.libfdx.graphics.GraphicsAttachmentRequirements;
import io.github.libfdx.graphics.GraphicsDevice;
import io.github.libfdx.graphics.GraphicsEnvironment;
import io.github.libfdx.graphics.GraphicsFrame;
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.NativeWindow;
import io.github.libfdx.graphics.PrimitiveTopology;
import io.github.libfdx.graphics.RenderPass;
import io.github.libfdx.graphics.RenderPassDescriptor;
import io.github.libfdx.graphics.RenderPipeline;
import io.github.libfdx.graphics.RenderPipelineDescriptor;
import io.github.libfdx.graphics.ShaderLanguage;
import io.github.libfdx.graphics.ShaderModule;
import io.github.libfdx.graphics.ShaderModuleDescriptor;
import io.github.libfdx.graphics.StoreOp;
import io.github.libfdx.graphics.Texture;
import io.github.libfdx.graphics.TextureDescriptor;
import io.github.libfdx.graphics.TextureFormat;
import io.github.libfdx.graphics.TextureUsage;
import io.github.libfdx.graphics.TextureView;
import io.github.libfdx.graphics.TextureWrap;
import io.github.libfdx.graphics.VertexAttribute;
import io.github.libfdx.graphics.VertexFormat;
import io.github.libfdx.graphics.VertexLayout;
import io.github.libfdx.graphics.VertexStepMode;
import io.github.libfdx.graphics.vulkan.VulkanConfiguration;
import io.github.libfdx.graphics.vulkan.VulkanProvider;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFWVulkan;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.KHRSurface;
import org.lwjgl.vulkan.KHRSwapchain;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkApplicationInfo;
import org.lwjgl.vulkan.VkAttachmentDescription;
import org.lwjgl.vulkan.VkAttachmentReference;
import org.lwjgl.vulkan.VkBufferCopy;
import org.lwjgl.vulkan.VkBufferImageCopy;
import org.lwjgl.vulkan.VkBufferCreateInfo;
import org.lwjgl.vulkan.VkClearValue;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkCommandBufferAllocateInfo;
import org.lwjgl.vulkan.VkCommandBufferBeginInfo;
import org.lwjgl.vulkan.VkCommandPoolCreateInfo;
import org.lwjgl.vulkan.VkDescriptorBufferInfo;
import org.lwjgl.vulkan.VkDescriptorImageInfo;
import org.lwjgl.vulkan.VkDescriptorPoolCreateInfo;
import org.lwjgl.vulkan.VkDescriptorPoolSize;
import org.lwjgl.vulkan.VkDescriptorSetAllocateInfo;
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding;
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkDeviceCreateInfo;
import org.lwjgl.vulkan.VkDeviceQueueCreateInfo;
import org.lwjgl.vulkan.VkExtent2D;
import org.lwjgl.vulkan.VkFenceCreateInfo;
import org.lwjgl.vulkan.VkFramebufferCreateInfo;
import org.lwjgl.vulkan.VkGraphicsPipelineCreateInfo;
import org.lwjgl.vulkan.VkImageCreateInfo;
import org.lwjgl.vulkan.VkImageMemoryBarrier;
import org.lwjgl.vulkan.VkImageSubresourceRange;
import org.lwjgl.vulkan.VkImageViewCreateInfo;
import org.lwjgl.vulkan.VkInstance;
import org.lwjgl.vulkan.VkInstanceCreateInfo;
import org.lwjgl.vulkan.VkLayerProperties;
import org.lwjgl.vulkan.VkMemoryAllocateInfo;
import org.lwjgl.vulkan.VkMemoryRequirements;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkPhysicalDeviceFeatures;
import org.lwjgl.vulkan.VkPhysicalDeviceMemoryProperties;
import org.lwjgl.vulkan.VkPhysicalDeviceProperties;
import org.lwjgl.vulkan.VkPipelineColorBlendAttachmentState;
import org.lwjgl.vulkan.VkPipelineColorBlendStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineDepthStencilStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineDynamicStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineInputAssemblyStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineLayoutCreateInfo;
import org.lwjgl.vulkan.VkPipelineMultisampleStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineRasterizationStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo;
import org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineViewportStateCreateInfo;
import org.lwjgl.vulkan.VkPresentInfoKHR;
import org.lwjgl.vulkan.VkQueue;
import org.lwjgl.vulkan.VkQueueFamilyProperties;
import org.lwjgl.vulkan.VkRect2D;
import org.lwjgl.vulkan.VkRenderPassBeginInfo;
import org.lwjgl.vulkan.VkRenderPassCreateInfo;
import org.lwjgl.vulkan.VkSamplerCreateInfo;
import org.lwjgl.vulkan.VkSemaphoreCreateInfo;
import org.lwjgl.vulkan.VkShaderModuleCreateInfo;
import org.lwjgl.vulkan.VkSubmitInfo;
import org.lwjgl.vulkan.VkSubpassDependency;
import org.lwjgl.vulkan.VkSubpassDescription;
import org.lwjgl.vulkan.VkSurfaceCapabilitiesKHR;
import org.lwjgl.vulkan.VkSurfaceFormatKHR;
import org.lwjgl.vulkan.VkSwapchainCreateInfoKHR;
import org.lwjgl.vulkan.VkVertexInputAttributeDescription;
import org.lwjgl.vulkan.VkVertexInputBindingDescription;
import org.lwjgl.vulkan.VkViewport;
import org.lwjgl.vulkan.VkWriteDescriptorSet;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.lwjgl.glfw.GLFWVulkan.glfwCreateWindowSurface;
import static org.lwjgl.glfw.GLFWVulkan.glfwGetRequiredInstanceExtensions;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.memByteBuffer;
import static org.lwjgl.system.MemoryUtil.NULL;
import static org.lwjgl.vulkan.KHRSurface.VK_COLOR_SPACE_SRGB_NONLINEAR_KHR;
import static org.lwjgl.vulkan.KHRSurface.VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR;
import static org.lwjgl.vulkan.KHRSurface.VK_PRESENT_MODE_FIFO_KHR;
import static org.lwjgl.vulkan.KHRSurface.VK_PRESENT_MODE_IMMEDIATE_KHR;
import static org.lwjgl.vulkan.KHRSurface.VK_PRESENT_MODE_MAILBOX_KHR;
import static org.lwjgl.vulkan.KHRSurface.VK_SURFACE_TRANSFORM_IDENTITY_BIT_KHR;
import static org.lwjgl.vulkan.KHRSurface.vkDestroySurfaceKHR;
import static org.lwjgl.vulkan.KHRSurface.vkGetPhysicalDeviceSurfaceCapabilitiesKHR;
import static org.lwjgl.vulkan.KHRSurface.vkGetPhysicalDeviceSurfaceFormatsKHR;
import static org.lwjgl.vulkan.KHRSurface.vkGetPhysicalDeviceSurfacePresentModesKHR;
import static org.lwjgl.vulkan.KHRSurface.vkGetPhysicalDeviceSurfaceSupportKHR;
import static org.lwjgl.vulkan.KHRSwapchain.VK_ERROR_OUT_OF_DATE_KHR;
import static org.lwjgl.vulkan.KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
import static org.lwjgl.vulkan.KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRSwapchain.VK_STRUCTURE_TYPE_PRESENT_INFO_KHR;
import static org.lwjgl.vulkan.KHRSwapchain.VK_SUBOPTIMAL_KHR;
import static org.lwjgl.vulkan.KHRSwapchain.vkAcquireNextImageKHR;
import static org.lwjgl.vulkan.KHRSwapchain.vkCreateSwapchainKHR;
import static org.lwjgl.vulkan.KHRSwapchain.vkDestroySwapchainKHR;
import static org.lwjgl.vulkan.KHRSwapchain.vkGetSwapchainImagesKHR;
import static org.lwjgl.vulkan.KHRSwapchain.vkQueuePresentKHR;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_TRANSFER_READ_BIT;
import static org.lwjgl.vulkan.VK10.VK_API_VERSION_1_0;
import static org.lwjgl.vulkan.VK10.VK_ATTACHMENT_LOAD_OP_CLEAR;
import static org.lwjgl.vulkan.VK10.VK_ATTACHMENT_LOAD_OP_DONT_CARE;
import static org.lwjgl.vulkan.VK10.VK_ATTACHMENT_LOAD_OP_LOAD;
import static org.lwjgl.vulkan.VK10.VK_ATTACHMENT_STORE_OP_DONT_CARE;
import static org.lwjgl.vulkan.VK10.VK_ATTACHMENT_STORE_OP_STORE;
import static org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_ONE;
import static org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_ZERO;
import static org.lwjgl.vulkan.VK10.VK_BLEND_OP_ADD;
import static org.lwjgl.vulkan.VK10.VK_BORDER_COLOR_FLOAT_OPAQUE_BLACK;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_INDEX_BUFFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_COLOR_COMPONENT_A_BIT;
import static org.lwjgl.vulkan.VK10.VK_COLOR_COMPONENT_B_BIT;
import static org.lwjgl.vulkan.VK10.VK_COLOR_COMPONENT_G_BIT;
import static org.lwjgl.vulkan.VK10.VK_COLOR_COMPONENT_R_BIT;
import static org.lwjgl.vulkan.VK10.VK_COMMAND_BUFFER_LEVEL_PRIMARY;
import static org.lwjgl.vulkan.VK10.VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_COMMAND_POOL_CREATE_TRANSIENT_BIT;
import static org.lwjgl.vulkan.VK10.VK_CULL_MODE_NONE;
import static org.lwjgl.vulkan.VK10.VK_COMPARE_OP_LESS_OR_EQUAL;
import static org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER;
import static org.lwjgl.vulkan.VK10.VK_DYNAMIC_STATE_SCISSOR;
import static org.lwjgl.vulkan.VK10.VK_DYNAMIC_STATE_VIEWPORT;
import static org.lwjgl.vulkan.VK10.VK_FALSE;
import static org.lwjgl.vulkan.VK10.VK_FENCE_CREATE_SIGNALED_BIT;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_B8G8R8A8_SRGB;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_B8G8R8A8_UNORM;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_D32_SFLOAT;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_R8G8B8A8_SRGB;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_R8G8B8A8_UNORM;
import static org.lwjgl.vulkan.VK10.VK_FRONT_FACE_COUNTER_CLOCKWISE;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_R32G32B32A32_SFLOAT;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_R32G32B32_SFLOAT;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_R32G32_SFLOAT;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_R32_SFLOAT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_ASPECT_COLOR_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_ASPECT_DEPTH_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_UNDEFINED;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_TILING_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_TYPE_2D;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_TRANSFER_SRC_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_VIEW_TYPE_2D;
import static org.lwjgl.vulkan.VK10.VK_INDEX_TYPE_UINT16;
import static org.lwjgl.vulkan.VK10.VK_LOGIC_OP_COPY;
import static org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT;
import static org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT;
import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
import static org.lwjgl.vulkan.VK10.VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_BIND_POINT_GRAPHICS;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT;
import static org.lwjgl.vulkan.VK10.VK_POLYGON_MODE_FILL;
import static org.lwjgl.vulkan.VK10.VK_PRIMITIVE_TOPOLOGY_LINE_LIST;
import static org.lwjgl.vulkan.VK10.VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST;
import static org.lwjgl.vulkan.VK10.VK_PRIMITIVE_TOPOLOGY_TRIANGLE_STRIP;
import static org.lwjgl.vulkan.VK10.VK_QUEUE_FAMILY_IGNORED;
import static org.lwjgl.vulkan.VK10.VK_QUEUE_GRAPHICS_BIT;
import static org.lwjgl.vulkan.VK10.VK_SAMPLE_COUNT_1_BIT;
import static org.lwjgl.vulkan.VK10.VK_SHADER_STAGE_FRAGMENT_BIT;
import static org.lwjgl.vulkan.VK10.VK_SHADER_STAGE_VERTEX_BIT;
import static org.lwjgl.vulkan.VK10.VK_SHARING_MODE_CONCURRENT;
import static org.lwjgl.vulkan.VK10.VK_SHARING_MODE_EXCLUSIVE;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_APPLICATION_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_FENCE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_PIPELINE_DEPTH_STENCIL_STATE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_SUBMIT_INFO;
import static org.lwjgl.vulkan.VK10.VK_SUBPASS_EXTERNAL;
import static org.lwjgl.vulkan.VK10.VK_NOT_READY;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;
import static org.lwjgl.vulkan.VK10.VK_TRUE;
import static org.lwjgl.vulkan.VK10.VK_TIMEOUT;
import static org.lwjgl.vulkan.VK10.VK_VERTEX_INPUT_RATE_INSTANCE;
import static org.lwjgl.vulkan.VK10.VK_VERTEX_INPUT_RATE_VERTEX;
import static org.lwjgl.vulkan.VK10.VK_WHOLE_SIZE;
import static org.lwjgl.vulkan.VK10.vkAllocateMemory;
import static org.lwjgl.vulkan.VK10.vkAllocateCommandBuffers;
import static org.lwjgl.vulkan.VK10.vkBeginCommandBuffer;
import static org.lwjgl.vulkan.VK10.vkCmdBeginRenderPass;
import static org.lwjgl.vulkan.VK10.vkCmdBindIndexBuffer;
import static org.lwjgl.vulkan.VK10.vkCmdBindPipeline;
import static org.lwjgl.vulkan.VK10.vkCmdBindVertexBuffers;
import static org.lwjgl.vulkan.VK10.vkCmdCopyBuffer;
import static org.lwjgl.vulkan.VK10.vkCmdCopyImageToBuffer;
import static org.lwjgl.vulkan.VK10.vkCmdDraw;
import static org.lwjgl.vulkan.VK10.vkCmdDrawIndexed;
import static org.lwjgl.vulkan.VK10.vkCmdEndRenderPass;
import static org.lwjgl.vulkan.VK10.vkCmdSetScissor;
import static org.lwjgl.vulkan.VK10.vkCmdSetViewport;
import static org.lwjgl.vulkan.VK10.vkCreateCommandPool;
import static org.lwjgl.vulkan.VK10.vkCreateBuffer;
import static org.lwjgl.vulkan.VK10.vkCreateDevice;
import static org.lwjgl.vulkan.VK10.vkCreateFence;
import static org.lwjgl.vulkan.VK10.vkCreateFramebuffer;
import static org.lwjgl.vulkan.VK10.vkCreateGraphicsPipelines;
import static org.lwjgl.vulkan.VK10.vkCreateImageView;
import static org.lwjgl.vulkan.VK10.vkCreateInstance;
import static org.lwjgl.vulkan.VK10.vkCreatePipelineLayout;
import static org.lwjgl.vulkan.VK10.vkCreateRenderPass;
import static org.lwjgl.vulkan.VK10.vkCreateSemaphore;
import static org.lwjgl.vulkan.VK10.vkCreateShaderModule;
import static org.lwjgl.vulkan.VK10.vkDestroyCommandPool;
import static org.lwjgl.vulkan.VK10.vkDestroyBuffer;
import static org.lwjgl.vulkan.VK10.vkDestroyDevice;
import static org.lwjgl.vulkan.VK10.vkDestroyFence;
import static org.lwjgl.vulkan.VK10.vkDestroyFramebuffer;
import static org.lwjgl.vulkan.VK10.vkDestroyImageView;
import static org.lwjgl.vulkan.VK10.vkDestroyInstance;
import static org.lwjgl.vulkan.VK10.vkDestroyPipeline;
import static org.lwjgl.vulkan.VK10.vkDestroyPipelineLayout;
import static org.lwjgl.vulkan.VK10.vkDestroyRenderPass;
import static org.lwjgl.vulkan.VK10.vkDestroySemaphore;
import static org.lwjgl.vulkan.VK10.vkDestroyShaderModule;
import static org.lwjgl.vulkan.VK10.vkDeviceWaitIdle;
import static org.lwjgl.vulkan.VK10.vkEndCommandBuffer;
import static org.lwjgl.vulkan.VK10.vkEnumerateInstanceLayerProperties;
import static org.lwjgl.vulkan.VK10.vkEnumeratePhysicalDevices;
import static org.lwjgl.vulkan.VK10.vkFreeCommandBuffers;
import static org.lwjgl.vulkan.VK10.vkFreeMemory;
import static org.lwjgl.vulkan.VK10.vkGetDeviceQueue;
import static org.lwjgl.vulkan.VK10.vkGetBufferMemoryRequirements;
import static org.lwjgl.vulkan.VK10.vkGetPhysicalDeviceMemoryProperties;
import static org.lwjgl.vulkan.VK10.vkGetPhysicalDeviceProperties;
import static org.lwjgl.vulkan.VK10.vkGetPhysicalDeviceQueueFamilyProperties;
import static org.lwjgl.vulkan.VK10.vkQueueSubmit;
import static org.lwjgl.vulkan.VK10.vkResetCommandBuffer;
import static org.lwjgl.vulkan.VK10.vkResetFences;
import static org.lwjgl.vulkan.VK10.vkBindBufferMemory;
import static org.lwjgl.vulkan.VK10.vkMapMemory;
import static org.lwjgl.vulkan.VK10.vkUnmapMemory;
import static org.lwjgl.vulkan.VK10.vkWaitForFences;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_SHADER_READ_BIT;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_TRANSFER_WRITE_BIT;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT;
import static org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_SRC_ALPHA;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_TRANSFER_DST_BIT;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_TRANSFER_SRC_BIT;
import static org.lwjgl.vulkan.VK10.VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;
import static org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
import static org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_POOL_CREATE_FREE_DESCRIPTOR_SET_BIT;
import static org.lwjgl.vulkan.VK10.VK_FILTER_LINEAR;
import static org.lwjgl.vulkan.VK10.VK_FILTER_NEAREST;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_SAMPLED_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_TRANSFER_DST_BIT;
import static org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_TRANSFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
import static org.lwjgl.vulkan.VK10.VK_SAMPLER_ADDRESS_MODE_MIRRORED_REPEAT;
import static org.lwjgl.vulkan.VK10.VK_SAMPLER_ADDRESS_MODE_REPEAT;
import static org.lwjgl.vulkan.VK10.VK_SAMPLER_MIPMAP_MODE_LINEAR;
import static org.lwjgl.vulkan.VK10.VK_SAMPLER_MIPMAP_MODE_NEAREST;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET;
import static org.lwjgl.vulkan.VK10.vkAllocateDescriptorSets;
import static org.lwjgl.vulkan.VK10.vkBindImageMemory;
import static org.lwjgl.vulkan.VK10.vkCmdBindDescriptorSets;
import static org.lwjgl.vulkan.VK10.vkCmdCopyBufferToImage;
import static org.lwjgl.vulkan.VK10.vkCmdPipelineBarrier;
import static org.lwjgl.vulkan.VK10.vkCreateDescriptorPool;
import static org.lwjgl.vulkan.VK10.vkCreateDescriptorSetLayout;
import static org.lwjgl.vulkan.VK10.vkCreateImage;
import static org.lwjgl.vulkan.VK10.vkCreateSampler;
import static org.lwjgl.vulkan.VK10.vkDestroyDescriptorPool;
import static org.lwjgl.vulkan.VK10.vkDestroyDescriptorSetLayout;
import static org.lwjgl.vulkan.VK10.vkDestroyImage;
import static org.lwjgl.vulkan.VK10.vkDestroySampler;
import static org.lwjgl.vulkan.VK10.vkFreeDescriptorSets;
import static org.lwjgl.vulkan.VK10.vkGetImageMemoryRequirements;
import static org.lwjgl.vulkan.VK10.vkQueueWaitIdle;
import static org.lwjgl.vulkan.VK10.vkUpdateDescriptorSets;

public final class DesktopVulkanProvider implements GraphicsAttachmentProvider {
    public static final ProviderId ID = VulkanProvider.ID;
    private static final String VALIDATION_LAYER = "VK_LAYER_KHRONOS_validation";
    private static final int MAX_TEXTURE_DESCRIPTOR_SETS = 4096;
    private static final int MAX_TEXTURE_DESCRIPTOR_SLOTS = 16;
    private static final int MAX_UNIFORM_DESCRIPTOR_SETS = 4096;
    private static final int PBR_UNIFORM_BYTE_COUNT = 224;
    private static final int PBR_TEXTURE_DESCRIPTOR_COUNT = 5;
    private static final int DEPTH_FORMAT = VK_FORMAT_D32_SFLOAT;
    private static final long FRAME_FENCE_TIMEOUT_NS = 33_000_000L;
    private static final long SWAPCHAIN_ACQUIRE_TIMEOUT_NS = 33_000_000L;
    private static final long READBACK_FENCE_TIMEOUT_NS = 250_000_000L;
    private static final long ONE_TIME_COMMAND_TIMEOUT_NS = 250_000_000L;
    private static final long DEVICE_DISPOSE_FENCE_TIMEOUT_NS = 250_000_000L;
    private static final ConcurrentHashMap<Long, long[]> FRAME_FENCES_BY_DEVICE = new ConcurrentHashMap<>();

    private VulkanConfiguration configuration = new VulkanConfiguration();

    @Override
    public ProviderId providerId() {
        return ID;
    }

    @Override
    public GraphicsAttachmentRequirements requirements() {
        return GraphicsAttachmentRequirements.vulkan();
    }

    @Override
    public GraphicsAttachment create(GraphicsEnvironment environment) {
        if (environment == null) {
            throw new FdxException("GraphicsEnvironment cannot be null");
        }
        NativeWindow nativeWindow = environment.nativeWindow();
        if (nativeWindow == null || nativeWindow.backendHandle() == 0L) {
            throw new FdxException("Desktop Vulkan requires a backend window handle");
        }
        VulkanContext context = new VulkanContext(configuration, nativeWindow.backendHandle(),
                environment.display().framebufferWidth(), environment.display().framebufferHeight());
        context.initialize();
        return context;
    }

    public VulkanConfiguration configuration() {
        return configuration;
    }

    public DesktopVulkanProvider configuration(VulkanConfiguration configuration) {
        this.configuration = configuration != null ? configuration : new VulkanConfiguration();
        return this;
    }

    public DesktopVulkanProvider vSync(boolean vSync) {
        configuration.vSync(vSync);
        return this;
    }

    public DesktopVulkanProvider validation(boolean validation) {
        configuration.validation(validation);
        return this;
    }

    public DesktopVulkanProvider framesInFlight(int framesInFlight) {
        configuration.framesInFlight(framesInFlight);
        return this;
    }

    private static boolean usesPbrUniformBlock(RenderPipelineDescriptor descriptor) {
        // The built-in Vulkan ModelBatch PBR shader declares five sampled texture slots and one PBR uniform block.
        return descriptor.sampledTextureCount() == PBR_TEXTURE_DESCRIPTOR_COUNT;
    }

    private static void waitDeviceIdleBeforeDestroy(VkDevice device, String resourceName) {
        if (device == null) {
            return;
        }
        long[] frameFences = FRAME_FENCES_BY_DEVICE.get(device.address());
        if (frameFences == null || frameFences.length == 0) {
            return;
        }
        waitForAllFences(device, frameFences, DEVICE_DISPOSE_FENCE_TIMEOUT_NS, "destroy " + resourceName);
    }

    private static void waitForAllFences(VkDevice device, long[] fences, long timeoutNs, String operation) {
        for (long fence : fences) {
            if (fence == VK_NULL_HANDLE) {
                continue;
            }
            int waitResult = vkWaitForFences(device, fence, true, timeoutNs);
            if (waitResult == VK_TIMEOUT) {
                throw new FdxException("Timed out while waiting for Vulkan " + operation);
            }
            check(waitResult, "Could not wait for Vulkan " + operation);
        }
    }

    private static boolean waitForFenceOrSkipFrame(VkDevice device, long fence, long timeoutNs, String operation) {
        if (fence == VK_NULL_HANDLE) {
            return true;
        }
        int waitResult = vkWaitForFences(device, fence, true, timeoutNs);
        if (waitResult == VK_TIMEOUT) {
            return false;
        }
        check(waitResult, "Could not wait for Vulkan " + operation);
        return true;
    }

    private static void registerFrameFences(VkDevice device, long[] frameFences) {
        if (device == null || frameFences == null) {
            return;
        }
        FRAME_FENCES_BY_DEVICE.put(device.address(), Arrays.copyOf(frameFences, frameFences.length));
    }

    private static void unregisterFrameFences(VkDevice device) {
        if (device != null) {
            FRAME_FENCES_BY_DEVICE.remove(device.address());
        }
    }

    private static final class VulkanContext implements GraphicsAttachment {
        private final VulkanConfiguration configuration;
        private final long windowHandle;
        private final VulkanGraphicsDevice graphicsDevice = new VulkanGraphicsDevice(this);
        private final VulkanCommandEncoder commandEncoder = new VulkanCommandEncoder(this);
        private final VulkanTextureViewHandle colorAttachment = new VulkanTextureViewHandle(this);
        private final VulkanFrameBuffer frameBuffer = new VulkanFrameBuffer(this, colorAttachment);
        private final VulkanGraphicsFrame currentFrame = new VulkanGraphicsFrame(this, commandEncoder,
                frameBuffer, colorAttachment);
        private VkInstance instance;
        private long surface;
        private VkPhysicalDevice physicalDevice;
        private VkDevice device;
        private VkQueue graphicsQueue;
        private VkQueue presentQueue;
        private int graphicsQueueFamily;
        private int presentQueueFamily;
        private long swapchain;
        private long[] swapchainImages = new long[0];
        private long[] imageViews = new long[0];
        private long[] framebuffers = new long[0];
        private long renderPassClearStore;
        private long renderPassClearDiscard;
        private long renderPassLoadStore;
        private long renderPassLoadDiscard;
        private int swapchainImageFormat;
        private TextureFormat surfaceFormat = TextureFormat.UNKNOWN;
        private int width;
        private int height;
        private boolean swapchainTransferSrcSupported;
        private long commandPool;
        private long textureDescriptorSetLayout;
        private long textureDescriptorPool;
        private long uniformDescriptorSetLayout;
        private long uniformDescriptorPool;
        private final Map<TextureDescriptorSetKey, Long> textureDescriptorSets = new HashMap<>();
        private long[] depthImages = new long[0];
        private long[] depthImageMemories = new long[0];
        private long[] depthImageViews = new long[0];
        private ArrayList<VulkanUniformAllocation>[] uniformAllocationsInFlight;
        private ArrayList<VulkanBufferAllocation>[] retiredBuffersInFlight;
        private final ArrayList<VulkanBufferHandle> usedBufferHandles = new ArrayList<VulkanBufferHandle>();
        private VkCommandBuffer[] commandBuffers;
        private long[] imageAvailableSemaphores;
        private long[] renderFinishedSemaphores;
        private long[] inFlightFences;
        private long[] imagesInFlight = new long[0];
        private int currentFrameSlot;
        private int currentImageIndex = -1;
        private boolean frameStarted;
        private boolean pendingResize;
        private int pendingResizeWidth;
        private int pendingResizeHeight;
        private boolean disposed = true;

        VulkanContext(VulkanConfiguration configuration, long windowHandle, int width, int height) {
            this.configuration = configuration != null ? configuration : new VulkanConfiguration();
            this.windowHandle = windowHandle;
            this.width = width;
            this.height = height;
        }

        void initialize() {
            if (!GLFWVulkan.glfwVulkanSupported()) {
                throw new FdxException("Vulkan is not supported by GLFW on this system");
            }
            disposed = false;
            try {
                createInstance();
                createSurface();
                selectPhysicalDevice();
                createLogicalDevice();
                createCommandPool();
                createTextureDescriptorResources();
                createUniformDescriptorResources();
                createSwapchain(width, height);
                createSyncObjects();
            } catch (RuntimeException error) {
                dispose();
                throw error;
            }
        }

        private void createInstance() {
            try (MemoryStack stack = stackPush()) {
                PointerBuffer requiredExtensions = glfwGetRequiredInstanceExtensions();
                if (requiredExtensions == null) {
                    throw new FdxException("GLFW did not provide Vulkan instance extensions");
                }

                PointerBuffer extensionNames = stack.mallocPointer(requiredExtensions.remaining());
                for (int i = 0; i < requiredExtensions.remaining(); i++) {
                    extensionNames.put(requiredExtensions.get(i));
                }
                extensionNames.flip();

                PointerBuffer layerNames = null;
                if (configuration.validation()) {
                    if (!validationLayerAvailable(stack)) {
                        throw new FdxException("Requested Vulkan validation layer is not available: " + VALIDATION_LAYER);
                    }
                    layerNames = stack.pointers(stack.UTF8(VALIDATION_LAYER));
                }

                VkApplicationInfo appInfo = VkApplicationInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_APPLICATION_INFO)
                        .pApplicationName(stack.UTF8(configuration.applicationName()))
                        .applicationVersion(VK10.VK_MAKE_VERSION(1, 0, 0))
                        .pEngineName(stack.UTF8("libfdx"))
                        .engineVersion(VK10.VK_MAKE_VERSION(1, 0, 0))
                        .apiVersion(VK_API_VERSION_1_0);

                VkInstanceCreateInfo createInfo = VkInstanceCreateInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO)
                        .pApplicationInfo(appInfo)
                        .ppEnabledExtensionNames(extensionNames)
                        .ppEnabledLayerNames(layerNames);

                PointerBuffer pInstance = stack.mallocPointer(1);
                check(vkCreateInstance(createInfo, null, pInstance), "Could not create Vulkan instance");
                instance = new VkInstance(pInstance.get(0), createInfo);
            }
        }

        private boolean validationLayerAvailable(MemoryStack stack) {
            IntBuffer count = stack.ints(0);
            check(vkEnumerateInstanceLayerProperties(count, null), "Could not enumerate Vulkan validation layers");
            VkLayerProperties.Buffer layers = VkLayerProperties.malloc(count.get(0), stack);
            check(vkEnumerateInstanceLayerProperties(count, layers), "Could not enumerate Vulkan validation layers");
            for (int i = 0; i < layers.capacity(); i++) {
                if (VALIDATION_LAYER.equals(layers.get(i).layerNameString())) {
                    return true;
                }
            }
            return false;
        }

        private void createSurface() {
            try (MemoryStack stack = stackPush()) {
                LongBuffer pSurface = stack.mallocLong(1);
                check(glfwCreateWindowSurface(instance, windowHandle, null, pSurface), "Could not create Vulkan window surface");
                surface = pSurface.get(0);
            }
        }

        private void selectPhysicalDevice() {
            try (MemoryStack stack = stackPush()) {
                IntBuffer count = stack.ints(0);
                check(vkEnumeratePhysicalDevices(instance, count, null), "Could not enumerate Vulkan physical devices");
                if (count.get(0) == 0) {
                    throw new FdxException("No Vulkan physical devices were found");
                }

                PointerBuffer devices = stack.mallocPointer(count.get(0));
                check(vkEnumeratePhysicalDevices(instance, count, devices), "Could not enumerate Vulkan physical devices");

                int bestScore = -1;
                VkPhysicalDevice bestDevice = null;
                QueueFamilyIndices bestIndices = null;
                for (int i = 0; i < devices.capacity(); i++) {
                    VkPhysicalDevice candidate = new VkPhysicalDevice(devices.get(i), instance);
                    QueueFamilyIndices indices = findQueueFamilies(candidate, stack);
                    if (!indices.complete() || !supportsSwapchain(candidate, stack)) {
                        continue;
                    }
                    int score = scoreDevice(candidate, stack);
                    if (score > bestScore) {
                        bestScore = score;
                        bestDevice = candidate;
                        bestIndices = indices;
                    }
                }

                if (bestDevice == null || bestIndices == null) {
                    throw new FdxException("No Vulkan physical device supports graphics, presentation, and swapchain");
                }
                physicalDevice = bestDevice;
                graphicsQueueFamily = bestIndices.graphicsFamily;
                presentQueueFamily = bestIndices.presentFamily;
            }
        }

        private int scoreDevice(VkPhysicalDevice candidate, MemoryStack stack) {
            VkPhysicalDeviceProperties properties = VkPhysicalDeviceProperties.malloc(stack);
            vkGetPhysicalDeviceProperties(candidate, properties);
            int score = 1;
            if (properties.deviceType() == VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU) {
                score += 1000;
            }
            return score;
        }

        private QueueFamilyIndices findQueueFamilies(VkPhysicalDevice candidate, MemoryStack stack) {
            QueueFamilyIndices indices = new QueueFamilyIndices();
            IntBuffer count = stack.ints(0);
            vkGetPhysicalDeviceQueueFamilyProperties(candidate, count, null);
            VkQueueFamilyProperties.Buffer families = VkQueueFamilyProperties.malloc(count.get(0), stack);
            vkGetPhysicalDeviceQueueFamilyProperties(candidate, count, families);

            IntBuffer presentSupport = stack.ints(VK_FALSE);
            for (int i = 0; i < families.capacity(); i++) {
                if ((families.get(i).queueFlags() & VK_QUEUE_GRAPHICS_BIT) != 0) {
                    indices.graphicsFamily = i;
                }
                presentSupport.put(0, VK_FALSE);
                check(vkGetPhysicalDeviceSurfaceSupportKHR(candidate, i, surface, presentSupport),
                        "Could not query Vulkan presentation support");
                if (presentSupport.get(0) == VK_TRUE) {
                    indices.presentFamily = i;
                }
                if (indices.complete()) {
                    break;
                }
            }
            return indices;
        }

        private boolean supportsSwapchain(VkPhysicalDevice candidate, MemoryStack stack) {
            IntBuffer count = stack.ints(0);
            check(vkGetPhysicalDeviceSurfaceFormatsKHR(candidate, surface, count, null),
                    "Could not query Vulkan surface formats");
            boolean hasFormats = count.get(0) > 0;
            count.put(0, 0);
            check(vkGetPhysicalDeviceSurfacePresentModesKHR(candidate, surface, count, null),
                    "Could not query Vulkan present modes");
            return hasFormats && count.get(0) > 0;
        }

        private void createLogicalDevice() {
            try (MemoryStack stack = stackPush()) {
                float priority = 1.0f;
                boolean sameQueue = graphicsQueueFamily == presentQueueFamily;
                VkDeviceQueueCreateInfo.Buffer queueInfos = VkDeviceQueueCreateInfo.calloc(sameQueue ? 1 : 2, stack);
                queueInfos.get(0)
                        .sType(VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO)
                        .queueFamilyIndex(graphicsQueueFamily)
                        .pQueuePriorities(stack.floats(priority));
                if (!sameQueue) {
                    queueInfos.get(1)
                            .sType(VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO)
                            .queueFamilyIndex(presentQueueFamily)
                            .pQueuePriorities(stack.floats(priority));
                }

                PointerBuffer extensionNames = stack.pointers(stack.UTF8(VK_KHR_SWAPCHAIN_EXTENSION_NAME));
                VkPhysicalDeviceFeatures features = VkPhysicalDeviceFeatures.calloc(stack);
                VkDeviceCreateInfo createInfo = VkDeviceCreateInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO)
                        .pQueueCreateInfos(queueInfos)
                        .ppEnabledExtensionNames(extensionNames)
                        .pEnabledFeatures(features);

                PointerBuffer pDevice = stack.mallocPointer(1);
                check(vkCreateDevice(physicalDevice, createInfo, null, pDevice), "Could not create Vulkan logical device");
                device = new VkDevice(pDevice.get(0), physicalDevice, createInfo);

                PointerBuffer pQueue = stack.mallocPointer(1);
                vkGetDeviceQueue(device, graphicsQueueFamily, 0, pQueue);
                graphicsQueue = new VkQueue(pQueue.get(0), device);
                vkGetDeviceQueue(device, presentQueueFamily, 0, pQueue);
                presentQueue = new VkQueue(pQueue.get(0), device);
            }
        }

        private void createCommandPool() {
            try (MemoryStack stack = stackPush()) {
                VkCommandPoolCreateInfo createInfo = VkCommandPoolCreateInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO)
                        .flags(VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT | VK_COMMAND_POOL_CREATE_TRANSIENT_BIT)
                        .queueFamilyIndex(graphicsQueueFamily);
                LongBuffer pCommandPool = stack.mallocLong(1);
                check(vkCreateCommandPool(device, createInfo, null, pCommandPool), "Could not create Vulkan command pool");
                commandPool = pCommandPool.get(0);
            }
        }

        private void createTextureDescriptorResources() {
            try (MemoryStack stack = stackPush()) {
                VkDescriptorSetLayoutBinding.Buffer bindings =
                        VkDescriptorSetLayoutBinding.calloc(MAX_TEXTURE_DESCRIPTOR_SLOTS, stack);
                for (int i = 0; i < MAX_TEXTURE_DESCRIPTOR_SLOTS; i++) {
                    bindings.get(i)
                            .binding(i)
                            .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                            .descriptorCount(1)
                            .stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT);
                }

                VkDescriptorSetLayoutCreateInfo layoutInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO)
                        .pBindings(bindings);
                LongBuffer pLayout = stack.mallocLong(1);
                check(vkCreateDescriptorSetLayout(device, layoutInfo, null, pLayout),
                        "Could not create Vulkan texture descriptor set layout");
                textureDescriptorSetLayout = pLayout.get(0);

                VkDescriptorPoolSize.Buffer poolSizes = VkDescriptorPoolSize.calloc(1, stack);
                poolSizes.get(0)
                        .type(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                        .descriptorCount(MAX_TEXTURE_DESCRIPTOR_SETS * MAX_TEXTURE_DESCRIPTOR_SLOTS);
                VkDescriptorPoolCreateInfo poolInfo = VkDescriptorPoolCreateInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO)
                        .pPoolSizes(poolSizes)
                        .maxSets(MAX_TEXTURE_DESCRIPTOR_SETS);
                LongBuffer pPool = stack.mallocLong(1);
                check(vkCreateDescriptorPool(device, poolInfo, null, pPool),
                        "Could not create Vulkan texture descriptor pool");
                textureDescriptorPool = pPool.get(0);
            }
        }

        private void createUniformDescriptorResources() {
            try (MemoryStack stack = stackPush()) {
                VkDescriptorSetLayoutBinding.Buffer bindings = VkDescriptorSetLayoutBinding.calloc(1, stack);
                bindings.get(0)
                        .binding(0)
                        .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                        .descriptorCount(1)
                        .stageFlags(VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT);

                VkDescriptorSetLayoutCreateInfo layoutInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO)
                        .pBindings(bindings);
                LongBuffer pLayout = stack.mallocLong(1);
                check(vkCreateDescriptorSetLayout(device, layoutInfo, null, pLayout),
                        "Could not create Vulkan uniform descriptor set layout");
                uniformDescriptorSetLayout = pLayout.get(0);

                VkDescriptorPoolSize.Buffer poolSizes = VkDescriptorPoolSize.calloc(1, stack);
                poolSizes.get(0)
                        .type(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                        .descriptorCount(MAX_UNIFORM_DESCRIPTOR_SETS);
                VkDescriptorPoolCreateInfo poolInfo = VkDescriptorPoolCreateInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO)
                        .flags(VK_DESCRIPTOR_POOL_CREATE_FREE_DESCRIPTOR_SET_BIT)
                        .pPoolSizes(poolSizes)
                        .maxSets(MAX_UNIFORM_DESCRIPTOR_SETS);
                LongBuffer pPool = stack.mallocLong(1);
                check(vkCreateDescriptorPool(device, poolInfo, null, pPool),
                        "Could not create Vulkan uniform descriptor pool");
                uniformDescriptorPool = pPool.get(0);
            }
        }

        private void createSwapchain(int requestedWidth, int requestedHeight) {
            if (requestedWidth <= 0 || requestedHeight <= 0) {
                return;
            }
            try (MemoryStack stack = stackPush()) {
                VkSurfaceCapabilitiesKHR capabilities = VkSurfaceCapabilitiesKHR.malloc(stack);
                check(vkGetPhysicalDeviceSurfaceCapabilitiesKHR(physicalDevice, surface, capabilities),
                        "Could not query Vulkan surface capabilities");

                VkSurfaceFormatKHR surfaceFormat = chooseSurfaceFormat(stack);
                int presentMode = choosePresentMode(stack);
                VkExtent2D extent = chooseExtent(capabilities, requestedWidth, requestedHeight, stack);
                int imageCount = Math.max(capabilities.minImageCount() + 1, configuration.framesInFlight());
                if (capabilities.maxImageCount() > 0 && imageCount > capabilities.maxImageCount()) {
                    imageCount = capabilities.maxImageCount();
                }
                swapchainTransferSrcSupported =
                        (capabilities.supportedUsageFlags() & VK_IMAGE_USAGE_TRANSFER_SRC_BIT) != 0;
                int imageUsage = VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT;
                if (swapchainTransferSrcSupported) {
                    imageUsage |= VK_IMAGE_USAGE_TRANSFER_SRC_BIT;
                }

                VkSwapchainCreateInfoKHR createInfo = VkSwapchainCreateInfoKHR.calloc(stack)
                        .sType(KHRSwapchain.VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR)
                        .surface(surface)
                        .minImageCount(imageCount)
                        .imageFormat(surfaceFormat.format())
                        .imageColorSpace(surfaceFormat.colorSpace())
                        .imageExtent(extent)
                        .imageArrayLayers(1)
                        .imageUsage(imageUsage)
                        .preTransform((capabilities.supportedTransforms() & VK_SURFACE_TRANSFORM_IDENTITY_BIT_KHR) != 0
                                ? VK_SURFACE_TRANSFORM_IDENTITY_BIT_KHR : capabilities.currentTransform())
                        .compositeAlpha(VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR)
                        .presentMode(presentMode)
                        .clipped(true)
                        .oldSwapchain(VK_NULL_HANDLE);

                if (graphicsQueueFamily != presentQueueFamily) {
                    createInfo.imageSharingMode(VK_SHARING_MODE_CONCURRENT)
                            .pQueueFamilyIndices(stack.ints(graphicsQueueFamily, presentQueueFamily));
                } else {
                    createInfo.imageSharingMode(VK_SHARING_MODE_EXCLUSIVE);
                }

                LongBuffer pSwapchain = stack.mallocLong(1);
                check(vkCreateSwapchainKHR(device, createInfo, null, pSwapchain), "Could not create Vulkan swapchain");
                swapchain = pSwapchain.get(0);
                swapchainImageFormat = surfaceFormat.format();
                this.surfaceFormat = toCommonFormat(swapchainImageFormat);
                width = extent.width();
                height = extent.height();

                createSwapchainImages(stack);
                createImageViews(stack);
                createDepthResources(stack);
                createRenderPasses(stack);
                createFramebuffers(stack);
                createCommandBuffers(stack);
            }
        }

        private VkSurfaceFormatKHR chooseSurfaceFormat(MemoryStack stack) {
            IntBuffer count = stack.ints(0);
            check(vkGetPhysicalDeviceSurfaceFormatsKHR(physicalDevice, surface, count, null),
                    "Could not query Vulkan surface formats");
            VkSurfaceFormatKHR.Buffer formats = VkSurfaceFormatKHR.malloc(count.get(0), stack);
            check(vkGetPhysicalDeviceSurfaceFormatsKHR(physicalDevice, surface, count, formats),
                    "Could not query Vulkan surface formats");

            VkSurfaceFormatKHR fallback = formats.get(0);
            for (int i = 0; i < formats.capacity(); i++) {
                VkSurfaceFormatKHR format = formats.get(i);
                if (format.format() == VK_FORMAT_B8G8R8A8_UNORM
                        && format.colorSpace() == VK_COLOR_SPACE_SRGB_NONLINEAR_KHR) {
                    return format;
                }
            }
            for (int i = 0; i < formats.capacity(); i++) {
                VkSurfaceFormatKHR format = formats.get(i);
                if (format.format() == VK_FORMAT_R8G8B8A8_UNORM
                        || format.format() == VK_FORMAT_B8G8R8A8_SRGB
                        || format.format() == VK_FORMAT_R8G8B8A8_SRGB) {
                    return format;
                }
            }
            return fallback;
        }

        private int choosePresentMode(MemoryStack stack) {
            IntBuffer count = stack.ints(0);
            check(vkGetPhysicalDeviceSurfacePresentModesKHR(physicalDevice, surface, count, null),
                    "Could not query Vulkan present modes");
            IntBuffer modes = stack.mallocInt(count.get(0));
            check(vkGetPhysicalDeviceSurfacePresentModesKHR(physicalDevice, surface, count, modes),
                    "Could not query Vulkan present modes");

            if (configuration.vSync()) {
                return VK_PRESENT_MODE_FIFO_KHR;
            }
            int fallback = VK_PRESENT_MODE_FIFO_KHR;
            for (int i = 0; i < modes.capacity(); i++) {
                int mode = modes.get(i);
                if (configuration.preferMailboxPresentMode() && mode == VK_PRESENT_MODE_MAILBOX_KHR) {
                    return mode;
                }
                if (mode == VK_PRESENT_MODE_IMMEDIATE_KHR) {
                    fallback = mode;
                }
            }
            return fallback;
        }

        private VkExtent2D chooseExtent(VkSurfaceCapabilitiesKHR capabilities, int requestedWidth,
                int requestedHeight, MemoryStack stack) {
            if (capabilities.currentExtent().width() != -1) {
                return capabilities.currentExtent();
            }
            VkExtent2D extent = VkExtent2D.malloc(stack);
            extent.width(clamp(requestedWidth, capabilities.minImageExtent().width(), capabilities.maxImageExtent().width()));
            extent.height(clamp(requestedHeight, capabilities.minImageExtent().height(), capabilities.maxImageExtent().height()));
            return extent;
        }

        private int clamp(int value, int min, int max) {
            return Math.max(min, Math.min(max, value));
        }

        private void createSwapchainImages(MemoryStack stack) {
            IntBuffer count = stack.ints(0);
            check(vkGetSwapchainImagesKHR(device, swapchain, count, null), "Could not query Vulkan swapchain images");
            LongBuffer images = stack.mallocLong(count.get(0));
            check(vkGetSwapchainImagesKHR(device, swapchain, count, images), "Could not query Vulkan swapchain images");
            swapchainImages = new long[count.get(0)];
            imagesInFlight = new long[count.get(0)];
            for (int i = 0; i < swapchainImages.length; i++) {
                swapchainImages[i] = images.get(i);
            }
        }

        private void createImageViews(MemoryStack stack) {
            imageViews = new long[swapchainImages.length];
            for (int i = 0; i < swapchainImages.length; i++) {
                VkImageViewCreateInfo createInfo = VkImageViewCreateInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
                        .image(swapchainImages[i])
                        .viewType(VK_IMAGE_VIEW_TYPE_2D)
                        .format(swapchainImageFormat);
                createInfo.components()
                        .r(VK10.VK_COMPONENT_SWIZZLE_IDENTITY)
                        .g(VK10.VK_COMPONENT_SWIZZLE_IDENTITY)
                        .b(VK10.VK_COMPONENT_SWIZZLE_IDENTITY)
                        .a(VK10.VK_COMPONENT_SWIZZLE_IDENTITY);
                createInfo.subresourceRange()
                        .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                        .baseMipLevel(0)
                        .levelCount(1)
                        .baseArrayLayer(0)
                        .layerCount(1);

                LongBuffer pImageView = stack.mallocLong(1);
                check(vkCreateImageView(device, createInfo, null, pImageView), "Could not create Vulkan image view");
                imageViews[i] = pImageView.get(0);
            }
        }

        private void createDepthResources(MemoryStack stack) {
            depthImages = new long[imageViews.length];
            depthImageMemories = new long[imageViews.length];
            depthImageViews = new long[imageViews.length];
            for (int i = 0; i < imageViews.length; i++) {
                VkImageCreateInfo imageInfo = VkImageCreateInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO)
                        .imageType(VK_IMAGE_TYPE_2D)
                        .format(DEPTH_FORMAT)
                        .mipLevels(1)
                        .arrayLayers(1)
                        .samples(VK_SAMPLE_COUNT_1_BIT)
                        .tiling(VK_IMAGE_TILING_OPTIMAL)
                        .usage(VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT)
                        .sharingMode(VK_SHARING_MODE_EXCLUSIVE)
                        .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);
                imageInfo.extent()
                        .width(width)
                        .height(height)
                        .depth(1);

                LongBuffer pImage = stack.mallocLong(1);
                check(vkCreateImage(device, imageInfo, null, pImage), "Could not create Vulkan depth image");
                long image = pImage.get(0);

                VkMemoryRequirements memoryRequirements = VkMemoryRequirements.malloc(stack);
                vkGetImageMemoryRequirements(device, image, memoryRequirements);
                VkMemoryAllocateInfo allocationInfo = VkMemoryAllocateInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                        .allocationSize(memoryRequirements.size())
                        .memoryTypeIndex(findMemoryType(memoryRequirements.memoryTypeBits(),
                                VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT));

                LongBuffer pMemory = stack.mallocLong(1);
                check(vkAllocateMemory(device, allocationInfo, null, pMemory),
                        "Could not allocate Vulkan depth memory");
                long memory = pMemory.get(0);
                check(vkBindImageMemory(device, image, memory, 0L), "Could not bind Vulkan depth memory");

                VkImageViewCreateInfo viewInfo = VkImageViewCreateInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
                        .image(image)
                        .viewType(VK_IMAGE_VIEW_TYPE_2D)
                        .format(DEPTH_FORMAT);
                viewInfo.subresourceRange()
                        .aspectMask(VK_IMAGE_ASPECT_DEPTH_BIT)
                        .baseMipLevel(0)
                        .levelCount(1)
                        .baseArrayLayer(0)
                        .layerCount(1);
                LongBuffer pImageView = stack.mallocLong(1);
                check(vkCreateImageView(device, viewInfo, null, pImageView),
                        "Could not create Vulkan depth image view");

                depthImages[i] = image;
                depthImageMemories[i] = memory;
                depthImageViews[i] = pImageView.get(0);
            }
        }

        private void createRenderPasses(MemoryStack stack) {
            renderPassClearStore = createRenderPass(stack, VK_ATTACHMENT_LOAD_OP_CLEAR,
                    VK_ATTACHMENT_STORE_OP_STORE, VK_IMAGE_LAYOUT_UNDEFINED);
            renderPassClearDiscard = createRenderPass(stack, VK_ATTACHMENT_LOAD_OP_CLEAR,
                    VK_ATTACHMENT_STORE_OP_DONT_CARE, VK_IMAGE_LAYOUT_UNDEFINED);
            renderPassLoadStore = createRenderPass(stack, VK_ATTACHMENT_LOAD_OP_LOAD,
                    VK_ATTACHMENT_STORE_OP_STORE, VK_IMAGE_LAYOUT_PRESENT_SRC_KHR);
            renderPassLoadDiscard = createRenderPass(stack, VK_ATTACHMENT_LOAD_OP_LOAD,
                    VK_ATTACHMENT_STORE_OP_DONT_CARE, VK_IMAGE_LAYOUT_PRESENT_SRC_KHR);
        }

        private long createRenderPass(MemoryStack stack, int loadOp, int storeOp, int initialLayout) {
            VkAttachmentDescription.Buffer attachments = VkAttachmentDescription.calloc(2, stack);
            attachments.get(0)
                    .format(swapchainImageFormat)
                    .samples(VK_SAMPLE_COUNT_1_BIT)
                    .loadOp(loadOp)
                    .storeOp(storeOp)
                    .stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                    .stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
                    .initialLayout(initialLayout)
                    .finalLayout(VK_IMAGE_LAYOUT_PRESENT_SRC_KHR);
            attachments.get(1)
                    .format(DEPTH_FORMAT)
                    .samples(VK_SAMPLE_COUNT_1_BIT)
                    .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
                    .storeOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
                    .stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                    .stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
                    .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                    .finalLayout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);

            VkAttachmentReference.Buffer colorAttachmentRef = VkAttachmentReference.calloc(1, stack)
                    .attachment(0)
                    .layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
            VkAttachmentReference depthAttachmentRef = VkAttachmentReference.calloc(stack)
                    .attachment(1)
                    .layout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);

            VkSubpassDescription.Buffer subpass = VkSubpassDescription.calloc(1, stack)
                    .pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS)
                    .colorAttachmentCount(1)
                    .pColorAttachments(colorAttachmentRef)
                    .pDepthStencilAttachment(depthAttachmentRef);

            VkSubpassDependency.Buffer dependency = VkSubpassDependency.calloc(1, stack)
                    .srcSubpass(VK_SUBPASS_EXTERNAL)
                    .dstSubpass(0)
                    .srcStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT
                            | VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT)
                    .srcAccessMask(0)
                    .dstStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT
                            | VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT)
                    .dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT
                            | VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT);

            VkRenderPassCreateInfo renderPassInfo = VkRenderPassCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO)
                    .pAttachments(attachments)
                    .pSubpasses(subpass)
                    .pDependencies(dependency);

            LongBuffer pRenderPass = stack.mallocLong(1);
            check(vkCreateRenderPass(device, renderPassInfo, null, pRenderPass), "Could not create Vulkan render pass");
            return pRenderPass.get(0);
        }

        private void createFramebuffers(MemoryStack stack) {
            framebuffers = new long[imageViews.length];
            for (int i = 0; i < imageViews.length; i++) {
                LongBuffer attachments = stack.longs(imageViews[i], depthImageViews[i]);
                VkFramebufferCreateInfo framebufferInfo = VkFramebufferCreateInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO)
                        .renderPass(renderPassClearStore)
                        .pAttachments(attachments)
                        .width(width)
                        .height(height)
                        .layers(1);
                LongBuffer pFramebuffer = stack.mallocLong(1);
                check(vkCreateFramebuffer(device, framebufferInfo, null, pFramebuffer),
                        "Could not create Vulkan framebuffer");
                framebuffers[i] = pFramebuffer.get(0);
            }
        }

        private void createCommandBuffers(MemoryStack stack) {
            int framesInFlight = configuration.framesInFlight();
            if (commandBuffers != null) {
                PointerBuffer oldBuffers = stack.mallocPointer(commandBuffers.length);
                for (VkCommandBuffer commandBuffer : commandBuffers) {
                    oldBuffers.put(commandBuffer.address());
                }
                oldBuffers.flip();
                vkFreeCommandBuffers(device, commandPool, oldBuffers);
            }
            commandBuffers = new VkCommandBuffer[framesInFlight];
            VkCommandBufferAllocateInfo allocateInfo = VkCommandBufferAllocateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
                    .commandPool(commandPool)
                    .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                    .commandBufferCount(framesInFlight);
            PointerBuffer pCommandBuffers = stack.mallocPointer(framesInFlight);
            check(vkAllocateCommandBuffers(device, allocateInfo, pCommandBuffers), "Could not allocate Vulkan command buffers");
            for (int i = 0; i < framesInFlight; i++) {
                commandBuffers[i] = new VkCommandBuffer(pCommandBuffers.get(i), device);
            }
        }

        private void createSyncObjects() {
            int framesInFlight = configuration.framesInFlight();
            imageAvailableSemaphores = new long[framesInFlight];
            renderFinishedSemaphores = new long[framesInFlight];
            inFlightFences = new long[framesInFlight];
            @SuppressWarnings("unchecked")
            ArrayList<VulkanUniformAllocation>[] uniformAllocations =
                    (ArrayList<VulkanUniformAllocation>[])new ArrayList[framesInFlight];
            uniformAllocationsInFlight = uniformAllocations;
            @SuppressWarnings("unchecked")
            ArrayList<VulkanBufferAllocation>[] retiredBuffers =
                    (ArrayList<VulkanBufferAllocation>[])new ArrayList[framesInFlight];
            retiredBuffersInFlight = retiredBuffers;
            for (int i = 0; i < framesInFlight; i++) {
                uniformAllocationsInFlight[i] = new ArrayList<VulkanUniformAllocation>();
                retiredBuffersInFlight[i] = new ArrayList<VulkanBufferAllocation>();
            }
            try (MemoryStack stack = stackPush()) {
                VkSemaphoreCreateInfo semaphoreInfo = VkSemaphoreCreateInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO);
                VkFenceCreateInfo fenceInfo = VkFenceCreateInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_FENCE_CREATE_INFO)
                        .flags(VK_FENCE_CREATE_SIGNALED_BIT);
                LongBuffer pSemaphore = stack.mallocLong(1);
                LongBuffer pFence = stack.mallocLong(1);
                for (int i = 0; i < framesInFlight; i++) {
                    check(vkCreateSemaphore(device, semaphoreInfo, null, pSemaphore),
                            "Could not create Vulkan image-available semaphore");
                    imageAvailableSemaphores[i] = pSemaphore.get(0);
                    pSemaphore.clear();
                    check(vkCreateSemaphore(device, semaphoreInfo, null, pSemaphore),
                            "Could not create Vulkan render-finished semaphore");
                    renderFinishedSemaphores[i] = pSemaphore.get(0);
                    pSemaphore.clear();
                    check(vkCreateFence(device, fenceInfo, null, pFence), "Could not create Vulkan frame fence");
                    inFlightFences[i] = pFence.get(0);
                    pFence.clear();
                }
                registerFrameFences(device, inFlightFences);
            }
        }

        @Override
        public void resize(int framebufferWidth, int framebufferHeight) {
            if (disposed || framebufferWidth <= 0 || framebufferHeight <= 0) {
                return;
            }
            if (frameStarted) {
                pendingResize = true;
                pendingResizeWidth = framebufferWidth;
                pendingResizeHeight = framebufferHeight;
                return;
            }
            recreateSwapchain(framebufferWidth, framebufferHeight);
        }

        private void recreateSwapchain(int framebufferWidth, int framebufferHeight) {
            if (framebufferWidth <= 0 || framebufferHeight <= 0 || device == null) {
                return;
            }
            waitForAllFences(device, inFlightFences, READBACK_FENCE_TIMEOUT_NS, "swapchain recreation");
            cleanupSwapchain();
            createSwapchain(framebufferWidth, framebufferHeight);
        }

        @Override
        public void processEvents() {
        }

        @Override
        public boolean beginFrame() {
            if (disposed || swapchain == VK_NULL_HANDLE || width <= 0 || height <= 0) {
                return false;
            }
            if (frameStarted) {
                throw new FdxException("Vulkan frame is already started");
            }

            try (MemoryStack stack = stackPush()) {
                if (!waitForFenceOrSkipFrame(device, inFlightFences[currentFrameSlot], FRAME_FENCE_TIMEOUT_NS,
                        "frame fence")) {
                    return false;
                }
                releaseUniformAllocations(currentFrameSlot);
                releaseRetiredBuffers(currentFrameSlot);

                IntBuffer pImageIndex = stack.ints(0);
                int acquireResult = vkAcquireNextImageKHR(device, swapchain, SWAPCHAIN_ACQUIRE_TIMEOUT_NS,
                        imageAvailableSemaphores[currentFrameSlot], VK_NULL_HANDLE, pImageIndex);
                if (acquireResult == VK_TIMEOUT || acquireResult == VK_NOT_READY) {
                    return false;
                }
                if (acquireResult == VK_ERROR_OUT_OF_DATE_KHR) {
                    applyPendingOrCurrentResize();
                    return false;
                }
                if (acquireResult != VK_SUCCESS && acquireResult != VK_SUBOPTIMAL_KHR) {
                    throw new FdxException("Could not acquire Vulkan swapchain image: " + acquireResult);
                }

                currentImageIndex = pImageIndex.get(0);
                if (imagesInFlight[currentImageIndex] != VK_NULL_HANDLE) {
                    if (!waitForFenceOrSkipFrame(device, imagesInFlight[currentImageIndex], READBACK_FENCE_TIMEOUT_NS,
                            "image fence")) {
                        throw new FdxException("Timed out waiting for Vulkan image fence");
                    }
                }
                imagesInFlight[currentImageIndex] = inFlightFences[currentFrameSlot];

                check(vkResetFences(device, inFlightFences[currentFrameSlot]), "Could not reset Vulkan frame fence");
                check(vkResetCommandBuffer(commandBuffers[currentFrameSlot], 0), "Could not reset Vulkan command buffer");

                VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)
                        .flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);
                check(vkBeginCommandBuffer(commandBuffers[currentFrameSlot], beginInfo),
                        "Could not begin Vulkan command buffer");

                frameStarted = true;
                return true;
            }
        }

        @Override
        public void endFrame() {
            if (!frameStarted) {
                return;
            }
            submitCurrentFrame(false);
        }

        ByteBuffer readPixelsRgba8() {
            if (!frameStarted) {
                throw new FdxException("Cannot read pixels before beginFrame()");
            }
            if (!swapchainTransferSrcSupported) {
                throw new FdxException("Vulkan swapchain does not support transfer-source readback");
            }
            int readWidth = width;
            int readHeight = height;
            int byteCount = readWidth * readHeight * 4;
            VulkanBufferAllocation readback = createHostVisibleBuffer(byteCount, VK_BUFFER_USAGE_TRANSFER_DST_BIT);
            try {
                recordReadbackCopy(commandBuffers[currentFrameSlot], swapchainImages[currentImageIndex],
                        readback.buffer(), readWidth, readHeight);
                submitCurrentFrame(true);
                return readMappedRgba8(readback, readWidth, readHeight);
            } finally {
                readback.dispose(device);
            }
        }

        private void recordReadbackCopy(VkCommandBuffer commandBuffer, long image, long buffer,
                int readWidth, int readHeight) {
            transitionSwapchainImageLayout(commandBuffer, image, VK_IMAGE_LAYOUT_PRESENT_SRC_KHR,
                    VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL);
            try (MemoryStack stack = stackPush()) {
                VkBufferImageCopy.Buffer region = VkBufferImageCopy.calloc(1, stack)
                        .bufferOffset(0L)
                        .bufferRowLength(0)
                        .bufferImageHeight(0);
                region.imageSubresource()
                        .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                        .mipLevel(0)
                        .baseArrayLayer(0)
                        .layerCount(1);
                region.imageOffset().set(0, 0, 0);
                region.imageExtent().set(readWidth, readHeight, 1);
                vkCmdCopyImageToBuffer(commandBuffer, image, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, buffer, region);
            }
            transitionSwapchainImageLayout(commandBuffer, image, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                    VK_IMAGE_LAYOUT_PRESENT_SRC_KHR);
        }

        private void transitionSwapchainImageLayout(VkCommandBuffer commandBuffer, long image,
                int oldLayout, int newLayout) {
            try (MemoryStack stack = stackPush()) {
                VkImageMemoryBarrier.Buffer barrier = VkImageMemoryBarrier.calloc(1, stack)
                        .sType(VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
                        .oldLayout(oldLayout)
                        .newLayout(newLayout)
                        .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                        .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                        .image(image);
                barrier.subresourceRange()
                        .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                        .baseMipLevel(0)
                        .levelCount(1)
                        .baseArrayLayer(0)
                        .layerCount(1);

                int sourceStage;
                int destinationStage;
                if (oldLayout == VK_IMAGE_LAYOUT_PRESENT_SRC_KHR
                        && newLayout == VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL) {
                    barrier.srcAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)
                            .dstAccessMask(VK_ACCESS_TRANSFER_READ_BIT);
                    sourceStage = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
                    destinationStage = VK_PIPELINE_STAGE_TRANSFER_BIT;
                } else if (oldLayout == VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL
                        && newLayout == VK_IMAGE_LAYOUT_PRESENT_SRC_KHR) {
                    barrier.srcAccessMask(VK_ACCESS_TRANSFER_READ_BIT)
                            .dstAccessMask(0);
                    sourceStage = VK_PIPELINE_STAGE_TRANSFER_BIT;
                    destinationStage = VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT;
                } else {
                    throw new FdxException("Unsupported Vulkan swapchain layout transition: "
                            + oldLayout + " -> " + newLayout);
                }

                vkCmdPipelineBarrier(commandBuffer, sourceStage, destinationStage, 0,
                        null, null, barrier);
            }
        }

        private ByteBuffer readMappedRgba8(VulkanBufferAllocation readback, int readWidth, int readHeight) {
            int byteCount = readWidth * readHeight * 4;
            try (MemoryStack stack = stackPush()) {
                PointerBuffer mappedPointer = stack.mallocPointer(1);
                check(vkMapMemory(device, readback.memory(), 0L, byteCount, 0, mappedPointer),
                        "Could not map Vulkan readback buffer");
                try {
                    ByteBuffer source = memByteBuffer(mappedPointer.get(0), byteCount);
                    ByteBuffer pixels = ByteBuffer.allocateDirect(byteCount).order(ByteOrder.nativeOrder());
                    boolean bgra = swapchainImageFormat == VK_FORMAT_B8G8R8A8_UNORM
                            || swapchainImageFormat == VK_FORMAT_B8G8R8A8_SRGB;
                    for (int y = readHeight - 1; y >= 0; y--) {
                        int rowOffset = y * readWidth * 4;
                        for (int x = 0; x < readWidth; x++) {
                            int pixelOffset = rowOffset + x * 4;
                            byte first = source.get(pixelOffset);
                            byte second = source.get(pixelOffset + 1);
                            byte third = source.get(pixelOffset + 2);
                            byte alpha = source.get(pixelOffset + 3);
                            if (bgra) {
                                pixels.put(third).put(second).put(first).put(alpha);
                            } else {
                                pixels.put(first).put(second).put(third).put(alpha);
                            }
                        }
                    }
                    pixels.flip();
                    return pixels;
                } finally {
                    vkUnmapMemory(device, readback.memory());
                }
            }
        }

        private void submitCurrentFrame(boolean waitForCompletion) {
            frameStarted = false;

            try (MemoryStack stack = stackPush()) {
                VkCommandBuffer commandBuffer = commandBuffers[currentFrameSlot];
                check(vkEndCommandBuffer(commandBuffer), "Could not end Vulkan command buffer");

                VkSubmitInfo submitInfo = VkSubmitInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
                        .pWaitSemaphores(stack.longs(imageAvailableSemaphores[currentFrameSlot]))
                        .pWaitDstStageMask(stack.ints(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT))
                        .pCommandBuffers(stack.pointers(commandBuffer.address()))
                        .pSignalSemaphores(stack.longs(renderFinishedSemaphores[currentFrameSlot]));
                check(vkQueueSubmit(graphicsQueue, submitInfo, inFlightFences[currentFrameSlot]),
                        "Could not submit Vulkan command buffer");
                if (waitForCompletion) {
                    if (!waitForFenceOrSkipFrame(device, inFlightFences[currentFrameSlot], READBACK_FENCE_TIMEOUT_NS,
                            "readback fence")) {
                        throw new FdxException("Timed out waiting for Vulkan readback frame fence");
                    }
                }

                VkPresentInfoKHR presentInfo = VkPresentInfoKHR.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_PRESENT_INFO_KHR)
                        .pWaitSemaphores(stack.longs(renderFinishedSemaphores[currentFrameSlot]))
                        .swapchainCount(1)
                        .pSwapchains(stack.longs(swapchain))
                        .pImageIndices(stack.ints(currentImageIndex));
                int presentResult = vkQueuePresentKHR(presentQueue, presentInfo);
                currentImageIndex = -1;
                resetUsedBufferHandles();
                currentFrameSlot = (currentFrameSlot + 1) % configuration.framesInFlight();

                if (presentResult == VK_ERROR_OUT_OF_DATE_KHR || presentResult == VK_SUBOPTIMAL_KHR || pendingResize) {
                    applyPendingOrCurrentResize();
                } else if (presentResult != VK_SUCCESS) {
                    throw new FdxException("Could not present Vulkan swapchain image: " + presentResult);
                }
            }
        }

        private void applyPendingOrCurrentResize() {
            int resizeWidth = pendingResize ? pendingResizeWidth : width;
            int resizeHeight = pendingResize ? pendingResizeHeight : height;
            pendingResize = false;
            recreateSwapchain(resizeWidth, resizeHeight);
        }

        @Override
        public GraphicsDevice device() {
            return graphicsDevice;
        }

        @Override
        public TextureFormat surfaceFormat() {
            return surfaceFormat;
        }

        @Override
        public GraphicsFrame currentFrame() {
            if (!frameStarted) {
                throw new FdxException("No Vulkan frame is active");
            }
            return currentFrame;
        }

        @Override
        public void clear(float red, float green, float blue, float alpha) {
            RenderPass pass = commandEncoder.beginRenderPass(RenderPassDescriptor
                    .color(colorAttachment, LoadOp.clear(red, green, blue, alpha), StoreOp.store())
                    .label("libfdx Vulkan clear"));
            pass.end();
        }

        VkDevice deviceHandle() {
            return device;
        }

        VkPhysicalDevice physicalDeviceHandle() {
            return physicalDevice;
        }

        VkQueue graphicsQueue() {
            return graphicsQueue;
        }

        long commandPool() {
            return commandPool;
        }

        long textureDescriptorSetLayout() {
            return textureDescriptorSetLayout;
        }

        long textureDescriptorPool() {
            return textureDescriptorPool;
        }

        long uniformDescriptorSetLayout() {
            return uniformDescriptorSetLayout;
        }

        long uniformDescriptorPool() {
            return uniformDescriptorPool;
        }

        long textureDescriptorSet(VulkanTextureHandle[] textures, int count) {
            if (count == 1) {
                return textures[0].descriptorSet();
            }
            TextureDescriptorSetKey key = new TextureDescriptorSetKey(textures, count);
            Long existing = textureDescriptorSets.get(key);
            if (existing != null) {
                return existing;
            }
            long descriptorSet = allocateTextureDescriptorSet();
            updateTextureDescriptorSet(descriptorSet, textures, count);
            textureDescriptorSets.put(key, descriptorSet);
            return descriptorSet;
        }

        private long allocateTextureDescriptorSet() {
            try (MemoryStack stack = stackPush()) {
                VkDescriptorSetAllocateInfo allocateInfo = VkDescriptorSetAllocateInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO)
                        .descriptorPool(textureDescriptorPool)
                        .pSetLayouts(stack.longs(textureDescriptorSetLayout));
                LongBuffer pDescriptorSet = stack.mallocLong(1);
                check(vkAllocateDescriptorSets(device, allocateInfo, pDescriptorSet),
                        "Could not allocate Vulkan texture descriptor set");
                return pDescriptorSet.get(0);
            }
        }

        private void updateTextureDescriptorSet(long descriptorSet, VulkanTextureHandle[] textures, int count) {
            try (MemoryStack stack = stackPush()) {
                VkDescriptorImageInfo.Buffer imageInfos = VkDescriptorImageInfo.calloc(count, stack);
                VkWriteDescriptorSet.Buffer descriptorWrites = VkWriteDescriptorSet.calloc(count, stack);
                for (int i = 0; i < count; i++) {
                    VulkanTextureHandle texture = textures[i];
                    imageInfos.get(i)
                            .imageLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                            .imageView(texture.imageView())
                            .sampler(texture.sampler());
                    imageInfos.position(i).limit(i + 1);
                    descriptorWrites.get(i)
                            .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                            .dstSet(descriptorSet)
                            .dstBinding(i)
                            .dstArrayElement(0)
                            .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                            .descriptorCount(1)
                            .pImageInfo(imageInfos);
                    imageInfos.position(0).limit(count);
                }
                vkUpdateDescriptorSets(device, descriptorWrites, null);
            }
        }

        long uniformDescriptorSet(ByteBuffer bytes) {
            VulkanUniformAllocation allocation = createUniformAllocation(bytes);
            if (uniformAllocationsInFlight != null && currentFrameSlot >= 0
                    && currentFrameSlot < uniformAllocationsInFlight.length) {
                uniformAllocationsInFlight[currentFrameSlot].add(allocation);
            }
            return allocation.descriptorSet();
        }

        private VulkanUniformAllocation createUniformAllocation(ByteBuffer bytes) {
            int byteCount = PBR_UNIFORM_BYTE_COUNT;
            VulkanBufferAllocation buffer = createHostVisibleBuffer(byteCount, VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT);
            try {
                try (MemoryStack stack = stackPush()) {
                    PointerBuffer mappedPointer = stack.mallocPointer(1);
                    check(vkMapMemory(device, buffer.memory(), 0L, byteCount, 0, mappedPointer),
                            "Could not map Vulkan uniform buffer");
                    ByteBuffer mapped = memByteBuffer(mappedPointer.get(0), byteCount);
                    ByteBuffer source = bytes.duplicate();
                    source.position(0);
                    source.limit(byteCount);
                    mapped.put(source);
                    vkUnmapMemory(device, buffer.memory());
                }

                long descriptorSet = allocateUniformDescriptorSet();
                updateUniformDescriptorSet(descriptorSet, buffer.buffer(), byteCount);
                return new VulkanUniformAllocation(buffer, descriptorSet);
            } catch (RuntimeException error) {
                buffer.dispose(device);
                throw error;
            }
        }

        private VulkanBufferAllocation createHostVisibleBuffer(int size, int usage) {
            try (MemoryStack stack = stackPush()) {
                VkBufferCreateInfo bufferInfo = VkBufferCreateInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
                        .size(size)
                        .usage(usage)
                        .sharingMode(VK_SHARING_MODE_EXCLUSIVE);

                LongBuffer pBuffer = stack.mallocLong(1);
                check(vkCreateBuffer(device, bufferInfo, null, pBuffer), "Could not create Vulkan uniform buffer");
                long nativeBuffer = pBuffer.get(0);

                VkMemoryRequirements memoryRequirements = VkMemoryRequirements.malloc(stack);
                vkGetBufferMemoryRequirements(device, nativeBuffer, memoryRequirements);

                VkMemoryAllocateInfo allocationInfo = VkMemoryAllocateInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                        .allocationSize(memoryRequirements.size())
                        .memoryTypeIndex(findMemoryType(memoryRequirements.memoryTypeBits(),
                                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT));

                LongBuffer pMemory = stack.mallocLong(1);
                int result = vkAllocateMemory(device, allocationInfo, null, pMemory);
                if (result != VK_SUCCESS) {
                    vkDestroyBuffer(device, nativeBuffer, null);
                    throw new FdxException("Could not allocate Vulkan uniform buffer memory: " + result);
                }
                long memory = pMemory.get(0);
                result = vkBindBufferMemory(device, nativeBuffer, memory, 0L);
                if (result != VK_SUCCESS) {
                    vkFreeMemory(device, memory, null);
                    vkDestroyBuffer(device, nativeBuffer, null);
                    throw new FdxException("Could not bind Vulkan uniform buffer memory: " + result);
                }
                return new VulkanBufferAllocation(nativeBuffer, memory);
            }
        }

        private long allocateUniformDescriptorSet() {
            try (MemoryStack stack = stackPush()) {
                VkDescriptorSetAllocateInfo allocateInfo = VkDescriptorSetAllocateInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO)
                        .descriptorPool(uniformDescriptorPool)
                        .pSetLayouts(stack.longs(uniformDescriptorSetLayout));
                LongBuffer pDescriptorSet = stack.mallocLong(1);
                check(vkAllocateDescriptorSets(device, allocateInfo, pDescriptorSet),
                        "Could not allocate Vulkan uniform descriptor set");
                return pDescriptorSet.get(0);
            }
        }

        private void updateUniformDescriptorSet(long descriptorSet, long buffer, int byteCount) {
            try (MemoryStack stack = stackPush()) {
                VkDescriptorBufferInfo.Buffer bufferInfo = VkDescriptorBufferInfo.calloc(1, stack)
                        .buffer(buffer)
                        .offset(0L)
                        .range(byteCount);
                VkWriteDescriptorSet.Buffer descriptorWrite = VkWriteDescriptorSet.calloc(1, stack)
                        .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                        .dstSet(descriptorSet)
                        .dstBinding(0)
                        .dstArrayElement(0)
                        .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                        .descriptorCount(1)
                        .pBufferInfo(bufferInfo);
                vkUpdateDescriptorSets(device, descriptorWrite, null);
            }
        }

        private void releaseUniformAllocations(int frameSlot) {
            if (uniformAllocationsInFlight == null || frameSlot < 0 || frameSlot >= uniformAllocationsInFlight.length) {
                return;
            }
            ArrayList<VulkanUniformAllocation> allocations = uniformAllocationsInFlight[frameSlot];
            for (int i = 0; i < allocations.size(); i++) {
                allocations.get(i).dispose(device, uniformDescriptorPool);
            }
            allocations.clear();
        }

        private void releaseAllUniformAllocations() {
            if (uniformAllocationsInFlight == null) {
                return;
            }
            for (int i = 0; i < uniformAllocationsInFlight.length; i++) {
                releaseUniformAllocations(i);
            }
            uniformAllocationsInFlight = null;
        }

        void retireBufferAfterFrame(VulkanBufferAllocation allocation) {
            if (allocation == null) {
                return;
            }
            if (retiredBuffersInFlight != null && currentFrameSlot >= 0
                    && currentFrameSlot < retiredBuffersInFlight.length) {
                retiredBuffersInFlight[currentFrameSlot].add(allocation);
                return;
            }
            allocation.dispose(device);
        }

        void markBufferUsedByRecordedCommand(VulkanBufferHandle buffer) {
            if (buffer != null && !buffer.usedByRecordedCommand()) {
                buffer.markUsedByRecordedCommand();
                usedBufferHandles.add(buffer);
            }
        }

        private void resetUsedBufferHandles() {
            for (int i = 0; i < usedBufferHandles.size(); i++) {
                usedBufferHandles.get(i).resetUsedByRecordedCommand();
            }
            usedBufferHandles.clear();
        }

        private void releaseRetiredBuffers(int frameSlot) {
            if (retiredBuffersInFlight == null || frameSlot < 0 || frameSlot >= retiredBuffersInFlight.length) {
                return;
            }
            ArrayList<VulkanBufferAllocation> buffers = retiredBuffersInFlight[frameSlot];
            for (int i = 0; i < buffers.size(); i++) {
                buffers.get(i).dispose(device);
            }
            buffers.clear();
        }

        private void releaseAllRetiredBuffers() {
            if (retiredBuffersInFlight == null) {
                return;
            }
            for (int i = 0; i < retiredBuffersInFlight.length; i++) {
                releaseRetiredBuffers(i);
            }
            retiredBuffersInFlight = null;
        }

        long pipelineRenderPass() {
            return renderPassClearStore;
        }

        VkCommandBuffer commandBuffer() {
            return commandBuffers[currentFrameSlot];
        }

        long framebuffer() {
            return framebuffers[currentImageIndex];
        }

        long currentImageView() {
            return imageViews[currentImageIndex];
        }

        long selectRenderPass(LoadOp loadOp, StoreOp storeOp) {
            boolean clear = loadOp != null && loadOp.isClear();
            boolean store = storeOp == null || storeOp.isStore();
            if (clear && store) {
                return renderPassClearStore;
            }
            if (clear) {
                return renderPassClearDiscard;
            }
            if (store) {
                return renderPassLoadStore;
            }
            return renderPassLoadDiscard;
        }

        boolean frameStarted() {
            return frameStarted;
        }

        int width() {
            return width;
        }

        int height() {
            return height;
        }

        private int findMemoryType(int typeFilter, int properties) {
            try (MemoryStack stack = stackPush()) {
                VkPhysicalDeviceMemoryProperties memoryProperties = VkPhysicalDeviceMemoryProperties.malloc(stack);
                vkGetPhysicalDeviceMemoryProperties(physicalDevice, memoryProperties);
                for (int i = 0; i < memoryProperties.memoryTypeCount(); i++) {
                    if ((typeFilter & (1 << i)) != 0
                            && (memoryProperties.memoryTypes(i).propertyFlags() & properties) == properties) {
                        return i;
                    }
                }
            }
            throw new FdxException("Could not find suitable Vulkan memory type");
        }

        @Override
        public ProviderId providerId() {
            return ID;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T as() {
            return (T) this;
        }

        @Override
        public void dispose() {
            if (disposed) {
                return;
            }
            disposed = true;
            if (device != null) {
                unregisterFrameFences(device);
                waitForAllFences(device, inFlightFences, DEVICE_DISPOSE_FENCE_TIMEOUT_NS, "context dispose");
            }
            cleanupSwapchain();
            if (device != null) {
                releaseAllUniformAllocations();
                releaseAllRetiredBuffers();
                destroySyncObjects();
                if (commandPool != VK_NULL_HANDLE) {
                    vkDestroyCommandPool(device, commandPool, null);
                    commandPool = VK_NULL_HANDLE;
                }
                if (uniformDescriptorPool != VK_NULL_HANDLE) {
                    vkDestroyDescriptorPool(device, uniformDescriptorPool, null);
                    uniformDescriptorPool = VK_NULL_HANDLE;
                }
                if (uniformDescriptorSetLayout != VK_NULL_HANDLE) {
                    vkDestroyDescriptorSetLayout(device, uniformDescriptorSetLayout, null);
                    uniformDescriptorSetLayout = VK_NULL_HANDLE;
                }
                if (textureDescriptorPool != VK_NULL_HANDLE) {
                    vkDestroyDescriptorPool(device, textureDescriptorPool, null);
                    textureDescriptorPool = VK_NULL_HANDLE;
                }
                if (textureDescriptorSetLayout != VK_NULL_HANDLE) {
                    vkDestroyDescriptorSetLayout(device, textureDescriptorSetLayout, null);
                    textureDescriptorSetLayout = VK_NULL_HANDLE;
                }
                vkDestroyDevice(device, null);
                device = null;
            }
            if (surface != VK_NULL_HANDLE) {
                vkDestroySurfaceKHR(instance, surface, null);
                surface = VK_NULL_HANDLE;
            }
            if (instance != null) {
                vkDestroyInstance(instance, null);
                instance = null;
            }
        }

        private void cleanupSwapchain() {
            if (device == null) {
                return;
            }
            for (long framebuffer : framebuffers) {
                if (framebuffer != VK_NULL_HANDLE) {
                    vkDestroyFramebuffer(device, framebuffer, null);
                }
            }
            framebuffers = new long[0];
            destroyRenderPass(renderPassClearStore);
            destroyRenderPass(renderPassClearDiscard);
            destroyRenderPass(renderPassLoadStore);
            destroyRenderPass(renderPassLoadDiscard);
            renderPassClearStore = VK_NULL_HANDLE;
            renderPassClearDiscard = VK_NULL_HANDLE;
            renderPassLoadStore = VK_NULL_HANDLE;
            renderPassLoadDiscard = VK_NULL_HANDLE;
            destroyDepthResources();
            for (long imageView : imageViews) {
                if (imageView != VK_NULL_HANDLE) {
                    vkDestroyImageView(device, imageView, null);
                }
            }
            imageViews = new long[0];
            swapchainImages = new long[0];
            imagesInFlight = new long[0];
            if (swapchain != VK_NULL_HANDLE) {
                vkDestroySwapchainKHR(device, swapchain, null);
                swapchain = VK_NULL_HANDLE;
            }
        }

        private void destroyDepthResources() {
            for (long imageView : depthImageViews) {
                if (imageView != VK_NULL_HANDLE) {
                    vkDestroyImageView(device, imageView, null);
                }
            }
            for (long image : depthImages) {
                if (image != VK_NULL_HANDLE) {
                    vkDestroyImage(device, image, null);
                }
            }
            for (long memory : depthImageMemories) {
                if (memory != VK_NULL_HANDLE) {
                    vkFreeMemory(device, memory, null);
                }
            }
            depthImageViews = new long[0];
            depthImages = new long[0];
            depthImageMemories = new long[0];
        }

        private void destroySyncObjects() {
            if (renderFinishedSemaphores != null) {
                for (long semaphore : renderFinishedSemaphores) {
                    if (semaphore != VK_NULL_HANDLE) {
                        vkDestroySemaphore(device, semaphore, null);
                    }
                }
                renderFinishedSemaphores = null;
            }
            if (imageAvailableSemaphores != null) {
                for (long semaphore : imageAvailableSemaphores) {
                    if (semaphore != VK_NULL_HANDLE) {
                        vkDestroySemaphore(device, semaphore, null);
                    }
                }
                imageAvailableSemaphores = null;
            }
            if (inFlightFences != null) {
                for (long fence : inFlightFences) {
                    if (fence != VK_NULL_HANDLE) {
                        vkDestroyFence(device, fence, null);
                    }
                }
                inFlightFences = null;
            }
        }

        private void destroyRenderPass(long renderPass) {
            if (renderPass != VK_NULL_HANDLE) {
                vkDestroyRenderPass(device, renderPass, null);
            }
        }

        @Override
        public boolean isDisposed() {
            return disposed;
        }
    }

    private static final class TextureDescriptorSetKey {
        private final long[] handles;
        private final int hashCode;

        TextureDescriptorSetKey(VulkanTextureHandle[] textures, int count) {
            handles = new long[count * 2];
            for (int i = 0; i < count; i++) {
                handles[i * 2] = textures[i].imageView();
                handles[i * 2 + 1] = textures[i].sampler();
            }
            hashCode = Arrays.hashCode(handles);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof TextureDescriptorSetKey
                    && Arrays.equals(handles, ((TextureDescriptorSetKey)other).handles);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }

    private static final class VulkanGraphicsDevice implements GraphicsDevice {
        private final VulkanContext context;

        VulkanGraphicsDevice(VulkanContext context) {
            this.context = context;
        }

        @Override
        public Buffer createBuffer(BufferDescriptor descriptor) {
            if (descriptor == null) {
                throw new FdxException("BufferDescriptor cannot be null");
            }
            if (descriptor.usage() != BufferUsage.VERTEX && descriptor.usage() != BufferUsage.INDEX) {
                throw new FdxException("Vulkan currently supports vertex and index buffers only");
            }
            int usage = descriptor.usage() == BufferUsage.INDEX
                    ? VK_BUFFER_USAGE_INDEX_BUFFER_BIT
                    : VK_BUFFER_USAGE_VERTEX_BUFFER_BIT;
            if (!descriptor.dynamic()) {
                VulkanBufferAllocation allocation = createNativeBuffer(descriptor.size(),
                        usage | VK_BUFFER_USAGE_TRANSFER_DST_BIT, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
                return new VulkanBufferHandle(context.deviceHandle(), allocation.buffer(), allocation.memory(), null,
                        descriptor.size(), usage, descriptor.usage());
            }

            VulkanBufferAllocation allocation = createMappedBuffer(descriptor.size(), usage);
            return new VulkanBufferHandle(context.deviceHandle(), allocation.buffer(), allocation.memory(),
                    allocation.mappedMemory(), descriptor.size(), usage, descriptor.usage());
        }

        private VulkanBufferAllocation createMappedBuffer(int size, int usage) {
            try (MemoryStack stack = stackPush()) {
                VkBufferCreateInfo bufferInfo = VkBufferCreateInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
                        .size(size)
                        .usage(usage)
                        .sharingMode(VK_SHARING_MODE_EXCLUSIVE);

                LongBuffer pBuffer = stack.mallocLong(1);
                check(vkCreateBuffer(context.deviceHandle(), bufferInfo, null, pBuffer),
                        "Could not create Vulkan buffer");
                long nativeBuffer = pBuffer.get(0);

                VkMemoryRequirements memoryRequirements = VkMemoryRequirements.malloc(stack);
                vkGetBufferMemoryRequirements(context.deviceHandle(), nativeBuffer, memoryRequirements);

                VkMemoryAllocateInfo allocationInfo = VkMemoryAllocateInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                        .allocationSize(memoryRequirements.size())
                        .memoryTypeIndex(findMemoryType(memoryRequirements.memoryTypeBits(),
                                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT));

                LongBuffer pMemory = stack.mallocLong(1);
                int result = vkAllocateMemory(context.deviceHandle(), allocationInfo, null, pMemory);
                if (result != VK_SUCCESS) {
                    vkDestroyBuffer(context.deviceHandle(), nativeBuffer, null);
                    throw new FdxException("Could not allocate Vulkan buffer memory: " + result);
                }
                long memory = pMemory.get(0);
                result = vkBindBufferMemory(context.deviceHandle(), nativeBuffer, memory, 0L);
                if (result != VK_SUCCESS) {
                    vkFreeMemory(context.deviceHandle(), memory, null);
                    vkDestroyBuffer(context.deviceHandle(), nativeBuffer, null);
                    throw new FdxException("Could not bind Vulkan buffer memory: " + result);
                }

                PointerBuffer mappedPointer = stack.mallocPointer(1);
                result = vkMapMemory(context.deviceHandle(), memory, 0L, size, 0, mappedPointer);
                if (result != VK_SUCCESS) {
                    vkDestroyBuffer(context.deviceHandle(), nativeBuffer, null);
                    vkFreeMemory(context.deviceHandle(), memory, null);
                    throw new FdxException("Could not map Vulkan buffer memory: " + result);
                }
                ByteBuffer mappedMemory = memByteBuffer(mappedPointer.get(0), size)
                        .order(ByteOrder.nativeOrder());

                return new VulkanBufferAllocation(nativeBuffer, memory, mappedMemory);
            }
        }

        @Override
        public void writeBuffer(Buffer buffer, ByteBuffer data) {
            if (buffer == null) {
                throw new FdxException("Buffer cannot be null");
            }
            if (data == null) {
                throw new FdxException("Buffer data cannot be null");
            }
            VulkanBufferHandle vulkanBuffer = buffer.as();
            if (data.remaining() > vulkanBuffer.size()) {
                throw new FdxException("Buffer data is larger than the destination buffer");
            }

            ByteBuffer source = data.duplicate();
            ByteBuffer mapped = vulkanBuffer.mappedMemory();
            if (mapped != null) {
                if (vulkanBuffer.usedByRecordedCommand()) {
                    context.retireBufferAfterFrame(vulkanBuffer.allocation());
                    VulkanBufferAllocation replacement = createMappedBuffer(vulkanBuffer.size(),
                            vulkanBuffer.nativeUsage());
                    vulkanBuffer.nativeAllocation(replacement);
                    mapped = vulkanBuffer.mappedMemory();
                }
                mapped.clear();
                mapped.limit(source.remaining());
                mapped.put(source);
                mapped.clear();
                return;
            }
            writeStaticBuffer(vulkanBuffer, source);
        }

        @Override
        public Texture createTexture(TextureDescriptor descriptor) {
            if (descriptor == null) {
                throw new FdxException("TextureDescriptor cannot be null");
            }
            if (descriptor.format() != TextureFormat.RGBA8_UNORM) {
                throw new FdxException("Vulkan currently supports RGBA8_UNORM sampled textures only");
            }
            if (descriptor.usage() != TextureUsage.SAMPLED) {
                throw new FdxException("Vulkan currently supports sampled textures only");
            }

            long image = VK_NULL_HANDLE;
            long memory = VK_NULL_HANDLE;
            long imageView = VK_NULL_HANDLE;
            long sampler = VK_NULL_HANDLE;
            try (MemoryStack stack = stackPush()) {
                VkImageCreateInfo imageInfo = VkImageCreateInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO)
                        .imageType(VK_IMAGE_TYPE_2D)
                        .format(toNativeFormat(descriptor.format()))
                        .mipLevels(1)
                        .arrayLayers(1)
                        .samples(VK_SAMPLE_COUNT_1_BIT)
                        .tiling(VK_IMAGE_TILING_OPTIMAL)
                        .usage(VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_SAMPLED_BIT)
                        .sharingMode(VK_SHARING_MODE_EXCLUSIVE)
                        .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);
                imageInfo.extent()
                        .width(descriptor.width())
                        .height(descriptor.height())
                        .depth(1);

                LongBuffer pImage = stack.mallocLong(1);
                check(vkCreateImage(context.deviceHandle(), imageInfo, null, pImage),
                        "Could not create Vulkan texture image");
                image = pImage.get(0);

                VkMemoryRequirements memoryRequirements = VkMemoryRequirements.malloc(stack);
                vkGetImageMemoryRequirements(context.deviceHandle(), image, memoryRequirements);
                VkMemoryAllocateInfo allocationInfo = VkMemoryAllocateInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                        .allocationSize(memoryRequirements.size())
                        .memoryTypeIndex(findMemoryType(memoryRequirements.memoryTypeBits(),
                                VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT));

                LongBuffer pMemory = stack.mallocLong(1);
                check(vkAllocateMemory(context.deviceHandle(), allocationInfo, null, pMemory),
                        "Could not allocate Vulkan texture memory");
                memory = pMemory.get(0);
                check(vkBindImageMemory(context.deviceHandle(), image, memory, 0L),
                        "Could not bind Vulkan texture memory");

                imageView = createTextureImageView(image, descriptor.format());
                sampler = createTextureSampler(descriptor.wrapS(), descriptor.wrapT());
                long descriptorSet = allocateTextureDescriptorSet();
                VulkanTextureHandle handle = new VulkanTextureHandle(context.deviceHandle(), image, memory, imageView,
                        sampler, descriptorSet, descriptor.width(), descriptor.height(), descriptor.format(),
                        descriptor.usage());
                updateTextureDescriptorSet(handle);
                return handle;
            } catch (RuntimeException error) {
                if (sampler != VK_NULL_HANDLE) {
                    vkDestroySampler(context.deviceHandle(), sampler, null);
                }
                if (imageView != VK_NULL_HANDLE) {
                    vkDestroyImageView(context.deviceHandle(), imageView, null);
                }
                if (image != VK_NULL_HANDLE) {
                    vkDestroyImage(context.deviceHandle(), image, null);
                }
                if (memory != VK_NULL_HANDLE) {
                    vkFreeMemory(context.deviceHandle(), memory, null);
                }
                throw error;
            }
        }

        @Override
        public void writeTexture(Texture texture, ByteBuffer data) {
            if (texture == null) {
                throw new FdxException("Texture cannot be null");
            }
            if (data == null) {
                throw new FdxException("Texture data cannot be null");
            }
            VulkanTextureHandle vulkanTexture = texture.as();
            int byteCount = vulkanTexture.width() * vulkanTexture.height() * 4;
            if (data.remaining() != byteCount) {
                throw new FdxException("Vulkan texture upload expects " + byteCount + " RGBA bytes");
            }

            VulkanBufferAllocation staging = createNativeBuffer(byteCount, VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
            try {
                try (MemoryStack stack = stackPush()) {
                    PointerBuffer mappedPointer = stack.mallocPointer(1);
                    check(vkMapMemory(context.deviceHandle(), staging.memory(), 0L, byteCount, 0,
                            mappedPointer), "Could not map Vulkan texture staging memory");
                    ByteBuffer mapped = memByteBuffer(mappedPointer.get(0), byteCount);
                    mapped.put(data.duplicate());
                    vkUnmapMemory(context.deviceHandle(), staging.memory());
                }

                VkCommandBuffer commandBuffer = beginSingleTimeCommands();
                transitionImageLayout(commandBuffer, vulkanTexture, vulkanTexture.layout(),
                        VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL);
                copyBufferToImage(commandBuffer, staging.buffer(), vulkanTexture);
                transitionImageLayout(commandBuffer, vulkanTexture, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                        VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
                endSingleTimeCommands(commandBuffer);
            } finally {
                staging.dispose(context.deviceHandle());
            }
        }

        private VulkanBufferAllocation createNativeBuffer(int size, int usage, int memoryProperties) {
            try (MemoryStack stack = stackPush()) {
                VkBufferCreateInfo bufferInfo = VkBufferCreateInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
                        .size(size)
                        .usage(usage)
                        .sharingMode(VK_SHARING_MODE_EXCLUSIVE);

                LongBuffer pBuffer = stack.mallocLong(1);
                check(vkCreateBuffer(context.deviceHandle(), bufferInfo, null, pBuffer),
                        "Could not create Vulkan buffer");
                long nativeBuffer = pBuffer.get(0);

                VkMemoryRequirements memoryRequirements = VkMemoryRequirements.malloc(stack);
                vkGetBufferMemoryRequirements(context.deviceHandle(), nativeBuffer, memoryRequirements);

                VkMemoryAllocateInfo allocationInfo = VkMemoryAllocateInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                        .allocationSize(memoryRequirements.size())
                        .memoryTypeIndex(findMemoryType(memoryRequirements.memoryTypeBits(), memoryProperties));

                LongBuffer pMemory = stack.mallocLong(1);
                int result = vkAllocateMemory(context.deviceHandle(), allocationInfo, null, pMemory);
                if (result != VK_SUCCESS) {
                    vkDestroyBuffer(context.deviceHandle(), nativeBuffer, null);
                    throw new FdxException("Could not allocate Vulkan buffer memory: " + result);
                }
                long memory = pMemory.get(0);
                result = vkBindBufferMemory(context.deviceHandle(), nativeBuffer, memory, 0L);
                if (result != VK_SUCCESS) {
                    vkFreeMemory(context.deviceHandle(), memory, null);
                    vkDestroyBuffer(context.deviceHandle(), nativeBuffer, null);
                    throw new FdxException("Could not bind Vulkan buffer memory: " + result);
                }
                return new VulkanBufferAllocation(nativeBuffer, memory);
            }
        }

        private void writeStaticBuffer(VulkanBufferHandle destination, ByteBuffer source) {
            int byteCount = source.remaining();
            VulkanBufferAllocation staging = createNativeBuffer(byteCount, VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
            try {
                try (MemoryStack stack = stackPush()) {
                    PointerBuffer mappedPointer = stack.mallocPointer(1);
                    check(vkMapMemory(context.deviceHandle(), staging.memory(), 0L, byteCount, 0,
                            mappedPointer), "Could not map Vulkan static buffer staging memory");
                    ByteBuffer mapped = memByteBuffer(mappedPointer.get(0), byteCount);
                    mapped.put(source);
                    vkUnmapMemory(context.deviceHandle(), staging.memory());
                }
                copyBuffer(staging.buffer(), destination.buffer(), byteCount);
            } finally {
                staging.dispose(context.deviceHandle());
            }
        }

        private void copyBuffer(long sourceBuffer, long destinationBuffer, long byteCount) {
            VkCommandBuffer commandBuffer = beginSingleTimeCommands();
            try (MemoryStack stack = stackPush()) {
                VkBufferCopy.Buffer copyRegion = VkBufferCopy.calloc(1, stack)
                        .srcOffset(0L)
                        .dstOffset(0L)
                        .size(byteCount);
                vkCmdCopyBuffer(commandBuffer, sourceBuffer, destinationBuffer, copyRegion);
            }
            endSingleTimeCommands(commandBuffer);
        }

        private long createTextureImageView(long image, TextureFormat format) {
            try (MemoryStack stack = stackPush()) {
                VkImageViewCreateInfo viewInfo = VkImageViewCreateInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
                        .image(image)
                        .viewType(VK_IMAGE_VIEW_TYPE_2D)
                        .format(toNativeFormat(format));
                viewInfo.components()
                        .r(VK10.VK_COMPONENT_SWIZZLE_IDENTITY)
                        .g(VK10.VK_COMPONENT_SWIZZLE_IDENTITY)
                        .b(VK10.VK_COMPONENT_SWIZZLE_IDENTITY)
                        .a(VK10.VK_COMPONENT_SWIZZLE_IDENTITY);
                viewInfo.subresourceRange()
                        .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                        .baseMipLevel(0)
                        .levelCount(1)
                        .baseArrayLayer(0)
                        .layerCount(1);
                LongBuffer pImageView = stack.mallocLong(1);
                check(vkCreateImageView(context.deviceHandle(), viewInfo, null, pImageView),
                        "Could not create Vulkan texture image view");
                return pImageView.get(0);
            }
        }

        private long createTextureSampler(TextureWrap wrapS, TextureWrap wrapT) {
            try (MemoryStack stack = stackPush()) {
                VkSamplerCreateInfo samplerInfo = VkSamplerCreateInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO)
                        .magFilter(VK_FILTER_LINEAR)
                        .minFilter(VK_FILTER_LINEAR)
                        .addressModeU(toNativeAddressMode(wrapS))
                        .addressModeV(toNativeAddressMode(wrapT))
                        .addressModeW(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                        .anisotropyEnable(false)
                        .maxAnisotropy(1.0f)
                        .borderColor(VK_BORDER_COLOR_FLOAT_OPAQUE_BLACK)
                        .unnormalizedCoordinates(false)
                        .compareEnable(false)
                        .mipmapMode(VK_SAMPLER_MIPMAP_MODE_LINEAR)
                        .mipLodBias(0.0f)
                        .minLod(0.0f)
                        .maxLod(0.0f);
                LongBuffer pSampler = stack.mallocLong(1);
                check(vkCreateSampler(context.deviceHandle(), samplerInfo, null, pSampler),
                        "Could not create Vulkan texture sampler");
                return pSampler.get(0);
            }
        }

        private int toNativeAddressMode(TextureWrap wrap) {
            if (wrap == TextureWrap.REPEAT) {
                return VK_SAMPLER_ADDRESS_MODE_REPEAT;
            }
            if (wrap == TextureWrap.MIRRORED_REPEAT) {
                return VK_SAMPLER_ADDRESS_MODE_MIRRORED_REPEAT;
            }
            return VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
        }

        private long allocateTextureDescriptorSet() {
            try (MemoryStack stack = stackPush()) {
                VkDescriptorSetAllocateInfo allocateInfo = VkDescriptorSetAllocateInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO)
                        .descriptorPool(context.textureDescriptorPool())
                        .pSetLayouts(stack.longs(context.textureDescriptorSetLayout()));
                LongBuffer pDescriptorSet = stack.mallocLong(1);
                check(vkAllocateDescriptorSets(context.deviceHandle(), allocateInfo, pDescriptorSet),
                        "Could not allocate Vulkan texture descriptor set");
                return pDescriptorSet.get(0);
            }
        }

        private void updateTextureDescriptorSet(VulkanTextureHandle texture) {
            try (MemoryStack stack = stackPush()) {
                VkDescriptorImageInfo.Buffer imageInfo = VkDescriptorImageInfo.calloc(1, stack)
                        .imageLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                        .imageView(texture.imageView())
                        .sampler(texture.sampler());
                VkWriteDescriptorSet.Buffer descriptorWrite = VkWriteDescriptorSet.calloc(1, stack)
                        .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                        .dstSet(texture.descriptorSet())
                        .dstBinding(0)
                        .dstArrayElement(0)
                        .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                        .descriptorCount(1)
                        .pImageInfo(imageInfo);
                vkUpdateDescriptorSets(context.deviceHandle(), descriptorWrite, null);
            }
        }

        private VkCommandBuffer beginSingleTimeCommands() {
            try (MemoryStack stack = stackPush()) {
                VkCommandBufferAllocateInfo allocateInfo = VkCommandBufferAllocateInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
                        .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                        .commandPool(context.commandPool())
                        .commandBufferCount(1);
                PointerBuffer pCommandBuffer = stack.mallocPointer(1);
                check(vkAllocateCommandBuffers(context.deviceHandle(), allocateInfo, pCommandBuffer),
                        "Could not allocate Vulkan texture upload command buffer");
                VkCommandBuffer commandBuffer = new VkCommandBuffer(pCommandBuffer.get(0), context.deviceHandle());
                VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)
                        .flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);
                check(vkBeginCommandBuffer(commandBuffer, beginInfo),
                        "Could not begin Vulkan texture upload command buffer");
                return commandBuffer;
            }
        }

        private void endSingleTimeCommands(VkCommandBuffer commandBuffer) {
            try (MemoryStack stack = stackPush()) {
                VkFenceCreateInfo fenceInfo = VkFenceCreateInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_FENCE_CREATE_INFO);
                LongBuffer pFence = stack.mallocLong(1);
                check(vkCreateFence(context.deviceHandle(), fenceInfo, null, pFence),
                        "Could not create Vulkan one-time command fence");
                long fence = pFence.get(0);

                check(vkEndCommandBuffer(commandBuffer), "Could not end Vulkan texture upload command buffer");
                VkSubmitInfo submitInfo = VkSubmitInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
                        .pCommandBuffers(stack.pointers(commandBuffer.address()));
                try {
                    check(vkQueueSubmit(context.graphicsQueue(), submitInfo, fence),
                        "Could not submit Vulkan texture upload command buffer");
                    waitForAllFences(context.deviceHandle(), new long[]{fence}, ONE_TIME_COMMAND_TIMEOUT_NS,
                            "texture upload command");
                } finally {
                    vkDestroyFence(context.deviceHandle(), fence, null);
                }
                vkFreeCommandBuffers(context.deviceHandle(), context.commandPool(),
                        stack.pointers(commandBuffer.address()));
            }
        }

        private void transitionImageLayout(VkCommandBuffer commandBuffer, VulkanTextureHandle texture,
                int oldLayout, int newLayout) {
            try (MemoryStack stack = stackPush()) {
                VkImageMemoryBarrier.Buffer barrier = VkImageMemoryBarrier.calloc(1, stack)
                        .sType(VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
                        .oldLayout(oldLayout)
                        .newLayout(newLayout)
                        .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                        .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                        .image(texture.image());
                barrier.subresourceRange()
                        .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                        .baseMipLevel(0)
                        .levelCount(1)
                        .baseArrayLayer(0)
                        .layerCount(1);

                int sourceStage;
                int destinationStage;
                if (oldLayout == VK_IMAGE_LAYOUT_UNDEFINED
                        && newLayout == VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL) {
                    barrier.srcAccessMask(0)
                            .dstAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT);
                    sourceStage = VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT;
                    destinationStage = VK_PIPELINE_STAGE_TRANSFER_BIT;
                } else if (oldLayout == VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL
                        && newLayout == VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL) {
                    barrier.srcAccessMask(VK_ACCESS_SHADER_READ_BIT)
                            .dstAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT);
                    sourceStage = VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
                    destinationStage = VK_PIPELINE_STAGE_TRANSFER_BIT;
                } else if (oldLayout == VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL
                        && newLayout == VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL) {
                    barrier.srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                            .dstAccessMask(VK_ACCESS_SHADER_READ_BIT);
                    sourceStage = VK_PIPELINE_STAGE_TRANSFER_BIT;
                    destinationStage = VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
                } else {
                    throw new FdxException("Unsupported Vulkan texture layout transition: "
                            + oldLayout + " -> " + newLayout);
                }

                vkCmdPipelineBarrier(commandBuffer, sourceStage, destinationStage, 0,
                        null, null, barrier);
                texture.layout(newLayout);
            }
        }

        private void copyBufferToImage(VkCommandBuffer commandBuffer, long buffer, VulkanTextureHandle texture) {
            try (MemoryStack stack = stackPush()) {
                VkBufferImageCopy.Buffer region = VkBufferImageCopy.calloc(1, stack)
                        .bufferOffset(0L)
                        .bufferRowLength(0)
                        .bufferImageHeight(0);
                region.imageSubresource()
                        .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                        .mipLevel(0)
                        .baseArrayLayer(0)
                        .layerCount(1);
                region.imageOffset().set(0, 0, 0);
                region.imageExtent().set(texture.width(), texture.height(), 1);
                vkCmdCopyBufferToImage(commandBuffer, buffer, texture.image(),
                        VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, region);
            }
        }

        private int findMemoryType(int typeFilter, int properties) {
            try (MemoryStack stack = stackPush()) {
                VkPhysicalDeviceMemoryProperties memoryProperties = VkPhysicalDeviceMemoryProperties.malloc(stack);
                vkGetPhysicalDeviceMemoryProperties(context.physicalDeviceHandle(), memoryProperties);
                for (int i = 0; i < memoryProperties.memoryTypeCount(); i++) {
                    if ((typeFilter & (1 << i)) != 0
                            && (memoryProperties.memoryTypes(i).propertyFlags() & properties) == properties) {
                        return i;
                    }
                }
            }
            throw new FdxException("Could not find suitable Vulkan memory type");
        }

        @Override
        public ShaderModule createShaderModule(ShaderModuleDescriptor descriptor) {
            if (descriptor == null) {
                throw new FdxException("ShaderModuleDescriptor cannot be null");
            }
            if (!descriptor.hasSource(ShaderLanguage.SPIRV)) {
                throw new FdxException("Vulkan requires SPIR-V shader modules");
            }
            long vertexModule = createShaderModule(descriptor.spirvVertexWords(), descriptor.label() + " vertex");
            long fragmentModule = createShaderModule(descriptor.spirvFragmentWords(), descriptor.label() + " fragment");
            return new VulkanShaderModuleHandle(context.deviceHandle(), vertexModule, fragmentModule);
        }

        private long createShaderModule(int[] words, String label) {
            try (MemoryStack stack = stackPush()) {
                ByteBuffer code = stack.malloc(words.length * 4).order(ByteOrder.LITTLE_ENDIAN);
                code.asIntBuffer().put(words);
                VkShaderModuleCreateInfo createInfo = VkShaderModuleCreateInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO)
                        .pCode(code);
                LongBuffer pShaderModule = stack.mallocLong(1);
                check(vkCreateShaderModule(context.deviceHandle(), createInfo, null, pShaderModule),
                        "Could not create Vulkan shader module " + label);
                return pShaderModule.get(0);
            }
        }

        @Override
        public RenderPipeline createRenderPipeline(RenderPipelineDescriptor descriptor) {
            if (descriptor == null) {
                throw new FdxException("RenderPipelineDescriptor cannot be null");
            }
            VulkanShaderModuleHandle shaderModule = descriptor.shaderModule().as();
            if (toNativeFormat(descriptor.colorFormat()) != toNativeFormat(context.surfaceFormat())) {
                throw new FdxException("Vulkan render pipeline color format must match the surface format");
            }
            if (descriptor.sampledTextureCount() > MAX_TEXTURE_DESCRIPTOR_SLOTS) {
                throw new FdxException("Vulkan sampled texture count exceeds the descriptor slot limit: "
                        + descriptor.sampledTextureCount());
            }

            try (MemoryStack stack = stackPush()) {
                VkPipelineShaderStageCreateInfo.Buffer shaderStages = VkPipelineShaderStageCreateInfo.calloc(2, stack);
                shaderStages.get(0)
                        .sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                        .stage(VK_SHADER_STAGE_VERTEX_BIT)
                        .module(shaderModule.vertexModule())
                        .pName(stack.UTF8(descriptor.vertexEntryPoint()));
                shaderStages.get(1)
                        .sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                        .stage(VK_SHADER_STAGE_FRAGMENT_BIT)
                        .module(shaderModule.fragmentModule())
                        .pName(stack.UTF8(descriptor.fragmentEntryPoint()));

                VkPipelineVertexInputStateCreateInfo vertexInput = createVertexInputState(descriptor.vertexLayouts(),
                        stack);
                VkPipelineInputAssemblyStateCreateInfo inputAssembly = VkPipelineInputAssemblyStateCreateInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO)
                        .topology(toNative(descriptor.primitiveTopology()))
                        .primitiveRestartEnable(false);
                VkPipelineViewportStateCreateInfo viewportState = VkPipelineViewportStateCreateInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO)
                        .viewportCount(1)
                        .scissorCount(1);
                VkPipelineRasterizationStateCreateInfo rasterizer = VkPipelineRasterizationStateCreateInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO)
                        .depthClampEnable(false)
                        .rasterizerDiscardEnable(false)
                        .polygonMode(VK_POLYGON_MODE_FILL)
                        .lineWidth(1.0f)
                        .cullMode(VK_CULL_MODE_NONE)
                        .frontFace(VK_FRONT_FACE_COUNTER_CLOCKWISE)
                        .depthBiasEnable(false);
                VkPipelineMultisampleStateCreateInfo multisampling = VkPipelineMultisampleStateCreateInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO)
                        .sampleShadingEnable(false)
                        .rasterizationSamples(VK_SAMPLE_COUNT_1_BIT);
                VkPipelineColorBlendAttachmentState.Buffer colorBlendAttachment = VkPipelineColorBlendAttachmentState.calloc(1, stack)
                        .colorWriteMask(VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT
                                | VK_COLOR_COMPONENT_B_BIT | VK_COLOR_COMPONENT_A_BIT)
                        .blendEnable(true)
                        .srcColorBlendFactor(VK_BLEND_FACTOR_SRC_ALPHA)
                        .dstColorBlendFactor(VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA)
                        .colorBlendOp(VK_BLEND_OP_ADD)
                        .srcAlphaBlendFactor(VK_BLEND_FACTOR_ONE)
                        .dstAlphaBlendFactor(VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA)
                        .alphaBlendOp(VK_BLEND_OP_ADD);
                VkPipelineColorBlendStateCreateInfo colorBlending = VkPipelineColorBlendStateCreateInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO)
                        .logicOpEnable(false)
                        .logicOp(VK_LOGIC_OP_COPY)
                        .pAttachments(colorBlendAttachment)
                        .blendConstants(stack.floats(0.0f, 0.0f, 0.0f, 0.0f));
                VkPipelineDynamicStateCreateInfo dynamicState = VkPipelineDynamicStateCreateInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO)
                        .pDynamicStates(stack.ints(VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR));
                VkPipelineDepthStencilStateCreateInfo depthStencilState = createDepthStencilState(descriptor, stack);

                VkPipelineLayoutCreateInfo pipelineLayoutInfo = VkPipelineLayoutCreateInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO);
                boolean uniformBufferEnabled = usesPbrUniformBlock(descriptor);
                int uniformDescriptorSetIndex = descriptor.sampledTextureCount() > 0 ? 1 : 0;
                if (descriptor.sampledTextureCount() > 0 && uniformBufferEnabled) {
                    pipelineLayoutInfo.pSetLayouts(stack.longs(context.textureDescriptorSetLayout(),
                            context.uniformDescriptorSetLayout()));
                } else if (descriptor.sampledTextureCount() > 0) {
                    pipelineLayoutInfo.pSetLayouts(stack.longs(context.textureDescriptorSetLayout()));
                } else if (uniformBufferEnabled) {
                    pipelineLayoutInfo.pSetLayouts(stack.longs(context.uniformDescriptorSetLayout()));
                }
                LongBuffer pPipelineLayout = stack.mallocLong(1);
                check(vkCreatePipelineLayout(context.deviceHandle(), pipelineLayoutInfo, null, pPipelineLayout),
                        "Could not create Vulkan pipeline layout");
                long pipelineLayout = pPipelineLayout.get(0);

                VkGraphicsPipelineCreateInfo.Buffer pipelineInfo = VkGraphicsPipelineCreateInfo.calloc(1, stack)
                        .sType(VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO)
                        .pStages(shaderStages)
                        .pVertexInputState(vertexInput)
                        .pInputAssemblyState(inputAssembly)
                        .pViewportState(viewportState)
                        .pRasterizationState(rasterizer)
                        .pMultisampleState(multisampling)
                        .pColorBlendState(colorBlending)
                        .pDynamicState(dynamicState)
                        .pDepthStencilState(depthStencilState)
                        .layout(pipelineLayout)
                        .renderPass(context.pipelineRenderPass())
                        .subpass(0)
                        .basePipelineHandle(VK_NULL_HANDLE)
                        .basePipelineIndex(-1);

                LongBuffer pPipeline = stack.mallocLong(1);
                int result = vkCreateGraphicsPipelines(context.deviceHandle(), VK_NULL_HANDLE, pipelineInfo, null, pPipeline);
                if (result != VK_SUCCESS) {
                    vkDestroyPipelineLayout(context.deviceHandle(), pipelineLayout, null);
                    throw new FdxException("Could not create Vulkan graphics pipeline: " + result);
                }
                return new VulkanRenderPipelineHandle(context.deviceHandle(), pPipeline.get(0), pipelineLayout,
                        descriptor.primitiveTopology(), descriptor.sampledTextureCount(),
                        uniformBufferEnabled, uniformDescriptorSetIndex);
            }
        }

        private VkPipelineDepthStencilStateCreateInfo createDepthStencilState(RenderPipelineDescriptor descriptor,
                MemoryStack stack) {
            return VkPipelineDepthStencilStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_DEPTH_STENCIL_STATE_CREATE_INFO)
                    .depthTestEnable(descriptor.depthTestEnabled())
                    .depthWriteEnable(descriptor.depthWriteEnabled())
                    .depthCompareOp(VK_COMPARE_OP_LESS_OR_EQUAL)
                    .depthBoundsTestEnable(false)
                    .stencilTestEnable(false)
                    .minDepthBounds(0.0f)
                    .maxDepthBounds(1.0f);
        }

        private VkPipelineVertexInputStateCreateInfo createVertexInputState(VertexLayout[] layouts, MemoryStack stack) {
            VkPipelineVertexInputStateCreateInfo vertexInput = VkPipelineVertexInputStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO);
            if (layouts == null || layouts.length == 0) {
                return vertexInput;
            }

            VkVertexInputBindingDescription.Buffer bindingDescriptions =
                    VkVertexInputBindingDescription.calloc(layouts.length, stack);
            int attributeCount = 0;
            for (int i = 0; i < layouts.length; i++) {
                VertexLayout layout = layouts[i];
                bindingDescriptions.get(i)
                        .binding(i)
                        .stride(layout.arrayStride())
                        .inputRate(layout.stepMode() == VertexStepMode.INSTANCE
                                ? VK_VERTEX_INPUT_RATE_INSTANCE
                                : VK_VERTEX_INPUT_RATE_VERTEX);
                attributeCount += layout.attributes().length;
            }

            VkVertexInputAttributeDescription.Buffer attributeDescriptions =
                    VkVertexInputAttributeDescription.calloc(attributeCount, stack);
            int attributeIndex = 0;
            for (int layoutIndex = 0; layoutIndex < layouts.length; layoutIndex++) {
                VertexAttribute[] attributes = layouts[layoutIndex].attributes();
                for (int i = 0; i < attributes.length; i++) {
                    VertexAttribute attribute = attributes[i];
                    attributeDescriptions.get(attributeIndex++)
                            .binding(layoutIndex)
                            .location(attribute.location())
                            .format(toNative(attribute.format()))
                            .offset(attribute.offset());
                }
            }

            return vertexInput
                    .pVertexBindingDescriptions(bindingDescriptions)
                    .pVertexAttributeDescriptions(attributeDescriptions);
        }

        private int toNative(VertexFormat format) {
            switch (format) {
                case FLOAT32:
                    return VK_FORMAT_R32_SFLOAT;
                case FLOAT32X2:
                    return VK_FORMAT_R32G32_SFLOAT;
                case FLOAT32X3:
                    return VK_FORMAT_R32G32B32_SFLOAT;
                case UNORM8X4:
                    return VK_FORMAT_R8G8B8A8_UNORM;
                case FLOAT32X4:
                default:
                    return VK_FORMAT_R32G32B32A32_SFLOAT;
            }
        }

        private int toNative(PrimitiveTopology topology) {
            if (topology == PrimitiveTopology.LINE_LIST) {
                return VK_PRIMITIVE_TOPOLOGY_LINE_LIST;
            }
            if (topology == PrimitiveTopology.TRIANGLE_STRIP) {
                return VK_PRIMITIVE_TOPOLOGY_TRIANGLE_STRIP;
            }
            return VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST;
        }

        @Override
        public ProviderId providerId() {
            return ID;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T as() {
            return (T) this;
        }
    }

    private static final class VulkanCommandEncoder implements CommandEncoder {
        private final VulkanContext context;

        VulkanCommandEncoder(VulkanContext context) {
            this.context = context;
        }

        @Override
        public RenderPass beginRenderPass(RenderPassDescriptor descriptor) {
            if (descriptor == null) {
                throw new FdxException("RenderPassDescriptor cannot be null");
            }
            if (!context.frameStarted()) {
                throw new FdxException("Cannot begin Vulkan render pass outside a frame");
            }
            descriptor.colorAttachment().as();
            try (MemoryStack stack = stackPush()) {
                VkRenderPassBeginInfo beginInfo = VkRenderPassBeginInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO)
                        .renderPass(context.selectRenderPass(descriptor.colorLoadOp(), descriptor.colorStoreOp()))
                        .framebuffer(context.framebuffer());
                beginInfo.renderArea().offset().set(0, 0);
                beginInfo.renderArea().extent().set(context.width(), context.height());
                VkClearValue.Buffer clearValues = VkClearValue.calloc(2, stack);
                clearValues.get(0).color(c -> c.float32(0, descriptor.colorLoadOp().red())
                        .float32(1, descriptor.colorLoadOp().green())
                        .float32(2, descriptor.colorLoadOp().blue())
                        .float32(3, descriptor.colorLoadOp().alpha()));
                clearValues.get(1).depthStencil()
                        .depth(descriptor.depthClearEnabled() ? descriptor.depthClearValue() : 1.0f)
                        .stencil(0);
                beginInfo.pClearValues(clearValues);

                vkCmdBeginRenderPass(context.commandBuffer(), beginInfo, VK10.VK_SUBPASS_CONTENTS_INLINE);

                VkViewport.Buffer viewport = VkViewport.calloc(1, stack)
                        .x(0.0f)
                        .y(context.height())
                        .width(context.width())
                        .height(-context.height())
                        .minDepth(0.0f)
                        .maxDepth(1.0f);
                VkRect2D.Buffer scissor = VkRect2D.calloc(1, stack);
                scissor.offset().set(0, 0);
                scissor.extent().set(context.width(), context.height());
                vkCmdSetViewport(context.commandBuffer(), 0, viewport);
                vkCmdSetScissor(context.commandBuffer(), 0, scissor);
            }
            return new VulkanRenderPass(context);
        }

        @Override
        public ProviderId providerId() {
            return ID;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T as() {
            return (T) context.commandBuffer();
        }
    }

    private static final class VulkanRenderPass implements RenderPass {
        private static final int MATRIX_FLOAT_COUNT = 16;
        private static final int MODEL_OFFSET = 0;
        private static final int VIEW_PROJECTION_OFFSET = 16;
        private static final int CAMERA_POSITION_OFFSET = 32;
        private static final int AMBIENT_COLOR_OFFSET = 36;
        private static final int LIGHT_DIRECTION_OFFSET = 40;
        private static final int LIGHT_COLOR_INTENSITY_OFFSET = 44;
        private static final int TEXTURE_FLAGS_OFFSET = 48;
        private static final int EMISSIVE_FLAGS_OFFSET = 52;

        private final VulkanContext context;
        private final ByteBuffer uniformBytes = ByteBuffer.allocateDirect(PBR_UNIFORM_BYTE_COUNT)
                .order(ByteOrder.nativeOrder());
        private final FloatBuffer uniformFloats = uniformBytes.asFloatBuffer();
        private VulkanRenderPipelineHandle pipeline;
        private VulkanBufferHandle vertexBuffer;
        private VulkanBufferHandle indexBuffer;
        private VulkanTextureHandle[] textures;
        private long uniformDescriptorSet;
        private boolean uniformDataDirty = true;
        private boolean hasUniformData;
        private boolean ended;

        VulkanRenderPass(VulkanContext context) {
            this.context = context;
            resetUniformData();
        }

        @Override
        public void setPipeline(RenderPipeline pipeline) {
            ensureOpen();
            this.pipeline = pipeline.as();
            textures = this.pipeline.sampledTextureCount() > 0
                    ? new VulkanTextureHandle[this.pipeline.sampledTextureCount()] : null;
            uniformDescriptorSet = VK_NULL_HANDLE;
            uniformDataDirty = true;
            vkCmdBindPipeline(context.commandBuffer(), VK_PIPELINE_BIND_POINT_GRAPHICS, this.pipeline.pipeline());
        }

        @Override
        public void setVertexBuffer(Buffer buffer) {
            setVertexBuffer(0, buffer);
        }

        @Override
        public void setVertexBuffer(int slot, Buffer buffer) {
            ensureOpen();
            if (buffer == null) {
                throw new FdxException("Vertex buffer cannot be null");
            }
            vertexBuffer = buffer.as();
            if (vertexBuffer.usage() != BufferUsage.VERTEX) {
                throw new FdxException("RenderPass.setVertexBuffer requires a vertex buffer");
            }
            try (MemoryStack stack = stackPush()) {
                LongBuffer buffers = stack.longs(vertexBuffer.buffer());
                LongBuffer offsets = stack.longs(0L);
                vkCmdBindVertexBuffers(context.commandBuffer(), slot, buffers, offsets);
            }
            context.markBufferUsedByRecordedCommand(vertexBuffer);
        }

        @Override
        public void setIndexBuffer(Buffer buffer) {
            ensureOpen();
            if (buffer == null) {
                throw new FdxException("Index buffer cannot be null");
            }
            indexBuffer = buffer.as();
            if (indexBuffer.usage() != BufferUsage.INDEX) {
                throw new FdxException("RenderPass.setIndexBuffer requires an index buffer");
            }
            vkCmdBindIndexBuffer(context.commandBuffer(), indexBuffer.buffer(), 0L, VK_INDEX_TYPE_UINT16);
            context.markBufferUsedByRecordedCommand(indexBuffer);
        }

        @Override
        public void setTexture(int slot, Texture texture) {
            ensureOpen();
            if (pipeline == null) {
                throw new FdxException("Render pipeline must be set before binding a texture");
            }
            if (texture == null) {
                throw new FdxException("Texture cannot be null");
            }
            if (slot < 0 || slot >= pipeline.sampledTextureCount()) {
                throw new FdxException("Texture slot is not declared by the active Vulkan pipeline: " + slot);
            }
            VulkanTextureHandle vulkanTexture = texture.as();
            textures[slot] = vulkanTexture;
        }

        @Override
        public void setScissor(int x, int y, int width, int height) {
            ensureOpen();
            if (width <= 0 || height <= 0) {
                throw new FdxException("Scissor size must be greater than zero");
            }
            try (MemoryStack stack = stackPush()) {
                VkRect2D.Buffer scissor = VkRect2D.calloc(1, stack);
                scissor.offset().set(x, y);
                scissor.extent().set(width, height);
                vkCmdSetScissor(context.commandBuffer(), 0, scissor);
            }
        }

        @Override
        public void setUniform1i(String name, int value) {
            if ("u_hasBaseColorTexture".equals(name)) {
                setUniformFloat(TEXTURE_FLAGS_OFFSET, value);
            }
            else if ("u_hasMetallicRoughnessTexture".equals(name)) {
                setUniformFloat(TEXTURE_FLAGS_OFFSET + 1, value);
            }
            else if ("u_hasNormalTexture".equals(name)) {
                setUniformFloat(TEXTURE_FLAGS_OFFSET + 2, value);
            }
            else if ("u_hasOcclusionTexture".equals(name)) {
                setUniformFloat(TEXTURE_FLAGS_OFFSET + 3, value);
            }
            else if ("u_hasEmissiveTexture".equals(name)) {
                setUniformFloat(EMISSIVE_FLAGS_OFFSET, value);
            }
        }

        @Override
        public void setUniform1f(String name, float value) {
            if ("u_lightIntensity".equals(name)) {
                setUniformFloat(LIGHT_COLOR_INTENSITY_OFFSET + 3, value);
            }
        }

        @Override
        public void setUniform3f(String name, float x, float y, float z) {
            if ("u_cameraPosition".equals(name)) {
                setUniform4f(CAMERA_POSITION_OFFSET, x, y, z, 1.0f);
            }
            else if ("u_ambientColor".equals(name)) {
                setUniform4f(AMBIENT_COLOR_OFFSET, x, y, z, 1.0f);
            }
            else if ("u_lightDirection".equals(name)) {
                setUniform4f(LIGHT_DIRECTION_OFFSET, x, y, z, 0.0f);
            }
            else if ("u_lightColor".equals(name)) {
                setUniform4f(LIGHT_COLOR_INTENSITY_OFFSET, x, y, z,
                        uniformFloats.get(LIGHT_COLOR_INTENSITY_OFFSET + 3));
            }
        }

        @Override
        public void setUniform4f(String name, float x, float y, float z, float w) {
            if ("u_cameraPosition".equals(name)) {
                setUniform4f(CAMERA_POSITION_OFFSET, x, y, z, w);
            }
            else if ("u_ambientColor".equals(name)) {
                setUniform4f(AMBIENT_COLOR_OFFSET, x, y, z, w);
            }
            else if ("u_lightDirection".equals(name)) {
                setUniform4f(LIGHT_DIRECTION_OFFSET, x, y, z, w);
            }
            else if ("u_lightColor".equals(name)) {
                setUniform4f(LIGHT_COLOR_INTENSITY_OFFSET, x, y, z, w);
            }
        }

        @Override
        public void setUniformMatrix4(String name, float[] values) {
            ensureOpen();
            if (values == null || values.length < MATRIX_FLOAT_COUNT) {
                throw new FdxException("Matrix uniform requires 16 float values");
            }
            if ("u_model".equals(name)) {
                setUniformMatrix(MODEL_OFFSET, values);
            }
            else if ("u_viewProjection".equals(name)) {
                setUniformMatrix(VIEW_PROJECTION_OFFSET, values);
            }
        }

        @Override
        public void draw(int vertexCount, int instanceCount, int firstVertex, int firstInstance) {
            ensureOpen();
            if (pipeline == null) {
                throw new FdxException("Render pipeline must be set before draw");
            }
            bindTextures();
            bindUniforms();
            vkCmdDraw(context.commandBuffer(), vertexCount, instanceCount, firstVertex, firstInstance);
        }

        @Override
        public void drawIndexed(int indexCount, int instanceCount, int firstIndex, int baseVertex, int firstInstance) {
            ensureOpen();
            if (pipeline == null) {
                throw new FdxException("Render pipeline must be set before drawIndexed");
            }
            if (indexBuffer == null) {
                throw new FdxException("Index buffer must be set before drawIndexed");
            }
            bindTextures();
            bindUniforms();
            vkCmdDrawIndexed(context.commandBuffer(), indexCount, instanceCount, firstIndex, baseVertex, firstInstance);
        }

        @Override
        public void end() {
            if (ended) {
                return;
            }
            ended = true;
            vkCmdEndRenderPass(context.commandBuffer());
        }

        private void ensureOpen() {
            if (ended) {
                throw new FdxException("Render pass has already ended");
            }
        }

        private void bindTextures() {
            int sampledTextureCount = pipeline.sampledTextureCount();
            if (sampledTextureCount == 0) {
                return;
            }
            for (int i = 0; i < sampledTextureCount; i++) {
                if (textures[i] == null) {
                    throw new FdxException("Texture slot " + i + " must be set before drawing with Vulkan pipeline");
                }
            }
            long descriptorSet = context.textureDescriptorSet(textures, sampledTextureCount);
            try (MemoryStack stack = stackPush()) {
                vkCmdBindDescriptorSets(context.commandBuffer(), VK_PIPELINE_BIND_POINT_GRAPHICS,
                        pipeline.pipelineLayout(), 0, stack.longs(descriptorSet), null);
            }
        }

        private void bindUniforms() {
            if (!pipeline.uniformBufferEnabled()) {
                return;
            }
            if (!hasUniformData) {
                throw new FdxException("Vulkan PBR uniforms must be set before drawing");
            }
            if (uniformDescriptorSet == VK_NULL_HANDLE || uniformDataDirty) {
                uniformDescriptorSet = context.uniformDescriptorSet(uniformBytes);
                uniformDataDirty = false;
            }
            try (MemoryStack stack = stackPush()) {
                vkCmdBindDescriptorSets(context.commandBuffer(), VK_PIPELINE_BIND_POINT_GRAPHICS,
                        pipeline.pipelineLayout(), pipeline.uniformDescriptorSetIndex(),
                        stack.longs(uniformDescriptorSet), null);
            }
        }

        private void setUniformMatrix(int offset, float[] values) {
            ensureOpen();
            for (int i = 0; i < MATRIX_FLOAT_COUNT; i++) {
                uniformFloats.put(offset + i, values[i]);
            }
            markUniformDirty();
        }

        private void setUniform4f(int offset, float x, float y, float z, float w) {
            ensureOpen();
            uniformFloats.put(offset, x);
            uniformFloats.put(offset + 1, y);
            uniformFloats.put(offset + 2, z);
            uniformFloats.put(offset + 3, w);
            markUniformDirty();
        }

        private void setUniformFloat(int offset, float value) {
            ensureOpen();
            uniformFloats.put(offset, value);
            markUniformDirty();
        }

        private void markUniformDirty() {
            hasUniformData = true;
            uniformDataDirty = true;
        }

        private void resetUniformData() {
            for (int i = 0; i < PBR_UNIFORM_BYTE_COUNT / 4; i++) {
                uniformFloats.put(i, 0.0f);
            }
            uniformFloats.put(MODEL_OFFSET, 1.0f);
            uniformFloats.put(MODEL_OFFSET + 5, 1.0f);
            uniformFloats.put(MODEL_OFFSET + 10, 1.0f);
            uniformFloats.put(MODEL_OFFSET + 15, 1.0f);
            uniformFloats.put(VIEW_PROJECTION_OFFSET, 1.0f);
            uniformFloats.put(VIEW_PROJECTION_OFFSET + 5, 1.0f);
            uniformFloats.put(VIEW_PROJECTION_OFFSET + 10, 1.0f);
            uniformFloats.put(VIEW_PROJECTION_OFFSET + 15, 1.0f);
        }

        @Override
        public ProviderId providerId() {
            return ID;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T as() {
            return (T) this;
        }
    }

    private static final class VulkanBufferHandle implements Buffer {
        private final VkDevice device;
        private long buffer;
        private long memory;
        private ByteBuffer mappedMemory;
        private final int size;
        private final int nativeUsage;
        private final BufferUsage usage;
        private boolean usedByRecordedCommand;
        private boolean disposed;

        VulkanBufferHandle(VkDevice device, long buffer, long memory, ByteBuffer mappedMemory, int size,
                int nativeUsage, BufferUsage usage) {
            this.device = device;
            this.buffer = buffer;
            this.memory = memory;
            this.mappedMemory = mappedMemory;
            this.size = size;
            this.nativeUsage = nativeUsage;
            this.usage = usage != null ? usage : BufferUsage.VERTEX;
        }

        long buffer() {
            return buffer;
        }

        long memory() {
            return memory;
        }

        ByteBuffer mappedMemory() {
            return mappedMemory;
        }

        int nativeUsage() {
            return nativeUsage;
        }

        VulkanBufferAllocation allocation() {
            return new VulkanBufferAllocation(buffer, memory, mappedMemory);
        }

        void nativeAllocation(VulkanBufferAllocation allocation) {
            buffer = allocation.buffer();
            memory = allocation.memory();
            mappedMemory = allocation.mappedMemory();
            usedByRecordedCommand = false;
        }

        void markUsedByRecordedCommand() {
            usedByRecordedCommand = true;
        }

        void resetUsedByRecordedCommand() {
            usedByRecordedCommand = false;
        }

        boolean usedByRecordedCommand() {
            return usedByRecordedCommand;
        }

        @Override
        public int size() {
            return size;
        }

        @Override
        public BufferUsage usage() {
            return usage;
        }

        @Override
        public ProviderId providerId() {
            return ID;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T as() {
            return (T) this;
        }

        @Override
        public void dispose() {
            if (disposed) {
                return;
            }
            disposed = true;
            waitDeviceIdleBeforeDestroy(device, "buffer");
            if (mappedMemory != null) {
                vkUnmapMemory(device, memory);
            }
            vkDestroyBuffer(device, buffer, null);
            vkFreeMemory(device, memory, null);
        }

        @Override
        public boolean isDisposed() {
            return disposed;
        }
    }

    private static final class VulkanBufferAllocation {
        private final long buffer;
        private final long memory;
        private final ByteBuffer mappedMemory;

        VulkanBufferAllocation(long buffer, long memory) {
            this(buffer, memory, null);
        }

        VulkanBufferAllocation(long buffer, long memory, ByteBuffer mappedMemory) {
            this.buffer = buffer;
            this.memory = memory;
            this.mappedMemory = mappedMemory;
        }

        long buffer() {
            return buffer;
        }

        long memory() {
            return memory;
        }

        ByteBuffer mappedMemory() {
            return mappedMemory;
        }

        void dispose(VkDevice device) {
            if (mappedMemory != null) {
                vkUnmapMemory(device, memory);
            }
            vkDestroyBuffer(device, buffer, null);
            vkFreeMemory(device, memory, null);
        }
    }

    private static final class VulkanUniformAllocation {
        private final VulkanBufferAllocation buffer;
        private final long descriptorSet;

        VulkanUniformAllocation(VulkanBufferAllocation buffer, long descriptorSet) {
            this.buffer = buffer;
            this.descriptorSet = descriptorSet;
        }

        long descriptorSet() {
            return descriptorSet;
        }

        void dispose(VkDevice device, long descriptorPool) {
            if (descriptorSet != VK_NULL_HANDLE && descriptorPool != VK_NULL_HANDLE) {
                check(vkFreeDescriptorSets(device, descriptorPool, descriptorSet),
                        "Could not free Vulkan uniform descriptor set");
            }
            buffer.dispose(device);
        }
    }

    private static final class VulkanTextureHandle implements Texture {
        private final VkDevice device;
        private final long image;
        private final long memory;
        private final long imageView;
        private final long sampler;
        private final long descriptorSet;
        private final int width;
        private final int height;
        private final TextureFormat format;
        private final TextureUsage usage;
        private int layout = VK_IMAGE_LAYOUT_UNDEFINED;
        private boolean disposed;

        VulkanTextureHandle(VkDevice device, long image, long memory, long imageView, long sampler,
                long descriptorSet, int width, int height, TextureFormat format, TextureUsage usage) {
            this.device = device;
            this.image = image;
            this.memory = memory;
            this.imageView = imageView;
            this.sampler = sampler;
            this.descriptorSet = descriptorSet;
            this.width = width;
            this.height = height;
            this.format = format;
            this.usage = usage;
        }

        long image() {
            return image;
        }

        long imageView() {
            return imageView;
        }

        long sampler() {
            return sampler;
        }

        long descriptorSet() {
            return descriptorSet;
        }

        int layout() {
            return layout;
        }

        void layout(int layout) {
            this.layout = layout;
        }

        @Override
        public int width() {
            return width;
        }

        @Override
        public int height() {
            return height;
        }

        @Override
        public TextureFormat format() {
            return format;
        }

        @Override
        public TextureUsage usage() {
            return usage;
        }

        @Override
        public ProviderId providerId() {
            return ID;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T as() {
            return (T) this;
        }

        @Override
        public void dispose() {
            if (disposed) {
                return;
            }
            disposed = true;
            waitDeviceIdleBeforeDestroy(device, "texture");
            vkDestroySampler(device, sampler, null);
            vkDestroyImageView(device, imageView, null);
            vkDestroyImage(device, image, null);
            vkFreeMemory(device, memory, null);
        }

        @Override
        public boolean isDisposed() {
            return disposed;
        }
    }

    private static final class VulkanGraphicsFrame implements GraphicsFrame {
        private final VulkanContext context;
        private final CommandEncoder commandEncoder;
        private final FrameBuffer frameBuffer;
        private final TextureView colorAttachment;

        VulkanGraphicsFrame(VulkanContext context, CommandEncoder commandEncoder,
                FrameBuffer frameBuffer, TextureView colorAttachment) {
            this.context = context;
            this.commandEncoder = commandEncoder;
            this.frameBuffer = frameBuffer;
            this.colorAttachment = colorAttachment;
        }

        @Override
        public CommandEncoder commandEncoder() {
            return commandEncoder;
        }

        @Override
        public FrameBuffer frameBuffer() {
            return frameBuffer;
        }

        @Override
        public TextureView colorAttachment() {
            return colorAttachment;
        }

        @Override
        public int width() {
            return context.width();
        }

        @Override
        public int height() {
            return context.height();
        }

        @Override
        public ProviderId providerId() {
            return ID;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T as() {
            return (T) this;
        }
    }

    private static final class VulkanFrameBuffer implements FrameBuffer {
        private final VulkanContext context;
        private final TextureView colorAttachment;

        VulkanFrameBuffer(VulkanContext context, TextureView colorAttachment) {
            this.context = context;
            this.colorAttachment = colorAttachment;
        }

        @Override
        public TextureView colorAttachment() {
            return colorAttachment;
        }

        @Override
        public TextureFormat format() {
            return context.surfaceFormat();
        }

        @Override
        public int width() {
            return context.width();
        }

        @Override
        public int height() {
            return context.height();
        }

        @Override
        public ByteBuffer readPixelsRgba8() {
            return context.readPixelsRgba8();
        }

        @Override
        public ProviderId providerId() {
            return ID;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T as() {
            return (T) this;
        }
    }

    private static final class VulkanTextureViewHandle implements TextureView {
        private final VulkanContext context;

        VulkanTextureViewHandle(VulkanContext context) {
            this.context = context;
        }

        long nativeView() {
            return context.currentImageView();
        }

        @Override
        public TextureFormat format() {
            return context.surfaceFormat();
        }

        @Override
        public ProviderId providerId() {
            return ID;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T as() {
            return (T) this;
        }
    }

    private static final class VulkanShaderModuleHandle implements ShaderModule {
        private final VkDevice device;
        private final long vertexModule;
        private final long fragmentModule;
        private boolean disposed;

        VulkanShaderModuleHandle(VkDevice device, long vertexModule, long fragmentModule) {
            this.device = device;
            this.vertexModule = vertexModule;
            this.fragmentModule = fragmentModule;
        }

        long vertexModule() {
            return vertexModule;
        }

        long fragmentModule() {
            return fragmentModule;
        }

        @Override
        public ShaderLanguage language() {
            return ShaderLanguage.SPIRV;
        }

        @Override
        public ProviderId providerId() {
            return ID;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T as() {
            return (T) this;
        }

        @Override
        public void dispose() {
            if (disposed) {
                return;
            }
            disposed = true;
            waitDeviceIdleBeforeDestroy(device, "shader module");
            vkDestroyShaderModule(device, fragmentModule, null);
            vkDestroyShaderModule(device, vertexModule, null);
        }

        @Override
        public boolean isDisposed() {
            return disposed;
        }
    }

    private static final class VulkanRenderPipelineHandle implements RenderPipeline {
        private final VkDevice device;
        private final long pipeline;
        private final long pipelineLayout;
        private final PrimitiveTopology primitiveTopology;
        private final int sampledTextureCount;
        private final boolean uniformBufferEnabled;
        private final int uniformDescriptorSetIndex;
        private boolean disposed;

        VulkanRenderPipelineHandle(VkDevice device, long pipeline, long pipelineLayout,
                PrimitiveTopology primitiveTopology, int sampledTextureCount,
                boolean uniformBufferEnabled, int uniformDescriptorSetIndex) {
            this.device = device;
            this.pipeline = pipeline;
            this.pipelineLayout = pipelineLayout;
            this.primitiveTopology = primitiveTopology;
            this.sampledTextureCount = sampledTextureCount;
            this.uniformBufferEnabled = uniformBufferEnabled;
            this.uniformDescriptorSetIndex = uniformDescriptorSetIndex;
        }

        long pipeline() {
            return pipeline;
        }

        long pipelineLayout() {
            return pipelineLayout;
        }

        int sampledTextureCount() {
            return sampledTextureCount;
        }

        boolean uniformBufferEnabled() {
            return uniformBufferEnabled;
        }

        int uniformDescriptorSetIndex() {
            return uniformDescriptorSetIndex;
        }

        PrimitiveTopology primitiveTopology() {
            return primitiveTopology;
        }

        @Override
        public ProviderId providerId() {
            return ID;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T as() {
            return (T) this;
        }

        @Override
        public void dispose() {
            if (disposed) {
                return;
            }
            disposed = true;
            waitDeviceIdleBeforeDestroy(device, "render pipeline");
            vkDestroyPipeline(device, pipeline, null);
            vkDestroyPipelineLayout(device, pipelineLayout, null);
        }

        @Override
        public boolean isDisposed() {
            return disposed;
        }
    }

    private static final class QueueFamilyIndices {
        int graphicsFamily = -1;
        int presentFamily = -1;

        boolean complete() {
            return graphicsFamily >= 0 && presentFamily >= 0;
        }
    }

    private static TextureFormat toCommonFormat(int format) {
        if (format == VK_FORMAT_B8G8R8A8_SRGB) {
            return TextureFormat.BGRA8_UNORM_SRGB;
        }
        if (format == VK_FORMAT_B8G8R8A8_UNORM) {
            return TextureFormat.BGRA8_UNORM;
        }
        if (format == VK_FORMAT_R8G8B8A8_SRGB) {
            return TextureFormat.RGBA8_UNORM_SRGB;
        }
        if (format == VK_FORMAT_R8G8B8A8_UNORM) {
            return TextureFormat.RGBA8_UNORM;
        }
        return TextureFormat.UNKNOWN;
    }

    private static int toNativeFormat(TextureFormat format) {
        switch (format) {
            case RGBA8_UNORM:
                return VK_FORMAT_R8G8B8A8_UNORM;
            case RGBA8_UNORM_SRGB:
                return VK_FORMAT_R8G8B8A8_SRGB;
            case BGRA8_UNORM:
                return VK_FORMAT_B8G8R8A8_UNORM;
            case BGRA8_UNORM_SRGB:
                return VK_FORMAT_B8G8R8A8_SRGB;
            case UNKNOWN:
            default:
                throw new FdxException("Cannot create Vulkan pipeline for unknown texture format");
        }
    }

    private static void check(int result, String message) {
        if (result != VK_SUCCESS) {
            throw new FdxException(message + ": " + result);
        }
    }
}
