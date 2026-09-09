package io.github.libfdx.backend.android;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Worker-only fingerprint of the actual loaded compiler library, including APK-resident code. */
final class AndroidShaderCompilerIdentity {
    private AndroidShaderCompilerIdentity() { }

    static String read(String loadedPath) {
        if (loadedPath == null || loadedPath.isEmpty()) return null;
        try {
            int separator = loadedPath.indexOf("!/");
            Path file = Paths.get(separator < 0 ? loadedPath : loadedPath.substring(0, separator));
            if (!file.isAbsolute()) return null;
            if (separator < 0) {
                try (InputStream input = Files.newInputStream(file)) { return digest(input); }
            }
            try (ZipFile archive = new ZipFile(file.toFile())) {
                ZipEntry entry = archive.getEntry(loadedPath.substring(separator + 2));
                if (entry == null || entry.isDirectory()) return null;
                try (InputStream input = archive.getInputStream(entry)) { return digest(input); }
            }
        } catch (IOException | NoSuchAlgorithmException | RuntimeException unavailable) {
            return null;
        }
    }

    private static String digest(InputStream input) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[65_536];
        int read;
        while ((read = input.read(buffer)) >= 0) if (read > 0) digest.update(buffer, 0, read);
        byte[] hash = digest.digest();
        StringBuilder identity = new StringBuilder("fdx-shaderc-jni-v2:");
        for (byte value : hash) {
            identity.append(Character.forDigit((value >>> 4) & 15, 16));
            identity.append(Character.forDigit(value & 15, 16));
        }
        return identity.toString();
    }
}
