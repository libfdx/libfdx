package io.github.libfdx.graphics.wgpu;

import org.teavm.extension.spi.substitution.SubstitutionPolicy;
import org.teavm.extension.spi.substitution.SubstitutionSink;

/**
 * Provides TeaVM class substitutions required by the WebGPU web runtime.
 *
 * @author xpenatan
 */
public final class WebWGPUSubstitutionPolicy implements SubstitutionPolicy {
    private static final String JAVAX_SCRIPT_PACKAGE = "javax.script";
    private static final String EMULATED_JAVAX_SCRIPT_PACKAGE = "emu.javax.script";

    /**
     * Contributes the WebGPU web class substitutions.
     *
     * @param sink the substitution sink
     */
    @Override
    public void contribute(SubstitutionSink sink) {
        sink.substitutePackage("com", "gen.com");
        sink.substitutePackage("com", "emu.com");
        sink.substitutePackage(JAVAX_SCRIPT_PACKAGE, EMULATED_JAVAX_SCRIPT_PACKAGE);
    }
}
