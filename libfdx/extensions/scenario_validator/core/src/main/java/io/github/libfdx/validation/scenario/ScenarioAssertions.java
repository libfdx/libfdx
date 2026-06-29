package io.github.libfdx.validation.scenario;

import java.util.function.Predicate;
import java.util.Objects;

/**
 * Represents a scenario assertions.
 *
 * @author xpenatan
 */
public final class ScenarioAssertions {
    private ScenarioAssertions() {
    }

    /**
     * Runs the event seen step.
     *
     * @param event the event
     * @return the event seen
     */
    public static ScenarioAssertion eventSeen(String event) {
        return assertion("eventSeen(" + event + ")", context ->
                context.assertTrue(context.events().contains(event), "Expected event was not seen: " + event));
    }

    /**
     * Runs the capture exists step.
     *
     * @param name the name
     * @return the capture exists
     */
    public static ScenarioAssertion captureExists(String name) {
        return assertion("captureExists(" + name + ")", context ->
                context.assertTrue(context.hasCapture(name), "Expected capture was not created: " + name));
    }

    /**
     * Runs the capture matches step.
     *
     * @param name the name
     * @return the capture matches
     */
    public static ScenarioAssertion captureMatches(String name) {
        return assertion("captureMatches(" + name + ")", context -> {
            ScenarioCapture capture = context.capture(name);
            context.assertTrue(capture != null, "Expected capture was not created: " + name);
            context.assertTrue(Boolean.TRUE.equals(capture.baselineMatched()),
                    "Capture did not match baseline: " + name
                            + ", path=" + capture.path()
                            + ", baseline=" + capture.baselinePath()
                            + ", message=" + capture.comparisonMessage());
        });
    }

    /**
     * Runs the elapsed less than step.
     *
     * @param millis the millis
     * @return the elapsed less than
     */
    public static ScenarioAssertion elapsedLessThan(long millis) {
        return assertion("elapsedLessThan(" + millis + ")", context ->
                context.assertTrue(context.elapsedMillis() < millis,
                        "Scenario elapsed time exceeded " + millis + "ms"));
    }

    /**
     * Runs the screen step.
     *
     * @param expected the expected
     * @return the screen
     */
    public static ScenarioAssertion screen(Object expected) {
        return assertion("screen(" + expected + ")", context -> {
            Object actual = context.host().screen();
            context.assertTrue(Objects.equals(expected, actual),
                    "Screen mismatch expected=[" + expected + "] actual=[" + actual + "]");
        });
    }

    /**
     * Runs the screen type step.
     *
     * @param expectedType the expected type
     * @return the screen type
     */
    public static ScenarioAssertion screenType(Class<?> expectedType) {
        if (expectedType == null) {
            throw new IllegalArgumentException("Expected screen type cannot be null.");
        }
        return assertion("screenType(" + expectedType.getName() + ")", context -> {
            Object actual = context.host().screen();
            context.assertTrue(actual != null && expectedType.isInstance(actual),
                    "Screen type mismatch expected=[" + expectedType.getName() + "] actual=["
                            + (actual != null ? actual.getClass().getName() : "null") + "]");
        });
    }

    /**
     * Runs the probe step.
     *
     * @param <T> the value type
     * @param type the expected Java type
     * @param predicate the predicate
     * @return the probe
     */
    public static <T> ScenarioAssertion probe(Class<T> type, Predicate<? super T> predicate) {
        return probe(type != null ? type.getName() : "probe", type, predicate);
    }

    /**
     * Runs the probe step.
     *
     * @param <T> the value type
     * @param name the name
     * @param type the expected Java type
     * @param predicate the predicate
     * @return the probe
     */
    public static <T> ScenarioAssertion probe(String name, Class<T> type, Predicate<? super T> predicate) {
        if (type == null) {
            throw new IllegalArgumentException("Probe type cannot be null.");
        }
        if (predicate == null) {
            throw new IllegalArgumentException("Probe predicate cannot be null.");
        }
        return assertion("probe(" + name + ")", context -> {
            T probe = context.requireProbe(type);
            context.assertTrue(predicate.test(probe), "Probe assertion failed: " + name);
        });
    }

    /**
     * Runs the assertion step.
     *
     * @param name the name
     * @param callback the callback to invoke
     * @return the assertion
     */
    public static ScenarioAssertion assertion(String name, ScenarioCallback callback) {
        return new CallbackAssertion(name, callback);
    }

    /**
     * Represents a callback assertion.
     *
     * @author xpenatan
     */
    private static final class CallbackAssertion implements ScenarioAssertion {
        private final String name;
        private final ScenarioCallback callback;

        CallbackAssertion(String name, ScenarioCallback callback) {
            if (callback == null) {
                throw new IllegalArgumentException("Scenario assertion callback cannot be null.");
            }
            this.name = name != null && name.length() > 0 ? name : "assertion";
            this.callback = callback;
        }

        /**
         * Returns the name.
         *
         * @return the name
         */
        @Override
        public String name() {
            return name;
        }

        /**
         * Runs the verify step.
         *
         * @param context the context
         */
        @Override
        public void verify(ScenarioContext context) {
            callback.run(context);
        }
    }
}
