package io.github.libfdx.backend.web;

import org.teavm.model.MethodReference;
import org.teavm.platform.metadata.ResourceArray;
import org.teavm.platform.plugin.MetadataRegistration;
import org.teavm.vm.spi.TeaVMHost;
import org.teavm.vm.spi.TeaVMPlugin;

public final class WebTeaVMPlugin implements TeaVMPlugin {
    @Override
    public void install(TeaVMHost host) {
        MetadataRegistration registration = host.getService(MetadataRegistration.class);
        if (registration != null) {
            registration.register(new MethodReference(WebGeneratedAssets.class, "assets", ResourceArray.class),
                    new WebAssetMetadataGenerator());
        }
    }
}
