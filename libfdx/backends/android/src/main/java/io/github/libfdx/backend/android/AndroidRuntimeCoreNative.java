package io.github.libfdx.backend.android;

final class AndroidRuntimeCoreNative {
    private static final Object LOCK = new Object();
    private static boolean attempted;
    private static boolean loaded;
    private static String failureMessage;

    private AndroidRuntimeCoreNative() {
    }

    static boolean load() {
        synchronized (LOCK) {
            if (attempted) {
                return loaded;
            }
            attempted = true;
            loaded = tryLoadLibrary();
            return loaded;
        }
    }

    static String failureMessage() {
        synchronized (LOCK) {
            return failureMessage;
        }
    }

    private static boolean tryLoadLibrary() {
        try {
            System.loadLibrary("fdx");
            return true;
        } catch (UnsatisfiedLinkError error) {
            failureMessage = error.getMessage();
            return false;
        }
    }
}
