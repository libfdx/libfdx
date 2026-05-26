package io.github.libfdx.validation.scenario;

import java.util.function.Predicate;

public final class ScenarioWaits {
    private ScenarioWaits() {
    }

    public static ConfigurableWait frames(int frames) {
        int safeFrames = Math.max(0, frames);
        return new ConfigurableWait("frames(" + safeFrames + ")",
                (context, startMillis, startFrame) -> context.frame() - startFrame >= safeFrames)
                .timeoutFrames(safeFrames);
    }

    public static ConfigurableWait millis(long millis) {
        long safeMillis = Math.max(0L, millis);
        return new ConfigurableWait("millis(" + safeMillis + ")",
                (context, startMillis, startFrame) -> context.elapsedMillis() - startMillis >= safeMillis)
                .timeoutMillis(safeMillis);
    }

    public static ConfigurableWait event(String event) {
        return new ConfigurableWait("event(" + event + ")",
                (context, startMillis, startFrame) -> context.events().contains(event))
                .timeoutMillis(1000L);
    }

    public static ConfigurableWait captureReady(String name) {
        return new ConfigurableWait("captureReady(" + name + ")",
                (context, startMillis, startFrame) -> context.hasCapture(name)
                        || context.events().contains("capture.ready:" + name))
                .timeoutMillis(1000L);
    }

    public static ConfigurableWait layoutSettled() {
        return event("validation.layout.settled");
    }

    public static ConfigurableWait animationFinished() {
        return event("validation.animation.finished");
    }

    public static ConfigurableWait until(Predicate<ScenarioContext> predicate) {
        if (predicate == null) {
            throw new IllegalArgumentException("Wait predicate cannot be null.");
        }
        return new ConfigurableWait("until", (context, startMillis, startFrame) -> predicate.test(context))
                .timeoutMillis(1000L);
    }

    public static ConfigurableWait until(String name, Predicate<ScenarioContext> predicate) {
        if (predicate == null) {
            throw new IllegalArgumentException("Wait predicate cannot be null.");
        }
        return new ConfigurableWait(name, (context, startMillis, startFrame) -> predicate.test(context))
                .timeoutMillis(1000L);
    }

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

        public ConfigurableWait timeoutMillis(long timeoutMillis) {
            this.timeoutMillis = Math.max(0L, timeoutMillis);
            return this;
        }

        public ConfigurableWait timeoutFrames(int timeoutFrames) {
            this.timeoutFrames = Math.max(0, timeoutFrames);
            return this;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public boolean complete(ScenarioContext context, long startMillis, int startFrame) {
            boolean complete = predicate.complete(context, startMillis, startFrame);
            String latest = context.events().latest();
            lastObservedValue = latest != null ? latest : "frame=" + context.frame()
                    + ", elapsedMillis=" + context.elapsedMillis();
            return complete;
        }

        @Override
        public long timeoutMillis() {
            return timeoutMillis;
        }

        @Override
        public int timeoutFrames() {
            return timeoutFrames;
        }

        @Override
        public String lastObservedValue() {
            return lastObservedValue;
        }
    }

    @FunctionalInterface
    private interface WaitPredicate {
        boolean complete(ScenarioContext context, long startMillis, int startFrame);
    }
}
