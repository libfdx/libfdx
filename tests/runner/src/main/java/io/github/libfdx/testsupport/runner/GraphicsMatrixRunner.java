package io.github.libfdx.testsupport.runner;

import io.github.libfdx.testsupport.TestSelector;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Host process supervisor. Each pair owns a fresh JVM and a separate log. */
public final class GraphicsMatrixRunner {
    public record Result(String status, String detail) {}
    private static final List<String> GRAPHICS = List.of("gl", "vulkan", "d3d12", "wgpu");

    private GraphicsMatrixRunner() {}

    /** Runs all requested pairs and returns a failing exit code only after the matrix finishes. */
    public static int run(List<String> javaCommand, String mainClass) throws IOException, InterruptedException {
        return run(javaCommand, mainClass, GRAPHICS, "desktop", () -> {});
    }

    public static int run(List<String> javaCommand, String mainClass, List<String> variants,
            String platform, Runnable cleanup) throws IOException, InterruptedException {
        List<String> tests = selection("libfdx.test.autoTests", Arrays.stream(TestSelector.testNames()).sorted().toList());
        List<String> graphics = selection("libfdx.test.autoGraphics", variants);
        long timeout = Long.parseLong(System.getProperty("libfdx.test.autoTimeoutSeconds", "180"));
        if (timeout <= 0) throw new IllegalArgumentException("autoTimeoutSeconds must be positive");
        Path root = Path.of(System.getProperty("libfdx.test.autoReportDirectory", "build/" + platform + "-auto")).toAbsolutePath();
        Files.createDirectories(root);
        Path directory = Files.createTempDirectory(root, "run-");
        List<Result> results = new ArrayList<>();
        for (String test : tests) for (String provider : graphics) results.add(new Result("PENDING", "Not run"));
        writeReport(directory, tests, graphics, results);
        System.out.println("[info] " + platform + " auto checklist: " + directory.resolve("checklist.md"));
        int index = 0;
        int failures = 0;
        for (String test : tests) {
            for (String provider : graphics) {
                String stem = test + "-" + provider;
                Path marker = directory.resolve(stem + ".complete");
                List<String> command = new ArrayList<>(javaCommand);
                command.add("-Dlibfdx.test.name=" + test);
                command.add("-Dlibfdx.test.mode=single");
                command.add("-Dlibfdx.test.graphics=" + provider);
                command.add("-Dlibfdx.test.graphicsLabel=" + provider);
                command.add("-Dlibfdx.test.frames=-1");
                command.add("-Dlibfdx.test.autoChild=true");
                command.add("-Dlibfdx.test.autoCompletionFile=" + marker);
                command.add("-XX:ErrorFile=" + directory.resolve(stem + "-hs_err_pid%p.log"));
                command.add("-cp");
                command.add(System.getProperty("java.class.path"));
                command.add(mainClass);
                results.set(index, new Result("RUNNING", "Started " + Instant.now()));
                writeReport(directory, tests, graphics, results);
                System.out.println("[info] " + platform + " auto " + (index + 1) + "/" + results.size() + ": " + stem);
                long started = System.nanoTime();
                Result result;
                try {
                    result = runProcess(command, directory.resolve(stem + ".log"), marker, timeout);
                } finally {
                    cleanup.run();
                }
                result = new Result(result.status(), result.detail() + " (" + TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - started) + "s)");
                results.set(index++, result);
                if (!result.status().equals("PASS")) failures++;
                writeReport(directory, tests, graphics, results);
                System.out.println("[info] " + stem + ": " + result.status() + " — " + result.detail());
            }
        }
        System.out.println("[info] " + platform + " auto complete: " + (results.size() - failures) + " passed, "
                + failures + " unsuccessful. Checklist: " + directory.resolve("checklist.md"));
        return failures == 0 ? 0 : 1;
    }

    /** A completion marker alone is insufficient: cleanup or native shutdown may still fail. */
    public static Result runProcess(List<String> command, Path log, Path marker, long timeoutSeconds)
            throws IOException, InterruptedException {
        Files.deleteIfExists(marker);
        Path stopping = Path.of(marker + ".stopping");
        Files.deleteIfExists(stopping);
        long shutdownSeconds = Long.parseLong(System.getProperty("libfdx.test.autoShutdownTimeoutSeconds", "15"));
        if (shutdownSeconds <= 0) throw new IllegalArgumentException("autoShutdownTimeoutSeconds must be positive");
        Process process;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        } catch (IOException error) {
            Files.writeString(log, error.toString());
            return new Result("LAUNCH ERROR", error.toString());
        }
        Thread shutdown = new Thread(() -> terminate(process), "desktop-auto-child-cleanup");
        Runtime.getRuntime().addShutdownHook(shutdown);
        try {
            long started = System.nanoTime();
            long shutdownStarted = 0;
            String timeoutReason = null;
            while (!process.waitFor(100, TimeUnit.MILLISECONDS)) {
                long now = System.nanoTime();
                if (shutdownStarted == 0 && Files.exists(stopping)) shutdownStarted = now;
                if (shutdownStarted != 0 && now - shutdownStarted >= TimeUnit.SECONDS.toNanos(shutdownSeconds)) {
                    timeoutReason = "Shutdown exceeded " + shutdownSeconds + "s after observation completed";
                    break;
                }
                if (now - started >= TimeUnit.SECONDS.toNanos(timeoutSeconds)) {
                    timeoutReason = "Exceeded " + timeoutSeconds + "s including startup and cleanup";
                    break;
                }
            }
            if (timeoutReason != null) {
                System.out.println("[warn] " + timeoutReason + "; terminating child PID " + process.pid());
                terminate(process);
                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                    throw new IOException("Timed-out child could not be terminated: " + process.pid());
                }
                return new Result("TIMEOUT", timeoutReason);
            }
            if (process.exitValue() != 0) {
                String detail = failureDetail(log);
                String status = detail.contains("Graphics device does not support feature") ? "UNSUPPORTED" : "FAIL";
                return new Result(status, "Exit " + process.exitValue() + "; " + detail);
            }
            if (!Files.exists(marker)) return new Result("FAIL", "Exited without completing observation and cleanup");
            return new Result("PASS", "Runtime checks and cleanup completed; visuals/input/audio not certified");
        } finally {
            terminate(process);
            Runtime.getRuntime().removeShutdownHook(shutdown);
        }
    }

    private static void terminate(Process process) {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        if (process.isAlive()) process.destroyForcibly();
    }

    private static String failureDetail(Path log) {
        try (var lines = Files.lines(log)) {
            return lines.filter(line -> line.contains("Exception") || line.contains("[error]") || line.contains("fatal error"))
                    .findFirst().orElse("See process log");
        } catch (IOException | java.io.UncheckedIOException error) {
            return "See process log (could not decode diagnostic text)";
        }
    }

    private static List<String> selection(String property, List<String> allowed) {
        String value = System.getProperty(property, "").trim();
        if (value.isEmpty()) return allowed;
        List<String> selected = new ArrayList<>();
        for (String part : value.split(",", -1)) {
            String match = allowed.stream().filter(item -> item.equalsIgnoreCase(part.trim())).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unknown " + property + " entry: " + part));
            if (!selected.contains(match)) selected.add(match);
        }
        return selected;
    }

    private static void writeReport(Path directory, List<String> tests, List<String> graphics, List<Result> results)
            throws IOException {
        long passed = results.stream().filter(result -> result.status().equals("PASS")).count();
        long completed = results.stream().filter(result -> !result.status().equals("PENDING")
                && !result.status().equals("RUNNING")).count();
        int fullyPassedTests = 0;
        for (int test = 0; test < tests.size(); test++) {
            boolean allPassed = true;
            for (int api = 0; api < graphics.size(); api++) {
                allPassed &= results.get(test * graphics.size() + api).status().equals("PASS");
            }
            if (allPassed) fullyPassedTests++;
        }
        StringBuilder report = new StringBuilder("# Automatic graphics checklist\n\n")
                .append("**PASS: ").append(passed).append('/').append(results.size()).append(" test/API checks** | Completed: ")
                .append(completed).append('/').append(results.size()).append("\n\n")
                .append("**Tests passing every selected API: ").append(fullyPassedTests).append('/').append(tests.size()).append("**\n\n")
                .append("Updated: ").append(Instant.now()).append("\n\n")
                .append("Platform: ").append(System.getProperty("os.name")).append(' ')
                .append(System.getProperty("os.arch")).append("; Java: ").append(System.getProperty("java.version")).append("\n\n")
                .append("PASS means runtime observation, built-in checks and cleanup completed. It does not certify visuals, input or audible output.\n\n");
        int index = 0;
        for (int testIndex = 0; testIndex < tests.size(); testIndex++) {
            String test = tests.get(testIndex);
            report.append("## ").append(testIndex + 1).append(". ").append(test).append("\n\n");
            for (String provider : graphics) {
                Result result = results.get(index++);
                boolean finished = !result.status().equals("PENDING") && !result.status().equals("RUNNING");
                report.append(finished ? "- [x] " : "- [ ] ").append(provider).append(": **")
                        .append(result.status()).append("** — ").append(result.detail().replace('\n', ' ').replace('\r', ' '))
                        .append(" ([log](").append(test).append('-').append(provider).append(".log))\n");
            }
            report.append('\n');
        }
        Path temporary = directory.resolve("checklist.md.tmp");
        Files.writeString(temporary, report);
        try {
            Files.move(temporary, directory.resolve("checklist.md"), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, directory.resolve("checklist.md"), StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
