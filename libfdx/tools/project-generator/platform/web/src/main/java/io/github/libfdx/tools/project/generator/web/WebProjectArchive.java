package io.github.libfdx.tools.project.generator.web;

import io.github.libfdx.tools.project.generator.GeneratedFile;
import io.github.libfdx.tools.project.generator.GeneratedProject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class WebProjectArchive {
    private WebProjectArchive() {
    }

    static byte[] zip(GeneratedProject project) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            for (int i = 0; i < project.files().size(); i++) {
                GeneratedFile file = project.files().get(i);
                ZipEntry entry = new ZipEntry(file.path());
                entry.setTime(0L);
                zip.putNextEntry(entry);
                if (file.isText()) {
                    zip.write(file.textContent().getBytes(StandardCharsets.UTF_8));
                } else {
                    zip.write(file.binaryContent());
                }
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }
}
