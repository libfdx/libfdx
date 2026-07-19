package io.github.libfdx.tests;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestChooserApplicationTest {
    @Test
    void formatsGraphicsProviderNamesForDisplay() {
        assertEquals("GL", TestChooserApplication.graphicsDisplayName("gl"));
        assertEquals("WGPU", TestChooserApplication.graphicsDisplayName("wgpu"));
        assertEquals("Vulkan", TestChooserApplication.graphicsDisplayName("vulkan"));
        assertEquals("Direct3D 12", TestChooserApplication.graphicsDisplayName("d3d12"));
        assertEquals("Direct3D 12", TestChooserApplication.graphicsDisplayName("direct3d12"));
        assertEquals("Direct3D 12", TestChooserApplication.graphicsDisplayName("directx12"));
        assertEquals("Direct3D 12", TestChooserApplication.graphicsDisplayName("dx12"));
    }
}
