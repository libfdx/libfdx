package io.github.libfdx.collections;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Iterator;
import java.util.Locale;

import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.results.Result;
import org.openjdk.jmh.results.RunResult;

/** Generates a readable Markdown report directly from JMH run results. */
public final class CollectionsBenchmarkReport {
    private static final double EFFECTIVELY_ZERO_ALLOCATION = 0.01d;

    private CollectionsBenchmarkReport() {
    }

    /**
     * Writes the Markdown report.
     *
     * @param runResults JMH results
     * @param jsonReport raw JSON report path
     * @param markdownReport Markdown report path
     * @throws IOException when the report cannot be written
     */
    public static void write(Collection<RunResult> runResults, Path jsonReport,
            Path markdownReport) throws IOException {
        if (runResults == null || runResults.isEmpty()) {
            throw new IllegalArgumentException("JMH returned no collection benchmark results");
        }

        Array<BenchmarkResult> results = readResults(runResults);
        results.sort((left, right) -> left.collection.compareTo(right.collection));
        Array<ComparisonGroup> comparisons = buildComparisons(results);
        BenchmarkParams metadata = runResults.iterator().next().getParams();
        String markdown = buildMarkdown(jsonReport, results, comparisons, metadata);

        Path parent = markdownReport.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(markdownReport, markdown, UTF_8);
    }

    private static Array<BenchmarkResult> readResults(Collection<RunResult> runResults) {
        Array<BenchmarkResult> results = new Array<BenchmarkResult>(runResults.size());
        Iterator<RunResult> iterator = runResults.iterator();
        while (iterator.hasNext()) {
            RunResult runResult = iterator.next();
            BenchmarkParams params = runResult.getParams();
            Result<?> primary = runResult.getPrimaryResult();
            String benchmark = params.getBenchmark();
            String method = benchmark.substring(benchmark.lastIndexOf('.') + 1);
            double[] confidence = primary.getScoreConfidence();

            results.add(new BenchmarkResult(
                    collectionName(method),
                    params.getParam("operation"),
                    options(params),
                    primary.getScore(),
                    confidence[0],
                    confidence[1],
                    primary.getScoreUnit(),
                    secondaryScore(runResult, "gc.alloc.rate.norm"),
                    secondaryScore(runResult, "gc.count")));
        }
        return results;
    }

    private static String options(BenchmarkParams params) {
        StringBuilder text = new StringBuilder();
        Iterator<?> iterator = params.getParamsKeys().iterator();
        while (iterator.hasNext()) {
            String name = (String)iterator.next();
            if ("operation".equals(name)) {
                continue;
            }
            if (text.length() > 0) {
                text.append(", ");
            }
            text.append(name).append('=').append(params.getParam(name));
        }
        return text.toString();
    }

    private static double secondaryScore(RunResult result, String name) {
        Result<?> metric = result.getSecondaryResults().get(name);
        return metric != null ? metric.getScore() : Double.NaN;
    }

    private static Array<ComparisonGroup> buildComparisons(Array<BenchmarkResult> results) {
        Array<ComparisonGroup> groups = new Array<ComparisonGroup>();
        for (int i = 0; i < results.size(); i++) {
            BenchmarkResult result = results.get(i);
            ComparisonGroup group = findGroup(groups, result.collection, result.operation);
            if (group == null) {
                group = new ComparisonGroup(result.collection, result.operation);
                groups.add(group);
            }
            group.results.add(result);
        }

        for (int i = groups.size() - 1; i >= 0; i--) {
            ComparisonGroup group = groups.get(i);
            if (group.results.size() < 2) {
                groups.removeIndex(i);
            }
            else {
                group.evaluate();
            }
        }
        return groups;
    }

    private static ComparisonGroup findGroup(Array<ComparisonGroup> groups, String collection,
            String operation) {
        for (int i = 0; i < groups.size(); i++) {
            ComparisonGroup group = groups.get(i);
            if (group.collection.equals(collection) && group.operation.equals(operation)) {
                return group;
            }
        }
        return null;
    }

    private static String buildMarkdown(Path jsonReport, Array<BenchmarkResult> results,
            Array<ComparisonGroup> comparisons, BenchmarkParams metadata) {
        StringBuilder text = new StringBuilder(48_000);
        text.append("# libFDX Collections Benchmark Report\n\n");
        text.append("Raw measurements: [")
                .append(markdownEscape(jsonReport.getFileName().toString()))
                .append("](")
                .append(markdownLink(jsonReport.getFileName().toString()))
                .append(")\n\n");
        text.append("> Lower `ns/op` is faster. Comparisons are made only between configurations ")
                .append("of the same collection and operation.\n\n");

        appendImplementationComparison(text, comparisons);
        appendRunConfiguration(text, metadata, results);
        appendConclusions(text, results, comparisons);
        appendComparisonTable(text, comparisons);
        appendDetailedResults(text, results);
        return text.toString();
    }

    private static void appendImplementationComparison(StringBuilder text,
            Array<ComparisonGroup> comparisons) {
        Array<ComparisonGroup> groups = new Array<ComparisonGroup>();
        for (int i = 0; i < comparisons.size(); i++) {
            ComparisonGroup group = comparisons.get(i);
            if ("OrderedIntNodeMap".equals(group.collection)
                    && hasOnlyImplementationOptions(group)) {
                groups.add(group);
            }
        }
        if (groups.isEmpty()) {
            return;
        }

        Array<String> implementations = new Array<String>();
        ComparisonGroup firstGroup = groups.first();
        for (int i = 0; i < firstGroup.results.size(); i++) {
            implementations.add(optionValue(firstGroup.results.get(i).options,
                    "implementation"));
        }

        text.append("## Performance comparison\n\n");
        text.append("| Collection |");
        for (int i = 0; i < groups.size(); i++) {
            text.append(' ').append(operationName(groups.get(i).operation)).append(" |");
        }
        text.append("\n|---|");
        for (int i = 0; i < groups.size(); i++) {
            text.append("---:|");
        }
        text.append('\n');

        for (int i = 0; i < implementations.size(); i++) {
            String implementation = implementations.get(i);
            text.append("| ").append(implementationName(implementation)).append(" |");
            for (int j = 0; j < groups.size(); j++) {
                BenchmarkResult result = findImplementationResult(groups.get(j), implementation);
                if (result == null) {
                    text.append(" - |");
                }
                else {
                    text.append(' ').append(formatScore(result.score)).append(' ')
                            .append(result.unit).append(" |");
                }
            }
            text.append('\n');
        }
        text.append("\nLower is faster. Detailed confidence intervals and allocation measurements ")
                .append("are included below.\n\n");
    }

    private static boolean hasOnlyImplementationOptions(ComparisonGroup group) {
        for (int i = 0; i < group.results.size(); i++) {
            String options = group.results.get(i).options;
            if (!options.startsWith("implementation=") || options.indexOf(',') >= 0) {
                return false;
            }
        }
        return true;
    }

    private static BenchmarkResult findImplementationResult(ComparisonGroup group,
            String implementation) {
        for (int i = 0; i < group.results.size(); i++) {
            BenchmarkResult result = group.results.get(i);
            if (implementation.equals(optionValue(result.options, "implementation"))) {
                return result;
            }
        }
        return null;
    }

    private static String optionValue(String options, String name) {
        String prefix = name + '=';
        return options.startsWith(prefix) ? options.substring(prefix.length()) : options;
    }

    private static String implementationName(String value) {
        StringBuilder name = new StringBuilder(value.length());
        boolean capitalize = true;
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character == '_') {
                capitalize = true;
            }
            else {
                name.append(capitalize ? Character.toUpperCase(character)
                        : Character.toLowerCase(character));
                capitalize = false;
            }
        }
        return name.toString();
    }

    private static String operationName(String value) {
        if ("GET_BY_INDEX_OR_KEY".equals(value)) {
            return "Get";
        }
        if ("REMOVE_BY_INDEX_OR_KEY".equals(value)) {
            return "Remove";
        }
        if ("REMOVE_DIRECT".equals(value)) {
            return "Direct removal";
        }
        if ("ITERATE_DENSE".equals(value)) {
            return "Dense iteration";
        }
        if ("ITERATE_ORDERED".equals(value)) {
            return "Ordered iteration";
        }
        return implementationName(value);
    }

    private static void appendRunConfiguration(StringBuilder text, BenchmarkParams metadata,
            Array<BenchmarkResult> results) {
        text.append("## Run configuration\n\n");
        text.append("| Setting | Value |\n");
        text.append("|---|---:|\n");
        row(text, "JMH", metadata.getJmhVersion());
        row(text, "JDK", metadata.getJdkVersion());
        row(text, "VM", metadata.getVmName());
        row(text, "Mode", metadata.getMode().longLabel());
        row(text, "Threads", Integer.toString(metadata.getThreads()));
        row(text, "Forks", Integer.toString(metadata.getForks()));
        row(text, "Warmup", metadata.getWarmup().getCount() + " x "
                + metadata.getWarmup().getTime());
        row(text, "Measurement", metadata.getMeasurement().getCount() + " x "
                + metadata.getMeasurement().getTime());
        row(text, "Operations per invocation", Integer.toString(metadata.getOpsPerInvocation()));
        row(text, "Collections", Integer.toString(collectionCount(results)));
        row(text, "Configurations", Integer.toString(results.size()));
        text.append('\n');
    }

    private static void appendConclusions(StringBuilder text, Array<BenchmarkResult> results,
            Array<ComparisonGroup> comparisons) {
        int effectivelyAllocationFree = 0;
        int zeroGc = 0;
        int clearComparisons = 0;
        for (int i = 0; i < results.size(); i++) {
            BenchmarkResult result = results.get(i);
            if (!Double.isNaN(result.allocation) && result.allocation < EFFECTIVELY_ZERO_ALLOCATION) {
                effectivelyAllocationFree++;
            }
            if (!Double.isNaN(result.gcCount) && result.gcCount == 0d) {
                zeroGc++;
            }
        }
        for (int i = 0; i < comparisons.size(); i++) {
            if (comparisons.get(i).clearWinner) {
                clearComparisons++;
            }
        }

        text.append("## Conclusions\n\n");
        text.append("- ").append(effectivelyAllocationFree).append('/').append(results.size())
                .append(" configurations measured below ")
                .append(formatAllocation(EFFECTIVELY_ZERO_ALLOCATION))
                .append(" of normalized allocation.\n");
        text.append("- ").append(zeroGc).append('/').append(results.size())
                .append(" configurations reported zero garbage collections during measurement.\n");
        if (comparisons.isEmpty()) {
            text.append("- This selection has no configuration variants, so option comparisons ")
                    .append("do not apply.\n");
        }
        else {
            text.append("- ").append(clearComparisons).append('/').append(comparisons.size())
                    .append(" option comparisons had a fastest result whose confidence range did ")
                    .append("not overlap any alternative.\n");
            text.append("- `Inconclusive` means the fastest observed score overlapped another ")
                    .append("option's confidence range; do not treat that ordering as proven.\n");
        }
        text.append('\n');
    }

    private static void appendComparisonTable(StringBuilder text,
            Array<ComparisonGroup> comparisons) {
        text.append("## Option comparisons\n\n");
        if (comparisons.isEmpty()) {
            text.append("No configurable alternatives exist for the selected collection ")
                    .append("operations.\n\n");
            return;
        }

        text.append("| Collection | Operation | Fastest observed configuration | Average time | ")
                .append("Slowest vs fastest | Conclusion |\n");
        text.append("|---|---|---|---:|---:|---|\n");
        for (int i = 0; i < comparisons.size(); i++) {
            ComparisonGroup group = comparisons.get(i);
            text.append('|').append(code(group.collection));
            text.append('|').append(code(group.operation));
            text.append('|').append(codeOrDash(group.best.options));
            text.append('|').append(formatScore(group.best.score)).append(' ')
                    .append(group.best.unit);
            text.append('|').append(formatPercent(group.spread));
            text.append('|').append(group.clearWinner ? "Clear separation" : "Inconclusive");
            text.append("|\n");
        }
        text.append('\n');
    }

    private static void appendDetailedResults(StringBuilder text, Array<BenchmarkResult> results) {
        text.append("## Detailed results\n\n");
        String collection = null;
        for (int i = 0; i < results.size(); i++) {
            BenchmarkResult result = results.get(i);
            if (!result.collection.equals(collection)) {
                if (collection != null) {
                    text.append('\n');
                }
                collection = result.collection;
                text.append("### ").append(markdownEscape(collection)).append("\n\n");
                text.append("| Operation | Configuration | Average time | ")
                        .append("99.9% confidence interval | Allocated bytes/op |\n");
                text.append("|---|---|---:|---:|---:|\n");
            }
            text.append('|').append(code(result.operation));
            text.append('|').append(codeOrDash(result.options));
            text.append('|').append(formatScore(result.score)).append(' ').append(result.unit);
            text.append('|').append(formatScore(result.confidenceLow)).append(" - ")
                    .append(formatScore(result.confidenceHigh)).append(' ').append(result.unit);
            text.append('|').append(formatAllocation(result.allocation));
            text.append("|\n");
        }
        text.append('\n');
    }

    private static int collectionCount(Array<BenchmarkResult> results) {
        Array<String> collections = new Array<String>();
        for (int i = 0; i < results.size(); i++) {
            String collection = results.get(i).collection;
            if (!collections.contains(collection)) {
                collections.add(collection);
            }
        }
        return collections.size();
    }

    private static void row(StringBuilder text, String name, String value) {
        text.append('|').append(markdownEscape(name)).append('|')
                .append(markdownEscape(value)).append("|\n");
    }

    private static String collectionName(String method) {
        return Character.toUpperCase(method.charAt(0)) + method.substring(1);
    }

    private static String code(String value) {
        return '`' + value.replace("`", "\\`") + '`';
    }

    private static String codeOrDash(String value) {
        return value.length() == 0 ? "-" : code(value);
    }

    private static String markdownEscape(String value) {
        return value.replace("|", "\\|");
    }

    private static String markdownLink(String value) {
        return value.replace(" ", "%20").replace("(", "%28").replace(")", "%29");
    }

    private static String formatScore(double value) {
        double absolute = Math.abs(value);
        if (absolute >= 100d) {
            return format("%.1f", value);
        }
        if (absolute >= 10d) {
            return format("%.2f", value);
        }
        return format("%.3f", value);
    }

    private static String formatAllocation(double value) {
        if (Double.isNaN(value)) {
            return "n/a";
        }
        double absolute = Math.abs(value);
        if (absolute == 0d) {
            return "0 B/op";
        }
        if (absolute < 0.001d) {
            return format("%.2e B/op", value);
        }
        return format("%.5f B/op", value);
    }

    private static String formatPercent(double value) {
        return Double.isNaN(value) ? "n/a" : format("%.1f%%", value);
    }

    private static String format(String pattern, double value) {
        return String.format(Locale.ROOT, pattern, value);
    }

    private static final class BenchmarkResult {
        private final String collection;
        private final String operation;
        private final String options;
        private final double score;
        private final double confidenceLow;
        private final double confidenceHigh;
        private final String unit;
        private final double allocation;
        private final double gcCount;

        private BenchmarkResult(String collection, String operation, String options, double score,
                double confidenceLow, double confidenceHigh, String unit, double allocation,
                double gcCount) {
            this.collection = collection;
            this.operation = operation;
            this.options = options;
            this.score = score;
            this.confidenceLow = confidenceLow;
            this.confidenceHigh = confidenceHigh;
            this.unit = unit;
            this.allocation = allocation;
            this.gcCount = gcCount;
        }
    }

    private static final class ComparisonGroup {
        private final String collection;
        private final String operation;
        private final Array<BenchmarkResult> results = new Array<BenchmarkResult>();
        private BenchmarkResult best;
        private BenchmarkResult worst;
        private double spread;
        private boolean clearWinner;

        private ComparisonGroup(String collection, String operation) {
            this.collection = collection;
            this.operation = operation;
        }

        private void evaluate() {
            best = results.get(0);
            worst = best;
            for (int i = 1; i < results.size(); i++) {
                BenchmarkResult result = results.get(i);
                if (result.score < best.score) {
                    best = result;
                }
                if (result.score > worst.score) {
                    worst = result;
                }
            }

            clearWinner = true;
            for (int i = 0; i < results.size(); i++) {
                BenchmarkResult result = results.get(i);
                if (result != best && best.confidenceHigh >= result.confidenceLow) {
                    clearWinner = false;
                    break;
                }
            }
            spread = best.score != 0d ? (worst.score / best.score - 1d) * 100d : Double.NaN;
        }
    }
}
