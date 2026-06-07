package io.github.libfdx.tools.project.generator.desktop;

import io.github.libfdx.tools.project.generator.GeneratedFile;
import io.github.libfdx.tools.project.generator.GeneratedProject;
import io.github.libfdx.tools.project.generator.ui.ProjectExportRequest;
import io.github.libfdx.tools.project.generator.ui.ProjectExportResult;
import io.github.libfdx.tools.project.generator.ui.ProjectExportTarget;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class DesktopProjectExportTarget implements ProjectExportTarget {
    private final String defaultDestination;

    public DesktopProjectExportTarget(String defaultDestination) {
        this.defaultDestination = defaultDestination != null ? defaultDestination : "";
    }

    @Override
    public String destinationLabel() {
        return "Output directory";
    }

    @Override
    public String defaultDestination() {
        return defaultDestination;
    }

    @Override
    public ProjectExportResult export(ProjectExportRequest request) {
        if (request == null || request.project() == null) {
            return ProjectExportResult.failure("No generated project was provided.");
        }
        if (request.destination().length() == 0) {
            return ProjectExportResult.failure("Output directory cannot be empty.");
        }
        try {
            Path root = Paths.get(request.destination()).toAbsolutePath().normalize();
            GeneratedProject project = request.project();
            validateTargets(root, project, request.overwriteExisting());
            writeFiles(root, project);
            return ProjectExportResult.success("wrote " + project.fileCount() + " files to " + root);
        } catch (IOException | RuntimeException error) {
            return ProjectExportResult.failure(error.getMessage());
        }
    }

    private void validateTargets(Path root, GeneratedProject project, boolean overwriteExisting) throws IOException {
        if (Files.exists(root) && !Files.isDirectory(root)) {
            throw new IOException("Output path exists but is not a directory: " + root);
        }
        for (int i = 0; i < project.files().size(); i++) {
            GeneratedFile file = project.files().get(i);
            Path target = root.resolve(file.path()).normalize();
            if (!target.startsWith(root)) {
                throw new IOException("Refusing to write outside output directory: " + file.path());
            }
            if (!overwriteExisting && Files.exists(target)) {
                throw new IOException("File already exists: " + target);
            }
        }
    }

    private void writeFiles(Path root, GeneratedProject project) throws IOException {
        Files.createDirectories(root);
        for (int i = 0; i < project.files().size(); i++) {
            GeneratedFile file = project.files().get(i);
            Path target = root.resolve(file.path()).normalize();
            Files.createDirectories(target.getParent());
            if (file.isText()) {
                Files.write(target, file.textContent().getBytes(StandardCharsets.UTF_8));
            } else {
                Files.write(target, file.binaryContent());
            }
        }
    }
}
