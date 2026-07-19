package io.github.libfdx.graphics.d3d12;

import io.github.libfdx.core.FdxException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class D3D12ConfigurationTest {
    @Test
    void defaultsAreSuitableForPresentation() {
        D3D12Configuration configuration = new D3D12Configuration();

        assertFalse(configuration.validation());
        assertTrue(configuration.vSync());
        assertEquals(2, configuration.framesInFlight());
    }

    @Test
    void fluentConfigurationKeepsTwoOrThreeFramesInFlight() {
        D3D12Configuration configuration = new D3D12Configuration();

        assertSame(configuration, configuration.validation(true));
        assertSame(configuration, configuration.vSync(false));
        assertSame(configuration, configuration.framesInFlight(3));
        assertTrue(configuration.validation());
        assertFalse(configuration.vSync());
        assertEquals(3, configuration.framesInFlight());
        assertThrows(FdxException.class, () -> configuration.framesInFlight(1));
        assertThrows(FdxException.class, () -> configuration.framesInFlight(4));
    }

    @Test
    void providerNullConfigurationRestoresDefaults() {
        D3D12Provider provider = new D3D12Provider()
                .validation(true)
                .vSync(false)
                .framesInFlight(3);

        assertSame(provider, provider.configuration(null));
        assertFalse(provider.configuration().validation());
        assertTrue(provider.configuration().vSync());
        assertEquals(2, provider.configuration().framesInFlight());
    }
}
