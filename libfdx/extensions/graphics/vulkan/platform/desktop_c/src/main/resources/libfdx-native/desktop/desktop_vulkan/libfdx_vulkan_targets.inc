// Shared desktop Vulkan target and texture implementation.
namespace {

void invalidateTargetFramebuffers(Context* context) {
    if (!context->frameStarted && !context->targetFramebuffers.empty()) {
        waitDeviceIdleBeforeDestroy(context, "Vulkan framebuffer disposal");
    }
    for (const TargetFramebuffer& entry : context->targetFramebuffers) {
        if (context->frameStarted) currentFrame(context).retiredFramebuffers.push_back(entry.framebuffer);
        else vkDestroyFramebuffer(context->device, entry.framebuffer, nullptr);
    }
    context->targetFramebuffers.clear();
}

void destroyTargetPasses(Context* context) {
    for (const TargetPass& entry : context->targetPasses)
        vkDestroyRenderPass(context->device, entry.pass, nullptr);
    context->targetPasses.clear();
}

VkRenderPass createTargetPass(Context* context, const FdxVulkanPass& pass) {
    VkAttachmentDescription descriptions[17]{};
    VkAttachmentReference colors[8]{}, resolves[8]{}, depth{};
    uint32_t count = 0;
    bool hasResolve = false;
    for (int i = 0; i < pass.targets.colorCount; ++i) {
        VkAttachmentDescription& color = descriptions[count];
        colors[i].attachment = count++;
        colors[i].layout = VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
        color.format = static_cast<VkFormat>(pass.targets.colors[i]);
        color.samples = static_cast<decltype(color.samples)>(pass.targets.samples);
        color.loadOp = static_cast<VkAttachmentLoadOp>(pass.load[i]);
        color.storeOp = static_cast<VkAttachmentStoreOp>(pass.store[i]);
        color.stencilLoadOp = VK_ATTACHMENT_LOAD_OP_DONT_CARE;
        color.stencilStoreOp = VK_ATTACHMENT_STORE_OP_DONT_CARE;
        bool surface = pass.colors[i] == 0;
        color.initialLayout = surface ? (pass.load[i] == VK_ATTACHMENT_LOAD_OP_LOAD
                ? VK_IMAGE_LAYOUT_PRESENT_SRC_KHR : VK_IMAGE_LAYOUT_UNDEFINED) : static_cast<VkImageLayout>(1);
        color.finalLayout = surface ? VK_IMAGE_LAYOUT_PRESENT_SRC_KHR : static_cast<VkImageLayout>(1);
    }
    for (int i = 0; i < pass.targets.colorCount; ++i) {
        resolves[i].attachment = ~0u;
        resolves[i].layout = VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
        if (pass.resolves[i] == 0) continue;
        hasResolve = true;
        VkAttachmentDescription& resolve = descriptions[count];
        resolves[i].attachment = count++;
        resolve.format = static_cast<VkFormat>(pass.targets.colors[i]);
        resolve.samples = VK_SAMPLE_COUNT_1_BIT;
        resolve.loadOp = VK_ATTACHMENT_LOAD_OP_DONT_CARE;
        resolve.storeOp = VK_ATTACHMENT_STORE_OP_STORE;
        resolve.stencilLoadOp = VK_ATTACHMENT_LOAD_OP_DONT_CARE;
        resolve.stencilStoreOp = VK_ATTACHMENT_STORE_OP_DONT_CARE;
        resolve.initialLayout = static_cast<VkImageLayout>(1);
        resolve.finalLayout = static_cast<VkImageLayout>(1);
    }
    if (pass.targets.depth != 0) {
        VkAttachmentDescription& target = descriptions[count];
        depth.attachment = count++;
        depth.layout = VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL;
        target.format = static_cast<VkFormat>(pass.targets.depth);
        target.samples = static_cast<decltype(target.samples)>(pass.targets.samples);
        target.loadOp = static_cast<VkAttachmentLoadOp>(pass.depthLoad);
        target.storeOp = static_cast<VkAttachmentStoreOp>(pass.depthStore);
        target.stencilLoadOp = static_cast<VkAttachmentLoadOp>(pass.stencilLoad);
        target.stencilStoreOp = static_cast<VkAttachmentStoreOp>(pass.stencilStore);
        target.initialLayout = pass.depth < 0 ? (pass.depthLoad == VK_ATTACHMENT_LOAD_OP_LOAD
                ? VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL : VK_IMAGE_LAYOUT_UNDEFINED)
                : static_cast<VkImageLayout>(1);
        target.finalLayout = pass.depth < 0 ? VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL
                : static_cast<VkImageLayout>(1);
    }
    VkSubpassDescription subpass{};
    subpass.pipelineBindPoint = VK_PIPELINE_BIND_POINT_GRAPHICS;
    subpass.colorAttachmentCount = pass.targets.colorCount;
    subpass.pColorAttachments = colors;
    subpass.pResolveAttachments = hasResolve ? resolves : nullptr;
    subpass.pDepthStencilAttachment = pass.targets.depth != 0 ? &depth : nullptr;
    VkSubpassDependency dependencies[2]{};
    dependencies[0].srcSubpass = VK_SUBPASS_EXTERNAL;
    dependencies[0].dstSubpass = 0;
    dependencies[1].srcSubpass = 0;
    dependencies[1].dstSubpass = VK_SUBPASS_EXTERNAL;
    for (int i = 0; i < 2; ++i) {
        dependencies[i].srcStageMask = 0x10000u; // ALL_COMMANDS
        dependencies[i].dstStageMask = 0x10000u;
        dependencies[i].srcAccessMask = 0x10000u; // MEMORY_WRITE
        dependencies[i].dstAccessMask = 0x18000u; // MEMORY_READ | MEMORY_WRITE
    }
    VkRenderPassCreateInfo info{};
    info.sType = VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO;
    info.attachmentCount = count;
    info.pAttachments = descriptions;
    info.subpassCount = 1;
    info.pSubpasses = &subpass;
    info.dependencyCount = 2;
    info.pDependencies = dependencies;
    VkRenderPass result = VK_NULL_HANDLE;
    check(vkCreateRenderPass(context->device, &info, nullptr, &result), "Could not create Vulkan target pass");
    return result;
}

VkRenderPass cachedTargetPass(Context* context, const FdxVulkanPass& pass) {
    std::vector<int32_t>& key = context->targetKey;
    key.clear();
    key.reserve(8 + pass.targets.colorCount * 5);
    key.insert(key.end(), {pass.targets.colorCount, pass.targets.depth, pass.targets.samples,
            pass.depth < 0, pass.depthLoad, pass.depthStore, pass.stencilLoad, pass.stencilStore});
    for (int i = 0; i < pass.targets.colorCount; ++i)
        key.insert(key.end(), {pass.targets.colors[i], pass.colors[i] == 0, pass.resolves[i] != 0,
                pass.load[i], pass.store[i]});
    for (const TargetPass& entry : context->targetPasses)
        if (entry.key == key) return entry.pass;
    TargetPass entry;
    entry.key = key;
    entry.pass = createTargetPass(context, pass);
    context->targetPasses.push_back(entry);
    return entry.pass;
}

VkImageView targetView(Context* context, int64_t value, int level) {
    Texture* texture = ptr<Texture>(value);
    if (texture == nullptr || texture->context != context || level < 0 || level >= texture->mipLevels)
        throw std::runtime_error("Invalid Vulkan target texture or mip level");
    if (texture->layout != static_cast<VkImageLayout>(1))
        transitionTextureLayout(currentFrame(context).commandBuffer, texture, texture->layout,
                static_cast<VkImageLayout>(1)); // GENERAL permits rendering and sampling between passes.
    texture->recordedSerial = context->recordingSerial;
    return texture->mipViews[level];
}

VkFramebuffer targetFramebuffer(Context* context, const FdxVulkanPass& pass, VkRenderPass renderPass) {
    std::vector<VkImageView>& views = context->targetViews;
    views.clear();
    views.reserve(17);
    for (int i = 0; i < pass.targets.colorCount; ++i)
        views.push_back(pass.colors[i] == 0 ? context->imageViews[context->imageIndex]
                : targetView(context, pass.colors[i], pass.colorMips[i]));
    for (int i = 0; i < pass.targets.colorCount; ++i)
        if (pass.resolves[i] != 0) views.push_back(targetView(context, pass.resolves[i], pass.resolveMips[i]));
    if (pass.targets.depth != 0)
        views.push_back(pass.depth < 0 ? context->depthAttachments[context->imageIndex].imageView
                : targetView(context, pass.depth, pass.depthMip));
    for (const TargetFramebuffer& entry : context->targetFramebuffers)
        if (entry.pass == renderPass && entry.width == pass.width && entry.height == pass.height
                && entry.views == views) return entry.framebuffer;
    VkFramebufferCreateInfo info{};
    info.sType = VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO;
    info.renderPass = renderPass;
    info.attachmentCount = static_cast<uint32_t>(views.size());
    info.pAttachments = views.data();
    info.width = pass.width;
    info.height = pass.height;
    info.layers = 1;
    TargetFramebuffer entry;
    check(vkCreateFramebuffer(context->device, &info, nullptr, &entry.framebuffer),
            "Could not create Vulkan target framebuffer");
    entry.pass = renderPass;
    entry.width = pass.width;
    entry.height = pass.height;
    entry.views = views;
    context->targetFramebuffers.push_back(entry);
    return entry.framebuffer;
}

void writeTextureLevel(Texture* texture, int level, const void* data, int size) {
    if (texture == nullptr || level < 0 || level >= texture->mipLevels || texture->samples != 1)
        throw std::runtime_error("Invalid Vulkan texture upload level");
    int bytesPerPixel = texture->format == 97 ? 8 : 4; // R16G16B16A16_SFLOAT
    int width = std::max(1u, texture->width >> level);
    int height = std::max(1u, texture->height >> level);
    if (size != width * height * bytesPerPixel || data == nullptr)
        throw std::runtime_error("Vulkan texture upload byte count does not match its format and mip size");
    Context* context = texture->context;
    bool replace = context->frameStarted && texture->recordedSerial == context->recordingSerial;
    if (replace) replaceTextureForRecordedWrite(texture);
    texture->uploadedLevels[level].assign(static_cast<const uint8_t*>(data), static_cast<const uint8_t*>(data) + size);
    for (int mip = 0; mip < texture->mipLevels; ++mip) {
        if ((!replace && mip != level) || texture->uploadedLevels[mip].empty()) continue;
        const std::vector<uint8_t>& bytes = texture->uploadedLevels[mip];
        TransientBuffer staging = createHostVisibleBuffer(context, bytes.size(), VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
                "Could not create Vulkan mip upload buffer");
        try {
            copyToHostVisibleBuffer(context, &staging, bytes.data(), bytes.size());
            VkCommandBuffer commands = beginSingleTimeCommands(context);
            transitionTextureLayout(commands, texture, texture->layout, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL);
            VkBufferImageCopy region{};
            region.imageSubresource.aspectMask = texture->format == 126 ? VK_IMAGE_ASPECT_DEPTH_BIT : VK_IMAGE_ASPECT_COLOR_BIT;
            region.imageSubresource.mipLevel = mip;
            region.imageSubresource.layerCount = 1;
            region.imageExtent = {std::max(1u, texture->width >> mip), std::max(1u, texture->height >> mip), 1};
            vkCmdCopyBufferToImage(commands, staging.buffer, texture->image, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, 1, &region);
            transitionTextureLayout(commands, texture, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, static_cast<VkImageLayout>(1));
            endSingleTimeCommands(context, commands);
        } catch (...) {
            waitDeviceIdleBeforeDestroy(context, "texture mip upload cleanup");
            destroyHostVisibleBuffer(context, &staging);
            throw;
        }
        destroyHostVisibleBuffer(context, &staging);
    }
}

} // namespace

extern "C" int64_t fdx_desktop_vulkan_create_texture_full(int64_t value, int32_t width, int32_t height,
        int32_t format, int32_t wrapS, int32_t wrapT, int32_t minFilter, int32_t magFilter,
        int32_t mipFilter, int32_t mipLevels, int32_t samples, int32_t usage) {
    try {
        return handle(createTextureResource(ptr<Context>(value), width, height, static_cast<VkFormat>(format),
                wrapS, wrapT, minFilter, magFilter, mipFilter, mipLevels, samples, usage));
    } catch (const std::exception& error) {
        logNativeError("Could not create Vulkan texture", error);
        return 0;
    }
}

extern "C" int32_t fdx_desktop_vulkan_write_texture_level(int64_t value, int32_t level, const void* data, int32_t size) {
    try {
        writeTextureLevel(ptr<Texture>(value), level, data, size);
        return 1;
    } catch (const std::exception& error) {
        logNativeError("Could not upload Vulkan texture", error);
        return 0;
    }
}

extern "C" int32_t fdx_desktop_vulkan_begin_pass_full(int64_t value, const FdxVulkanPass* pass) {
    Context* context = ptr<Context>(value);
    try {
        if (!context->frameStarted || context->renderPassStarted)
            throw std::runtime_error("Vulkan render pass requires an active frame without an open pass");
        VkClearValue clears[17]{};
        int count = pass->targets.colorCount;
        for (int i = 0; i < count; ++i)
            std::copy(pass->clear[i], pass->clear[i] + 4, clears[i].color.float32);
        for (int i = 0; i < pass->targets.colorCount; ++i) if (pass->resolves[i] != 0) ++count;
        if (pass->targets.depth != 0) {
            clears[count].depthStencil.depth = pass->depthClear;
            clears[count++].depthStencil.stencil = pass->stencilClear;
        }
        VkRenderPassBeginInfo info{};
        info.sType = VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO;
        info.renderPass = cachedTargetPass(context, *pass);
        info.framebuffer = targetFramebuffer(context, *pass, info.renderPass);
        info.renderArea.extent = {static_cast<uint32_t>(pass->width), static_cast<uint32_t>(pass->height)};
        info.clearValueCount = count;
        info.pClearValues = clears;
        VkCommandBuffer commands = currentFrame(context).commandBuffer;
        vkCmdBeginRenderPass(commands, &info, VK_SUBPASS_CONTENTS_INLINE);
        VkViewport viewport{};
        viewport.y = static_cast<float>(pass->height);
        viewport.width = static_cast<float>(pass->width);
        viewport.height = -static_cast<float>(pass->height);
        viewport.maxDepth = 1;
        VkRect2D scissor{};
        scissor.extent = info.renderArea.extent;
        vkCmdSetViewport(commands, 0, 1, &viewport);
        vkCmdSetScissor(commands, 0, 1, &scissor);
        context->targetExtent = info.renderArea.extent;
        context->renderPassStarted = true;
        return 1;
    } catch (const std::exception& error) {
        logNativeError("Could not begin Vulkan target pass", error);
        return 0;
    }
}
