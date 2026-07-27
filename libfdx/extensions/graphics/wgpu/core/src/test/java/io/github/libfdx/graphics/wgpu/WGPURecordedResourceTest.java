package io.github.libfdx.graphics.wgpu;

import com.github.xpenatan.webgpu.WGPUBindGroupLayout;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.BufferUsage;
import io.github.libfdx.graphics.shader.ShaderLanguage;
import io.github.libfdx.graphics.TextureFilter;
import io.github.libfdx.graphics.TextureFormat;
import io.github.libfdx.graphics.TextureUsage;
import io.github.libfdx.graphics.TextureWrap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class WGPURecordedResourceTest {
    @Test
    void resourceDomainKeepsNativeOwnerAliveUntilEveryContextCloses() {
        WGPUResourceDomain domain = new WGPUResourceDomain();
        int[] releaseCount = {0};
        domain.setNativeRelease(() -> releaseCount[0]++);
        domain.retainContext();
        domain.retainContext();

        domain.releaseContext();
        assertEquals(1, domain.contextReferences());
        assertEquals(0, releaseCount[0]);
        assertFalse(domain.isClosed());

        domain.releaseContext();
        assertEquals(0, domain.contextReferences());
        assertEquals(1, releaseCount[0]);
        assertTrue(domain.isClosed());
        assertThrows(FdxException.class, domain::retainContext);
        assertThrows(FdxException.class, domain::releaseContext);
    }

    @Test
    void oneContextMarksTheSameResourceOnlyOnce() {
        WGPUResourceDomain domain = new WGPUResourceDomain();
        TestResource resource = new TestResource(domain);
        WGPURecordedResources recording = new WGPURecordedResources();

        recording.mark(resource);
        recording.mark(resource);

        assertEquals(1, recording.size());
        assertEquals(1, resource.recordingReferences());
        recording.releaseAll();
        assertEquals(0, resource.recordingReferences());
        assertEquals(0, resource.releaseCount);
    }

    @Test
    void retiredResourceWaitsForEveryRecordingContext() {
        WGPUResourceDomain domain = new WGPUResourceDomain();
        TestResource resource = new TestResource(domain);
        WGPURecordedResources first = new WGPURecordedResources();
        WGPURecordedResources second = new WGPURecordedResources();
        first.mark(resource);
        second.mark(resource);

        resource.retire();
        assertTrue(resource.isRetired());
        assertFalse(resource.isReleased());
        first.releaseAll();
        assertFalse(resource.isReleased());
        second.releaseAll();

        assertTrue(resource.isReleased());
        assertEquals(1, resource.releaseCount);
    }

    @Test
    void retirementHookRunsOnceBeforeDeferredNativeRelease() {
        WGPUResourceDomain domain = new WGPUResourceDomain();
        HookResource resource = new HookResource(domain, false);
        WGPURecordedResources recording = new WGPURecordedResources();
        recording.mark(resource);

        resource.retire();
        resource.retire();
        assertEquals(1, resource.retirementCount);
        assertEquals(0, resource.releaseCount);

        recording.releaseAll();
        assertEquals(1, resource.retirementCount);
        assertEquals(1, resource.releaseCount);
    }

    @Test
    void retirementPreservesHookAndNativeReleaseFailures() {
        HookResource resource = new HookResource(new WGPUResourceDomain(), true);

        FdxException failure = assertThrows(FdxException.class, resource::retire);

        assertEquals("retirement", failure.getMessage());
        assertEquals(1, failure.getSuppressed().length);
        assertEquals("release", failure.getSuppressed()[0].getMessage());
        assertEquals(1, resource.retirementCount);
        assertEquals(1, resource.releaseCount);
    }

    @Test
    void recordingReleaseContinuesCleanupAndPreservesEveryFailure() {
        WGPUResourceDomain domain = new WGPUResourceDomain();
        FailingResource first = new FailingResource(domain, "first");
        TestResource middle = new TestResource(domain);
        FailingResource last = new FailingResource(domain, "last");
        WGPURecordedResources recording = new WGPURecordedResources();
        recording.mark(first);
        recording.mark(middle);
        recording.mark(last);
        first.retire();
        middle.retire();
        last.retire();

        FdxException failure = assertThrows(FdxException.class, recording::releaseAll);

        assertEquals("first", failure.getMessage());
        assertEquals(1, failure.getSuppressed().length);
        assertEquals("last", failure.getSuppressed()[0].getMessage());
        assertEquals(1, first.releaseCount);
        assertEquals(1, middle.releaseCount);
        assertEquals(1, last.releaseCount);
        assertEquals(0, recording.size());
    }

    @Test
    void replacingRecordedBufferRetiresOnlyItsOldAllocation() {
        WGPUResourceDomain domain = new WGPUResourceDomain();
        WGPUBufferAllocation oldAllocation = new WGPUBufferAllocation(domain, null);
        WGPUBufferAllocation newAllocation = new WGPUBufferAllocation(domain, null);
        WGPUBufferHandle handle = new WGPUBufferHandle(domain, oldAllocation, "buffer", 16, BufferUsage.VERTEX);
        WGPURecordedResources recording = new WGPURecordedResources();
        recording.mark(oldAllocation);

        handle.replaceAllocation(newAllocation);

        assertTrue(oldAllocation.isRetired());
        assertFalse(oldAllocation.isReleased());
        assertFalse(newAllocation.isRetired());
        assertSame(newAllocation, handle.allocation());
        recording.releaseAll();
        assertTrue(oldAllocation.isReleased());
        assertFalse(newAllocation.isReleased());

        handle.dispose();
        assertTrue(newAllocation.isReleased());
    }

    @Test
    void bufferValidationRejectsForeignAndDisposedHandles() {
        WGPUResourceDomain firstDomain = new WGPUResourceDomain();
        WGPUResourceDomain secondDomain = new WGPUResourceDomain();
        WGPUBufferHandle handle = new WGPUBufferHandle(firstDomain,
                new WGPUBufferAllocation(firstDomain, null), "buffer", 16, BufferUsage.VERTEX);

        assertSame(handle, WGPUResources.requireBuffer(handle, firstDomain, "Buffer"));
        assertThrows(FdxException.class,
                () -> WGPUResources.requireBuffer(handle, secondDomain, "Buffer"));

        handle.dispose();
        assertThrows(FdxException.class,
                () -> WGPUResources.requireBuffer(handle, firstDomain, "Buffer"));
    }

    @Test
    void replacingRecordedTextureRetiresOnlyItsOldAllocation() {
        WGPUResourceDomain domain = new WGPUResourceDomain();
        WGPUTextureAllocation oldAllocation =
                new WGPUTextureAllocation(domain, null, null, null, null);
        WGPUTextureAllocation newAllocation =
                new WGPUTextureAllocation(domain, null, null, null, null);
        WGPUTextureHandle handle = texture(domain, oldAllocation);
        WGPURecordedResources first = new WGPURecordedResources();
        WGPURecordedResources second = new WGPURecordedResources();
        first.mark(oldAllocation);
        second.mark(oldAllocation);

        handle.replaceAllocation(newAllocation);
        assertTrue(oldAllocation.isRetired());
        assertFalse(oldAllocation.isReleased());
        first.releaseAll();
        assertFalse(oldAllocation.isReleased());
        second.releaseAll();
        assertTrue(oldAllocation.isReleased());
        assertSame(newAllocation, handle.allocation());

        handle.dispose();
        assertTrue(newAllocation.isReleased());
    }

    @Test
    void pipelineDisposalWaitsForEveryRecordingContext() {
        WGPUResourceDomain domain = new WGPUResourceDomain();
        WGPURenderPipelineHandle pipeline = new WGPURenderPipelineHandle(
                domain, null, null, null, new WGPUBindGroupLayout[0],
                0, -1, 0, null, null);
        WGPURecordedResources first = new WGPURecordedResources();
        WGPURecordedResources second = new WGPURecordedResources();
        first.mark(pipeline);
        second.mark(pipeline);

        pipeline.dispose();
        assertTrue(pipeline.isDisposed());
        assertFalse(pipeline.isReleased());
        first.releaseAll();
        assertFalse(pipeline.isReleased());
        second.releaseAll();
        assertTrue(pipeline.isReleased());
    }

    @Test
    void textureShaderAndPipelineValidationRejectForeignAndDisposedHandles() {
        WGPUResourceDomain firstDomain = new WGPUResourceDomain();
        WGPUResourceDomain secondDomain = new WGPUResourceDomain();
        WGPUTextureHandle texture = texture(firstDomain,
                new WGPUTextureAllocation(
                        firstDomain, null, null, null, null));
        WGPUShaderModuleHandle shader = new WGPUShaderModuleHandle(firstDomain, null, ShaderLanguage.WGSL);
        WGPURenderPipelineHandle pipeline = new WGPURenderPipelineHandle(
                firstDomain, null, null, null,
                new WGPUBindGroupLayout[0], 0, -1, 0, null, null);

        assertSame(texture, WGPUResources.requireTexture(texture, firstDomain, "Texture"));
        assertSame(shader, WGPUResources.requireShaderModule(shader, firstDomain, "Shader"));
        assertSame(pipeline, WGPUResources.requirePipeline(pipeline, firstDomain, "Pipeline"));
        assertThrows(FdxException.class,
                () -> WGPUResources.requireTexture(texture, secondDomain, "Texture"));
        assertThrows(FdxException.class,
                () -> WGPUResources.requireShaderModule(shader, secondDomain, "Shader"));
        assertThrows(FdxException.class,
                () -> WGPUResources.requirePipeline(pipeline, secondDomain, "Pipeline"));

        texture.dispose();
        shader.dispose();
        pipeline.dispose();
        assertThrows(FdxException.class,
                () -> WGPUResources.requireTexture(texture, firstDomain, "Texture"));
        assertThrows(FdxException.class,
                () -> WGPUResources.requireShaderModule(shader, firstDomain, "Shader"));
        assertThrows(FdxException.class,
                () -> WGPUResources.requirePipeline(pipeline, firstDomain, "Pipeline"));
    }

    private static WGPUTextureHandle texture(WGPUResourceDomain domain, WGPUTextureAllocation allocation) {
        return new WGPUTextureHandle(domain, allocation, "texture", 2, 2, 1, 1, TextureFormat.RGBA8_UNORM,
                TextureUsage.SAMPLED, TextureFilter.LINEAR, TextureWrap.CLAMP_TO_EDGE, TextureWrap.CLAMP_TO_EDGE);
    }

    private static final class TestResource extends WGPURecordedResource {
        private int releaseCount;

        TestResource(WGPUResourceDomain resourceDomain) {
            super(resourceDomain);
        }

        @Override
        protected void releaseNative() {
            releaseCount++;
        }
    }

    private static final class FailingResource extends WGPURecordedResource {
        private final String message;
        private int releaseCount;

        FailingResource(WGPUResourceDomain resourceDomain, String message) {
            super(resourceDomain);
            this.message = message;
        }

        @Override
        protected void releaseNative() {
            releaseCount++;
            throw new FdxException(message);
        }
    }

    private static final class HookResource extends WGPURecordedResource {
        private final boolean fail;
        private int retirementCount;
        private int releaseCount;

        HookResource(WGPUResourceDomain resourceDomain, boolean fail) {
            super(resourceDomain);
            this.fail = fail;
        }

        @Override
        protected void onRetired() {
            retirementCount++;
            if (fail) {
                throw new FdxException("retirement");
            }
        }

        @Override
        protected void releaseNative() {
            releaseCount++;
            if (fail) {
                throw new FdxException("release");
            }
        }
    }
}
