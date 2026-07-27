package io.github.libfdx.graphics.shadergraph.document;

/**
 * Constants for the self-contained shader-graph document format.
 */
public final class ShaderGraphDocumentFormat {
    /**
     * Version 2 distinguishes the document envelope from the original
     * semantic-only graph JSON that used the same file extension.
     */
    public static final int CURRENT_VERSION = 2;
    public static final String FILE_EXTENSION = ".fdxgraph";

    private ShaderGraphDocumentFormat() {
    }
}
