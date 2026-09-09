package io.github.libfdx.testsupport.runner;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** One device case. The host force-stops the application even if this worker times out. */
public final class AndroidMatrixWorker {
    private static final String PACKAGE = "io.github.libfdx.tests.android";

    public static void main(String[] args) throws Exception {
        stopApp();
        String provider = System.getProperty("libfdx.test.graphics");
        String activity = switch (provider) {
            case "gles" -> "AndroidGlesTestActivity";
            case "vulkan" -> "AndroidVulkanTestActivity";
            case "wgpu_jni" -> "AndroidWgpuTestActivity";
            default -> throw new IllegalArgumentException(provider);
        };
        String token = UUID.randomUUID().toString();
        String dump = adb("shell", "dumpsys", "package", PACKAGE);
        var matcher = java.util.regex.Pattern.compile("userId=(\\d+)").matcher(dump);
        if (!matcher.find()) throw new IllegalStateException("Test APK is not installed or its UID is unavailable");
        Process logcat = new ProcessBuilder(command("logcat", "--uid=" + matcher.group(1), "-v", "brief", "-T", "1"))
                .redirectErrorStream(true).start();
        try {
            List<String> launch = new ArrayList<>(List.of("shell", "am", "start", "-W", "-n", PACKAGE + "/" + PACKAGE + "." + activity));
            extra(launch, "libfdx.test.name", System.getProperty("libfdx.test.name"));
            extra(launch, "libfdx.test.frames", "-1");
            extra(launch, "libfdx.test.autoChild", "true");
            extra(launch, "libfdx.test.autoToken", token);
            extra(launch, "libfdx.test.autoDurationSeconds", System.getProperty("libfdx.test.autoDurationSeconds", "6"));
            String started = adb(launch.toArray(String[]::new));
            System.out.println(started);
            if (started.contains("Error:")) throw new IllegalStateException("Activity launch failed");
            try (var reader = logcat.inputReader()) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                    if (line.contains("[libfdx-auto] PASS " + token)) {
                        Files.writeString(Path.of(System.getProperty("libfdx.test.autoCompletionFile")), "completed\n");
                        return;
                    }
                    if (line.contains("FATAL EXCEPTION") || line.contains("Fatal signal")
                            || line.contains("Graphics device does not support feature")) {
                        throw new IllegalStateException("Android test failed: " + line);
                    }
                }
            }
            throw new IllegalStateException("Logcat ended before test completion");
        } finally {
            logcat.destroyForcibly();
        }
    }

    public static void requireDevice() {
        String state = adb("get-state").trim();
        if (!state.equals("device")) throw new IllegalStateException("No unique authorized Android device; set ANDROID_SERIAL: " + state);
        System.out.println("Android device: " + adb("get-serialno").trim());
    }

    public static void stopApp() { adb("shell", "am", "force-stop", PACKAGE); }

    private static void extra(List<String> args, String key, String value) {
        args.addAll(List.of("--es", key, value));
    }

    private static List<String> command(String... args) {
        List<String> command = new ArrayList<>();
        command.add(System.getProperty("libfdx.test.autoAdb", "adb"));
        command.addAll(List.of(args));
        return command;
    }

    private static String adb(String... args) {
        try {
            Process process = new ProcessBuilder(command(args)).redirectErrorStream(true).start();
            // Drain concurrently so a verbose dumpsys cannot fill the pipe before waitFor.
            var output = new java.io.ByteArrayOutputStream();
            Thread reader = Thread.ofVirtual().start(() -> {
                try { process.getInputStream().transferTo(output); } catch (java.io.IOException ignored) {}
            });
            if (!process.waitFor(20, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException("ADB command timed out");
            }
            reader.join();
            String text = output.toString(java.nio.charset.StandardCharsets.UTF_8);
            if (process.exitValue() != 0) throw new IllegalStateException("ADB failed: " + text);
            return text;
        } catch (Exception error) {
            throw new IllegalStateException("ADB operation failed", error);
        }
    }
}
