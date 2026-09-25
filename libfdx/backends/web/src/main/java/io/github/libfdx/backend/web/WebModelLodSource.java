package io.github.libfdx.backend.web;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** Compiler-only resource reader replaced with a string constant by the web plugin. */
final class WebModelLodSource {
    private WebModelLodSource() { }
    static String source() {
        String path = "io/github/libfdx/backend/web/internal/lod-worker.js";
        try(var stream = WebModelLodSource.class.getClassLoader().getResourceAsStream(path)) {
            if(stream == null) throw new IllegalStateException("Missing compiled LOD worker: " + path);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch(IOException error) { throw new UncheckedIOException(error); }
    }
}
