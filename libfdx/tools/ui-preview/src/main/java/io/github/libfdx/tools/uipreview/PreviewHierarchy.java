package io.github.libfdx.tools.uipreview;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Selectable rows. Only a local row has a setup factory. */
final class PreviewHierarchy {
    static final String ENTRY_ROOT = "@root";

    record Item(String id, String label, int depth, UiPreviewSetup setup, Entry entry) {
        Item(String id, String label, int depth, UiPreviewSetup setup) {
            this(id, label, depth, setup, null);
        }

    }

    record Entry(String className, UiPreviewConfig config) { }

    private PreviewHierarchy() {
    }

    static List<Item> targets(List<UiPreviewTarget> targets) {
        List<Item> items = new ArrayList<>();
        append(items, targets, 0, new HashSet<>(), null);
        return List.copyOf(items);
    }

    static List<Item> entries(UiPreviewEntry entry, UiPreviewConfig config, List<String> entries) {
        List<Item> items = new ArrayList<>();
        String activeClass = entry.getClass().getName();
        List<String> names = entries.stream().map(name -> name.substring(name.lastIndexOf('.') + 1)).toList();
        for (int i = 0; i < entries.size(); i++) {
            String type = entries.get(i);
            String name = names.get(i);
            String label = names.indexOf(name) == names.lastIndexOf(name) ? name : type;
            if (type.equals(activeClass)) {
                items.addAll(entryItems(entry, config, label));
            } else {
                items.addAll(availableEntry(type, label, entry.getClass().getClassLoader(), config));
            }
        }
        return List.copyOf(items);
    }

    private static List<Item> availableEntry(String type, String label, ClassLoader loader, UiPreviewConfig config) {
        try {
            var constructor = Class.forName(type, false, loader).asSubclass(UiPreviewEntry.class).getDeclaredConstructor();
            if (constructor.trySetAccessible()) {
                UiPreviewEntry entry = constructor.newInstance();
                UiPreviewConfig other = entry.previewConfig();
                if (config.sharesWindowWith(other)) return entryItems(entry, other, label);
                label = entryLabel(entry, label);
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            System.err.println("[ui-preview] Cannot register " + type + " in the current window: " + failure);
        }
        return List.of(new Item(type, label, 0, null));
    }

    private static List<Item> entryItems(UiPreviewEntry entry, UiPreviewConfig config, String label) {
        List<Item> items = new ArrayList<>();
        Entry owner = new Entry(entry.getClass().getName(), config);
        items.add(new Item(ENTRY_ROOT, entryLabel(entry, label), 0, entry, owner));
        append(items, entry.previewChildren(), 1, new HashSet<>(), owner);
        return items;
    }

    private static String entryLabel(UiPreviewEntry entry, String fallback) {
        String label = entry.previewLabel();
        return label.equals(entry.getClass().getSimpleName()) ? fallback : label;
    }

    private static void append(List<Item> items, List<UiPreviewTarget> targets, int depth, Set<String> ids, Entry entry) {
        for (UiPreviewTarget target : targets) {
            if (!ids.add(target.id())) throw new IllegalArgumentException("Duplicate preview target: " + target.id());
            items.add(new Item(target.id(), target.label(), depth, target.setup(), entry));
            append(items, target.children(), depth + 1, ids, entry);
        }
    }

    static int initial(List<Item> items, String requested, String restored) {
        return initial(items, null, requested, restored);
    }

    static int initial(List<Item> items, String entryClass, String requested, String restored) {
        String id = restored.isEmpty() ? requested : restored;
        int first = -1;
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            if (item.setup() == null) continue;
            if (entryClass != null && !item.entry().className().equals(entryClass)) continue;
            if (first < 0) first = i;
            if (item.id().equals(id)) return i;
        }
        if (first < 0) throw new IllegalArgumentException("At least one local preview target required");
        if (!id.isEmpty() && restored.isEmpty()) throw new IllegalArgumentException("Unknown preview target: " + id);
        return first;
    }

}
