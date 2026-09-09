package io.github.libfdx.testsupport.runner;

import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Host entry point for device/browser matrices; the host survives worker failure. */
public final class PlatformMatrixLauncher {
    public static void main(String[] args) throws Exception {
        String platform = System.getProperty("libfdx.test.autoPlatform");
        boolean android = "android".equals(platform);
        if (!android && !"web".equals(platform)) throw new IllegalArgumentException("Expected android or web");
        if (android) AndroidMatrixWorker.requireDevice();
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        for (String arg : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            if (arg.startsWith("-Dlibfdx.") || arg.startsWith("-Djava.io.tmpdir=") || arg.startsWith("-Xmx")) command.add(arg);
        }
        int result = GraphicsMatrixRunner.run(command,
                android ? AndroidMatrixWorker.class.getName() : WebMatrixWorker.class.getName(),
                android ? List.of("gles", "vulkan", "wgpu_jni") : List.of("js-webgl", "js-webgpu", "wasm-webgl", "wasm-webgpu"),
                platform, android ? AndroidMatrixWorker::stopApp : () -> {});
        System.exit(result);
    }
}
