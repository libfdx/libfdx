package io.github.libfdx.tools.project.generator.ui;

public interface ProjectExportTarget {
    String destinationLabel();

    String defaultDestination();

    default boolean supportsOverwriteExisting() {
        return true;
    }

    default String overwriteLabel() {
        return "Overwrite";
    }

    ProjectExportResult export(ProjectExportRequest request);
}
