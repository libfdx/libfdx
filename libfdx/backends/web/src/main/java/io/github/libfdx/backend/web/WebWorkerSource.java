package io.github.libfdx.backend.web;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** Read only by the compiler: TeaVM substitutes source() with an application string constant. */
final class WebWorkerSource {
    static final String RESOURCE = "io/github/libfdx/backend/web/internal/worker.js";
    private WebWorkerSource() { }

    static String source() {
        try (var input = WebWorkerSource.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (input == null) throw new IllegalStateException("Missing compiled backend worker: " + RESOURCE);
            return "(function(root) {\n" + WebShaderCompilerScript.source()
                    + "\nroot.libfdxInstallShaderCompiler = installShaderCompiler;\n})(self);\n"
                    + new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
    }
}
