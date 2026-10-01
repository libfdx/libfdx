// Included by the native Vulkan implementation to share device and frame ownership.
namespace {
void computeMemoryBarrier(Context* context) {
    VkMemoryBarrier barrier{};
    barrier.sType = VK_STRUCTURE_TYPE_MEMORY_BARRIER;
    barrier.srcAccessMask = VK_ACCESS_MEMORY_WRITE_BIT;
    barrier.dstAccessMask = VK_ACCESS_MEMORY_READ_BIT | VK_ACCESS_MEMORY_WRITE_BIT;
    vkCmdPipelineBarrier(currentFrame(context).commandBuffer, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT,
            VK_PIPELINE_STAGE_ALL_COMMANDS_BIT | VK_PIPELINE_STAGE_HOST_BIT,
            0, 1, &barrier, 0, nullptr, 0, nullptr);
}

void requireComputeScope(Context* context) {
    if (!context || !context->frameStarted || context->renderPassStarted) {
        throw std::runtime_error("Vulkan compute and copies require a frame outside a render pass");
    }
}
}

extern "C" int64_t fdx_desktop_vulkan_create_compute(int64_t contextHandle, const uint32_t* words,
        int32_t wordCount, const char* entry, const int32_t* bindings, int32_t count) {
    Context* context = ptr<Context>(contextHandle);
    if (!context || !words || wordCount <= 0 || !entry || count < 0 || count > 64) {
        throw std::invalid_argument("Invalid Vulkan compute pipeline arguments");
    }
    Pipeline* pipeline = new Pipeline();
    pipeline->context = context;
    pipeline->computeBindingCount = count;
    VkShaderModule shader = VK_NULL_HANDLE;
    try {
        for (int i = 0; i < count; ++i) {
            int group = bindings[i * 3];
            int binding = bindings[i * 3 + 1];
            int type = bindings[i * 3 + 2];
            if (group < 0 || group >= 2 || binding < 0
                    || (type != VK_DESCRIPTOR_TYPE_STORAGE_BUFFER
                            && type != VK_DESCRIPTOR_TYPE_STORAGE_IMAGE
                            && type != VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)) {
                throw std::invalid_argument("Invalid Vulkan compute binding");
            }
            pipeline->computeGroupCount = std::max(pipeline->computeGroupCount, group + 1);
            for (int j = 0; j < 3; ++j) pipeline->computeBindings[i][j] = bindings[i * 3 + j];
        }
        VkDescriptorSetLayout layouts[2]{};
        for (int group = 0; group < pipeline->computeGroupCount; ++group) {
            VkDescriptorSetLayoutBinding entries[64]{};
            int entryCount = 0;
            for (int i = 0; i < count; ++i) {
                if (pipeline->computeBindings[i][0] != group) continue;
                auto& value = entries[entryCount++];
                value.binding = pipeline->computeBindings[i][1];
                value.descriptorType = static_cast<decltype(value.descriptorType)>(pipeline->computeBindings[i][2]);
                value.descriptorCount = 1;
                value.stageFlags = VK_SHADER_STAGE_COMPUTE_BIT;
            }
            VkDescriptorSetLayoutCreateInfo info{};
            info.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO;
            info.bindingCount = entryCount;
            info.pBindings = entries;
            check(vkCreateDescriptorSetLayout(context->device, &info, nullptr, &layouts[group]),
                    "Could not create Vulkan compute descriptor layout");
            if (group == 0) pipeline->textureDescriptorSetLayout = layouts[group];
            else pipeline->uniformDescriptorSetLayout = layouts[group];
        }
        VkPipelineLayoutCreateInfo layoutInfo{};
        layoutInfo.sType = VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO;
        layoutInfo.setLayoutCount = pipeline->computeGroupCount;
        layoutInfo.pSetLayouts = layouts;
        check(vkCreatePipelineLayout(context->device, &layoutInfo, nullptr, &pipeline->layout),
                "Could not create Vulkan compute pipeline layout");
        shader = createShaderModule(context, std::vector<uint32_t>(words, words + wordCount));
        VkComputePipelineCreateInfo info{};
        info.sType = VK_STRUCTURE_TYPE_COMPUTE_PIPELINE_CREATE_INFO;
        info.layout = pipeline->layout;
        info.stage.sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
        info.stage.stage = VK_SHADER_STAGE_COMPUTE_BIT;
        info.stage.module = shader;
        info.stage.pName = entry;
        info.basePipelineIndex = -1;
        check(vkCreateComputePipelines(context->device, VK_NULL_HANDLE, 1, &info, nullptr, &pipeline->pipeline),
                "Could not create Vulkan compute pipeline");
        vkDestroyShaderModule(context->device, shader, nullptr);
        return handle(pipeline);
    } catch (...) {
        if (shader) vkDestroyShaderModule(context->device, shader, nullptr);
        fdx_desktop_vulkan_destroy_render_pipeline(handle(pipeline), 0);
        throw;
    }
}

extern "C" void fdx_desktop_vulkan_dispatch_compute(int64_t contextHandle, int64_t pipelineHandle,
        const int64_t* resources, int32_t count, int32_t x, int32_t y, int32_t z) {
    Context* context = ptr<Context>(contextHandle);
    Pipeline* pipeline = ptr<Pipeline>(pipelineHandle);
    requireComputeScope(context);
    if (!pipeline || pipeline->context != context || count != pipeline->computeBindingCount
            || x <= 0 || y <= 0 || z <= 0) {
        throw std::invalid_argument("Invalid Vulkan compute dispatch");
    }
    auto& frame = currentFrame(context);
    VkDescriptorSet sets[2]{};
    VkDescriptorSetLayout layouts[2] = {pipeline->textureDescriptorSetLayout, pipeline->uniformDescriptorSetLayout};
    if (pipeline->computeGroupCount) {
        uint32_t uniformBuffers = 0, storageBuffers = 0, storageImages = 0;
        for (int i = 0; i < count; ++i) {
            switch (pipeline->computeBindings[i][2]) {
                case VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER: ++uniformBuffers; break;
                case VK_DESCRIPTOR_TYPE_STORAGE_BUFFER: ++storageBuffers; break;
                case VK_DESCRIPTOR_TYPE_STORAGE_IMAGE: ++storageImages; break;
            }
        }
        allocateDescriptorSets(context, pipeline->computeGroupCount, layouts, sets,
                0, uniformBuffers, storageBuffers, storageImages);
    }
    VkWriteDescriptorSet writes[64]{};
    VkDescriptorBufferInfo buffers[64]{};
    VkDescriptorImageInfo images[64]{};
    for (int i = 0; i < count; ++i) {
        auto& write = writes[i];
        write.sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET;
        write.dstSet = sets[pipeline->computeBindings[i][0]];
        write.dstBinding = pipeline->computeBindings[i][1];
        write.descriptorType = static_cast<decltype(write.descriptorType)>(pipeline->computeBindings[i][2]);
        write.descriptorCount = 1;
        if (write.descriptorType == VK_DESCRIPTOR_TYPE_STORAGE_IMAGE) {
            Texture* texture = ptr<Texture>(resources[i * 3]);
            if (!texture || texture->context != context || !(texture->usage & 4)) {
                throw std::invalid_argument("Invalid Vulkan storage texture");
            }
            if (texture->layout != VK_IMAGE_LAYOUT_GENERAL) {
                transitionTextureLayout(frame.commandBuffer, texture, texture->layout, VK_IMAGE_LAYOUT_GENERAL);
            }
            texture->recordedSerial = context->recordingSerial;
            images[i].imageLayout = VK_IMAGE_LAYOUT_GENERAL;
            images[i].imageView = texture->mipViews[0];
            write.pImageInfo = &images[i];
        } else {
            Buffer* buffer = ptr<Buffer>(resources[i * 3]);
            int64_t offset = resources[i * 3 + 1], size = resources[i * 3 + 2];
            if (!buffer || buffer->context != context || offset < 0 || size <= 0
                    || static_cast<uint64_t>(offset + size) > buffer->size) {
                throw std::invalid_argument("Invalid Vulkan compute buffer range");
            }
            buffers[i].buffer = buffer->buffer;
            buffers[i].offset = offset;
            buffers[i].range = size;
            write.pBufferInfo = &buffers[i];
        }
    }
    if (count) vkUpdateDescriptorSets(context->device, count, writes, 0, nullptr);
    computeMemoryBarrier(context);
    vkCmdBindPipeline(frame.commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, pipeline->pipeline);
    if (pipeline->computeGroupCount) {
        vkCmdBindDescriptorSets(frame.commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, pipeline->layout,
                0, pipeline->computeGroupCount, sets, 0, nullptr);
    }
    vkCmdDispatch(frame.commandBuffer, x, y, z);
    computeMemoryBarrier(context);
}

extern "C" void fdx_desktop_vulkan_copy_buffer(int64_t contextHandle, int64_t sourceHandle,
        int32_t sourceOffset, int64_t destinationHandle, int32_t destinationOffset, int32_t size) {
    Context* context = ptr<Context>(contextHandle);
    requireComputeScope(context);
    Buffer* source = ptr<Buffer>(sourceHandle);
    Buffer* destination = ptr<Buffer>(destinationHandle);
    if (!source || !destination || source->context != context || destination->context != context
            || size < 0 || sourceOffset < 0 || destinationOffset < 0
            || static_cast<uint64_t>(sourceOffset) + size > source->size
            || static_cast<uint64_t>(destinationOffset) + size > destination->size) {
        throw std::invalid_argument("Invalid Vulkan buffer copy range");
    }
    if (!size) return;
    VkBufferCopy region{};
    region.srcOffset = sourceOffset;
    region.dstOffset = destinationOffset;
    region.size = size;
    computeMemoryBarrier(context);
    vkCmdCopyBuffer(currentFrame(context).commandBuffer, source->buffer, destination->buffer, 1, &region);
    computeMemoryBarrier(context);
}

extern "C" void fdx_desktop_vulkan_read_buffer(int64_t sourceHandle, int32_t offset, void* output, int32_t size) {
    Buffer* source = ptr<Buffer>(sourceHandle);
    if (!source || !output || offset < 0 || size < 0 || static_cast<uint64_t>(offset) + size > source->size) {
        throw std::invalid_argument("Invalid Vulkan readback range");
    }
    // Readback observes submitted work. Commands in the current frame remain unsubmitted.
    ensureNoActiveFramesOrThrow(source->context, TRANSFER_FENCE_TIMEOUT_NS, "buffer readback");
    std::memcpy(output, static_cast<const uint8_t*>(source->mapped) + offset, size);
}
