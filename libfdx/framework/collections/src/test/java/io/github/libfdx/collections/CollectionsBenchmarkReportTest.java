package io.github.libfdx.collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CollectionsBenchmarkReportTest {
    @Test
    void retainedRemovalBenchmarkContributesToOrderedIntNodeMap() {
        assertEquals("OrderedIntNodeMap",
                CollectionsBenchmarkReport.collectionName("orderedIntNodeRetainedRemoval"));
    }

    @Test
    void includesEveryStandaloneCollectionAndMarksUnsupportedOperations() {
        Array<CollectionsBenchmarkReport.BenchmarkResult> results = new Array<>();
        results.add(result("CustomBag", "ADD", "", 4d));
        results.add(result("CustomBag", "GET", "", 3d));
        results.add(result("CustomBag", "REMOVE", "", 2d));
        results.add(result("CustomBag", "ITERATE", "", 1d));

        String markdown = comparison(results);

        assertTrue(markdown.contains("| Collection | Add / put | Lookup | Remove | "
                + "Remove by retained node | Loop all |"));
        assertTrue(markdown.contains("| libFDX CustomBag | 4.000 ns/op / ~0 B/op | "
                + "3.000 ns/op / ~0 B/op | 2.000 ns/op / ~0 B/op | - | "
                + "1.000 ns/op / ~0 B/op |"));
    }

    @Test
    void keepsSemanticVariantsAsSeparateRowsAndSelectsFastestTuningOption() {
        Array<CollectionsBenchmarkReport.BenchmarkResult> results = new Array<>();
        results.add(result("Array", "ADD", "ordered=true", 4d));
        results.add(result("Array", "ADD", "ordered=false", 3d));
        results.add(result("ObjectMap", "PUT",
                "keyComparison=EQUALITY, loadFactor=0.50", 8d));
        results.add(result("ObjectMap", "PUT",
                "keyComparison=EQUALITY, loadFactor=0.75", 6d));
        results.add(result("ObjectMap", "PUT",
                "keyComparison=IDENTITY, loadFactor=0.75", 5d));

        String markdown = comparison(results);

        assertTrue(markdown.contains("| libFDX Array (ordered) | 4.000 ns/op"));
        assertTrue(markdown.contains("| libFDX Array (unordered) | 3.000 ns/op"));
        assertTrue(markdown.contains("| libFDX ObjectMap (equality) | 6.000 ns/op"));
        assertTrue(markdown.contains("| libFDX ObjectMap (identity) | 5.000 ns/op"));
        assertFalse(markdown.contains("| libFDX ObjectMap (equality) | 8.000 ns/op"));
    }

    @Test
    void standardizedComparisonWinsOverDuplicateStandaloneMeasurements() {
        Array<CollectionsBenchmarkReport.BenchmarkResult> results = new Array<>();
        results.add(result("OrderedIntNodeMap", "ADD", "implementation=INT_MAP", 5d));
        results.add(result("OrderedIntNodeMap", "GET_BY_INDEX_OR_KEY",
                "implementation=INT_MAP", 4d));
        results.add(result("OrderedIntNodeMap", "REMOVE_BY_RETAINED_NODE",
                "implementation=INT_MAP", 0.5d));
        results.add(result("OrderedIntNodeMap", "REMOVE_BY_RETAINED_NODE",
                "implementation=ORDERED_INT_NODE_MAP", 2d));
        results.add(result("IntMap", "PUT", "loadFactor=0.75", 1d));

        String markdown = comparison(results);

        assertTrue(markdown.contains("| libFDX IntMap | 5.000 ns/op / ~0 B/op | "
                + "4.000 ns/op / ~0 B/op |"));
        assertTrue(markdown.contains("| libFDX IntMap | 5.000 ns/op / ~0 B/op | "
                + "4.000 ns/op / ~0 B/op | - | - | - |"));
        assertTrue(markdown.contains("| libFDX OrderedIntNodeMap | - | - | - | "
                + "2.000 ns/op / ~0 B/op | - |"));
        assertFalse(markdown.contains("| libFDX IntMap | 1.000 ns/op"));
        assertFalse(markdown.contains("0.500 ns/op"));
        assertEquals(1, occurrences(markdown, "| libFDX IntMap |"));
    }

    private static CollectionsBenchmarkReport.BenchmarkResult result(String collection,
            String operation, String options, double score) {
        return new CollectionsBenchmarkReport.BenchmarkResult(collection, operation, options,
                score, score - 0.1d, score + 0.1d, "ns/op", 0d, 0d);
    }

    private static String comparison(
            Array<CollectionsBenchmarkReport.BenchmarkResult> results) {
        StringBuilder markdown = new StringBuilder();
        CollectionsBenchmarkReport.appendAllCollectionsComparison(markdown, results);
        return markdown.toString();
    }

    private static int occurrences(String text, String value) {
        int count = 0;
        int start = 0;
        while ((start = text.indexOf(value, start)) >= 0) {
            count++;
            start += value.length();
        }
        return count;
    }
}
