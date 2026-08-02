package io.github.libfdx.collections;

import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

/**
 * JMH coverage for every concrete libFDX collection.
 *
 * <p>Each invocation performs {@value #ELEMENT_COUNT} logical collection
 * operations and is normalized by JMH to nanoseconds per logical operation.
 * Mutation setup happens outside the timed region and reuses reserved storage.
 * Full runs also enable JMH's GC profiler through the Gradle task.</p>
 *
 * <p>Array benchmarks cover ordered and unordered storage. Hash collections
 * cover 0.50 and 0.75 load factors, while {@link ObjectMap} additionally covers
 * equality and identity keys. Indexed {@code GET} cases use a deterministic
 * scrambled access order; indexed loops remain sequential. The
 * {@link OrderedIntNodeMap} benchmark directly compares unordered array,
 * unordered primitive map, ordered object map, hash-backed ordered nodes,
 * and sparse-set ordered nodes.</p>
 *
 * <p>Run the complete benchmark with {@code benchmarkCollections}, or verify
 * the matrix quickly with {@code benchmarkCollectionsQuick}. The full task
 * writes both raw JSON and a readable Markdown report under
 * {@code build/reports/jmh}. Use an individual task such as
 * {@code benchmarkArray}, or use {@code benchmarkSelectedCollections} with
 * {@code -Pcollections=Array,ObjectMap}, to run a subset with its own
 * reports.</p>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(value = 2, jvmArgsAppend = {
        "-Xms512m",
        "-Xmx512m",
        "-XX:+AlwaysPreTouch"
})
@Threads(1)
public class CollectionsBenchmark {
    /** Number of logical operations performed by every benchmark invocation. */
    public static final int ELEMENT_COUNT = 10_000;

    private static final BenchmarkValue[] OBJECT_VALUES = new BenchmarkValue[ELEMENT_COUNT];
    private static final BenchmarkKey[] OBJECT_KEYS = new BenchmarkKey[ELEMENT_COUNT];
    private static final BenchmarkKey[] EQUAL_OBJECT_KEYS = new BenchmarkKey[ELEMENT_COUNT];
    private static final int[] ACCESS_INDICES = new int[ELEMENT_COUNT];
    private static final int[] REMOVAL_INDICES = new int[ELEMENT_COUNT];
    private static final int[] REMOVAL_VALUE_INDICES = new int[ELEMENT_COUNT];

    static {
        int[] denseValueIndices = new int[ELEMENT_COUNT];
        for (int i = 0; i < ELEMENT_COUNT; i++) {
            OBJECT_VALUES[i] = new BenchmarkValue(i);
            OBJECT_KEYS[i] = new BenchmarkKey(i);
            EQUAL_OBJECT_KEYS[i] = new BenchmarkKey(i);
            ACCESS_INDICES[i] = i * 7_919 % ELEMENT_COUNT;
            denseValueIndices[i] = i;
        }
        for (int i = 0, remaining = ELEMENT_COUNT; i < ELEMENT_COUNT; i++, remaining--) {
            int removalIndex = ACCESS_INDICES[i] % remaining;
            REMOVAL_INDICES[i] = removalIndex;
            REMOVAL_VALUE_INDICES[i] = denseValueIndices[removalIndex];
            denseValueIndices[removalIndex] = denseValueIndices[remaining - 1];
        }
    }

    /** Runs JMH with any supplied command-line overrides. */
    public static void main(String[] arguments) throws Exception {
        org.openjdk.jmh.Main.main(arguments);
    }

    /** Operations supported by indexed array collections. */
    public enum ArrayOperation {
        ADD,
        GET,
        REMOVE_FIRST,
        REMOVE_LAST,
        LOOP_INDEXED,
        ITERATE
    }

    /** Operations supported by map collections. */
    public enum MapOperation {
        PUT,
        GET,
        REMOVE,
        ITERATE_KEYS,
        ITERATE_VALUES,
        ITERATE_ENTRIES
    }

    /** Operations supported by set collections. */
    public enum SetOperation {
        ADD,
        CONTAINS,
        REMOVE,
        ITERATE
    }

    /** Operations supported by {@link ObjectQueue}. */
    public enum QueueOperation {
        ADD_FIRST,
        ADD_LAST,
        REMOVE_FIRST,
        GET,
        ITERATE
    }

    /** Operations supported by {@link ObjectLinkedList}. */
    public enum LinkedListOperation {
        ADD_FIRST,
        ADD_LAST,
        REMOVE_FIRST,
        REMOVE_LAST,
        FIRST,
        LAST,
        ITERATE
    }

    /** Implementations in the ordered int node map comparison. */
    public enum NodeMapComparisonImplementation {
        ARRAY,
        INT_MAP,
        ORDERED_MAP,
        ORDERED_INT_NODE_MAP,
        ORDERED_INT_SPARSE_NODE_MAP
    }

    /** Operations in the ordered int node map comparison. */
    public enum NodeMapComparisonOperation {
        ADD,
        GET_BY_INDEX_OR_KEY,
        REMOVE_BY_INDEX_OR_KEY,
        REMOVE_DIRECT,
        ITERATE_DENSE,
        ITERATE_ORDERED
    }

    /** State for ordered and unordered {@link Array} variants. */
    @State(Scope.Thread)
    public static class ArrayState {
        @Param({"true", "false"})
        public boolean ordered;

        @Param
        public ArrayOperation operation;

        public Array<BenchmarkValue> values;

        @Setup(Level.Trial)
        public void setupTrial() {
            values = new Array<BenchmarkValue>(ordered, ELEMENT_COUNT);
            if (isArrayRead(operation)) {
                fill(values);
            }
            if (operation == ArrayOperation.ITERATE) {
                values.iterator();
            }
        }

        @Setup(Level.Invocation)
        public void setupInvocation() {
            if (operation == ArrayOperation.ADD) {
                values.clear();
            }
            else if (isArrayRemoval(operation)) {
                values.clear();
                fill(values);
            }
        }

        @TearDown(Level.Invocation)
        public void validate() {
            validateSize("Array", values.size(), isArrayRemoval(operation) ? 0 : ELEMENT_COUNT);
        }
    }

    /** State for ordered and unordered {@link IntArray} variants. */
    @State(Scope.Thread)
    public static class IntArrayState {
        @Param({"true", "false"})
        public boolean ordered;

        @Param
        public ArrayOperation operation;

        public IntArray values;

        @Setup(Level.Trial)
        public void setupTrial() {
            values = new IntArray(ordered, ELEMENT_COUNT);
            if (isArrayRead(operation)) {
                fill(values);
            }
            if (operation == ArrayOperation.ITERATE) {
                values.iterator();
            }
        }

        @Setup(Level.Invocation)
        public void setupInvocation() {
            if (operation == ArrayOperation.ADD) {
                values.clear();
            }
            else if (isArrayRemoval(operation)) {
                values.clear();
                fill(values);
            }
        }

        @TearDown(Level.Invocation)
        public void validate() {
            validateSize("IntArray", values.size(), isArrayRemoval(operation) ? 0 : ELEMENT_COUNT);
        }
    }

    /** State for ordered and unordered {@link LongArray} variants. */
    @State(Scope.Thread)
    public static class LongArrayState {
        @Param({"true", "false"})
        public boolean ordered;

        @Param
        public ArrayOperation operation;

        public LongArray values;

        @Setup(Level.Trial)
        public void setupTrial() {
            values = new LongArray(ordered, ELEMENT_COUNT);
            if (isArrayRead(operation)) {
                fill(values);
            }
            if (operation == ArrayOperation.ITERATE) {
                values.iterator();
            }
        }

        @Setup(Level.Invocation)
        public void setupInvocation() {
            if (operation == ArrayOperation.ADD) {
                values.clear();
            }
            else if (isArrayRemoval(operation)) {
                values.clear();
                fill(values);
            }
        }

        @TearDown(Level.Invocation)
        public void validate() {
            validateSize("LongArray", values.size(), isArrayRemoval(operation) ? 0 : ELEMENT_COUNT);
        }
    }

    /** State for ordered and unordered {@link FloatArray} variants. */
    @State(Scope.Thread)
    public static class FloatArrayState {
        @Param({"true", "false"})
        public boolean ordered;

        @Param
        public ArrayOperation operation;

        public FloatArray values;

        @Setup(Level.Trial)
        public void setupTrial() {
            values = new FloatArray(ordered, ELEMENT_COUNT);
            if (isArrayRead(operation)) {
                fill(values);
            }
            if (operation == ArrayOperation.ITERATE) {
                values.iterator();
            }
        }

        @Setup(Level.Invocation)
        public void setupInvocation() {
            if (operation == ArrayOperation.ADD) {
                values.clear();
            }
            else if (isArrayRemoval(operation)) {
                values.clear();
                fill(values);
            }
        }

        @TearDown(Level.Invocation)
        public void validate() {
            validateSize("FloatArray", values.size(), isArrayRemoval(operation) ? 0 : ELEMENT_COUNT);
        }
    }

    /** State for equality and identity {@link ObjectMap} variants. */
    @State(Scope.Thread)
    public static class ObjectMapState {
        @Param({"EQUALITY", "IDENTITY"})
        public KeyComparison keyComparison;

        @Param({"0.50", "0.75"})
        public float loadFactor;

        @Param
        public MapOperation operation;

        public ObjectMap<BenchmarkKey, BenchmarkValue> values;
        public BenchmarkKey[] lookupKeys;

        @Setup(Level.Trial)
        public void setupTrial() {
            values = new ObjectMap<BenchmarkKey, BenchmarkValue>(
                    ELEMENT_COUNT, loadFactor, keyComparison);
            lookupKeys = keyComparison == KeyComparison.IDENTITY ? OBJECT_KEYS : EQUAL_OBJECT_KEYS;
            if (isMapRead(operation)) {
                fill(values);
            }
            prewarmMapIterator(values, operation);
        }

        @Setup(Level.Invocation)
        public void setupInvocation() {
            if (operation == MapOperation.PUT) {
                values.clear();
            }
            else if (operation == MapOperation.REMOVE) {
                values.clear();
                fill(values);
            }
        }

        @TearDown(Level.Invocation)
        public void validate() {
            validateSize("ObjectMap", values.size(), operation == MapOperation.REMOVE ? 0 : ELEMENT_COUNT);
        }
    }

    /** State for insertion-ordered {@link OrderedMap}. */
    @State(Scope.Thread)
    public static class OrderedMapState {
        @Param({"0.50", "0.75"})
        public float loadFactor;

        @Param
        public MapOperation operation;

        public OrderedMap<BenchmarkKey, BenchmarkValue> values;

        @Setup(Level.Trial)
        public void setupTrial() {
            values = new OrderedMap<BenchmarkKey, BenchmarkValue>(ELEMENT_COUNT, loadFactor);
            if (isMapRead(operation)) {
                fill(values);
            }
            prewarmMapIterator(values, operation);
        }

        @Setup(Level.Invocation)
        public void setupInvocation() {
            if (operation == MapOperation.PUT) {
                values.clear();
            }
            else if (operation == MapOperation.REMOVE) {
                values.clear();
                fill(values);
            }
        }

        @TearDown(Level.Invocation)
        public void validate() {
            validateSize("OrderedMap", values.size(), operation == MapOperation.REMOVE ? 0 : ELEMENT_COUNT);
        }
    }

    /** State for {@link IntMap}. */
    @State(Scope.Thread)
    public static class IntMapState {
        @Param({"0.50", "0.75"})
        public float loadFactor;

        @Param
        public MapOperation operation;

        public IntMap<BenchmarkValue> values;

        @Setup(Level.Trial)
        public void setupTrial() {
            values = new IntMap<BenchmarkValue>(ELEMENT_COUNT, loadFactor);
            if (isMapRead(operation)) {
                fill(values);
            }
            prewarmMapIterator(values, operation);
        }

        @Setup(Level.Invocation)
        public void setupInvocation() {
            if (operation == MapOperation.PUT) {
                values.clear();
            }
            else if (operation == MapOperation.REMOVE) {
                values.clear();
                fill(values);
            }
        }

        @TearDown(Level.Invocation)
        public void validate() {
            validateSize("IntMap", values.size(), operation == MapOperation.REMOVE ? 0 : ELEMENT_COUNT);
        }
    }

    /** State for {@link LongMap}. */
    @State(Scope.Thread)
    public static class LongMapState {
        @Param({"0.50", "0.75"})
        public float loadFactor;

        @Param
        public MapOperation operation;

        public LongMap<BenchmarkValue> values;

        @Setup(Level.Trial)
        public void setupTrial() {
            values = new LongMap<BenchmarkValue>(ELEMENT_COUNT, loadFactor);
            if (isMapRead(operation)) {
                fill(values);
            }
            prewarmMapIterator(values, operation);
        }

        @Setup(Level.Invocation)
        public void setupInvocation() {
            if (operation == MapOperation.PUT) {
                values.clear();
            }
            else if (operation == MapOperation.REMOVE) {
                values.clear();
                fill(values);
            }
        }

        @TearDown(Level.Invocation)
        public void validate() {
            validateSize("LongMap", values.size(), operation == MapOperation.REMOVE ? 0 : ELEMENT_COUNT);
        }
    }

    /** State for {@link FloatMap}. */
    @State(Scope.Thread)
    public static class FloatMapState {
        @Param({"0.50", "0.75"})
        public float loadFactor;

        @Param
        public MapOperation operation;

        public FloatMap<BenchmarkValue> values;

        @Setup(Level.Trial)
        public void setupTrial() {
            values = new FloatMap<BenchmarkValue>(ELEMENT_COUNT, loadFactor);
            if (isMapRead(operation)) {
                fill(values);
            }
            prewarmMapIterator(values, operation);
        }

        @Setup(Level.Invocation)
        public void setupInvocation() {
            if (operation == MapOperation.PUT) {
                values.clear();
            }
            else if (operation == MapOperation.REMOVE) {
                values.clear();
                fill(values);
            }
        }

        @TearDown(Level.Invocation)
        public void validate() {
            validateSize("FloatMap", values.size(), operation == MapOperation.REMOVE ? 0 : ELEMENT_COUNT);
        }
    }

    /** State for {@link ObjectSet}. */
    @State(Scope.Thread)
    public static class ObjectSetState {
        @Param({"0.50", "0.75"})
        public float loadFactor;

        @Param
        public SetOperation operation;

        public ObjectSet<BenchmarkKey> values;

        @Setup(Level.Trial)
        public void setupTrial() {
            values = new ObjectSet<BenchmarkKey>(ELEMENT_COUNT, loadFactor);
            if (isSetRead(operation)) {
                fill(values);
            }
            if (operation == SetOperation.ITERATE) {
                values.iterator();
            }
        }

        @Setup(Level.Invocation)
        public void setupInvocation() {
            if (operation == SetOperation.ADD) {
                values.clear();
            }
            else if (operation == SetOperation.REMOVE) {
                values.clear();
                fill(values);
            }
        }

        @TearDown(Level.Invocation)
        public void validate() {
            validateSize("ObjectSet", values.size(), operation == SetOperation.REMOVE ? 0 : ELEMENT_COUNT);
        }
    }

    /** State for {@link IntSet}. */
    @State(Scope.Thread)
    public static class IntSetState {
        @Param({"0.50", "0.75"})
        public float loadFactor;

        @Param
        public SetOperation operation;

        public IntSet values;

        @Setup(Level.Trial)
        public void setupTrial() {
            values = new IntSet(ELEMENT_COUNT, loadFactor);
            if (isSetRead(operation)) {
                fill(values);
            }
            if (operation == SetOperation.ITERATE) {
                values.iterator();
            }
        }

        @Setup(Level.Invocation)
        public void setupInvocation() {
            if (operation == SetOperation.ADD) {
                values.clear();
            }
            else if (operation == SetOperation.REMOVE) {
                values.clear();
                fill(values);
            }
        }

        @TearDown(Level.Invocation)
        public void validate() {
            validateSize("IntSet", values.size(), operation == SetOperation.REMOVE ? 0 : ELEMENT_COUNT);
        }
    }

    /** State for {@link ObjectQueue}. */
    @State(Scope.Thread)
    public static class ObjectQueueState {
        @Param
        public QueueOperation operation;

        public ObjectQueue<BenchmarkValue> values;

        @Setup(Level.Trial)
        public void setupTrial() {
            values = new ObjectQueue<BenchmarkValue>(ELEMENT_COUNT);
            if (operation == QueueOperation.GET || operation == QueueOperation.ITERATE) {
                fill(values);
            }
            if (operation == QueueOperation.ITERATE) {
                values.iterator();
            }
        }

        @Setup(Level.Invocation)
        public void setupInvocation() {
            if (operation == QueueOperation.ADD_FIRST || operation == QueueOperation.ADD_LAST) {
                values.clear();
            }
            else if (operation == QueueOperation.REMOVE_FIRST) {
                values.clear();
                fill(values);
            }
        }

        @TearDown(Level.Invocation)
        public void validate() {
            validateSize("ObjectQueue", values.size(),
                    operation == QueueOperation.REMOVE_FIRST ? 0 : ELEMENT_COUNT);
        }
    }

    /** State for pooled-node {@link ObjectLinkedList}. */
    @State(Scope.Thread)
    public static class ObjectLinkedListState {
        @Param
        public LinkedListOperation operation;

        public ObjectLinkedList<BenchmarkValue> values;

        @Setup(Level.Trial)
        public void setupTrial() {
            values = new ObjectLinkedList<BenchmarkValue>(ELEMENT_COUNT);
            if (isLinkedListRead(operation)) {
                fill(values);
            }
            if (operation == LinkedListOperation.ITERATE) {
                values.iterator();
            }
        }

        @Setup(Level.Invocation)
        public void setupInvocation() {
            if (operation == LinkedListOperation.ADD_FIRST || operation == LinkedListOperation.ADD_LAST) {
                values.clear();
            }
            else if (operation == LinkedListOperation.REMOVE_FIRST
                    || operation == LinkedListOperation.REMOVE_LAST) {
                values.clear();
                fill(values);
            }
        }

        @TearDown(Level.Invocation)
        public void validate() {
            boolean removal = operation == LinkedListOperation.REMOVE_FIRST
                    || operation == LinkedListOperation.REMOVE_LAST;
            validateSize("ObjectLinkedList", values.size(), removal ? 0 : ELEMENT_COUNT);
        }
    }

    /**
     * State for the direct array, unordered map, ordered map, and customizable
     * ordered node map comparison.
     */
    @State(Scope.Thread)
    public static class OrderedIntNodeMapComparisonState {
        @Param
        public NodeMapComparisonImplementation implementation;

        @Param
        public NodeMapComparisonOperation operation;

        public Array<BenchmarkValue> array;
        public IntMap<BenchmarkValue> intMap;
        public OrderedMap<BenchmarkKey, BenchmarkValue> orderedMap;
        public OrderedIntNodeMap<BenchmarkValue, BenchmarkNode> nodeMap;
        public OrderedIntSparseNodeMap<BenchmarkValue, BenchmarkSparseNode> sparseNodeMap;
        public BenchmarkNode[] nodeRemovalOrder;
        public BenchmarkSparseNode[] sparseNodeRemovalOrder;

        @Setup(Level.Trial)
        public void setupTrial() {
            switch (implementation) {
                case ARRAY:
                    array = new Array<BenchmarkValue>(false, ELEMENT_COUNT);
                    break;
                case INT_MAP:
                    intMap = new IntMap<BenchmarkValue>(ELEMENT_COUNT);
                    break;
                case ORDERED_MAP:
                    orderedMap = new OrderedMap<BenchmarkKey, BenchmarkValue>(ELEMENT_COUNT);
                    break;
                case ORDERED_INT_NODE_MAP:
                    nodeMap = new OrderedIntNodeMap<BenchmarkValue, BenchmarkNode>(
                            ELEMENT_COUNT, BenchmarkNode::new);
                    nodeRemovalOrder = new BenchmarkNode[ELEMENT_COUNT];
                    break;
                case ORDERED_INT_SPARSE_NODE_MAP:
                    sparseNodeMap = new OrderedIntSparseNodeMap<BenchmarkValue, BenchmarkSparseNode>(
                            ELEMENT_COUNT, ELEMENT_COUNT, BenchmarkSparseNode::new);
                    sparseNodeRemovalOrder = new BenchmarkSparseNode[ELEMENT_COUNT];
                    break;
                default:
                    throw new AssertionError(implementation);
            }
            if (isNodeMapComparisonRead(operation)) {
                fillComparison(this);
            }
            prewarmComparisonIterator(this);
        }

        @Setup(Level.Invocation)
        public void setupInvocation() {
            if (operation == NodeMapComparisonOperation.ADD) {
                clearComparison(this);
            }
            else if (isNodeMapComparisonRemoval(operation)) {
                clearComparison(this);
                fillComparison(this);
                if (operation == NodeMapComparisonOperation.REMOVE_DIRECT) {
                    prepareDirectRemoval(this);
                }
            }
        }

        @TearDown(Level.Invocation)
        public void validate() {
            boolean removal = isNodeMapComparisonRemoval(operation);
            validateSize(implementation.name(), comparisonSize(this), removal ? 0 : ELEMENT_COUNT);
        }
    }

    /** State for the standalone {@link OrderedIntSparseNodeMap} benchmark. */
    @State(Scope.Thread)
    public static class OrderedIntSparseNodeMapState {
        @Param
        public NodeMapComparisonOperation operation;

        public OrderedIntSparseNodeMap<BenchmarkValue, BenchmarkSparseNode> values;
        public BenchmarkSparseNode[] removalOrder;

        @Setup(Level.Trial)
        public void setupTrial() {
            values = new OrderedIntSparseNodeMap<BenchmarkValue, BenchmarkSparseNode>(
                    ELEMENT_COUNT, ELEMENT_COUNT, BenchmarkSparseNode::new);
            removalOrder = new BenchmarkSparseNode[ELEMENT_COUNT];
            if (isNodeMapComparisonRead(operation)) {
                fillComparison(values);
            }
            if (operation == NodeMapComparisonOperation.ITERATE_ORDERED) {
                values.iterator();
            }
        }

        @Setup(Level.Invocation)
        public void setupInvocation() {
            if (operation == NodeMapComparisonOperation.ADD) {
                values.clear();
            }
            else if (isNodeMapComparisonRemoval(operation)) {
                values.clear();
                fillComparison(values);
                if (operation == NodeMapComparisonOperation.REMOVE_DIRECT) {
                    prepareDirectRemoval(values, removalOrder);
                }
            }
        }

        @TearDown(Level.Invocation)
        public void validate() {
            boolean removal = isNodeMapComparisonRemoval(operation);
            validateSize("OrderedIntSparseNodeMap", values.size(),
                    removal ? 0 : ELEMENT_COUNT);
        }
    }

    @Benchmark
    @OperationsPerInvocation(ELEMENT_COUNT)
    public long array(ArrayState state) {
        switch (state.operation) {
            case ADD:
                for (int i = 0; i < ELEMENT_COUNT; i++) {
                    state.values.add(OBJECT_VALUES[i]);
                }
                return state.values.size();
            case GET:
                return randomAccessSum(state.values);
            case LOOP_INDEXED:
                return indexedSum(state.values);
            case REMOVE_FIRST:
                return removeFirstSum(state.values);
            case REMOVE_LAST:
                return removeLastSum(state.values);
            case ITERATE:
                return sum(state.values);
            default:
                throw new AssertionError(state.operation);
        }
    }

    @Benchmark
    @OperationsPerInvocation(ELEMENT_COUNT)
    public long intArray(IntArrayState state) {
        switch (state.operation) {
            case ADD:
                for (int i = 0; i < ELEMENT_COUNT; i++) {
                    state.values.add(intValue(i));
                }
                return state.values.size();
            case GET:
                return randomAccessSum(state.values);
            case LOOP_INDEXED:
                return indexedSum(state.values);
            case REMOVE_FIRST:
                return removeFirstSum(state.values);
            case REMOVE_LAST:
                return removeLastSum(state.values);
            case ITERATE:
                return sum(state.values);
            default:
                throw new AssertionError(state.operation);
        }
    }

    @Benchmark
    @OperationsPerInvocation(ELEMENT_COUNT)
    public long longArray(LongArrayState state) {
        switch (state.operation) {
            case ADD:
                for (int i = 0; i < ELEMENT_COUNT; i++) {
                    state.values.add(longValue(i));
                }
                return state.values.size();
            case GET:
                return randomAccessSum(state.values);
            case LOOP_INDEXED:
                return indexedSum(state.values);
            case REMOVE_FIRST:
                return removeFirstSum(state.values);
            case REMOVE_LAST:
                return removeLastSum(state.values);
            case ITERATE:
                return sum(state.values);
            default:
                throw new AssertionError(state.operation);
        }
    }

    @Benchmark
    @OperationsPerInvocation(ELEMENT_COUNT)
    public long floatArray(FloatArrayState state) {
        switch (state.operation) {
            case ADD:
                for (int i = 0; i < ELEMENT_COUNT; i++) {
                    state.values.add(floatValue(i));
                }
                return state.values.size();
            case GET:
                return randomAccessSum(state.values);
            case LOOP_INDEXED:
                return indexedSum(state.values);
            case REMOVE_FIRST:
                return removeFirstSum(state.values);
            case REMOVE_LAST:
                return removeLastSum(state.values);
            case ITERATE:
                return sum(state.values);
            default:
                throw new AssertionError(state.operation);
        }
    }

    @Benchmark
    @OperationsPerInvocation(ELEMENT_COUNT)
    public long objectMap(ObjectMapState state) {
        switch (state.operation) {
            case PUT:
                for (int i = 0; i < ELEMENT_COUNT; i++) {
                    state.values.put(OBJECT_KEYS[i], OBJECT_VALUES[i]);
                }
                return state.values.size();
            case GET:
                return getSum(state.values, state.lookupKeys);
            case REMOVE:
                return removeSum(state.values, state.lookupKeys);
            case ITERATE_KEYS:
                return objectKeySum(state.values.keys());
            case ITERATE_VALUES:
                return sum(state.values.values());
            case ITERATE_ENTRIES:
                return objectMapEntrySum(state.values.entries());
            default:
                throw new AssertionError(state.operation);
        }
    }

    @Benchmark
    @OperationsPerInvocation(ELEMENT_COUNT)
    public long orderedMap(OrderedMapState state) {
        switch (state.operation) {
            case PUT:
                for (int i = 0; i < ELEMENT_COUNT; i++) {
                    state.values.put(OBJECT_KEYS[i], OBJECT_VALUES[i]);
                }
                return state.values.size();
            case GET:
                return getSum(state.values);
            case REMOVE:
                return removeSum(state.values);
            case ITERATE_KEYS:
                return objectKeySum(state.values.keys());
            case ITERATE_VALUES:
                return sum(state.values.values());
            case ITERATE_ENTRIES:
                return orderedMapEntrySum(state.values.entries());
            default:
                throw new AssertionError(state.operation);
        }
    }

    @Benchmark
    @OperationsPerInvocation(ELEMENT_COUNT)
    public long intMap(IntMapState state) {
        switch (state.operation) {
            case PUT:
                for (int i = 0; i < ELEMENT_COUNT; i++) {
                    state.values.put(intKey(i), OBJECT_VALUES[i]);
                }
                return state.values.size();
            case GET:
                return getSum(state.values);
            case REMOVE:
                return removeSum(state.values);
            case ITERATE_KEYS:
                return sum(state.values.keys());
            case ITERATE_VALUES:
                return sum(state.values.values());
            case ITERATE_ENTRIES:
                return intMapEntrySum(state.values.entries());
            default:
                throw new AssertionError(state.operation);
        }
    }

    /**
     * Directly compares native operations across an unordered array,
     * {@link IntMap}, insertion-ordered {@link OrderedMap}, and
     * {@link OrderedIntNodeMap}. Array access/removal uses known dense indices;
     * map access/removal uses keys. Dense and ordered node-map traversals are
     * measured independently, while the baseline collections repeat their
     * native traversal in both iteration rows. Direct removal uses a retained
     * node reference for node maps; collections without node handles repeat
     * their native known-index or key removal as the baseline.
     *
     * @param state the selected implementation and operation
     * @return a checksum consumed by JMH
     */
    @Benchmark
    @OperationsPerInvocation(ELEMENT_COUNT)
    public long orderedIntNodeMap(OrderedIntNodeMapComparisonState state) {
        switch (state.implementation) {
            case ARRAY:
                return compareArray(state.array, state.operation);
            case INT_MAP:
                return compareIntMap(state.intMap, state.operation);
            case ORDERED_MAP:
                return compareOrderedMap(state.orderedMap, state.operation);
            case ORDERED_INT_NODE_MAP:
                return compareOrderedIntNodeMap(
                        state.nodeMap, state.nodeRemovalOrder, state.operation);
            case ORDERED_INT_SPARSE_NODE_MAP:
                return compareOrderedIntSparseNodeMap(
                        state.sparseNodeMap, state.sparseNodeRemovalOrder, state.operation);
            default:
                throw new AssertionError(state.implementation);
        }
    }

    /**
     * Benchmarks the sparse-set ordered node map independently.
     *
     * @param state the selected operation
     * @return a checksum consumed by JMH
     */
    @Benchmark
    @OperationsPerInvocation(ELEMENT_COUNT)
    public long orderedIntSparseNodeMap(OrderedIntSparseNodeMapState state) {
        return compareOrderedIntSparseNodeMap(
                state.values, state.removalOrder, state.operation);
    }

    @Benchmark
    @OperationsPerInvocation(ELEMENT_COUNT)
    public long longMap(LongMapState state) {
        switch (state.operation) {
            case PUT:
                for (int i = 0; i < ELEMENT_COUNT; i++) {
                    state.values.put(longKey(i), OBJECT_VALUES[i]);
                }
                return state.values.size();
            case GET:
                return getSum(state.values);
            case REMOVE:
                return removeSum(state.values);
            case ITERATE_KEYS:
                return sum(state.values.keys());
            case ITERATE_VALUES:
                return sum(state.values.values());
            case ITERATE_ENTRIES:
                return longMapEntrySum(state.values.entries());
            default:
                throw new AssertionError(state.operation);
        }
    }

    @Benchmark
    @OperationsPerInvocation(ELEMENT_COUNT)
    public long floatMap(FloatMapState state) {
        switch (state.operation) {
            case PUT:
                for (int i = 0; i < ELEMENT_COUNT; i++) {
                    state.values.put(floatKey(i), OBJECT_VALUES[i]);
                }
                return state.values.size();
            case GET:
                return getSum(state.values);
            case REMOVE:
                return removeSum(state.values);
            case ITERATE_KEYS:
                return floatBitsSum(state.values.keys());
            case ITERATE_VALUES:
                return sum(state.values.values());
            case ITERATE_ENTRIES:
                return floatMapEntrySum(state.values.entries());
            default:
                throw new AssertionError(state.operation);
        }
    }

    @Benchmark
    @OperationsPerInvocation(ELEMENT_COUNT)
    public long objectSet(ObjectSetState state) {
        switch (state.operation) {
            case ADD:
                return addSum(state.values);
            case CONTAINS:
                return containsSum(state.values);
            case REMOVE:
                return removeSum(state.values);
            case ITERATE:
                return objectKeySum(state.values);
            default:
                throw new AssertionError(state.operation);
        }
    }

    @Benchmark
    @OperationsPerInvocation(ELEMENT_COUNT)
    public long intSet(IntSetState state) {
        switch (state.operation) {
            case ADD:
                return addSum(state.values);
            case CONTAINS:
                return containsSum(state.values);
            case REMOVE:
                return removeSum(state.values);
            case ITERATE:
                return sum(state.values);
            default:
                throw new AssertionError(state.operation);
        }
    }

    @Benchmark
    @OperationsPerInvocation(ELEMENT_COUNT)
    public long objectQueue(ObjectQueueState state) {
        switch (state.operation) {
            case ADD_FIRST:
                for (int i = 0; i < ELEMENT_COUNT; i++) {
                    state.values.addFirst(OBJECT_VALUES[i]);
                }
                return state.values.size();
            case ADD_LAST:
                for (int i = 0; i < ELEMENT_COUNT; i++) {
                    state.values.addLast(OBJECT_VALUES[i]);
                }
                return state.values.size();
            case REMOVE_FIRST:
                long removeChecksum = 0L;
                for (int i = 0; i < ELEMENT_COUNT; i++) {
                    removeChecksum += state.values.removeFirst().id;
                }
                return removeChecksum;
            case GET:
                long getChecksum = 0L;
                for (int i = 0; i < ELEMENT_COUNT; i++) {
                    getChecksum += state.values.get(ACCESS_INDICES[i]).id;
                }
                return getChecksum;
            case ITERATE:
                return sum(state.values);
            default:
                throw new AssertionError(state.operation);
        }
    }

    @Benchmark
    @OperationsPerInvocation(ELEMENT_COUNT)
    public long objectLinkedList(ObjectLinkedListState state) {
        switch (state.operation) {
            case ADD_FIRST:
                for (int i = 0; i < ELEMENT_COUNT; i++) {
                    state.values.addFirst(OBJECT_VALUES[i]);
                }
                return state.values.size();
            case ADD_LAST:
                for (int i = 0; i < ELEMENT_COUNT; i++) {
                    state.values.addLast(OBJECT_VALUES[i]);
                }
                return state.values.size();
            case REMOVE_FIRST:
                long firstChecksum = 0L;
                for (int i = 0; i < ELEMENT_COUNT; i++) {
                    firstChecksum += state.values.removeFirst().id;
                }
                return firstChecksum;
            case REMOVE_LAST:
                long lastChecksum = 0L;
                for (int i = 0; i < ELEMENT_COUNT; i++) {
                    lastChecksum += state.values.removeLast().id;
                }
                return lastChecksum;
            case FIRST:
                long firstReadChecksum = 0L;
                for (int i = 0; i < ELEMENT_COUNT; i++) {
                    firstReadChecksum += state.values.first().id;
                }
                return firstReadChecksum;
            case LAST:
                long lastReadChecksum = 0L;
                for (int i = 0; i < ELEMENT_COUNT; i++) {
                    lastReadChecksum += state.values.last().id;
                }
                return lastReadChecksum;
            case ITERATE:
                return sum(state.values);
            default:
                throw new AssertionError(state.operation);
        }
    }

    private static long compareArray(Array<BenchmarkValue> values,
            NodeMapComparisonOperation operation) {
        switch (operation) {
            case ADD:
                for (int i = 0; i < ELEMENT_COUNT; i++) {
                    values.add(OBJECT_VALUES[i]);
                }
                return values.size();
            case GET_BY_INDEX_OR_KEY:
                return randomAccessSum(values);
            case REMOVE_BY_INDEX_OR_KEY:
            case REMOVE_DIRECT:
                long removeChecksum = 0L;
                for (int i = 0; i < ELEMENT_COUNT; i++) {
                    removeChecksum += values.removeIndex(REMOVAL_INDICES[i]).id;
                }
                return removeChecksum;
            case ITERATE_DENSE:
            case ITERATE_ORDERED:
                return indexedSum(values);
            default:
                throw new AssertionError(operation);
        }
    }

    private static long compareIntMap(IntMap<BenchmarkValue> values,
            NodeMapComparisonOperation operation) {
        switch (operation) {
            case ADD:
                for (int i = 0; i < ELEMENT_COUNT; i++) {
                    values.put(comparisonKey(i), OBJECT_VALUES[i]);
                }
                return values.size();
            case GET_BY_INDEX_OR_KEY:
                long getChecksum = 0L;
                for (int i = 0; i < ELEMENT_COUNT; i++) {
                    getChecksum += values.get(comparisonKey(ACCESS_INDICES[i])).id;
                }
                return getChecksum;
            case REMOVE_BY_INDEX_OR_KEY:
            case REMOVE_DIRECT:
                long removeChecksum = 0L;
                for (int i = 0; i < ELEMENT_COUNT; i++) {
                    int valueIndex = REMOVAL_VALUE_INDICES[i];
                    removeChecksum += values.remove(comparisonKey(valueIndex)).id;
                }
                return removeChecksum;
            case ITERATE_DENSE:
            case ITERATE_ORDERED:
                return sum(values.values());
            default:
                throw new AssertionError(operation);
        }
    }

    private static long compareOrderedMap(OrderedMap<BenchmarkKey, BenchmarkValue> values,
            NodeMapComparisonOperation operation) {
        switch (operation) {
            case ADD:
                for (int i = 0; i < ELEMENT_COUNT; i++) {
                    values.put(OBJECT_KEYS[i], OBJECT_VALUES[i]);
                }
                return values.size();
            case GET_BY_INDEX_OR_KEY:
                return getSum(values);
            case REMOVE_BY_INDEX_OR_KEY:
            case REMOVE_DIRECT:
                long removeChecksum = 0L;
                for (int i = 0; i < ELEMENT_COUNT; i++) {
                    int valueIndex = REMOVAL_VALUE_INDICES[i];
                    removeChecksum += values.remove(EQUAL_OBJECT_KEYS[valueIndex]).id;
                }
                return removeChecksum;
            case ITERATE_DENSE:
            case ITERATE_ORDERED:
                return sum(values.values());
            default:
                throw new AssertionError(operation);
        }
    }

    private static long compareOrderedIntNodeMap(
            OrderedIntNodeMap<BenchmarkValue, BenchmarkNode> values,
            BenchmarkNode[] removalOrder,
            NodeMapComparisonOperation operation) {
        switch (operation) {
            case ADD:
                for (int i = 0; i < ELEMENT_COUNT; i++) {
                    values.put(comparisonKey(i), OBJECT_VALUES[i]);
                }
                return values.size();
            case GET_BY_INDEX_OR_KEY:
                long getChecksum = 0L;
                for (int i = 0; i < ELEMENT_COUNT; i++) {
                    getChecksum += values.get(comparisonKey(ACCESS_INDICES[i])).id;
                }
                return getChecksum;
            case REMOVE_BY_INDEX_OR_KEY:
                long removeChecksum = 0L;
                for (int i = 0; i < ELEMENT_COUNT; i++) {
                    int valueIndex = REMOVAL_VALUE_INDICES[i];
                    removeChecksum += values.remove(comparisonKey(valueIndex)).id;
                }
                return removeChecksum;
            case REMOVE_DIRECT:
                long directRemoveChecksum = 0L;
                for (int i = 0; i < ELEMENT_COUNT; i++) {
                    directRemoveChecksum += values.removeNode(removalOrder[i]).id;
                }
                return directRemoveChecksum;
            case ITERATE_DENSE:
                long denseChecksum = 0L;
                for (int i = 0; i < ELEMENT_COUNT; i++) {
                    denseChecksum += values.nodeAt(i).value().id;
                }
                return denseChecksum;
            case ITERATE_ORDERED:
                long orderedChecksum = 0L;
                ObjectIterator<BenchmarkNode> iterator = values.iterator();
                while (iterator.hasNext()) {
                    orderedChecksum += iterator.next().value().id;
                }
                return orderedChecksum;
            default:
                throw new AssertionError(operation);
        }
    }

    private static long compareOrderedIntSparseNodeMap(
            OrderedIntSparseNodeMap<BenchmarkValue, BenchmarkSparseNode> values,
            BenchmarkSparseNode[] removalOrder,
            NodeMapComparisonOperation operation) {
        switch (operation) {
            case ADD:
                for (int i = 0; i < ELEMENT_COUNT; i++) {
                    values.put(comparisonKey(i), OBJECT_VALUES[i]);
                }
                return values.size();
            case GET_BY_INDEX_OR_KEY:
                long getChecksum = 0L;
                for (int i = 0; i < ELEMENT_COUNT; i++) {
                    getChecksum += values.get(comparisonKey(ACCESS_INDICES[i])).id;
                }
                return getChecksum;
            case REMOVE_BY_INDEX_OR_KEY:
                long removeChecksum = 0L;
                for (int i = 0; i < ELEMENT_COUNT; i++) {
                    int valueIndex = REMOVAL_VALUE_INDICES[i];
                    removeChecksum += values.remove(comparisonKey(valueIndex)).id;
                }
                return removeChecksum;
            case REMOVE_DIRECT:
                long directRemoveChecksum = 0L;
                for (int i = 0; i < ELEMENT_COUNT; i++) {
                    directRemoveChecksum += values.removeNode(removalOrder[i]).id;
                }
                return directRemoveChecksum;
            case ITERATE_DENSE:
                long denseChecksum = 0L;
                for (int i = 0; i < ELEMENT_COUNT; i++) {
                    denseChecksum += values.nodeAt(i).value().id;
                }
                return denseChecksum;
            case ITERATE_ORDERED:
                long orderedChecksum = 0L;
                ObjectIterator<BenchmarkSparseNode> iterator = values.iterator();
                while (iterator.hasNext()) {
                    orderedChecksum += iterator.next().value().id;
                }
                return orderedChecksum;
            default:
                throw new AssertionError(operation);
        }
    }

    private static boolean isNodeMapComparisonRead(NodeMapComparisonOperation operation) {
        return operation != NodeMapComparisonOperation.ADD
                && !isNodeMapComparisonRemoval(operation);
    }

    private static boolean isNodeMapComparisonRemoval(NodeMapComparisonOperation operation) {
        return operation == NodeMapComparisonOperation.REMOVE_BY_INDEX_OR_KEY
                || operation == NodeMapComparisonOperation.REMOVE_DIRECT;
    }

    private static void prepareDirectRemoval(OrderedIntNodeMapComparisonState state) {
        switch (state.implementation) {
            case ORDERED_INT_NODE_MAP:
                prepareDirectRemoval(state.nodeMap, state.nodeRemovalOrder);
                break;
            case ORDERED_INT_SPARSE_NODE_MAP:
                prepareDirectRemoval(state.sparseNodeMap, state.sparseNodeRemovalOrder);
                break;
            case ARRAY:
            case INT_MAP:
            case ORDERED_MAP:
                break;
            default:
                throw new AssertionError(state.implementation);
        }
    }

    private static void fillComparison(OrderedIntNodeMapComparisonState state) {
        switch (state.implementation) {
            case ARRAY:
                fill(state.array);
                break;
            case INT_MAP:
                fillComparison(state.intMap);
                break;
            case ORDERED_MAP:
                fill(state.orderedMap);
                break;
            case ORDERED_INT_NODE_MAP:
                fillComparison(state.nodeMap);
                break;
            case ORDERED_INT_SPARSE_NODE_MAP:
                fillComparison(state.sparseNodeMap);
                break;
            default:
                throw new AssertionError(state.implementation);
        }
    }

    private static void clearComparison(OrderedIntNodeMapComparisonState state) {
        switch (state.implementation) {
            case ARRAY:
                state.array.clear();
                break;
            case INT_MAP:
                state.intMap.clear();
                break;
            case ORDERED_MAP:
                state.orderedMap.clear();
                break;
            case ORDERED_INT_NODE_MAP:
                state.nodeMap.clear();
                break;
            case ORDERED_INT_SPARSE_NODE_MAP:
                state.sparseNodeMap.clear();
                break;
            default:
                throw new AssertionError(state.implementation);
        }
    }

    private static int comparisonSize(OrderedIntNodeMapComparisonState state) {
        switch (state.implementation) {
            case ARRAY:
                return state.array.size();
            case INT_MAP:
                return state.intMap.size();
            case ORDERED_MAP:
                return state.orderedMap.size();
            case ORDERED_INT_NODE_MAP:
                return state.nodeMap.size();
            case ORDERED_INT_SPARSE_NODE_MAP:
                return state.sparseNodeMap.size();
            default:
                throw new AssertionError(state.implementation);
        }
    }

    private static void prewarmComparisonIterator(OrderedIntNodeMapComparisonState state) {
        if (state.operation != NodeMapComparisonOperation.ITERATE_DENSE
                && state.operation != NodeMapComparisonOperation.ITERATE_ORDERED) {
            return;
        }
        switch (state.implementation) {
            case ARRAY:
                break;
            case INT_MAP:
                state.intMap.values().iterator();
                break;
            case ORDERED_MAP:
                state.orderedMap.values().iterator();
                break;
            case ORDERED_INT_NODE_MAP:
                if (state.operation == NodeMapComparisonOperation.ITERATE_ORDERED) {
                    state.nodeMap.iterator();
                }
                break;
            case ORDERED_INT_SPARSE_NODE_MAP:
                if (state.operation == NodeMapComparisonOperation.ITERATE_ORDERED) {
                    state.sparseNodeMap.iterator();
                }
                break;
            default:
                throw new AssertionError(state.implementation);
        }
    }

    private static boolean isArrayRemoval(ArrayOperation operation) {
        return operation == ArrayOperation.REMOVE_FIRST || operation == ArrayOperation.REMOVE_LAST;
    }

    private static boolean isArrayRead(ArrayOperation operation) {
        return operation != ArrayOperation.ADD && !isArrayRemoval(operation);
    }

    private static boolean isMapRead(MapOperation operation) {
        return operation != MapOperation.PUT && operation != MapOperation.REMOVE;
    }

    private static boolean isSetRead(SetOperation operation) {
        return operation == SetOperation.CONTAINS || operation == SetOperation.ITERATE;
    }

    private static boolean isLinkedListRead(LinkedListOperation operation) {
        return operation == LinkedListOperation.FIRST
                || operation == LinkedListOperation.LAST
                || operation == LinkedListOperation.ITERATE;
    }

    private static void validateSize(String collection, int actual, int expected) {
        if (actual != expected) {
            throw new IllegalStateException(collection + " size mismatch: " + actual + " != " + expected);
        }
    }

    private static int comparisonKey(int index) {
        return index;
    }

    private static int intKey(int index) {
        return index * 0x9E3779B9;
    }

    private static int intValue(int index) {
        return index * 31 + 7;
    }

    private static long longKey(int index) {
        return 0x1_0000_0000L + index * 0x9E37L;
    }

    private static long longValue(int index) {
        return 0x2_0000_0000L + index * 31L;
    }

    private static float floatKey(int index) {
        return index + 0.25f;
    }

    private static float floatValue(int index) {
        return index + 0.5f;
    }

    private static void fill(Array<BenchmarkValue> values) {
        for (int i = 0; i < ELEMENT_COUNT; i++) {
            values.add(OBJECT_VALUES[i]);
        }
    }

    private static void fill(IntArray values) {
        for (int i = 0; i < ELEMENT_COUNT; i++) {
            values.add(intValue(i));
        }
    }

    private static void fill(LongArray values) {
        for (int i = 0; i < ELEMENT_COUNT; i++) {
            values.add(longValue(i));
        }
    }

    private static void fill(FloatArray values) {
        for (int i = 0; i < ELEMENT_COUNT; i++) {
            values.add(floatValue(i));
        }
    }

    private static void fill(ObjectMap<BenchmarkKey, BenchmarkValue> values) {
        for (int i = 0; i < ELEMENT_COUNT; i++) {
            values.put(OBJECT_KEYS[i], OBJECT_VALUES[i]);
        }
    }

    private static void fill(OrderedMap<BenchmarkKey, BenchmarkValue> values) {
        for (int i = 0; i < ELEMENT_COUNT; i++) {
            values.put(OBJECT_KEYS[i], OBJECT_VALUES[i]);
        }
    }

    private static void fill(IntMap<BenchmarkValue> values) {
        for (int i = 0; i < ELEMENT_COUNT; i++) {
            values.put(intKey(i), OBJECT_VALUES[i]);
        }
    }

    private static void fill(OrderedIntNodeMap<BenchmarkValue, BenchmarkNode> values) {
        for (int i = 0; i < ELEMENT_COUNT; i++) {
            values.put(intKey(i), OBJECT_VALUES[i]);
        }
    }

    private static void fillComparison(IntMap<BenchmarkValue> values) {
        for (int i = 0; i < ELEMENT_COUNT; i++) {
            values.put(comparisonKey(i), OBJECT_VALUES[i]);
        }
    }

    private static void fillComparison(
            OrderedIntNodeMap<BenchmarkValue, BenchmarkNode> values) {
        for (int i = 0; i < ELEMENT_COUNT; i++) {
            values.put(comparisonKey(i), OBJECT_VALUES[i]);
        }
    }

    private static void fillComparison(
            OrderedIntSparseNodeMap<BenchmarkValue, BenchmarkSparseNode> values) {
        for (int i = 0; i < ELEMENT_COUNT; i++) {
            values.put(comparisonKey(i), OBJECT_VALUES[i]);
        }
    }

    private static void prepareDirectRemoval(
            OrderedIntNodeMap<BenchmarkValue, BenchmarkNode> values,
            BenchmarkNode[] removalOrder) {
        for (int i = 0; i < ELEMENT_COUNT; i++) {
            int valueIndex = REMOVAL_VALUE_INDICES[i];
            removalOrder[i] = values.getNode(comparisonKey(valueIndex));
        }
    }

    private static void prepareDirectRemoval(
            OrderedIntSparseNodeMap<BenchmarkValue, BenchmarkSparseNode> values,
            BenchmarkSparseNode[] removalOrder) {
        for (int i = 0; i < ELEMENT_COUNT; i++) {
            int valueIndex = REMOVAL_VALUE_INDICES[i];
            removalOrder[i] = values.getNode(comparisonKey(valueIndex));
        }
    }

    private static void fill(LongMap<BenchmarkValue> values) {
        for (int i = 0; i < ELEMENT_COUNT; i++) {
            values.put(longKey(i), OBJECT_VALUES[i]);
        }
    }

    private static void fill(FloatMap<BenchmarkValue> values) {
        for (int i = 0; i < ELEMENT_COUNT; i++) {
            values.put(floatKey(i), OBJECT_VALUES[i]);
        }
    }

    private static void fill(ObjectSet<BenchmarkKey> values) {
        for (int i = 0; i < ELEMENT_COUNT; i++) {
            values.add(OBJECT_KEYS[i]);
        }
    }

    private static void fill(IntSet values) {
        for (int i = 0; i < ELEMENT_COUNT; i++) {
            values.add(intKey(i));
        }
    }

    private static void fill(ObjectQueue<BenchmarkValue> values) {
        for (int i = 0; i < ELEMENT_COUNT; i++) {
            values.addLast(OBJECT_VALUES[i]);
        }
    }

    private static void fill(ObjectLinkedList<BenchmarkValue> values) {
        for (int i = 0; i < ELEMENT_COUNT; i++) {
            values.addLast(OBJECT_VALUES[i]);
        }
    }

    private static void prewarmMapIterator(ObjectMap<BenchmarkKey, BenchmarkValue> values,
            MapOperation operation) {
        if (operation == MapOperation.ITERATE_KEYS) {
            values.keys().iterator();
        }
        else if (operation == MapOperation.ITERATE_VALUES) {
            values.values().iterator();
        }
        else if (operation == MapOperation.ITERATE_ENTRIES) {
            values.entries().iterator();
        }
    }

    private static void prewarmMapIterator(OrderedMap<BenchmarkKey, BenchmarkValue> values,
            MapOperation operation) {
        if (operation == MapOperation.ITERATE_KEYS) {
            values.keys().iterator();
        }
        else if (operation == MapOperation.ITERATE_VALUES) {
            values.values().iterator();
        }
        else if (operation == MapOperation.ITERATE_ENTRIES) {
            values.entries().iterator();
        }
    }

    private static void prewarmMapIterator(IntMap<BenchmarkValue> values, MapOperation operation) {
        if (operation == MapOperation.ITERATE_KEYS) {
            values.keys().iterator();
        }
        else if (operation == MapOperation.ITERATE_VALUES) {
            values.values().iterator();
        }
        else if (operation == MapOperation.ITERATE_ENTRIES) {
            values.entries().iterator();
        }
    }

    private static void prewarmMapIterator(LongMap<BenchmarkValue> values, MapOperation operation) {
        if (operation == MapOperation.ITERATE_KEYS) {
            values.keys().iterator();
        }
        else if (operation == MapOperation.ITERATE_VALUES) {
            values.values().iterator();
        }
        else if (operation == MapOperation.ITERATE_ENTRIES) {
            values.entries().iterator();
        }
    }

    private static void prewarmMapIterator(FloatMap<BenchmarkValue> values, MapOperation operation) {
        if (operation == MapOperation.ITERATE_KEYS) {
            values.keys().iterator();
        }
        else if (operation == MapOperation.ITERATE_VALUES) {
            values.values().iterator();
        }
        else if (operation == MapOperation.ITERATE_ENTRIES) {
            values.entries().iterator();
        }
    }

    private static long randomAccessSum(Array<BenchmarkValue> values) {
        long checksum = 0L;
        for (int i = 0; i < ELEMENT_COUNT; i++) {
            checksum += values.get(ACCESS_INDICES[i]).id;
        }
        return checksum;
    }

    private static long randomAccessSum(IntArray values) {
        long checksum = 0L;
        for (int i = 0; i < ELEMENT_COUNT; i++) {
            checksum += values.get(ACCESS_INDICES[i]);
        }
        return checksum;
    }

    private static long randomAccessSum(LongArray values) {
        long checksum = 0L;
        for (int i = 0; i < ELEMENT_COUNT; i++) {
            checksum += values.get(ACCESS_INDICES[i]);
        }
        return checksum;
    }

    private static long randomAccessSum(FloatArray values) {
        long checksum = 0L;
        for (int i = 0; i < ELEMENT_COUNT; i++) {
            checksum += Float.floatToRawIntBits(values.get(ACCESS_INDICES[i]));
        }
        return checksum;
    }

    private static long indexedSum(Array<BenchmarkValue> values) {
        long checksum = 0L;
        for (int i = 0; i < ELEMENT_COUNT; i++) {
            checksum += values.get(i).id;
        }
        return checksum;
    }

    private static long indexedSum(IntArray values) {
        long checksum = 0L;
        for (int i = 0; i < ELEMENT_COUNT; i++) {
            checksum += values.get(i);
        }
        return checksum;
    }

    private static long indexedSum(LongArray values) {
        long checksum = 0L;
        for (int i = 0; i < ELEMENT_COUNT; i++) {
            checksum += values.get(i);
        }
        return checksum;
    }

    private static long indexedSum(FloatArray values) {
        long checksum = 0L;
        for (int i = 0; i < ELEMENT_COUNT; i++) {
            checksum += Float.floatToRawIntBits(values.get(i));
        }
        return checksum;
    }

    private static long removeFirstSum(Array<BenchmarkValue> values) {
        long checksum = 0L;
        for (int i = 0; i < ELEMENT_COUNT; i++) {
            checksum += values.removeIndex(0).id;
        }
        return checksum;
    }

    private static long removeFirstSum(IntArray values) {
        long checksum = 0L;
        for (int i = 0; i < ELEMENT_COUNT; i++) {
            checksum += values.removeIndex(0);
        }
        return checksum;
    }

    private static long removeFirstSum(LongArray values) {
        long checksum = 0L;
        for (int i = 0; i < ELEMENT_COUNT; i++) {
            checksum += values.removeIndex(0);
        }
        return checksum;
    }

    private static long removeFirstSum(FloatArray values) {
        long checksum = 0L;
        for (int i = 0; i < ELEMENT_COUNT; i++) {
            checksum += Float.floatToRawIntBits(values.removeIndex(0));
        }
        return checksum;
    }

    private static long removeLastSum(Array<BenchmarkValue> values) {
        long checksum = 0L;
        for (int i = ELEMENT_COUNT - 1; i >= 0; i--) {
            checksum += values.removeIndex(i).id;
        }
        return checksum;
    }

    private static long removeLastSum(IntArray values) {
        long checksum = 0L;
        for (int i = ELEMENT_COUNT - 1; i >= 0; i--) {
            checksum += values.removeIndex(i);
        }
        return checksum;
    }

    private static long removeLastSum(LongArray values) {
        long checksum = 0L;
        for (int i = ELEMENT_COUNT - 1; i >= 0; i--) {
            checksum += values.removeIndex(i);
        }
        return checksum;
    }

    private static long removeLastSum(FloatArray values) {
        long checksum = 0L;
        for (int i = ELEMENT_COUNT - 1; i >= 0; i--) {
            checksum += Float.floatToRawIntBits(values.removeIndex(i));
        }
        return checksum;
    }

    private static long getSum(ObjectMap<BenchmarkKey, BenchmarkValue> values, BenchmarkKey[] keys) {
        long checksum = 0L;
        for (int i = 0; i < ELEMENT_COUNT; i++) {
            checksum += values.get(keys[ACCESS_INDICES[i]]).id;
        }
        return checksum;
    }

    private static long removeSum(ObjectMap<BenchmarkKey, BenchmarkValue> values, BenchmarkKey[] keys) {
        long checksum = 0L;
        for (int i = 0; i < ELEMENT_COUNT; i++) {
            checksum += values.remove(keys[ACCESS_INDICES[i]]).id;
        }
        return checksum;
    }

    private static long getSum(OrderedMap<BenchmarkKey, BenchmarkValue> values) {
        long checksum = 0L;
        for (int i = 0; i < ELEMENT_COUNT; i++) {
            checksum += values.get(EQUAL_OBJECT_KEYS[ACCESS_INDICES[i]]).id;
        }
        return checksum;
    }

    private static long removeSum(OrderedMap<BenchmarkKey, BenchmarkValue> values) {
        long checksum = 0L;
        for (int i = 0; i < ELEMENT_COUNT; i++) {
            checksum += values.remove(EQUAL_OBJECT_KEYS[ACCESS_INDICES[i]]).id;
        }
        return checksum;
    }

    private static long getSum(IntMap<BenchmarkValue> values) {
        long checksum = 0L;
        for (int i = 0; i < ELEMENT_COUNT; i++) {
            checksum += values.get(intKey(ACCESS_INDICES[i])).id;
        }
        return checksum;
    }

    private static long removeSum(IntMap<BenchmarkValue> values) {
        long checksum = 0L;
        for (int i = 0; i < ELEMENT_COUNT; i++) {
            checksum += values.remove(intKey(ACCESS_INDICES[i])).id;
        }
        return checksum;
    }

    private static long getSum(LongMap<BenchmarkValue> values) {
        long checksum = 0L;
        for (int i = 0; i < ELEMENT_COUNT; i++) {
            checksum += values.get(longKey(ACCESS_INDICES[i])).id;
        }
        return checksum;
    }

    private static long removeSum(LongMap<BenchmarkValue> values) {
        long checksum = 0L;
        for (int i = 0; i < ELEMENT_COUNT; i++) {
            checksum += values.remove(longKey(ACCESS_INDICES[i])).id;
        }
        return checksum;
    }

    private static long getSum(FloatMap<BenchmarkValue> values) {
        long checksum = 0L;
        for (int i = 0; i < ELEMENT_COUNT; i++) {
            checksum += values.get(floatKey(ACCESS_INDICES[i])).id;
        }
        return checksum;
    }

    private static long removeSum(FloatMap<BenchmarkValue> values) {
        long checksum = 0L;
        for (int i = 0; i < ELEMENT_COUNT; i++) {
            checksum += values.remove(floatKey(ACCESS_INDICES[i])).id;
        }
        return checksum;
    }

    private static long addSum(ObjectSet<BenchmarkKey> values) {
        long checksum = 0L;
        for (int i = 0; i < ELEMENT_COUNT; i++) {
            if (values.add(OBJECT_KEYS[i])) {
                checksum++;
            }
        }
        return checksum;
    }

    private static long containsSum(ObjectSet<BenchmarkKey> values) {
        long checksum = 0L;
        for (int i = 0; i < ELEMENT_COUNT; i++) {
            if (values.contains(EQUAL_OBJECT_KEYS[ACCESS_INDICES[i]])) {
                checksum++;
            }
        }
        return checksum;
    }

    private static long removeSum(ObjectSet<BenchmarkKey> values) {
        long checksum = 0L;
        for (int i = 0; i < ELEMENT_COUNT; i++) {
            if (values.remove(EQUAL_OBJECT_KEYS[ACCESS_INDICES[i]])) {
                checksum++;
            }
        }
        return checksum;
    }

    private static long addSum(IntSet values) {
        long checksum = 0L;
        for (int i = 0; i < ELEMENT_COUNT; i++) {
            if (values.add(intKey(i))) {
                checksum++;
            }
        }
        return checksum;
    }

    private static long containsSum(IntSet values) {
        long checksum = 0L;
        for (int i = 0; i < ELEMENT_COUNT; i++) {
            if (values.contains(intKey(ACCESS_INDICES[i]))) {
                checksum++;
            }
        }
        return checksum;
    }

    private static long removeSum(IntSet values) {
        long checksum = 0L;
        for (int i = 0; i < ELEMENT_COUNT; i++) {
            if (values.remove(intKey(ACCESS_INDICES[i]))) {
                checksum++;
            }
        }
        return checksum;
    }

    private static long sum(ObjectIterable<BenchmarkValue> values) {
        long checksum = 0L;
        ObjectIterator<BenchmarkValue> iterator = values.iterator();
        while (iterator.hasNext()) {
            checksum += iterator.next().id;
        }
        return checksum;
    }

    private static long objectKeySum(ObjectIterable<BenchmarkKey> values) {
        long checksum = 0L;
        ObjectIterator<BenchmarkKey> iterator = values.iterator();
        while (iterator.hasNext()) {
            checksum += iterator.next().id;
        }
        return checksum;
    }

    private static long sum(IntIterable values) {
        long checksum = 0L;
        IntIterator iterator = values.iterator();
        while (iterator.hasNext()) {
            checksum += iterator.nextInt();
        }
        return checksum;
    }

    private static long sum(LongIterable values) {
        long checksum = 0L;
        LongIterator iterator = values.iterator();
        while (iterator.hasNext()) {
            checksum += iterator.nextLong();
        }
        return checksum;
    }

    private static long floatBitsSum(FloatIterable values) {
        long checksum = 0L;
        FloatIterator iterator = values.iterator();
        while (iterator.hasNext()) {
            checksum += Float.floatToRawIntBits(iterator.nextFloat());
        }
        return checksum;
    }

    private static long sum(FloatIterable values) {
        return floatBitsSum(values);
    }

    private static long objectMapEntrySum(ObjectIterable<ObjectMap.Entry<BenchmarkKey, BenchmarkValue>> values) {
        long checksum = 0L;
        ObjectIterator<ObjectMap.Entry<BenchmarkKey, BenchmarkValue>> iterator = values.iterator();
        while (iterator.hasNext()) {
            ObjectMap.Entry<BenchmarkKey, BenchmarkValue> entry = iterator.next();
            checksum += entry.key().id + entry.value().id;
        }
        return checksum;
    }

    private static long orderedMapEntrySum(
            ObjectIterable<OrderedMap.Entry<BenchmarkKey, BenchmarkValue>> values) {
        long checksum = 0L;
        ObjectIterator<OrderedMap.Entry<BenchmarkKey, BenchmarkValue>> iterator = values.iterator();
        while (iterator.hasNext()) {
            OrderedMap.Entry<BenchmarkKey, BenchmarkValue> entry = iterator.next();
            checksum += entry.key().id + entry.value().id;
        }
        return checksum;
    }

    private static long intMapEntrySum(ObjectIterable<IntMap.Entry<BenchmarkValue>> values) {
        long checksum = 0L;
        ObjectIterator<IntMap.Entry<BenchmarkValue>> iterator = values.iterator();
        while (iterator.hasNext()) {
            IntMap.Entry<BenchmarkValue> entry = iterator.next();
            checksum += entry.key() + entry.value().id;
        }
        return checksum;
    }

    private static long longMapEntrySum(ObjectIterable<LongMap.Entry<BenchmarkValue>> values) {
        long checksum = 0L;
        ObjectIterator<LongMap.Entry<BenchmarkValue>> iterator = values.iterator();
        while (iterator.hasNext()) {
            LongMap.Entry<BenchmarkValue> entry = iterator.next();
            checksum += entry.key() + entry.value().id;
        }
        return checksum;
    }

    private static long floatMapEntrySum(ObjectIterable<FloatMap.Entry<BenchmarkValue>> values) {
        long checksum = 0L;
        ObjectIterator<FloatMap.Entry<BenchmarkValue>> iterator = values.iterator();
        while (iterator.hasNext()) {
            FloatMap.Entry<BenchmarkValue> entry = iterator.next();
            checksum += Float.floatToRawIntBits(entry.key()) + entry.value().id;
        }
        return checksum;
    }

    /** Customizable pooled node used by the ordered int node map comparison. */
    public static final class BenchmarkNode
            extends OrderedIntNodeMap.Node<BenchmarkValue, BenchmarkNode> {
        private int customData;

        @Override
        protected void reset() {
            customData = 0;
        }
    }

    /** Customizable pooled node used by the sparse-set comparison. */
    public static final class BenchmarkSparseNode
            extends OrderedIntSparseNodeMap.Node<BenchmarkValue, BenchmarkSparseNode> {
        private int customData;

        @Override
        protected void reset() {
            customData = 0;
        }
    }

    /** Object key with stable equality hashing and a separate equal probe set. */
    public static final class BenchmarkKey {
        private final int id;

        BenchmarkKey(int id) {
            this.id = id;
        }

        @Override
        public int hashCode() {
            return intKey(id);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof BenchmarkKey && ((BenchmarkKey)other).id == id;
        }
    }

    /** Preallocated object value used to avoid boxing in object collection cases. */
    public static final class BenchmarkValue {
        private final int id;

        BenchmarkValue(int id) {
            this.id = id;
        }
    }
}
