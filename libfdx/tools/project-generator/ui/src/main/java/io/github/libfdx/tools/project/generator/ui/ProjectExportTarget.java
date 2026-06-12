package io.github.libfdx.tools.project.generator.ui;

/**
 * Defines the contract for project export target implementations.
 *
 * @author xpenatan
 */
public interface ProjectExportTarget {
    /**
     * Returns the destination label.
     *
     * @return the destination label
     */
    String destinationLabel();

    /**
     * Returns the default destination.
     *
     * @return the default destination
     */
    String defaultDestination();

    /**
     * Returns the supports overwrite existing.
     *
     * @return true if supports overwrite existing succeeds or is active; false otherwise
     */
    default boolean supportsOverwriteExisting() {
        return true;
    }

    /**
     * Returns the overwrite label.
     *
     * @return the overwrite label
     */
    default String overwriteLabel() {
        return "Overwrite";
    }

    /**
     * Runs the export step.
     *
     * @param request the request
     * @return the export
     */
    ProjectExportResult export(ProjectExportRequest request);
}
