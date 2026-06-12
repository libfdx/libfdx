package io.github.libfdx.validation.scenario.ui.kit;

import io.github.libfdx.ui.UiNode;
import io.github.libfdx.ui.UiRoot;
import io.github.libfdx.validation.scenario.ScenarioContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents an ui scenario target.
 *
 * @author xpenatan
 */
public final class UiScenarioTarget {
    private final String description;
    private final String eventName;
    private final NodeMatcher matcher;

    UiScenarioTarget(String description, NodeMatcher matcher) {
        this(description, description, matcher);
    }

    UiScenarioTarget(String description, String eventName, NodeMatcher matcher) {
        this.description = description != null && description.length() > 0 ? description : "ui-target";
        this.eventName = eventName != null && eventName.length() > 0 ? eventName : this.description;
        this.matcher = matcher;
    }

    /**
     * Returns the description.
     *
     * @return the description
     */
    public String description() {
        return description;
    }

    /**
     * Returns the event name.
     *
     * @return the event name
     */
    public String eventName() {
        return eventName;
    }

    /**
     * Runs the resolve step.
     *
     * @param root the root
     * @return the resolve
     */
    public UiNode resolve(UiRoot root) {
        if (root == null) {
            return null;
        }
        return find(root.rootNode());
    }

    /**
     * Runs the require step.
     *
     * @param context the context
     * @return the require
     */
    public UiNode require(ScenarioContext context) {
        UiRoot root = context.requireProbe(UiRoot.class);
        UiNode node = resolve(root);
        if (node == null) {
            context.fail("UI target not found: " + description);
        }
        return node;
    }

    /**
     * Runs the resolve all step.
     *
     * @param root the root
     * @return the resolve all
     */
    public List<UiNode> resolveAll(UiRoot root) {
        if (root == null) {
            return Collections.emptyList();
        }
        ArrayList<UiNode> nodes = new ArrayList<UiNode>();
        collect(root.rootNode(), nodes);
        return Collections.unmodifiableList(nodes);
    }

    private UiNode find(UiNode node) {
        if (node == null) {
            return null;
        }
        if (matcher.matches(node)) {
            return node;
        }
        List<UiNode> children = node.children();
        for (int i = 0; i < children.size(); i++) {
            UiNode found = find(children.get(i));
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private void collect(UiNode node, List<UiNode> nodes) {
        if (node == null) {
            return;
        }
        if (matcher.matches(node)) {
            nodes.add(node);
        }
        List<UiNode> children = node.children();
        for (int i = 0; i < children.size(); i++) {
            collect(children.get(i), nodes);
        }
    }

    /**
     * Defines the contract for node matcher implementations.
     *
     * @author xpenatan
     */
    @FunctionalInterface
    interface NodeMatcher {
        /**
         * Runs the matches step.
         *
         * @param node the node
         * @return true if matches succeeds or is active; false otherwise
         */
        boolean matches(UiNode node);
    }
}
