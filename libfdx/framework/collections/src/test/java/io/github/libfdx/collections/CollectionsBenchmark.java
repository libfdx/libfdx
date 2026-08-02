package io.github.libfdx.collections;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Runs a directional collection performance smoke benchmark.
 *
 * <p>This is intentionally not a pass/fail microbenchmark. It validates
 * results with matching checksums and reports medians against like-for-like
 * JDK containers. Run it with the {@code benchmarkCollections} Gradle task.</p>
 */
public final class CollectionsBenchmark {
    private static final int COUNT = 100_000;
    private static final int WARMUP_ROUNDS = 2;
    private static final int MEASURE_ROUNDS = 5;
    private static volatile long sink;

    private CollectionsBenchmark() {
    }

    /**
     * Runs the benchmark.
     *
     * @param arguments ignored
     */
    public static void main(String[] arguments) {
        System.out.println("libFDX collection benchmark: " + COUNT + " entries, median of "
                + MEASURE_ROUNDS + " rounds after " + WARMUP_ROUNDS + " warmups");
        System.out.printf("%-28s %14s %14s %12s%n", "operation", "libFDX ns/op", "JDK ns/op", "lib/JDK");

        compare("Array add", arrayAdd(), arrayListAdd());
        compare("Array indexed get", arrayGet(), arrayListGet());
        compare("Array remove last", arrayRemove(), arrayListRemove());
        compare("Array enhanced loop", arrayLoop(), arrayListLoop());

        compare("ObjectMap put", objectMapPut(), hashMapPut());
        compare("ObjectMap get", objectMapGet(), hashMapGet());
        compare("ObjectMap remove", objectMapRemove(), hashMapRemove());
        compare("ObjectMap values loop", objectMapLoop(), hashMapLoop());

        compare("IntMap put", intMapPut(), hashMapPut());
        compare("IntMap get", intMapGet(), hashMapGet());
        compare("IntMap remove", intMapRemove(), hashMapRemove());
        compare("IntMap values loop", intMapLoop(), hashMapLoop());

        compare("OrderedMap put", orderedMapPut(), linkedHashMapPut());
        compare("OrderedMap get", orderedMapGet(), linkedHashMapGet());
        compare("OrderedMap remove", orderedMapRemove(), linkedHashMapRemove());
        compare("OrderedMap values loop", orderedMapLoop(), linkedHashMapLoop());
        System.out.println("checksum=" + sink);
    }

    private static void compare(String label, BenchmarkCase libfdx, BenchmarkCase jdk) {
        Measurement left = measure(libfdx);
        Measurement right = measure(jdk);
        if (left.checksum != right.checksum) {
            throw new IllegalStateException(label + " checksum mismatch: " + left.checksum + " != "
                    + right.checksum);
        }
        double leftNs = (double)left.medianNanos / libfdx.operations();
        double rightNs = (double)right.medianNanos / jdk.operations();
        System.out.printf("%-28s %14.2f %14.2f %12.2fx%n", label, leftNs, rightNs, leftNs / rightNs);
    }

    private static Measurement measure(BenchmarkCase benchmark) {
        long checksum = 0L;
        for (int i = 0; i < WARMUP_ROUNDS; i++) {
            checksum = benchmark.setup().run();
            sink ^= checksum;
        }
        long[] samples = new long[MEASURE_ROUNDS];
        for (int i = 0; i < MEASURE_ROUNDS; i++) {
            Trial trial = benchmark.setup();
            long start = System.nanoTime();
            checksum = trial.run();
            samples[i] = System.nanoTime() - start;
            sink ^= checksum;
        }
        Arrays.sort(samples);
        return new Measurement(samples[samples.length / 2], checksum);
    }

    private static BenchmarkCase arrayAdd() {
        return () -> {
            Array<Integer> values = new Array<Integer>(COUNT);
            return () -> {
                long checksum = 0L;
                for (int i = 0; i < COUNT; i++) {
                    values.add(i);
                    checksum += i;
                }
                return checksum + values.size();
            };
        };
    }

    private static BenchmarkCase arrayListAdd() {
        return () -> {
            ArrayList<Integer> values = new ArrayList<Integer>(COUNT);
            return () -> {
                long checksum = 0L;
                for (int i = 0; i < COUNT; i++) {
                    values.add(i);
                    checksum += i;
                }
                return checksum + values.size();
            };
        };
    }

    private static BenchmarkCase arrayGet() {
        return () -> {
            Array<Integer> values = libfdxArray();
            return () -> indexedArraySum(values);
        };
    }

    private static BenchmarkCase arrayListGet() {
        return () -> {
            ArrayList<Integer> values = jdkArray();
            return () -> indexedArrayListSum(values);
        };
    }

    private static BenchmarkCase arrayRemove() {
        return () -> {
            Array<Integer> values = libfdxArray();
            return () -> {
                long checksum = 0L;
                for (int i = COUNT - 1; i >= 0; i--) {
                    checksum += values.removeIndex(i);
                }
                return checksum;
            };
        };
    }

    private static BenchmarkCase arrayListRemove() {
        return () -> {
            ArrayList<Integer> values = jdkArray();
            return () -> {
                long checksum = 0L;
                for (int i = COUNT - 1; i >= 0; i--) {
                    checksum += values.remove(i);
                }
                return checksum;
            };
        };
    }

    private static BenchmarkCase arrayLoop() {
        return () -> {
            Array<Integer> values = libfdxArray();
            return () -> iterableSum(values);
        };
    }

    private static BenchmarkCase arrayListLoop() {
        return () -> {
            ArrayList<Integer> values = jdkArray();
            return () -> iterableSum(values);
        };
    }

    private static BenchmarkCase objectMapPut() {
        return () -> {
            ObjectMap<Integer, Integer> values = new ObjectMap<Integer, Integer>(COUNT);
            return () -> {
                for (int i = 0; i < COUNT; i++) {
                    values.put(i, i);
                }
                return values.size() + values.get(COUNT - 1);
            };
        };
    }

    private static BenchmarkCase objectMapGet() {
        return () -> {
            ObjectMap<Integer, Integer> values = libfdxObjectMap();
            return () -> objectMapGetSum(values);
        };
    }

    private static BenchmarkCase objectMapRemove() {
        return () -> {
            ObjectMap<Integer, Integer> values = libfdxObjectMap();
            return () -> objectMapRemoveSum(values);
        };
    }

    private static BenchmarkCase objectMapLoop() {
        return () -> {
            ObjectMap<Integer, Integer> values = libfdxObjectMap();
            return () -> iterableSum(values.values());
        };
    }

    private static BenchmarkCase intMapPut() {
        return () -> {
            IntMap<Integer> values = new IntMap<Integer>(COUNT);
            return () -> {
                for (int i = 0; i < COUNT; i++) {
                    values.put(i, i);
                }
                return values.size() + values.get(COUNT - 1);
            };
        };
    }

    private static BenchmarkCase intMapGet() {
        return () -> {
            IntMap<Integer> values = libfdxIntMap();
            return () -> intMapGetSum(values);
        };
    }

    private static BenchmarkCase intMapRemove() {
        return () -> {
            IntMap<Integer> values = libfdxIntMap();
            return () -> intMapRemoveSum(values);
        };
    }

    private static BenchmarkCase intMapLoop() {
        return () -> {
            IntMap<Integer> values = libfdxIntMap();
            return () -> iterableSum(values.values());
        };
    }

    private static BenchmarkCase orderedMapPut() {
        return () -> {
            OrderedMap<Integer, Integer> values = new OrderedMap<Integer, Integer>(COUNT);
            return () -> {
                for (int i = 0; i < COUNT; i++) {
                    values.put(i, i);
                }
                return values.size() + values.get(COUNT - 1);
            };
        };
    }

    private static BenchmarkCase orderedMapGet() {
        return () -> {
            OrderedMap<Integer, Integer> values = libfdxOrderedMap();
            return () -> orderedMapGetSum(values);
        };
    }

    private static BenchmarkCase orderedMapRemove() {
        return () -> {
            OrderedMap<Integer, Integer> values = libfdxOrderedMap();
            return () -> orderedMapRemoveSum(values);
        };
    }

    private static BenchmarkCase orderedMapLoop() {
        return () -> {
            OrderedMap<Integer, Integer> values = libfdxOrderedMap();
            return () -> iterableSum(values.values());
        };
    }

    private static BenchmarkCase hashMapPut() {
        return () -> {
            HashMap<Integer, Integer> values = new HashMap<Integer, Integer>(jdkMapCapacity());
            return () -> {
                for (int i = 0; i < COUNT; i++) {
                    values.put(i, i);
                }
                return values.size() + values.get(COUNT - 1);
            };
        };
    }

    private static BenchmarkCase hashMapGet() {
        return () -> {
            HashMap<Integer, Integer> values = jdkHashMap();
            return () -> mapGetSum(values);
        };
    }

    private static BenchmarkCase hashMapRemove() {
        return () -> {
            HashMap<Integer, Integer> values = jdkHashMap();
            return () -> mapRemoveSum(values);
        };
    }

    private static BenchmarkCase hashMapLoop() {
        return () -> {
            HashMap<Integer, Integer> values = jdkHashMap();
            return () -> iterableSum(values.values());
        };
    }

    private static BenchmarkCase linkedHashMapPut() {
        return () -> {
            LinkedHashMap<Integer, Integer> values = new LinkedHashMap<Integer, Integer>(jdkMapCapacity());
            return () -> {
                for (int i = 0; i < COUNT; i++) {
                    values.put(i, i);
                }
                return values.size() + values.get(COUNT - 1);
            };
        };
    }

    private static BenchmarkCase linkedHashMapGet() {
        return () -> {
            LinkedHashMap<Integer, Integer> values = jdkLinkedHashMap();
            return () -> mapGetSum(values);
        };
    }

    private static BenchmarkCase linkedHashMapRemove() {
        return () -> {
            LinkedHashMap<Integer, Integer> values = jdkLinkedHashMap();
            return () -> mapRemoveSum(values);
        };
    }

    private static BenchmarkCase linkedHashMapLoop() {
        return () -> {
            LinkedHashMap<Integer, Integer> values = jdkLinkedHashMap();
            return () -> iterableSum(values.values());
        };
    }

    private static Array<Integer> libfdxArray() {
        Array<Integer> values = new Array<Integer>(COUNT);
        for (int i = 0; i < COUNT; i++) {
            values.add(i);
        }
        return values;
    }

    private static ArrayList<Integer> jdkArray() {
        ArrayList<Integer> values = new ArrayList<Integer>(COUNT);
        for (int i = 0; i < COUNT; i++) {
            values.add(i);
        }
        return values;
    }

    private static ObjectMap<Integer, Integer> libfdxObjectMap() {
        ObjectMap<Integer, Integer> values = new ObjectMap<Integer, Integer>(COUNT);
        for (int i = 0; i < COUNT; i++) {
            values.put(i, i);
        }
        return values;
    }

    private static IntMap<Integer> libfdxIntMap() {
        IntMap<Integer> values = new IntMap<Integer>(COUNT);
        for (int i = 0; i < COUNT; i++) {
            values.put(i, i);
        }
        return values;
    }

    private static OrderedMap<Integer, Integer> libfdxOrderedMap() {
        OrderedMap<Integer, Integer> values = new OrderedMap<Integer, Integer>(COUNT);
        for (int i = 0; i < COUNT; i++) {
            values.put(i, i);
        }
        return values;
    }

    private static HashMap<Integer, Integer> jdkHashMap() {
        HashMap<Integer, Integer> values = new HashMap<Integer, Integer>(jdkMapCapacity());
        fill(values);
        return values;
    }

    private static LinkedHashMap<Integer, Integer> jdkLinkedHashMap() {
        LinkedHashMap<Integer, Integer> values = new LinkedHashMap<Integer, Integer>(jdkMapCapacity());
        fill(values);
        return values;
    }

    private static void fill(Map<Integer, Integer> values) {
        for (int i = 0; i < COUNT; i++) {
            values.put(i, i);
        }
    }

    private static long indexedArraySum(Array<Integer> values) {
        long checksum = 0L;
        for (int i = 0; i < values.size(); i++) {
            checksum += values.get(i);
        }
        return checksum;
    }

    private static long indexedArrayListSum(ArrayList<Integer> values) {
        long checksum = 0L;
        for (int i = 0; i < values.size(); i++) {
            checksum += values.get(i);
        }
        return checksum;
    }

    private static long objectMapGetSum(ObjectMap<Integer, Integer> values) {
        long checksum = 0L;
        for (int i = 0; i < COUNT; i++) {
            checksum += values.get(i);
        }
        return checksum;
    }

    private static long objectMapRemoveSum(ObjectMap<Integer, Integer> values) {
        long checksum = 0L;
        for (int i = 0; i < COUNT; i++) {
            checksum += values.remove(i);
        }
        return checksum;
    }

    private static long intMapGetSum(IntMap<Integer> values) {
        long checksum = 0L;
        for (int i = 0; i < COUNT; i++) {
            checksum += values.get(i);
        }
        return checksum;
    }

    private static long intMapRemoveSum(IntMap<Integer> values) {
        long checksum = 0L;
        for (int i = 0; i < COUNT; i++) {
            checksum += values.remove(i);
        }
        return checksum;
    }

    private static long orderedMapGetSum(OrderedMap<Integer, Integer> values) {
        long checksum = 0L;
        for (int i = 0; i < COUNT; i++) {
            checksum += values.get(i);
        }
        return checksum;
    }

    private static long orderedMapRemoveSum(OrderedMap<Integer, Integer> values) {
        long checksum = 0L;
        for (int i = 0; i < COUNT; i++) {
            checksum += values.remove(i);
        }
        return checksum;
    }

    private static long mapGetSum(Map<Integer, Integer> values) {
        long checksum = 0L;
        for (int i = 0; i < COUNT; i++) {
            checksum += values.get(i);
        }
        return checksum;
    }

    private static long mapRemoveSum(Map<Integer, Integer> values) {
        long checksum = 0L;
        for (int i = 0; i < COUNT; i++) {
            checksum += values.remove(i);
        }
        return checksum;
    }

    private static long iterableSum(Iterable<Integer> values) {
        long checksum = 0L;
        for (Integer value : values) {
            checksum += value;
        }
        return checksum;
    }

    private static int jdkMapCapacity() {
        return (int)Math.ceil(COUNT / 0.75d) + 1;
    }

    private interface BenchmarkCase {
        Trial setup();

        default int operations() {
            return COUNT;
        }
    }

    @FunctionalInterface
    private interface Trial {
        long run();
    }

    private static final class Measurement {
        private final long medianNanos;
        private final long checksum;

        private Measurement(long medianNanos, long checksum) {
            this.medianNanos = medianNanos;
            this.checksum = checksum;
        }
    }
}
