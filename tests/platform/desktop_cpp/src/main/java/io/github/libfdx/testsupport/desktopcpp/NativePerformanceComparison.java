package io.github.libfdx.testsupport.desktopcpp;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Host-side sequential comparison of the same scenes in two release executables. */
public final class NativePerformanceComparison {
    public static void main(String[] args) throws Exception {
        if (args.length != 4)
            throw new IllegalArgumentException("Expected cpp-exe c-exe assets report-directory");
        Path cpp = Path.of(args[0]).toAbsolutePath();
        Path reference = Path.of(args[1]).toAbsolutePath();
        Path assets = Path.of(args[2]).toAbsolutePath();
        Path output = Path.of(args[3]).toAbsolutePath();
        if (!Files.isRegularFile(cpp) || !Files.isRegularFile(reference)) {
            throw new IllegalArgumentException("Both release executables must exist");
        }
        Files.createDirectories(output);
        int repeats = Integer.getInteger("performance.repeats", 5);
        double floor = Double.parseDouble(System.getProperty("performance.minRatio", "1.0"));
        if (repeats < 1 || repeats > 20 || !Double.isFinite(floor) || floor < 0) {
            throw new IllegalArgumentException("Invalid repetition count or FPS ratio floor");
        }
        List<String> tests =
                Arrays.asList(
                        System.getProperty("performance.tests", "SpriteBatchStressTest")
                                .split(","));
        Files.writeString(
                output.resolve("executables.txt"),
                "desktop_cpp="
                        + cpp
                        + "\nsha256="
                        + sha256(cpp)
                        + "\ndesktop_c="
                        + reference
                        + "\nsha256="
                        + sha256(reference)
                        + "\n");
        Files.writeString(
                output.resolve("summary.md"),
                "Measurement in progress. Completed runs are saved in results.csv.\n");
        Files.deleteIfExists(output.resolve("summary.csv"));
        Files.deleteIfExists(output.resolve("failures.csv"));
        Files.deleteIfExists(output.resolve("eligibility.txt"));
        var rows = new ArrayList<Result>();
        var failures = new LinkedHashMap<String, Exception>();
        writeCsv(output, rows);
        try {
            for (String test : tests) {
                if (!test.matches("[A-Za-z0-9_]+"))
                    throw new IllegalArgumentException("Invalid test name: " + test);
                for (int repetition = 1; repetition <= repeats; repetition++) {
                    // Alternate order to reduce persistent warm-cache and thermal bias.
                    boolean cppFirst = repetition % 2 == 0;
                    for (boolean cppRun : new boolean[] {cppFirst, !cppFirst}) {
                        String key = test + "," + (cppRun ? "desktop_cpp" : "desktop_c");
                        if (failures.containsKey(key)) continue;
                        try {
                            rows.add(
                                    run(
                                            cppRun ? cpp : reference,
                                            cppRun,
                                            test,
                                            repetition,
                                            assets,
                                            output));
                        } catch (Exception error) {
                            // Still measure the other backend and remaining scenes.
                            // Repeating an unavailable or failed process adds no FPS evidence.
                            failures.put(key, error);
                        }
                        writeCsv(output, rows);
                    }
                }
            }
            if (!failures.isEmpty()) {
                StringBuilder errors = new StringBuilder("test,backend,status,detail\n");
                failures.forEach(
                        (key, error) ->
                                errors.append(key)
                                        .append(',')
                                        .append(
                                                error instanceof UnsupportedOperationException
                                                        ? "UNSUPPORTED"
                                                        : "ERROR")
                                        .append(",\"")
                                        .append(
                                                error.toString()
                                                        .replace("\"", "\"\"")
                                                        .replace('\n', ' ')
                                                        .replace('\r', ' '))
                                        .append("\"\n"));
                Files.writeString(output.resolve("failures.csv"), errors);
                String message =
                        "Incomplete backend comparison; see " + output.resolve("failures.csv");
                if (failures.values().stream()
                        .allMatch(error -> error instanceof UnsupportedOperationException))
                    throw new UnsupportedOperationException(message);
                throw new IllegalStateException(message);
            }
        } catch (Exception error) {
            Files.writeString(
                    output.resolve("summary.md"),
                    "Measurement FAILED: "
                            + error.getMessage()
                            + "\n\n"
                            + rows.size()
                            + " completed runs are retained in results.csv; raw logs are beside"
                            + " it.\n");
            throw error;
        }
        for (String test : tests) {
            var signatures = rows.stream().filter(row -> row.test.equals(test))
                    .map(row -> row.values.getOrDefault("workload", "UNVERIFIED")).distinct().toList();
            if (signatures.contains("UNVERIFIED") || signatures.size() != 1) {
                String eligibility = signatures.contains("UNVERIFIED") ? "UNVERIFIED" : "MISMATCH";
                Files.writeString(output.resolve("eligibility.txt"), eligibility + "\n");
                Files.writeString(output.resolve("summary.md"),
                        "Workload comparison " + eligibility + ". Raw timings are retained; no throughput pass is claimed.\n");
                throw new IllegalStateException("Workload comparison " + eligibility + ": " + output);
            }
        }
        Files.writeString(output.resolve("eligibility.txt"), "MATCHED_CONFIGURATION\n");
        StringBuilder report =
                new StringBuilder(
                        "# Native scene performance\n\nMeasured: "
                                + Instant.now()
                                + "\n\n"
                                + provider()
                                + ", release, VSync off, frame cap off, "
                                + setting("width", "960")
                                + "x"
                                + setting("height", "640")
                                + ", sequential"
                                + " runs. Medians of "
                                + repeats
                                + " runs after "
                                + setting("warmupSeconds", "5")
                                + " seconds warm-up, measured for "
                                + setting("seconds", "10")
                                + " seconds each. Frame-count mode overrides these durations when"
                                + " requested.\n"
                                + "Requested measured/warmup frames: "
                                + setting("frames", "0")
                                + "/"
                                + setting("warmupFrames", "60")
                                + "; fixed particle delta: "
                                + setting("particleDelta", "0")
                                + ".\n\n"
                                + "Host: "
                                + System.getProperty("os.name")
                                + " "
                                + System.getProperty("os.version")
                                + ", "
                                + System.getenv()
                                        .getOrDefault(
                                                "PROCESSOR_IDENTIFIER",
                                                System.getProperty("os.arch"))
                                + "\n\nC++ executable: `"
                                + cpp
                                + "`\n\nC executable: `"
                                + reference
                                + "`\n\n"
                                + "Sprite count: "
                                + setting("spriteCount", "20000")
                                + "\n\n"
                                + "| Scene | C FPS | C++ FPS | Median ratio | Paired ratio | C"
                                + " render ms | C++ render ms |\n"
                                + "|---|---:|---:|---:|---:|---:|---:|\n");
        boolean failed = false;
        StringBuilder summaryCsv =
                new StringBuilder(
                        "test,provider,runs,cFps,cppFps,medianRatio,pairedRatio,cRenderMs,cppRenderMs,cP95Ms,cppP95Ms,cP99Ms,cppP99Ms,cMinFps,cMaxFps,cppMinFps,cppMaxFps\n");
        for (String test : tests) {
            double cFps = median(rows, test, "desktop_c", "fps");
            double cppFps = median(rows, test, "desktop_cpp", "fps");
            double ratio = cppFps / cFps;
            double[] paired = new double[repeats];
            for (int repeat = 1; repeat <= repeats; repeat++) {
                paired[repeat - 1] =
                        metric(rows, test, "desktop_cpp", repeat, "fps")
                                / metric(rows, test, "desktop_c", repeat, "fps");
            }
            double pairedRatio = median(paired);
            failed |= ratio < floor || pairedRatio < floor;
            double[] cRange = values(rows, test, "desktop_c", "fps");
            double[] cppRange = values(rows, test, "desktop_cpp", "fps");
            summaryCsv
                    .append(test)
                    .append(',')
                    .append(provider())
                    .append(',')
                    .append(repeats)
                    .append(',')
                    .append(cFps)
                    .append(',')
                    .append(cppFps)
                    .append(',')
                    .append(ratio)
                    .append(',')
                    .append(pairedRatio)
                    .append(',')
                    .append(median(rows, test, "desktop_c", "renderMeanMs"))
                    .append(',')
                    .append(median(rows, test, "desktop_cpp", "renderMeanMs"))
                    .append(',')
                    .append(median(rows, test, "desktop_c", "p95Ms"))
                    .append(',')
                    .append(median(rows, test, "desktop_cpp", "p95Ms"))
                    .append(',')
                    .append(median(rows, test, "desktop_c", "p99Ms"))
                    .append(',')
                    .append(median(rows, test, "desktop_cpp", "p99Ms"))
                    .append(',')
                    .append(cRange[0])
                    .append(',')
                    .append(cRange[cRange.length - 1])
                    .append(',')
                    .append(cppRange[0])
                    .append(',')
                    .append(cppRange[cppRange.length - 1])
                    .append('\n');
            report.append(
                    String.format(
                            Locale.ROOT,
                            "| %s | %.2f | %.2f | %.3f | %.3f | %.5f | %.5f |%n",
                            test,
                            cFps,
                            cppFps,
                            ratio,
                            pairedRatio,
                            median(rows, test, "desktop_c", "renderMeanMs"),
                            median(rows, test, "desktop_cpp", "renderMeanMs")));
        }
        report.append("\n| Scene | Backend | FPS range | Mean ms | p95 ms | p99 ms |\n")
                .append("|---|---|---:|---:|---:|---:|\n");
        for (String test : tests) {
            for (String backend : List.of("desktop_c", "desktop_cpp")) {
                double[] fps = values(rows, test, backend, "fps");
                report.append(
                        String.format(
                                Locale.ROOT,
                                "| %s | %s | %.2f–%.2f | %.5f | %.2f | %.2f |%n",
                                test,
                                backend,
                                fps[0],
                                fps[fps.length - 1],
                                median(rows, test, backend, "meanMs"),
                                median(rows, test, backend, "p95Ms"),
                                median(rows, test, backend, "p99Ms")));
            }
        }
        report.append("\nRequired FPS ratio: ")
                .append(floor)
                .append(". Result: ")
                .append(failed ? "FAIL" : "PASS")
                .append(".\n\n")
                .append(
                        "Frame intervals include presentation and backend work. Render-call"
                                + " durations are in results.csv. ")
                .append("Percentiles are histogram upper bounds with 0.01 ms resolution. ")
                .append("Raw process output is retained beside this report.\n");
        Files.writeString(output.resolve("summary.md"), report);
        Files.writeString(output.resolve("summary.csv"), summaryCsv);
        System.out.println(report);
        if (failed)
            throw new IllegalStateException(
                    "FPS comparison failed; see " + output.resolve("summary.md"));
    }

    private static String sha256(Path executable) throws Exception {
        return HexFormat.of()
                .formatHex(
                        MessageDigest.getInstance("SHA-256")
                                .digest(Files.readAllBytes(executable)));
    }

    private static void writeCsv(Path output, List<Result> rows) throws Exception {
        StringBuilder csv =
                new StringBuilder(
                        "test,backend,repeat,frames,seconds,fps,meanMs,p95Ms,p99Ms,renderMeanMs,processSeconds\n");
        for (Result row : rows) {
            csv.append(row.test).append(',').append(row.backend).append(',').append(row.repetition);
            for (String column :
                    List.of(
                            "frames",
                            "seconds",
                            "fps",
                            "meanMs",
                            "p95Ms",
                            "p99Ms",
                            "renderMeanMs",
                            "processSeconds")) {
                csv.append(',').append(row.values.get(column));
            }
            csv.append('\n');
        }
        Files.writeString(output.resolve("results.csv"), csv);
    }

    private static Result run(
            Path executable, boolean cpp, String test, int repetition, Path assets, Path output)
            throws Exception {
        String backend = cpp ? "desktop_cpp" : "desktop_c";
        Path log = output.resolve(test + "-" + backend + "-" + repetition + ".log");
        var command = new ArrayList<String>();
        command.add(executable.toString());
        if (cpp) command.add(provider());
        command.add(test);
        command.add("0");
        command.add("-Dlibfdx.test.width=" + dimension("width", "960"));
        command.add("-Dlibfdx.test.height=" + dimension("height", "640"));
        command.add("-Dlibfdx.test.maximized=false");
        command.add("-Dlibfdx.test.fpsLogSeconds=0");
        command.add("-Dlibfdx.test.performance=true");
        command.add("-Dlibfdx.test.performance.warmupSeconds=" + setting("warmupSeconds", "5"));
        command.add("-Dlibfdx.test.performance.seconds=" + setting("seconds", "10"));
        command.add("-Dlibfdx.test.performance.minFps=" + setting("minFps", "0"));
        command.add("-Dlibfdx.test.performance.frames=" + setting("frames", "0"));
        command.add("-Dlibfdx.test.performance.warmupFrames=" + setting("warmupFrames", "60"));
        command.add("-Dlibfdx.test.particleDelta=" + setting("particleDelta", "0"));
        int spriteCount = Integer.parseInt(setting("spriteCount", "20000"));
        if (spriteCount < 1) throw new IllegalArgumentException("Sprite count must be positive");
        command.add("-Dlibfdx.test.spriteCount=" + spriteCount);
        Files.writeString(
                log.resolveSibling(log.getFileName() + ".command.txt"), String.join("\n", command));
        System.out.println("Measuring " + test + " / " + backend + " / " + repetition);
        long started = System.nanoTime();
        Process process =
                new ProcessBuilder(command)
                        .directory(assets.toFile())
                        .redirectErrorStream(true)
                        .redirectOutput(log.toFile())
                        .start();
        try {
            if (!process.waitFor(180, TimeUnit.SECONDS))
                throw new IllegalStateException("Measurement timed out: " + log);
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
        double processSeconds = (System.nanoTime() - started) * 1e-9;
        List<String> lines = Files.readAllLines(log);
        if (process.exitValue() != 0) {
            if (lines.stream().anyMatch(line -> line.startsWith("[performance-unavailable] "))) {
                throw new UnsupportedOperationException("Scene unavailable: " + log);
            }
            throw new IllegalStateException("Measurement failed: " + log);
        }
        if (test.equals("SpriteBatchStressTest")
                && lines.stream()
                        .noneMatch(
                                line ->
                                        line.contains(
                                                "SpriteBatchStressTest prepared "
                                                        + spriteCount
                                                        + " sprites"))) {
            throw new IllegalStateException("Sprite workload differs: " + log);
        }
        List<String> summaries =
                lines.stream().filter(line -> line.startsWith("[performance] ")).toList();
        if (summaries.size() != 1)
            throw new IllegalStateException("Missing unique performance result: " + log);
        Map<String, String> values = new LinkedHashMap<>();
        for (String part : summaries.getFirst().substring(14).split(" ")) {
            int equals = part.indexOf('=');
            if (equals > 0) values.put(part.substring(0, equals), part.substring(equals + 1));
        }
        if (!test.equals(values.get("test"))
                || !setting("width", "960").equals(values.get("width"))
                || !setting("height", "640").equals(values.get("height"))
                || !provider().equals(values.get("provider"))) {
            throw new IllegalStateException("Measurement settings differ: " + log);
        }
        long requestedFrames = Long.parseLong(setting("frames", "0"));
        if (requestedFrames > 0
                && (requestedFrames != Long.parseLong(values.get("frames"))
                        || !"frames".equals(values.get("measurement")))) {
            throw new IllegalStateException("Frame-count workload differs: " + log);
        }
        if (!Double.isFinite(Double.parseDouble(values.get("fps")))
                || !(Double.parseDouble(values.get("fps")) > 0)
                || Long.parseLong(values.get("frames")) < 2) {
            throw new IllegalStateException("Insufficient measured frames: " + log);
        }
        values.put("processSeconds", Double.toString(processSeconds));
        List<String> workloads = lines.stream().filter(line -> line.startsWith("[performance-workload] ")).toList();
        if (workloads.size() == 1) values.put("workload", workloads.getFirst().substring(23));
        return new Result(test, backend, repetition, values);
    }

    private static String setting(String name, String fallback) {
        return System.getProperty("performance." + name, fallback);
    }

    private static String provider() {
        String provider = setting("provider", "gl");
        if (!provider.equals("gl") && !provider.equals("vulkan")) {
            throw new IllegalArgumentException("Expected gl or vulkan provider");
        }
        return provider;
    }

    private static int dimension(String name, String fallback) {
        int value = Integer.parseInt(setting(name, fallback));
        if (value < 1 || value > 16384) throw new IllegalArgumentException("Invalid " + name);
        return value;
    }

    private static double median(List<Result> rows, String test, String backend, String column) {
        return median(values(rows, test, backend, column));
    }

    private static double[] values(List<Result> rows, String test, String backend, String column) {
        return rows.stream()
                .filter(row -> row.test.equals(test) && row.backend.equals(backend))
                .mapToDouble(row -> Double.parseDouble(row.values.get(column)))
                .sorted()
                .toArray();
    }

    private static double metric(
            List<Result> rows, String test, String backend, int repetition, String column) {
        return rows.stream()
                .filter(
                        row ->
                                row.test.equals(test)
                                        && row.backend.equals(backend)
                                        && row.repetition == repetition)
                .mapToDouble(row -> Double.parseDouble(row.values.get(column)))
                .findFirst()
                .orElseThrow();
    }

    private static double median(double[] values) {
        Arrays.sort(values);
        int middle = values.length / 2;
        return values.length % 2 == 0 ? (values[middle - 1] + values[middle]) / 2 : values[middle];
    }

    private record Result(
            String test, String backend, int repetition, Map<String, String> values) {}
}
