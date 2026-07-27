package io.github.libfdx.samples.shadergraph;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.shadergraph.document.ShaderGraphDocument;
import io.github.libfdx.graphics.shadergraph.document.ShaderGraphDocumentCodec;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Generates the sample asset from the direct Java authoring path.
 */
public final class ShaderGraphSampleAssetGenerator {
    private ShaderGraphSampleAssetGenerator() {
    }

    /**
     * Writes the deterministic graph JSON to the requested path.
     *
     * @param args one output path
     */
    public static void main(String[] args) {
        if (args == null || args.length != 1
                || args[0] == null || args[0].isBlank()) {
            throw new FdxException(
                    "Expected one shader graph output path");
        }
        Path output = Path.of(args[0]).toAbsolutePath().normalize();
        try {
            Path parent = output.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(output,
                    ShaderGraphDocumentCodec.write(
                            ShaderGraphDocument.of(
                                    ShaderGraphSampleGraphs
                                            .codeAuthoredSurface()))
                            + System.lineSeparator(),
                    StandardCharsets.UTF_8);
            System.out.println("Generated " + output);
        } catch (IOException failure) {
            throw new FdxException(
                    "Could not generate shader graph sample asset "
                            + output,
                    failure);
        }
    }
}
