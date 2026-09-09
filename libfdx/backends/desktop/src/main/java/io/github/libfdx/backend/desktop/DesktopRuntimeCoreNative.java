package io.github.libfdx.backend.desktop;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Provides native bindings for desktop runtime core.
 *
 * @author xpenatan
 */
final class DesktopRuntimeCoreNative {
    private static final Object LOCK = new Object();
    private static final Object IDENTITY_LOCK = new Object();
    private static boolean attempted;
    private static boolean loaded;
    private static String failureMessage;
    private static Path loadedPath;
    private static String binaryIdentity;
    private static boolean identityAttempted;

    private DesktopRuntimeCoreNative() {
    }

    static boolean load() {
        synchronized (LOCK) {
            if (attempted) {
                return loaded;
            }
            attempted = true;
            loaded = tryLoadConfiguredPath() || tryLoadPackagedResource() || tryLoadLibraryPath();
            return loaded;
        }
    }

    static String failureMessage() {
        synchronized (LOCK) {
            return failureMessage;
        }
    }

    /** Worker-only: hash the exact loaded library once; unidentified library-path loads stay uncached. */
    static String binaryIdentity() {
        Path path;
        synchronized (LOCK) {
            if (!load() || loadedPath == null) return null;
            path = loadedPath;
        }
        // Runtime loading/math use LOCK; file hashing must never hold that shared lock.
        synchronized (IDENTITY_LOCK) {
            if (!identityAttempted) {
                identityAttempted = true;
                try (InputStream input = Files.newInputStream(path)) {
                    MessageDigest digest = MessageDigest.getInstance("SHA-256");
                    byte[] buffer = new byte[65_536];
                    int read;
                    while ((read = input.read(buffer)) >= 0) if (read > 0) digest.update(buffer, 0, read);
                    binaryIdentity = "fdx-shaderc-ffm-v2:" + HexFormat.of().formatHex(digest.digest());
                } catch (IOException | NoSuchAlgorithmException | RuntimeException unavailable) {
                    binaryIdentity = null;
                }
            }
            return binaryIdentity;
        }
    }

    private static boolean tryLoadConfiguredPath() {
        String configured = trim(System.getProperty("libfdx.desktop.runtimeFdxNative"));
        if (configured == null) {
            configured = trim(System.getProperty("libfdx.desktop.runtimeCoreNative"));
        }
        if (configured == null) {
            return false;
        }
        try {
            Path path = Path.of(configured).toAbsolutePath();
            System.load(path.toString());
            loadedPath = path;
            return true;
        } catch (RuntimeException | UnsatisfiedLinkError error) {
            failureMessage = error.getMessage();
            return false;
        }
    }

    private static boolean tryLoadPackagedResource() {
        String resource = "libfdx-native/desktop/" + platformClassifier() + "/" + libraryFileName();
        ClassLoader loader = DesktopRuntimeCoreNative.class.getClassLoader();
        try (InputStream input = loader.getResourceAsStream(resource)) {
            if (input == null) {
                return false;
            }
            Path extracted = Files.createTempFile("libfdx-runtime-fdx-", librarySuffix());
            Files.copy(input, extracted, StandardCopyOption.REPLACE_EXISTING);
            extracted.toFile().deleteOnExit();
            System.load(extracted.toAbsolutePath().toString());
            loadedPath = extracted.toAbsolutePath();
            return true;
        } catch (IOException | RuntimeException | UnsatisfiedLinkError error) {
            failureMessage = error.getMessage();
            return false;
        }
    }

    private static boolean tryLoadLibraryPath() {
        try {
            System.loadLibrary("fdx");
            return true;
        } catch (UnsatisfiedLinkError error) {
            failureMessage = error.getMessage();
            return false;
        }
    }

    private static String platformClassifier() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String arch = System.getProperty("os.arch", "").toLowerCase();
        String osPart;
        if (os.contains("windows")) {
            osPart = "windows";
        } else if (os.contains("mac") || os.contains("darwin")) {
            osPart = "macos";
        } else if (os.contains("linux")) {
            osPart = "linux";
        } else {
            osPart = "unknown";
        }

        String archPart = arch.contains("aarch64") || arch.contains("arm64") ? "arm64" : "x64";
        return osPart + "-" + archPart;
    }

    private static String libraryFileName() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("windows")) {
            return "fdx.dll";
        }
        if (os.contains("mac") || os.contains("darwin")) {
            return "libfdx.dylib";
        }
        return "libfdx.so";
    }

    private static String librarySuffix() {
        String name = libraryFileName();
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot) : ".bin";
    }

    private static String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > 0 ? trimmed : null;
    }
}
