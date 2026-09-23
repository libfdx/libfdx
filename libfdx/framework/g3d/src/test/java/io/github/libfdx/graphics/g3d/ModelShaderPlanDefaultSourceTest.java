package io.github.libfdx.graphics.g3d;

import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.GraphicsCapabilities;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.GraphicsDevice;
import io.github.libfdx.graphics.shader.ShaderProfile;
import io.github.libfdx.graphics.shader.runtime.ShaderProvider;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public final class ModelShaderPlanDefaultSourceTest {
    private static final class Source implements StandardPbrSourcePreparer.Owned {
        int disposals;
        @Override public FdxFuture<StandardPbrSources> prepare(ShaderProfile profile, Consumer<Runnable> execute) {
            throw new AssertionError("Construction must not compile sources");
        }
        @Override public void dispose() { disposals++; }
        @Override public boolean isDisposed() { return disposals != 0; }
    }

    @Test void ownedStrategiesAreIndependentAndDisposedOnce() {
        var sources = new ArrayList<Source>();
        Supplier<StandardPbrSourcePreparer.Owned> factory = () -> {
            Source source = new Source(); sources.add(source); return source;
        };
        var first = new ModelShaderPlan(graphics(() -> false), null, null, factory);
        var second = new ModelShaderPlan(graphics(() -> false), null, null, factory);
        assertEquals(2, sources.size());
        first.dispose(); first.dispose();
        assertEquals(1, sources.get(0).disposals);
        assertEquals(0, sources.get(1).disposals);
        second.dispose();
        assertEquals(1, sources.get(1).disposals);
    }

    @Test void explicitStrategiesAndCustomProvidersRemainBorrowed() {
        Source borrowed = new Source();
        new ModelShaderPlan(graphics(() -> false), null, borrowed).dispose();
        new ModelShaderPlan(graphics(() -> false), null, null).dispose();
        new ModelShaderPlan(graphics(() -> false), new ShaderProvider() {}, null,
                () -> { throw new AssertionError("Custom provider invoked the default factory"); }).dispose();
        assertEquals(0, borrowed.disposals);
    }

    @Test void constructionFailureReleasesOwnedStrategy() {
        Source owned = new Source(); boolean[] created = {false};
        assertThrows(IllegalStateException.class, () -> new ModelShaderPlan(graphics(() -> created[0]),
                null, null, () -> { created[0] = true; return owned; }));
        assertEquals(1, owned.disposals);
    }

    @Test void nativeDefaultsConstructWithoutWorkersOrGpuWork() {
        new ModelShaderPlan(graphics(() -> false)).dispose();
        new ModelShaderPlan(graphics(() -> false), null).dispose();
    }

    private static GraphicsContext graphics(BooleanSupplier failTechnique) {
        Object domain = new Object();
        GraphicsDevice device = (GraphicsDevice) Proxy.newProxyInstance(GraphicsDevice.class.getClassLoader(),
                new Class<?>[]{GraphicsDevice.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "resourceDomain" -> domain;
                    case "capabilities" -> {
                        if (failTechnique.getAsBoolean()) throw new IllegalStateException("Injected setup failure");
                        yield GraphicsCapabilities.conservativeRender();
                    }
                    default -> throw new AssertionError(method.getName());
                });
        return (GraphicsContext) Proxy.newProxyInstance(GraphicsContext.class.getClassLoader(),
                new Class<?>[]{GraphicsContext.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "device" -> device;
                    case "providerId" -> ProviderId.of("gl");
                    default -> throw new AssertionError(method.getName());
                });
    }
}
