package io.github.libfdx.graphics.wgpu;

import com.github.xpenatan.webgpu.WGPUBindGroupLayout;
import io.github.libfdx.core.FdxException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

final class WGPUDeviceLossDomainTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void lossClosesAdmissionButWaitsForContextsAndNativeWork(boolean workFirst) {
        WGPUResourceDomain domain = new WGPUResourceDomain();
        int[] releases = {0};
        domain.setNativeRelease(() -> releases[0]++);
        domain.retainContext();
        domain.retainContext();
        domain.retainPreparation();
        domain.deviceLost("first loss");
        domain.deviceLost("duplicate notification");
        assertEquals("first loss", domain.deviceLoss());
        assertTrue(domain.isClosed());
        assertThrows(FdxException.class, domain::retainContext);
        assertThrows(FdxException.class, domain::retainPreparation);
        assertEquals(0, releases[0]);
        domain.releaseContext();
        if (workFirst) domain.releasePreparation();
        else domain.releaseContext();
        assertEquals(0, releases[0]);
        if (workFirst) domain.releaseContext();
        else domain.releasePreparation();
        assertEquals(1, releases[0]);
        domain.deviceLost("late duplicate");
        assertEquals(1, releases[0]);
    }

    @Test
    void anAlreadyPreparedPipelineCannotBeUsedAfterLossButCanBeDisposed() {
        WGPUResourceDomain domain = new WGPUResourceDomain();
        WGPURenderPipelineHandle pipeline = new WGPURenderPipelineHandle(domain, null, null, null,
                new WGPUBindGroupLayout[0], 0, -1, 0, null, null);
        assertSame(pipeline, WGPUResources.requirePipeline(pipeline, domain, "Pipeline"));
        domain.deviceLost("lost");
        assertThrows(FdxException.class, () -> WGPUResources.requirePipeline(pipeline, domain, "Pipeline"));
        pipeline.dispose();
        pipeline.dispose();
        assertTrue(pipeline.isDisposed());
    }
}
