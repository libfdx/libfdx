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

        appendAllCollectionsComparison(text, results);
        appendRunConfiguration(text, metadata, results);
        appendConclusions(text, results, comparisons);
        appendComparisonTable(text, comparisons);
        appendDetailedResults(text, results);
        return text.toString();
    }

    static void appendAllCollectionsComparison(StringBuilder text,
            Array<BenchmarkResult> results) {
        Array<SummaryRow> rows = buildSummaryRows(results);
        if (rows.isEmpty()) {
            return;
        }

        text.append("## All collections performance comparison\n\n");
        text.append("Each result is `average time / allocated bytes per operation`. A `-` means ")
                .append("the collection does not expose a matching benchmark operation.\n\n");
        text.append("| Collection | Add / put | Lookup | Remove | Direct removal | Loop all |\n");
        text.append("|---|---:|---:|---:|---:|---:|\n");
        for (int i = 0; i < rows.size(); i++) {
            SummaryRow row = rows.get(i);
            text.append("| ").append(row.name).append(" |");
            for (int column = 0; column < SummaryColumn.COUNT; column++) {
                appendSummaryCell(text, row.results[column]);
            }
            text.append('\n');
        }
        text.append("\nEvery collection in the report is listed. When present, ordered and ")
                .append("unordered array variants, plus ObjectMap equality and identity modes, ")
                .append("have separate rows. `Lookup` includes get, contains, first, or last ")
                .append("operations. When ")
                .append("multiple operations or configurations fit one cell, it shows the ")
                .append("fastest observed result; this is not a claim of statistical separation. ")
                .append("`Loop all` selects the fastest measured complete traversal and does not ")
                .append("require the same visit order or element representation. Java HashMap ")
                .append("uses Integer keys because Java has no primitive-int HashMap. Exact ")
                .append("operations, configurations, confidence intervals, and allocations are ")
                .append("listed below.\n\n");
    }

    private static Array<SummaryRow> buildSummaryRows(Array<BenchmarkResult> results) {
        Array<SummaryRow> rows = new Array<SummaryRow>();
        Array<String> standardizedKeys = new Array<String>();

        for (int i = 0; i < results.size(); i++) {
            BenchmarkResult result = results.get(i);
            if (!isImplementationComparison(result)) {
                continue;
            }
            String implementation = optionValue(result.options, "implementation");
            SummaryIdentity identity = implementationIdentity(implementation);
            SummaryRow row = findOrCreateSummaryRow(rows, identity);
            row.offer(result);
            if (!standardizedKeys.contains(identity.key)) {
                standardizedKeys.add(identity.key);
            }
        }

        for (int i = 0; i < results.size(); i++) {
            BenchmarkResult result = results.get(i);
            if (isImplementationComparison(result)) {
                continue;
            }
            SummaryIdentity identity = resultIdentity(result);
            if (!standardizedKeys.contains(identity.key)) {
                findOrCreateSummaryRow(rows, identity).offer(result);
            }
        }

        rows.sort((left, right) -> left.key.compareTo(right.key));
        return rows;
    }

    private static SummaryRow findOrCreateSummaryRow(Array<SummaryRow> rows,
            SummaryIdentity identity) {
        for (int i = 0; i < rows.size(); i++) {
            SummaryRow row = rows.get(i);
            if (row.key.equals(identity.key)) {
                return row;
            }
        }
        SummaryRow row = new SummaryRow(identity.key, identity.name);
        rows.add(row);
        return row;
    }

    private static boolean isImplementationComparison(BenchmarkResult result) {
        return "OrderedIntNodeMap".equals(result.collection)
                && result.options.startsWith("implementation=")
                && result.options.indexOf(',') < 0;
    }

    private static SummaryIdentity implementationIdentity(String implementation) {
        if ("ARRAY_ORDERED".equals(implementation)) {
            return new SummaryIdentity("Array|0", "libFDX Array (ordered)");
        }
        if ("ARRAY_UNORDERED".equals(implementation)) {
            return new SummaryIdentity("Array|1", "libFDX Array (unordered)");
        }
        if ("JAVA_ARRAY_LIST".equals(implementation)) {
            return new SummaryIdentity("ArrayList", "Java ArrayList");
        }
        if ("JAVA_HASH_MAP".equals(implementation)) {
            return new SummaryIdentity("HashMap", "Java HashMap (Integer keys)");
        }
        if ("INT_MAP".equals(implementation)) {
            return new SummaryIdentity("IntMap", "libFDX IntMap");
        }
        if ("ORDERED_INT_MAP".equals(implementation)) {
            return new SummaryIdentity("OrderedIntMap", "libFDX OrderedIntMap");
        }
        if ("ORDERED_MAP".equals(implementation)) {
            return new SummaryIdentity("OrderedMap", "libFDX OrderedMap");
        }
        if ("ORDERED_INT_NODE_MAP".equals(implementation)) {
            return new SummaryIdentity("OrderedIntNodeMap", "libFDX OrderedIntNodeMap");
        }
        if ("ORDERED_INT_SPARSE_MAP".equals(implementation)) {
            return new SummaryIdentity("OrderedIntSparseMap", "libFDX OrderedIntSparseMap");
        }
        if ("ORDERED_INT_SPARSE_NODE_MAP".equals(implementation)) {
            return new SummaryIdentity("OrderedIntSparseNodeMap",
                    "libFDX OrderedIntSparseNodeMap");
        }
        return new SummaryIdentity("comparison|" + implementation,
                implementationName(implementation));
    }

    private static SummaryIdentity resultIdentity(BenchmarkResult result) {
        String collection = result.collection;
        if (isArrayCollection(collection)) {
            boolean ordered = Boolean.parseBoolean(optionValue(result.options, "ordered"));
            return new SummaryIdentity(collection + (ordered ? "|0" : "|1"),
                    "libFDX " + collection + (ordered ? " (ordered)" : " (unordered)"));
        }
        if ("ObjectMap".equals(collection)) {
            String comparison = optionValue(result.options, "keyComparison");
            boolean identity = "IDENTITY".equals(comparison);
            return new SummaryIdentity(collection + (identity ? "|1" : "|0"),
                    "libFDX ObjectMap (" + (identity ? "identity" : "equality") + ')');
        }
        if ("ArrayList".equals(collection)) {
            return new SummaryIdentity(collection, "Java ArrayList");
        }
        if ("HashMap".equals(collection)) {
            return new SummaryIdentity(collection, "Java HashMap (Integer keys)");
        }
        return new SummaryIdentity(collection, "libFDX " + collection);
    }

    private static boolean isArrayCollection(String collection) {
        return "Array".equals(collection) || "IntArray".equals(collection)
                || "LongArray".equals(collection) || "FloatArray".equals(collection);
    }

    private static void appendSummaryCell(StringBuilder text, BenchmarkResult result) {
        if (result == null) {
            text.append(" - |");
            return;
        }
        text.append(' ').append(formatScore(result.score)).append(' ')
                .append(result.unit).append(" / ")
                .append(formatComparisonAllocation(result.allocation)).append(" |");
    }

    private static String optionValue(String options, String name) {
        String prefix = name + '=';
        int start = 0;
        while (start < options.length()) {
            int end = options.indexOf(", ", start);
            if (end < 0) {
                end = options.length();
            }
            if (options.startsWith(prefix, start)) {
                return options.substring(start + prefix.length(), end);
            }
            start = end + 2;
        }
        return "";
    }

    private static String implementationName(String value) {
        if ("ARRAY_ORDERED".equals(value)) {
            return "libFDX Array (ordered)";
        }
        if ("ARRAY_UNORDERED".equals(value)) {
            return "libFDX Array (unordered)";
        }
        if ("JAVA_ARRAY_LIST".equals(value)) {
            return "Java ArrayList";
        }
        if ("INT_MAP".equals(value)) {
            return "libFDX IntMap";
        }
        if ("ORDERED_INT_MAP".equals(value)) {
            return "libFDX OrderedIntMap";
        }
        if ("JAVA_HASH_MAP".equals(value)) {
            return "Java HashMap (Integer keys)";
        }
        if ("ORDERED_MAP".equals(value)) {
            return "libFDX OrderedMap";
        }
        if ("ORDERED_INT_NODE_MAP".equals(value)) {
            return "libFDX OrderedIntNodeMap";
        }
        if ("ORDERED_INT_SPARSE_MAP".equals(value)) {
            return "libFDX OrderedIntSparseMap";
        }
        if ("ORDERED_INT_SPARSE_NODE_MAP".equals(value)) {
            return "libFDX OrderedIntSparseNodeMap";
        }
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

    private static String formatComparisonAllocation(double value) {
        if (!Double.isNaN(value) && Math.abs(value) < 0.001d) {
            return "~0 B/op";
        }
        return formatAllocation(value);
    }

    private static String formatPercent(double value) {
        return Double.isNaN(value) ? "n/a" : format("%.1f%%", value);
    }

    private static String format(String pattern, double value) {
        return String.format(Locale.ROOT, pattern, value);
    }

    static final class BenchmarkResult {
        private final String collection;
        private final String operation;
        private final String options;
        private final double score;
        private final double confidenceLow;
        private final double confidenceHigh;
        private final String unit;
        private final double allocation;
        private final double gcCount;

        BenchmarkResult(String collection, String operation, String options, double score,
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

    private static final class SummaryColumn {
        private static final int ADD = 0;
        private static final int LOOKUP = 1;
        private static final int REMOVE = 2;
        private static final int DIRECT_REMOVE = 3;
        private static final int LOOP = 4;
        private static final int COUNT = 5;

        private SummaryColumn() {
        }

        private static int forOperation(String operation) {
            if ("ADD".equals(operation) || "PUT".equals(operation)
                    || "ADD_FIRST".equals(operation) || "ADD_LAST".equals(operation)) {
                return ADD;
            }
            if ("GET".equals(operation) || "CONTAINS".equals(operation)
                    || "FIRST".equals(operation) || "LAST".equals(operation)
                    || "GET_BY_INDEX_OR_KEY".equals(operation)) {
                return LOOKUP;
            }
            if ("REMOVE".equals(operation) || "REMOVE_FIRST".equals(operation)
                    || "REMOVE_LAST".equals(operation)
                    || "REMOVE_BY_INDEX_OR_KEY".equals(operation)) {
                return REMOVE;
            }
            if ("REMOVE_DIRECT".equals(operation)) {
                return DIRECT_REMOVE;
            }
            if ("ITERATE".equals(operation) || "ITERATE_KEYS".equals(operation)
                    || "ITERATE_VALUES".equals(operation)
                    || "ITERATE_ENTRIES".equals(operation)
                    || "LOOP_INDEXED".equals(operation) || "LOOP_ALL".equals(operation)) {
                return LOOP;
            }
            return -1;
        }
    }

    private static final class SummaryIdentity {
        private final String key;
        private final String name;

        private SummaryIdentity(String key, String name) {
            this.key = key;
            this.name = name;
        }
    }

    private static final class SummaryRow {
        private final String key;
        private final String name;
        private final BenchmarkResult[] results = new BenchmarkResult[SummaryColumn.COUNT];

        private SummaryRow(String key, String name) {
            this.key = key;
            this.name = name;
        }

        private void offer(BenchmarkResult result) {
            int column = SummaryColumn.forOperation(result.operation);
            if (column < 0) {
                return;
            }
            BenchmarkResult current = results[column];
            if (current == null || result.score < current.score) {
                results[column] = result;
            }
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
