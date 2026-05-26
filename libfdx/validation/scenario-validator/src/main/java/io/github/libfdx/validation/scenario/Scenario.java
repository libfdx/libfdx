package io.github.libfdx.validation.scenario;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Scenario {
    private final String name;
    private final ArrayList<Step> steps = new ArrayList<Step>();
    private ScenarioSetup<?> setup;
    private ScenarioContent<Object> content;
    private boolean visualBaselineRequired;

    private Scenario(String name) {
        if (name == null || name.length() == 0) {
            throw new IllegalArgumentException("Scenario name cannot be empty.");
        }
        this.name = name;
    }

    public static Scenario named(String name) {
        return new Scenario(name);
    }

    public String name() {
        return name;
    }

    public <T> Scenario setup(ScenarioSetup<T> setup) {
        this.setup = setup;
        return this;
    }

    @SuppressWarnings("unchecked")
    public <T> Scenario content(ScenarioContent<? super T> content) {
        this.content = (ScenarioContent<Object>) content;
        return this;
    }

    public Scenario action(ScenarioAction action) {
        if (action == null) {
            throw new IllegalArgumentException("Scenario action cannot be null.");
        }
        steps.add(new ActionStep(action));
        return this;
    }

    public Scenario waitFor(ScenarioWait wait) {
        if (wait == null) {
            throw new IllegalArgumentException("Scenario wait cannot be null.");
        }
        steps.add(new WaitStep(wait));
        return this;
    }

    public Scenario expect(ScenarioAssertion assertion) {
        if (assertion == null) {
            throw new IllegalArgumentException("Scenario assertion cannot be null.");
        }
        steps.add(new AssertionStep(assertion));
        return this;
    }

    public Scenario capture(String name) {
        steps.add(new CallbackStep("capture(" + name + ")", context -> context.requestCapture(name)));
        return this;
    }

    public Scenario custom(String name, ScenarioCallback callback) {
        if (callback == null) {
            throw new IllegalArgumentException("Scenario callback cannot be null.");
        }
        steps.add(new CallbackStep(name, callback));
        return this;
    }

    public Scenario visualBaselineRequired() {
        this.visualBaselineRequired = true;
        return this;
    }

    public boolean requiresVisualBaseline() {
        return visualBaselineRequired;
    }

    public List<String> operationNames() {
        ArrayList<String> names = new ArrayList<String>(steps.size());
        for (int i = 0; i < steps.size(); i++) {
            names.add(steps.get(i).name());
        }
        return Collections.unmodifiableList(names);
    }

    ScenarioResult run(ScenarioHost host) {
        ScenarioContext context = new ScenarioContext(host, this);
        Object setupInstance = null;
        try {
            if (setup != null) {
                setupInstance = setup.create();
            }
        } catch (RuntimeException ex) {
            return failed(host, "setup", -1, ex);
        }
        try {
            if (content != null) {
                content.build(setupInstance);
            }
        } catch (RuntimeException ex) {
            return failed(host, "content", -1, ex);
        }
        for (int i = 0; i < steps.size(); i++) {
            Step step = steps.get(i);
            try {
                step.run(context);
            } catch (RuntimeException ex) {
                return failed(host, step.name(), i, ex);
            }
        }
        if (visualBaselineRequired) {
            List<ScenarioCapture> captures = host.captures();
            if (captures.isEmpty()) {
                return ScenarioResult.failed(name, "visualBaselineRequired", steps.size(),
                        "Scenario requires a visual baseline but produced no capture.",
                        host.frame(), host.elapsedMillis(), operationNames(), host.events().recent(),
                        captures, true);
            }
            for (int i = 0; i < captures.size(); i++) {
                ScenarioCapture capture = captures.get(i);
                if (!Boolean.TRUE.equals(capture.baselineMatched())) {
                    return ScenarioResult.failed(name, "visualBaselineRequired", steps.size(),
                            "Scenario capture did not match a required baseline: " + capture.name()
                                    + ", path=" + capture.path()
                                    + ", baseline=" + capture.baselinePath()
                                    + ", message=" + capture.comparisonMessage(),
                            host.frame(), host.elapsedMillis(), operationNames(), host.events().recent(),
                            captures, true);
                }
            }
        }
        return ScenarioResult.passed(name, host.frame(), host.elapsedMillis(), operationNames(),
                host.events().recent(), host.captures(), visualBaselineRequired);
    }

    private ScenarioResult failed(ScenarioHost host, String operationName, int operationIndex, RuntimeException ex) {
        String message = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getName();
        return ScenarioResult.failed(name, operationName, operationIndex, message, host.frame(), host.elapsedMillis(),
                operationNames(), host.events().recent(), host.captures(), visualBaselineRequired);
    }

    private interface Step {
        String name();

        void run(ScenarioContext context);
    }

    private static final class ActionStep implements Step {
        private final ScenarioAction action;

        ActionStep(ScenarioAction action) {
            this.action = action;
        }

        @Override
        public String name() {
            return action.name();
        }

        @Override
        public void run(ScenarioContext context) {
            action.perform(context);
        }
    }

    private static final class AssertionStep implements Step {
        private final ScenarioAssertion assertion;

        AssertionStep(ScenarioAssertion assertion) {
            this.assertion = assertion;
        }

        @Override
        public String name() {
            return assertion.name();
        }

        @Override
        public void run(ScenarioContext context) {
            assertion.verify(context);
        }
    }

    private static final class WaitStep implements Step {
        private final ScenarioWait wait;

        WaitStep(ScenarioWait wait) {
            this.wait = wait;
        }

        @Override
        public String name() {
            return wait.name();
        }

        @Override
        public void run(ScenarioContext context) {
            long startMillis = context.elapsedMillis();
            int startFrame = context.frame();
            if (wait.complete(context, startMillis, startFrame)) {
                return;
            }
            while (canAdvance(context, startMillis, startFrame)) {
                context.host().advanceFrame(context);
                if (wait.complete(context, startMillis, startFrame)) {
                    return;
                }
            }
            context.fail("Wait timed out: " + wait.name() + ", lastObserved=" + wait.lastObservedValue()
                    + ", recentEvents=" + context.events().recent());
        }

        private boolean canAdvance(ScenarioContext context, long startMillis, int startFrame) {
            int timeoutFrames = wait.timeoutFrames();
            long timeoutMillis = wait.timeoutMillis();
            if (timeoutFrames <= 0 && timeoutMillis <= 0L) {
                return false;
            }
            if (timeoutFrames > 0 && context.frame() - startFrame >= timeoutFrames) {
                return false;
            }
            if (timeoutMillis > 0L && context.elapsedMillis() - startMillis >= timeoutMillis) {
                return false;
            }
            return true;
        }
    }

    private static final class CallbackStep implements Step {
        private final String name;
        private final ScenarioCallback callback;

        CallbackStep(String name, ScenarioCallback callback) {
            this.name = name != null && name.length() > 0 ? name : "custom";
            this.callback = callback;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public void run(ScenarioContext context) {
            callback.run(context);
        }
    }
}
