package io.github.libfdx.benchmark.desktop;

/** Java entry point for the C++-owned sprite benchmark. */
public final class NativeSpriteBenchmarkLauncher {
    private NativeSpriteBenchmarkLauncher() {
    }

    public static void main(String[] args) {
        System.load(System.getProperty("libfdx.benchmark.nativeSpriteLibrary"));
        run(args);
    }

    private static native void run(String[] args);
}
