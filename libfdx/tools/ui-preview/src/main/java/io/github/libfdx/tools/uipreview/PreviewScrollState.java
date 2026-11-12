package io.github.libfdx.tools.uipreview;

import io.github.libfdx.ui.UiNode;
import io.github.libfdx.ui.UiRoot;
import io.github.libfdx.ui.UiScrollState;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/** Copies viewport offsets without retaining application objects or migrating component state. */
final class PreviewScrollState {
    private record Offset(float x, float y) { }

    private final Map<String, Offset> offsets = new HashMap<>();

    static PreviewScrollState capture(UiRoot root) {
        PreviewScrollState snapshot = new PreviewScrollState();
        snapshot.capture(root.rootNode());
        return snapshot;
    }

    private void capture(UiNode node) {
        if (node == null) return;
        UiScrollState scroll = node.scrollState();
        if (scroll != null) offsets.put(node.identity(), new Offset(scroll.x(), scroll.y()));
        var children = node.children();
        for (int i = 0; i < children.size(); i++) capture(children.get(i));
    }

    void restore(UiRoot root) {
        // Restoring a virtual list can compose a different set of nested scroll nodes.
        // Repeat against the new tree until offsets settle, using the UI root's pass limit.
        for (int pass = 0; pass < 8 && restore(root.rootNode()); pass++) {
            root.requestCompose();
            root.update(0);
        }
    }

    private boolean restore(UiNode node) {
        if (node == null) return false;
        boolean changed = false;
        UiScrollState scroll = node.scrollState();
        Offset offset = offsets.get(node.identity());
        if (scroll != null && offset != null) {
            float x = scroll.x();
            float y = scroll.y();
            scroll.scrollTo(offset.x(), offset.y());
            changed = scroll.x() != x || scroll.y() != y;
        }
        var children = node.children();
        for (int i = 0; i < children.size(); i++) changed |= restore(children.get(i));
        return changed;
    }

    Properties values(String entry, String target) {
        Properties values = new Properties();
        values.setProperty("entry", entry);
        values.setProperty("target", target);
        values.setProperty("count", Integer.toString(offsets.size()));
        int index = 0;
        for (var item : offsets.entrySet()) {
            String prefix = "scroll." + index++;
            values.setProperty(prefix + ".identity", item.getKey());
            values.setProperty(prefix + ".x", Float.toString(item.getValue().x()));
            values.setProperty(prefix + ".y", Float.toString(item.getValue().y()));
        }
        return values;
    }

    static PreviewScrollState read(Properties values, String entry, String target) {
        PreviewScrollState snapshot = new PreviewScrollState();
        if (!entry.equals(values.getProperty("entry")) || !target.equals(values.getProperty("target"))) return snapshot;
        int count = Integer.parseInt(values.getProperty("count", "0"));
        if (count < 0 || count > values.size() / 3) throw new IllegalArgumentException("Invalid scroll snapshot size");
        for (int i = 0; i < count; i++) {
            String prefix = "scroll." + i;
            String identity = values.getProperty(prefix + ".identity");
            float x = Float.parseFloat(values.getProperty(prefix + ".x", "NaN"));
            float y = Float.parseFloat(values.getProperty(prefix + ".y", "NaN"));
            if (identity == null || !Float.isFinite(x) || !Float.isFinite(y)) {
                throw new IllegalArgumentException("Invalid scroll offset");
            }
            snapshot.offsets.put(identity, new Offset(x, y));
        }
        return snapshot;
    }

    static long fingerprint(UiNode node, long hash) {
        if (node == null) return hash;
        UiScrollState scroll = node.scrollState();
        if (scroll != null) {
            hash = hash * 31 + node.identity().hashCode();
            hash = hash * 31 + Float.floatToIntBits(scroll.x());
            hash = hash * 31 + Float.floatToIntBits(scroll.y());
        }
        var children = node.children();
        for (int i = 0; i < children.size(); i++) hash = fingerprint(children.get(i), hash);
        return hash;
    }

}
