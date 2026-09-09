package io.github.libfdx.graphics.shader.runtime;

import java.util.List;

/** Immutable import diagnostics. Resolving recipes does not imply that preparation succeeded. */
public record ShaderPreloadImportReport(List<Item> items, List<String> diagnostics) {
    public record Item(ShaderPreloadRecipe recipe, ShaderPreloadResolver.Status status, String message) { }
    public ShaderPreloadImportReport { items = List.copyOf(items); diagnostics = List.copyOf(diagnostics); }
    public boolean hasUnresolvedEntries() {
        if (!diagnostics.isEmpty()) return true;
        for (Item item : items) if (item.status() != ShaderPreloadResolver.Status.RESOLVED) return true;
        return false;
    }
    public int resolvedCount() {
        int count = 0;
        for (Item item : items) if (item.status() == ShaderPreloadResolver.Status.RESOLVED) count++;
        return count;
    }
}
