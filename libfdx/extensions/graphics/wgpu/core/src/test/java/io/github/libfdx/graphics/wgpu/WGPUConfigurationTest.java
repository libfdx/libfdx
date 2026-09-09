package io.github.libfdx.graphics.wgpu;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class WGPUConfigurationTest {
    @Test void platformDefaultsRespectAvailableProcessorsAndExplicitOverrides() {
        WGPUConfiguration configuration = new WGPUConfiguration();
        int available = Runtime.getRuntime().availableProcessors();
        assertEquals(Math.min(2, available), configuration.preparationWorkerLimitOrDefault(2));
        assertEquals(Math.min(8, available), configuration.preparationWorkerLimit());
        configuration.preparationWorkerLimit(4);
        assertEquals(4, configuration.preparationWorkerLimitOrDefault(2));
        assertEquals(4, configuration.preparationWorkerLimit());
        assertThrows(IllegalArgumentException.class, () -> configuration.preparationWorkerLimit(0));
    }
}
