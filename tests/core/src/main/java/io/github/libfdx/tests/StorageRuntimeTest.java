package io.github.libfdx.tests;

import io.github.libfdx.Fdx;
import io.github.libfdx.application.Application;
import io.github.libfdx.application.ApplicationAdapter;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.json.JsonValue;
import io.github.libfdx.storage.KeyValueStore;
import io.github.libfdx.storage.Storage;

/**
 * Validates the runtime storage service.
 *
 * @author xpenatan
 */
public final class StorageRuntimeTest extends ApplicationAdapter {
    private final long exitAfterFrames;
    private Application app;
    private long renderedFrames;

    /**
     * Creates a storage runtime test.
     *
     * @param exitAfterFrames the exit frame count
     */
    public StorageRuntimeTest(long exitAfterFrames) {
        this.exitAfterFrames = exitAfterFrames;
    }

    /**
     * Initializes the application with the libFDX runtime root.
     *
     * @param fdx the libFDX runtime root
     */
    @Override
    public void create(Fdx fdx) {
        app = fdx.app();
        Storage storage = fdx.storage();
        if (storage == null) {
            throw new FdxException("Fdx.storage() is not available");
        }

        String storeName = System.getProperty("libfdx.test.storageName", "runtime-storage-test");
        KeyValueStore local = storage.local(storeName).load();
        int run = local.getInt("run", 0) + 1;
        local.putInt("run", run)
                .putString("scope", "local")
                .putJson("json", JsonValue.object().put("valid", true).put("run", run))
                .flush();

        KeyValueStore reopenedLocal = storage.local(storeName).load();
        if (reopenedLocal.getInt("run", 0) != run
                || !"local".equals(reopenedLocal.getString("scope", ""))
                || !reopenedLocal.getJson("json", JsonValue.object()).booleanValue("valid", false)) {
            throw new FdxException("Local storage did not round-trip values");
        }

        KeyValueStore cache = storage.cache(storeName).load();
        cache.putBoolean("valid", true).flush();
        if (!storage.cache(storeName).load().getBoolean("valid", false)) {
            throw new FdxException("Cache storage did not round-trip values");
        }

        fdx.logger().info("StorageRuntimeTest stored run " + run + " in " + storage.providerId());
    }

    /**
     * Renders one validation frame.
     */
    @Override
    public void render() {
        renderedFrames++;
        if (exitAfterFrames > 0L && renderedFrames >= exitAfterFrames) {
            app.requestExit();
        }
    }
}
