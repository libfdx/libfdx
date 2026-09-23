package io.github.libfdx.backend.web.internal;

/** TeaVM entry point embedded as an application string; never initializes the application. */
public final class PreparationWorkerMain {
    private PreparationWorkerMain() { }
    public static void main(String[] args) {
        switch (args[0]) {
            case "libfdx-assets" -> AssetWorkerMain.main(args);
            case "libfdx-pbr" -> PbrSourceWorkerMain.main(args);
            case "libfdx-shader" -> ShaderWorkerMain.start();
            default -> throw new IllegalArgumentException("Unknown preparation worker role: " + args[0]);
        }
    }
}
