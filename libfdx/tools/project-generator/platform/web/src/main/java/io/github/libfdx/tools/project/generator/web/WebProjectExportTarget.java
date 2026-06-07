package io.github.libfdx.tools.project.generator.web;

import io.github.libfdx.tools.project.generator.GeneratedProject;
import io.github.libfdx.tools.project.generator.ui.ProjectExportRequest;
import io.github.libfdx.tools.project.generator.ui.ProjectExportResult;
import io.github.libfdx.tools.project.generator.ui.ProjectExportTarget;
import java.io.IOException;
import java.util.Base64;
import org.teavm.jso.JSBody;

public final class WebProjectExportTarget implements ProjectExportTarget {
    @Override
    public String destinationLabel() {
        return "Download file";
    }

    @Override
    public String defaultDestination() {
        return "libfdx-game.zip";
    }

    @Override
    public boolean supportsOverwriteExisting() {
        return false;
    }

    @Override
    public ProjectExportResult export(ProjectExportRequest request) {
        if (request == null || request.project() == null) {
            return ProjectExportResult.failure("No generated project was provided.");
        }
        GeneratedProject project = request.project();
        String fileName = downloadFileName(request.destination(), project.name());
        try {
            byte[] archive = WebProjectArchive.zip(project);
            String base64 = Base64.getEncoder().encodeToString(archive);
            if (!downloadZip(fileName, base64)) {
                return ProjectExportResult.failure("Browser download is not available.");
            }
            return ProjectExportResult.success("downloaded " + fileName + " with " + project.fileCount() + " files");
        } catch (IOException | RuntimeException error) {
            return ProjectExportResult.failure(error.getMessage());
        }
    }

    private static String downloadFileName(String requested, String projectName) {
        String value = requested != null && requested.trim().length() > 0 ? requested.trim() : projectName + ".zip";
        value = value.replace('\\', '/');
        int slash = value.lastIndexOf('/');
        if (slash >= 0) {
            value = value.substring(slash + 1);
        }
        StringBuilder clean = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c <= 31 || c == ':' || c == '*' || c == '?' || c == '"' || c == '<' || c == '>' || c == '|') {
                clean.append('_');
            } else {
                clean.append(c);
            }
        }
        String fileName = clean.toString().trim();
        if (fileName.length() == 0) {
            fileName = "libfdx-game.zip";
        }
        return fileName.toLowerCase().endsWith(".zip") ? fileName : fileName + ".zip";
    }

    @JSBody(params = { "fileName", "base64" }, script =
            "try {\n" +
            "  if (typeof document === 'undefined' || typeof Blob === 'undefined' || typeof URL === 'undefined') {\n" +
            "    return false;\n" +
            "  }\n" +
            "  var binary = atob(base64 || '');\n" +
            "  var bytes = new Uint8Array(binary.length);\n" +
            "  for (var i = 0; i < binary.length; i++) {\n" +
            "    bytes[i] = binary.charCodeAt(i) & 255;\n" +
            "  }\n" +
            "  var blob = new Blob([bytes], { type: 'application/zip' });\n" +
            "  var url = URL.createObjectURL(blob);\n" +
            "  var anchor = document.createElement('a');\n" +
            "  anchor.href = url;\n" +
            "  anchor.download = fileName || 'libfdx-game.zip';\n" +
            "  anchor.style.display = 'none';\n" +
            "  document.body.appendChild(anchor);\n" +
            "  anchor.click();\n" +
            "  setTimeout(function() {\n" +
            "    URL.revokeObjectURL(url);\n" +
            "    if (anchor.parentNode) anchor.parentNode.removeChild(anchor);\n" +
            "  }, 0);\n" +
            "  return true;\n" +
            "} catch (error) {\n" +
            "  if (typeof console !== 'undefined' && console.error) console.error(error);\n" +
            "  return false;\n" +
            "}")
    private static native boolean downloadZip(String fileName, String base64);
}
