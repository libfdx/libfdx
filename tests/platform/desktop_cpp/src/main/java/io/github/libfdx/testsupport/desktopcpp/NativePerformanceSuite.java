package io.github.libfdx.testsupport.desktopcpp;

import io.github.libfdx.testsupport.TestSelector;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Runs every selected registry scenario independently and preserves every failure in the report.
 */
public final class NativePerformanceSuite {
    private NativePerformanceSuite() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 5) {
            throw new IllegalArgumentException(
                    "Expected cpp-exe c-gl-exe c-vulkan-exe assets report-directory");
        }
        Path output = Path.of(args[4]).toAbsolutePath();
        Files.createDirectories(output);
        String selection = System.getProperty("performance.tests", "all");
        Set<String> selected = Set.of(selection.split(","));
        List<TestSelector.TestDescriptor> tests =
                Arrays.stream(TestSelector.descriptors())
                        .filter(test -> selection.equals("all") || selected.contains(test.name()))
                        .toList();
        if (tests.isEmpty() || (!selection.equals("all") && tests.size() != selected.size())) {
            throw new IllegalArgumentException(
                    "Unknown or empty performance test selection: " + selection);
        }
        List<String> providers =
                Arrays.asList(System.getProperty("performance.providers", "gl,vulkan").split(","));
        if (providers.isEmpty()
                || providers.size() != Set.copyOf(providers).size()
                || providers.stream()
                        .anyMatch(
                                provider -> !provider.equals("gl") && !provider.equals("vulkan"))) {
            throw new IllegalArgumentException("Expected unique gl/vulkan providers");
        }
        Files.writeString(
                output.resolve("inventory.csv"),
                "test,category,width,height\n"
                        + tests.stream()
                                .map(
                                        test ->
                                                test.name()
                                                        + ","
                                                        + test.category()
                                                        + ","
                                                        + test.defaultWidth()
                                                        + ","
                                                        + test.defaultHeight())
                                .collect(Collectors.joining("\n"))
                        + "\n");
        var previous = new LinkedHashMap<String, String>();
        var results = new ArrayList<Outcome>();
        int completed = 0;
        try {
            set(previous, "repeats", System.getProperty("performance.repeats", "3"));
            for (String provider : providers) {
                for (TestSelector.TestDescriptor test : tests) {
                    set(previous, "provider", provider);
                    set(previous, "tests", test.name());
                    set(previous, "width", Integer.toString(test.defaultWidth()));
                    set(previous, "height", Integer.toString(test.defaultHeight()));
                    boolean particles =
                            test.name().equals("Particles2DTest")
                                    || test.name().equals("Particles3DTest");
                    set(previous, "particleDelta", particles ? "0.016666667" : "0");
                    set(
                            previous,
                            "frames",
                            particles
                                    ? System.getProperty("performance.particleFrames", "1200")
                                    : "0");
                    set(
                            previous,
                            "warmupFrames",
                            System.getProperty("performance.particleWarmupFrames", "120"));
                    Path directory = output.resolve(provider).resolve(test.name());
                    Files.createDirectories(directory);
                    Files.deleteIfExists(directory.resolve("summary.csv"));
                    System.out.println(
                            "Suite "
                                    + (++completed)
                                    + "/"
                                    + (tests.size() * providers.size())
                                    + ": "
                                    + provider
                                    + " / "
                                    + test.name());
                    Exception failure = null;
                    PrintStream originalOutput = System.out;
                    try (PrintStream detail =
                            new PrintStream(
                                    Files.newOutputStream(directory.resolve("runner.log")))) {
                        System.setOut(detail);
                        try {
                            NativePerformanceComparison.main(
                                    new String[] {
                                        args[0],
                                        args[provider.equals("gl") ? 1 : 2],
                                        args[3],
                                        directory.toString()
                                    });
                        } catch (Exception error) {
                            error.printStackTrace(detail);
                            failure = error;
                        } finally {
                            System.setOut(originalOutput);
                        }
                    }
                    Map<String, String> metrics = Map.of();
                    try {
                        metrics = readSummary(directory.resolve("summary.csv"));
                        if (metrics.isEmpty())
                            metrics = readPartial(directory.resolve("results.csv"));
                    } catch (Exception error) {
                        if (failure == null) failure = error;
                        else failure.addSuppressed(error);
                    }
                    metrics = new LinkedHashMap<>(metrics);
                    metrics.put("cppStatus", metrics.containsKey("cppFps") ? "EXECUTED" : "ERROR");
                    metrics.put("cStatus", metrics.containsKey("cFps") ? "EXECUTED" : "ERROR");
                    Path failures = directory.resolve("failures.csv");
                    if (Files.isRegularFile(failures)) {
                        List<String> failureLines = Files.readAllLines(failures);
                        for (String line : failureLines.subList(Math.min(1, failureLines.size()), failureLines.size())) {
                            String[] columns = line.split(",", 4);
                            if (columns.length >= 3)
                                metrics.put(columns[1].equals("desktop_cpp") ? "cppStatus" : "cStatus", columns[2]);
                        }
                    }
                    Path eligibility = directory.resolve("eligibility.txt");
                    String workload = Files.isRegularFile(eligibility)
                            ? Files.readString(eligibility).trim() : "UNAVAILABLE";
                    metrics.put("workload", workload);
                    String throughput = metrics.containsKey("pairedRatio")
                            ? (failure == null ? "AT_TARGET" : "BELOW_TARGET") : "NOT_COMPARABLE";
                    metrics.put("throughput", throughput);
                    String status = !metrics.get("cppStatus").equals("EXECUTED")
                            ? "CPP_" + metrics.get("cppStatus")
                            : !metrics.get("cStatus").equals("EXECUTED")
                                    ? "REFERENCE_" + metrics.get("cStatus")
                                    : throughput.equals("NOT_COMPARABLE") ? "WORKLOAD_" + workload
                                            : throughput.equals("AT_TARGET") ? "PASS" : "BELOW_TARGET";
                    results.add(
                            new Outcome(
                                    provider,
                                    test.name(),
                                    status,
                                    metrics,
                                    failure == null ? "" : failure.toString()));
                    save(output, results, tests.size() * providers.size());
                    System.out.println(
                            "  "
                                    + status
                                    + (metrics.isEmpty()
                                            ? ""
                                            : " C="
                                                    + metrics.getOrDefault("cFps", "unavailable")
                                                    + " C++="
                                                    + metrics.getOrDefault("cppFps", "unavailable")
                                                    + " paired="
                                                    + metrics.getOrDefault(
                                                            "pairedRatio", "unavailable")));
                }
            }
        } finally {
            previous.forEach(
                    (name, value) -> {
                        if (value == null) System.clearProperty(name);
                        else System.setProperty(name, value);
                    });
        }
        if (results.stream().anyMatch(result -> !result.status.equals("PASS"))) {
            throw new IllegalStateException(
                    "Suite contains failures, unavailable scenes or results below target; see "
                            + output.resolve("summary.md"));
        }
    }

    private static void set(Map<String, String> previous, String name, String value) {
        String key = "performance." + name;
        if (!previous.containsKey(key)) previous.put(key, System.getProperty(key));
        System.setProperty(key, value);
    }

    private static Map<String, String> readSummary(Path path) throws Exception {
        if (!Files.isRegularFile(path)) return Map.of();
        List<String> lines = Files.readAllLines(path);
        if (lines.size() != 2)
            throw new IllegalStateException("Expected exactly one scene result: " + path);
        String[] names = lines.get(0).split(","), values = lines.get(1).split(",", -1);
        if (names.length != values.length)
            throw new IllegalStateException("Malformed result: " + path);
        var result = new LinkedHashMap<String, String>();
        for (int index = 0; index < names.length; index++) result.put(names[index], values[index]);
        return result;
    }

    private static Map<String, String> readPartial(Path path) throws Exception {
        if (!Files.isRegularFile(path)) return Map.of();
        List<String> lines = Files.readAllLines(path);
        if (lines.size() < 2) return Map.of();
        List<String> headers = Arrays.asList(lines.getFirst().split(","));
        var result = new LinkedHashMap<String, String>();
        for (String backend : List.of("desktop_c", "desktop_cpp")) {
            List<String[]> rows =
                    lines.stream()
                            .skip(1)
                            .map(line -> line.split(",", -1))
                            .filter(
                                    row ->
                                            row.length == headers.size()
                                                    && row[headers.indexOf("backend")].equals(
                                                            backend))
                            .toList();
            if (rows.isEmpty()) continue;
            String prefix = backend.equals("desktop_c") ? "c" : "cpp";
            for (var metric :
                    Map.of(
                                    "fps",
                                    "Fps",
                                    "renderMeanMs",
                                    "RenderMs",
                                    "p95Ms",
                                    "P95Ms",
                                    "p99Ms",
                                    "P99Ms")
                            .entrySet()) {
                int column = headers.indexOf(metric.getKey());
                double[] values =
                        rows.stream()
                                .mapToDouble(row -> Double.parseDouble(row[column]))
                                .sorted()
                                .toArray();
                int middle = values.length / 2;
                double median =
                        values.length % 2 == 0
                                ? (values[middle - 1] + values[middle]) / 2
                                : values[middle];
                result.put(prefix + metric.getValue(), Double.toString(median));
            }
        }
        return result;
    }

    private static void save(Path output, List<Outcome> results, int total) throws Exception {
        StringBuilder csv =
                new StringBuilder(
                        "provider,test,status,cFps,cppFps,medianRatio,pairedRatio,cRenderMs,cppRenderMs,cP95Ms,cppP95Ms,cP99Ms,cppP99Ms,cppStatus,cStatus,workload,throughput,detail\n");
        StringBuilder report =
                new StringBuilder(
                        "# Native performance suite\n\nUpdated: "
                                + Instant.now()
                                + "\n\nCompleted "
                                + results.size()
                                + " of "
                                + total
                                + " provider/scenario comparisons. Release, uncapped, registry"
                                + " viewport per scene. Particles use a fixed step and equal frame"
                                + " counts. Raw measurements, ranges, commands and executable"
                                + " hashes are in each scene directory.\n\n"
                                + "Execution, workload configuration and throughput are reported separately. A configuration match still requires functional and visual validation. PASS compares C++ to C; it does not mean improvement over an earlier C++ build.\n\n"
                                + "| Provider | Test | Status | C FPS | C++ FPS | Paired ratio | C"
                                + " render ms | C++ render ms |\n"
                                + "|---|---|---|---:|---:|---:|---:|---:|\n");
        for (Outcome result : results) {
            csv.append(result.provider)
                    .append(',')
                    .append(result.test)
                    .append(',')
                    .append(result.status);
            for (String metric :
                    List.of(
                            "cFps",
                            "cppFps",
                            "medianRatio",
                            "pairedRatio",
                            "cRenderMs",
                            "cppRenderMs",
                            "cP95Ms",
                            "cppP95Ms",
                            "cP99Ms",
                            "cppP99Ms", "cppStatus", "cStatus", "workload", "throughput")) {
                csv.append(',').append(result.metrics.getOrDefault(metric, ""));
            }
            csv.append(',')
                    .append('"')
                    .append(
                            result.detail
                                    .replace("\"", "\"\"")
                                    .replace('\n', ' ')
                                    .replace('\r', ' '))
                    .append('"')
                    .append('\n');
            report.append('|')
                    .append(result.provider)
                    .append('|')
                    .append(result.test)
                    .append('|')
                    .append(result.status);
            for (String metric :
                    List.of("cFps", "cppFps", "pairedRatio", "cRenderMs", "cppRenderMs")) {
                String value = result.metrics.get(metric);
                report.append('|')
                        .append(
                                value == null
                                        ? "—"
                                        : String.format(
                                                Locale.ROOT, "%.3f", Double.parseDouble(value)));
            }
            report.append("|\n");
        }
        Files.writeString(output.resolve("results.csv"), csv);
        Files.writeString(output.resolve("summary.md"), report);
    }

    private record Outcome(
            String provider,
            String test,
            String status,
            Map<String, String> metrics,
            String detail) {}
}
