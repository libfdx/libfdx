package io.github.libfdx.validation.scenario;

import java.util.function.Predicate;

/**
 * Represents a scenario waits.
 *
 * @author xpenatan
 */
public final class ScenarioWaits {
    private ScenarioWaits() {
    }

    /**
     * Runs the frames step.
     *
     * @param frames the frames
     * @return the frames
     */
    public static ConfigurableWait frames(int frames) {
        int safeFrames = Math.max(0, frames);
        return new ConfigurableWait("frames(" + safeFrames + ")",
                (context, startMillis, startFrame) -> context.frame() - startFrame >= safeFrames)
                .timeoutFrames(safeFrames);
    }

    /**
     * Runs the millis step.
     *
     * @param millis the millis
     * @return the millis
     */
    public static ConfigurableWait millis(long millis) {
        long safeMillis = Math.max(0L, millis);
        return new ConfigurableWait("millis(" + safeMillis + ")",
                (context, startMillis, startFrame) -> context.elapsedMillis() - startMillis >= safeMillis)
                .timeoutMillis(safeMillis);
    }

    /**
     * Runs the event step.
     *
     * @param event the event
     * @return the event
     */
    public static ConfigurableWait event(String event) {
        return new ConfigurableWait("event(" + event + ")",
                (context, startMillis, startFrame) -> context.events().contains(event));
    }

    /**
     * Runs the capture ready step.
     *
     * @param name the name
     * @return the capture ready
     */
    public static ConfigurableWait captureReady(String name) {
        return new ConfigurableWait("captureReady(" + name + ")",
                (context, startMillis, startFrame) -> context.hasCapture(name)
                        || context.events().contains("capture.ready:" + name));
    }

    /**
     * Returns the layout settled.
     *
     * @return the layout settled
     */
    public static ConfigurableWait layoutSettled() {
        return event("validation.layout.settled");
    }

    /**
     * Returns the animation finished.
     *
     * @return the animation finished
     */
    public static ConfigurableWait animationFinished() {
        return event("validation.animation.finished");
    }

    /**
     * Runs the until step.
     *
     * @param predicate the predicate
     * @return the until
     */
    public static ConfigurableWait until(Predicate<ScenarioContext> predicate) {
        if (predicate == null) {
            throw new IllegalArgumentException("Wait predicate cannot be null.");
        }
        return new ConfigurableWait("until", (context, startMillis, startFrame) -> predicate.test(context));
    }

    /**
     * Runs the until step.
     *
     * @param name the name
     * @param predicate the predicate
     * @return the until
     */
    public static ConfigurableWait until(String name, Predicate<ScenarioContext> predicate) {
        if (predicate == null) {
            throw new IllegalArgumentException("Wait predicate cannot be null.");
        }
        return new ConfigurableWait(name, (context, startMillis, startFrame) -> predicate.test(context));
    }

    /**
     * Represents a configurable wait.
     *
     * @author xpenatan
     */
    public static final class ConfigurableWait implements ScenarioWait {
        private final String name;
        private final WaitPredicate predicate;
        private long timeoutMillis;
        private int timeoutFrames;
        private String lastObservedValue;

        private ConfigurableWait(String name, WaitPredicate predicate) {
            this.name = name != null && name.length() > 0 ? name : "wait";
            this.predicate = predicate;
        }

        /**
         * Sets the timeout millis and returns this configurable wait.
         *
         * @param timeoutMillis the timeout millis
         * @return this configurable wait for chaining
         */
        public ConfigurableWait timeoutMillis(long timeoutMillis) {
            this.timeoutMillis = Math.max(0L, timeoutMillis);
            return this;
        }

        /**
         * Sets the timeout frames and returns this configurable wait.
         *
         * @param timeoutFrames the timeout frames
         * @return this configurable wait for chaining
         */
        public ConfigurableWait timeoutFrames(int timeoutFrames) {
            this.timeoutFrames = Math.max(0, timeoutFrames);
            return this;
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
         * Runs the complete step.
         *
         * @param context the context
         * @param startMillis the start millis
         * @param startFrame the start frame
         * @return true if complete succeeds or is active; false otherwise
         */
        @Override
        public boolean complete(ScenarioContext context, long startMillis, int startFrame) {
            boolean complete = predicate.complete(context, startMillis, startFrame);
            String latest = context.events().latest();
            lastObservedValue = latest != null ? latest : "frame=" + context.frame()
                    + ", elapsedMillis=" + context.elapsedMillis();
            return complete;
        }

        /**
         * Returns the timeout millis.
         *
         * @return the timeout millis
         */
        @Override
        public long timeoutMillis() {
            return timeoutMillis;
        }

        /**
         * Returns the timeout frames.
         *
         * @return the timeout frames
         */
        @Override
        public int timeoutFrames() {
            return timeoutFrames;
        }

        /**
         * Returns the last observed value.
         *
         * @return the last observed value
         */
        @Override
        public String lastObservedValue() {
            return lastObservedValue;
        }
    }

    /**
     * Defines the contract for wait predicate implementations.
     *
     * @author xpenatan
     */
    @FunctionalInterface
    private interface WaitPredicate {
        /**
         * Runs the complete step.
         *
         * @param context the context
         * @param startMillis the start millis
         * @param startFrame the start frame
         * @return true if complete succeeds or is active; false otherwise
         */
        boolean complete(ScenarioContext context, long startMillis, int startFrame);
    }
}
