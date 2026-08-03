package io.github.libfdx.collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CollectionsBenchmarkRunnerTest {
    @Test
    void orderedIntNodeMapSelectionIncludesOnlyItsSharedAndRetainedNodeBenchmarks() {
        Array<String> collections = new Array<>();
        collections.add("OrderedIntNodeMap");

        String filter = CollectionsBenchmarkRunner.benchmarkFilter(collections);

        assertTrue("io.github.libfdx.collections.CollectionsBenchmark.orderedIntNodeMap"
                .matches(filter));
        assertTrue("io.github.libfdx.collections.CollectionsBenchmark.orderedIntNodeRetainedRemoval"
                .matches(filter));
        assertFalse("io.github.libfdx.collections.CollectionsBenchmark.orderedIntSparseNodeMap"
                .matches(filter));
    }
}
