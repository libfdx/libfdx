package io.github.libfdx.tools.shader;

import io.github.libfdx.graphics.ShaderValidationDiagnostic;

import java.nio.file.Path;

/**
 * Represents a report produced by a fdx shader validation.
 *
 * @author xpenatan
 */
public final class FdxShaderValidationReport {
    private static final Entry[] EMPTY_ENTRIES = new Entry[0];

    private final Entry[] entries;

    private FdxShaderValidationReport(Entry[] entries) {
        this.entries = entries != null ? entries.clone() : EMPTY_ENTRIES;
    }

    /**
     * Creates a fdx shader validation report from the supplied values.
     *
     * @param entries the entries
     * @return a new fdx shader validation report
     */
    public static FdxShaderValidationReport of(Entry[] entries) {
        return new FdxShaderValidationReport(entries);
    }

    /**
     * Returns the entries.
     *
     * @return the entries
     */
    public Entry[] entries() {
        return entries.clone();
    }

    /**
     * Returns the error count.
     *
     * @return the error count
     */
    public int errorCount() {
        int count = 0;
        for (Entry entry : entries) {
            count += entry.errorCount();
        }
        return count;
    }

    /**
     * Returns the success.
     *
     * @return true if success succeeds or is active; false otherwise
     */
    public boolean success() {
        return errorCount() == 0;
    }

    /**
     * Runs the to markdown step.
     *
     * @param root the root
     * @return the to markdown
     */
    public String toMarkdown(Path root) {
        StringBuilder builder = new StringBuilder();
        builder.append("# libFDX Shader Validation\n\n");
        builder.append("status: ").append(success() ? "PASS" : "FAIL").append('\n');
        builder.append("shaders: ").append(entries.length).append('\n');
        builder.append("errors: ").append(errorCount()).append("\n\n");
        for (Entry entry : entries) {
            builder.append("## ").append(relative(root, entry.path())).append('\n');
            builder.append("profile: ").append(entry.profileId()).append('\n');
            if (entry.diagnostics().length == 0) {
                builder.append("result: PASS\n\n");
                continue;
            }
            builder.append("result: FAIL\n");
            for (ShaderValidationDiagnostic diagnostic : entry.diagnostics()) {
                builder.append("- ")
                        .append(diagnostic.severity())
                        .append(' ')
                        .append(diagnostic.code())
                        .append(": ")
                        .append(diagnostic.message())
                        .append('\n');
            }
            builder.append('\n');
        }
        return builder.toString();
    }

    private static String relative(Path root, Path path) {
        if (root == null || path == null) {
            return String.valueOf(path);
        }
        try {
            return root.toAbsolutePath().normalize().relativize(path.toAbsolutePath().normalize()).toString()
                    .replace('\\', '/');
        } catch (IllegalArgumentException ignored) {
            return path.toString().replace('\\', '/');
        }
    }

    /**
     * Represents an entry.
     *
     * @author xpenatan
     */
    public static final class Entry {
        private final Path path;
        private final String profileId;
        private final ShaderValidationDiagnostic[] diagnostics;

        private Entry(Path path, String profileId, ShaderValidationDiagnostic[] diagnostics) {
            this.path = path;
            this.profileId = profileId;
            this.diagnostics = diagnostics != null ? diagnostics.clone() : new ShaderValidationDiagnostic[0];
        }

        /**
         * Creates an entry from the supplied values.
         *
         * @param path the asset or file path
         * @param profileId the profile ID
         * @param diagnostics the diagnostics
         * @return a new entry
         */
        public static Entry of(Path path, String profileId, ShaderValidationDiagnostic[] diagnostics) {
            return new Entry(path, profileId, diagnostics);
        }

        /**
         * Returns the path.
         *
         * @return the path
         */
        public Path path() {
            return path;
        }

        /**
         * Returns the profile ID.
         *
         * @return the profile ID
         */
        public String profileId() {
            return profileId;
        }

        /**
         * Returns the diagnostics.
         *
         * @return the diagnostics
         */
        public ShaderValidationDiagnostic[] diagnostics() {
            return diagnostics.clone();
        }

        /**
         * Returns the error count.
         *
         * @return the error count
         */
        public int errorCount() {
            int count = 0;
            for (ShaderValidationDiagnostic diagnostic : diagnostics) {
                if (diagnostic.severity() == io.github.libfdx.graphics.ShaderValidationSeverity.ERROR) {
                    count++;
                }
            }
            return count;
        }
    }
}
