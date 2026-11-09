package io.github.libfdx.tests;

import io.github.libfdx.graphics.wgpu.WGPUBackend;
import io.github.libfdx.graphics.wgpu.WGPUNativeAsyncPipelineChecks;
import java.util.stream.Stream;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Exercises native async entry points with each loader in a separate JVM. */
@EnabledOnOs(OS.WINDOWS)
@EnabledIfSystemProperty(named = "libfdx.test.nativeWgpuAsync", matches = "true")
@Timeout(25)
final class WGPUNativeAsyncPipelineTest {
    @ParameterizedTest
    @MethodSource("backends")
    void callbacksRejectInvalidEntriesAndValidPipelinesStillRender(WGPUBackend backend) {
        WGPUNativeAsyncPipelineChecks.verify(backend);
    }

    private static Stream<WGPUBackend> backends() {
        // The published Windows Dawn package builds only D3D12; wgpu-native also includes Vulkan.
        return "DAWN".equals(System.getProperty("libfdx.test.wgpuLoader", "DAWN"))
                ? Stream.of(WGPUBackend.D3D12) : Stream.of(WGPUBackend.D3D12, WGPUBackend.VULKAN);
    }

}
