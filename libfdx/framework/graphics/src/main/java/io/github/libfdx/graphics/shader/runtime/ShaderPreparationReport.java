package io.github.libfdx.graphics.shader.runtime;

import java.util.List;

/** Immutable outcome snapshot. Contains no borrowed native resources. */
public final class ShaderPreparationReport {
    /** A scope member's outcome and optional diagnostics. */
    public record Item(ShaderRequest request, ShaderPreparationState state, Throwable failure, ShaderPreparationTimings timings) { }

    private final String label;
    private final List<Item> items;
    private final int ready, failed, unsupported, cancelled;

    ShaderPreparationReport(String label, List<Item> items) {
        this.label = label;
        this.items = List.copyOf(items);
        int ready = 0, failed = 0, unsupported = 0, cancelled = 0;
        for (Item item : items) {
            switch (item.state()) {
                case READY -> ready++;
                case FAILED -> failed++;
                case UNSUPPORTED -> unsupported++;
                case CANCELLED -> cancelled++;
                default -> throw new IllegalArgumentException("Report contains unsettled work");
            }
        }
        this.ready = ready;
        this.failed = failed;
        this.unsupported = unsupported;
        this.cancelled = cancelled;
    }

    public String label() { return label; }
    public List<Item> items() { return items; }
    public int totalCount() { return items.size(); }
    public int readyCount() { return ready; }
    public int failedCount() { return failed; }
    public int unsupportedCount() { return unsupported; }
    public int cancelledCount() { return cancelled; }
    public boolean allReady() { return ready == items.size(); }
}

