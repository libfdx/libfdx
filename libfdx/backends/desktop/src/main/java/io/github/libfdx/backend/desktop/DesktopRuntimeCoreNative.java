package io.github.libfdx.backend.desktop;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

final class DesktopRuntimeCoreNative {
    private static final Object LOCK = new Object();
    private static boolean attempted;
    private static boolean loaded;
    private static String failureMessage;

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

    private static boolean tryLoadConfiguredPath() {
        String configured = trim(System.getProperty("libfdx.desktop.runtimeFdxNative"));
        if (configured == null) {
            configured = trim(System.getProperty("libfdx.desktop.runtimeCoreNative"));
        }
        if (configured == null) {
            return false;
        }
        try {
            System.load(Path.of(configured).toAbsolutePath().toString());
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
