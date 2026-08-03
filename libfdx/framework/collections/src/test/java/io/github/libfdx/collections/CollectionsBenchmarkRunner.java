package io.github.libfdx.collections;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;

import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/** Runs selected collection benchmarks and writes JSON and Markdown reports. */
public final class CollectionsBenchmarkRunner {
    private static final String[] COLLECTION_NAMES = {
            "Array",
            "ArrayList",
            "IntArray",
            "LongArray",
            "FloatArray",
            "ObjectMap",
            "OrderedMap",
            "OrderedIntNodeMap",
            "OrderedIntSparseMap",
            "OrderedIntSparseNodeMap",
            "IntMap",
            "OrderedIntMap",
            "HashMap",
            "LongMap",
            "OrderedLongMap",
            "FloatMap",
            "ObjectSet",
            "IntSet",
            "ObjectQueue",
            "ObjectLinkedList"
    };

    private CollectionsBenchmarkRunner() {
    }

    /**
     * Runs the selected benchmarks.
     *
     * @param arguments report directory, report stem, and collection names
     * @throws Exception when JMH or report generation fails
     */
    public static void main(String[] arguments) throws Exception {
        if (arguments.length < 3) {
            throw new IllegalArgumentException("Usage: CollectionsBenchmarkRunner "
                    + "<report-directory> <report-stem> <collection>[,<collection>...]");
        }

        Path reportDirectory = Path.of(arguments[0]).toAbsolutePath().normalize();
        String reportStem = validateReportStem(arguments[1]);
        Array<String> collections = selectedCollections(arguments, 2);
        Path jsonReport = reportDirectory.resolve(reportStem + ".json");
        Path markdownReport = reportDirectory.resolve(reportStem + ".md");
        Files.createDirectories(reportDirectory);

        Options options = new OptionsBuilder()
                .include(benchmarkFilter(collections))
                .addProfiler(GCProfiler.class)
                .resultFormat(ResultFormatType.JSON)
                .result(jsonReport.toString())
                .shouldFailOnError(true)
                .build();

        System.out.println("Selected collections: " + joinedCollections(collections));
        Collection<RunResult> results = new Runner(options).run();
        CollectionsBenchmarkReport.write(results, jsonReport, markdownReport);
        System.out.println("Collections benchmark report: " + markdownReport);
    }

    private static String validateReportStem(String value) {
        if (!value.matches("[A-Za-z0-9][A-Za-z0-9-]*")) {
            throw new IllegalArgumentException("Invalid report name: " + value);
        }
        return value;
    }

    private static Array<String> selectedCollections(String[] arguments, int offset) {
        Array<String> selected = new Array<String>(COLLECTION_NAMES.length);
        for (int i = offset; i < arguments.length; i++) {
            String[] names = arguments[i].split(",");
            for (int j = 0; j < names.length; j++) {
                String name = names[j].trim();
                if (name.length() == 0) {
                    continue;
                }
                String canonical = canonicalCollectionName(name);
                if (!selected.contains(canonical)) {
                    selected.add(canonical);
                }
            }
        }
        if (selected.isEmpty()) {
            throw new IllegalArgumentException("No collections selected. Available collections: "
                    + joinedAvailableCollections());
        }
        return selected;
    }

    private static String canonicalCollectionName(String value) {
        for (int i = 0; i < COLLECTION_NAMES.length; i++) {
            String name = COLLECTION_NAMES[i];
            if (name.equalsIgnoreCase(value)
                    || benchmarkMethod(name).equalsIgnoreCase(value)
                    || reportStem(name).equalsIgnoreCase(value)) {
                return name;
            }
        }
        throw new IllegalArgumentException("Unknown collection '" + value
                + "'. Available collections: " + joinedAvailableCollections());
    }

    static String benchmarkFilter(Array<String> collections) {
        StringBuilder filter = new StringBuilder(160);
        filter.append("io\\.github\\.libfdx\\.collections\\.CollectionsBenchmark\\.(?:");
        for (int i = 0; i < collections.size(); i++) {
            if (i > 0) {
                filter.append('|');
            }
            filter.append(benchmarkPattern(collections.get(i)));
        }
        return filter.append(")\\z").toString();
    }

    private static String benchmarkMethod(String collectionName) {
        return Character.toLowerCase(collectionName.charAt(0)) + collectionName.substring(1);
    }

    private static String benchmarkPattern(String collectionName) {
        if ("OrderedIntNodeMap".equals(collectionName)) {
            return "(?:orderedIntNodeMap|orderedIntNodeRetainedRemoval)";
        }
        return benchmarkMethod(collectionName);
    }

    static String reportStem(String collectionName) {
        StringBuilder stem = new StringBuilder(collectionName.length() + 4);
        for (int i = 0; i < collectionName.length(); i++) {
            char character = collectionName.charAt(i);
            if (Character.isUpperCase(character) && i > 0) {
                stem.append('-');
            }
            stem.append(Character.toLowerCase(character));
        }
        return stem.toString();
    }

    private static String joinedCollections(Array<String> collections) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < collections.size(); i++) {
            if (i > 0) {
                text.append(", ");
            }
            text.append(collections.get(i));
        }
        return text.toString();
    }

    private static String joinedAvailableCollections() {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < COLLECTION_NAMES.length; i++) {
            if (i > 0) {
                text.append(", ");
            }
            text.append(COLLECTION_NAMES[i]);
        }
        return text.toString();
    }
}
