package io.github.libfdx.backend.web;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.libfdx.application.ApplicationAdapter;
import org.junit.jupiter.api.Test;

final class WebApplicationConfigTest {
    @Test
    void acceptsAStandardApplicationListenerForPreloadRendering() {
        ApplicationAdapter listener = new ApplicationAdapter();

        WebApplicationConfig config = new WebApplicationConfig().preloadApplication(listener);

        assertSame(listener, config.applicationPreloadListener());
        assertTrue(config.preloadApplicationListener() == null);
    }

    @Test
    void contextAwarePreloadListenerReplacesAStandardListener() {
        ApplicationAdapter applicationListener = new ApplicationAdapter();
        WebPreloadApplicationListener webListener = context -> {
        };
        WebApplicationConfig config = new WebApplicationConfig()
                .preloadApplication(applicationListener)
                .preloadApplicationListener(webListener);

        assertSame(webListener, config.preloadApplicationListener());
        assertTrue(config.applicationPreloadListener() == null);
    }
}
