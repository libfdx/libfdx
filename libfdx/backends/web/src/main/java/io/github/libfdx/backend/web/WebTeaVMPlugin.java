package io.github.libfdx.backend.web;

import org.teavm.model.MethodReference;
import org.teavm.platform.metadata.ResourceArray;
import org.teavm.platform.plugin.MetadataRegistration;
import org.teavm.vm.spi.TeaVMHost;
import org.teavm.vm.spi.TeaVMPlugin;

/**
 * Represents a web tea VM plugin.
 *
 * @author xpenatan
 */
public final class WebTeaVMPlugin implements TeaVMPlugin {
    /**
     * Runs the install step.
     *
     * @param host the host
     */
    @Override
    public void install(TeaVMHost host) {
        MetadataRegistration registration = host.getService(MetadataRegistration.class);
        if (registration != null) {
            registration.register(new MethodReference(WebGeneratedAssets.class, "assets", ResourceArray.class),
                    new WebAssetMetadataGenerator());
        }
    }
}
