package io.github.libfdx.backend.android;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AndroidShaderCompilerIdentityTest {
    @Test void extractedAndApkResidentLibrariesShareOnlyTheirExactBinaryIdentity() throws Exception {
        Path build = Paths.get("build").toAbsolutePath();
        Files.createDirectories(build);
        Path directory = Files.createTempDirectory(build, "android-compiler-identity-");
        Path library = directory.resolve("libfdx.so"), archive = directory.resolve("test.apk");
        byte[] binary = {1, 3, 5, 7};
        Files.write(library, binary);
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            zip.putNextEntry(new ZipEntry("lib/arm64-v8a/libfdx.so"));
            zip.write(binary);
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("lib/x86_64/libfdx.so"));
            zip.write(new byte[]{2, 4, 6, 8});
            zip.closeEntry();
        }
        String extracted = AndroidShaderCompilerIdentity.read(library.toString());
        assertNotNull(extracted);
        assertEquals(extracted, AndroidShaderCompilerIdentity.read(archive + "!/lib/arm64-v8a/libfdx.so"));
        assertNotEquals(extracted, AndroidShaderCompilerIdentity.read(archive + "!/lib/x86_64/libfdx.so"));
        Files.write(library, new byte[]{1, 3, 5, 9});
        assertNotEquals(extracted, AndroidShaderCompilerIdentity.read(library.toString()));
        assertNull(AndroidShaderCompilerIdentity.read(archive + "!/lib/missing/libfdx.so"));
        assertNull(AndroidShaderCompilerIdentity.read(directory.resolve("missing.so").toString()));
        assertNull(AndroidShaderCompilerIdentity.read("libfdx.so"));
        assertNull(AndroidShaderCompilerIdentity.read(null));
    }
}
