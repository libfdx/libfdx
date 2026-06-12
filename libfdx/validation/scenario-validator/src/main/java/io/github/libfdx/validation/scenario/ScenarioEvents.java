package io.github.libfdx.validation.scenario;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a scenario events.
 *
 * @author xpenatan
 */
public final class ScenarioEvents {
    private final String[] events;
    private int writeIndex;
    private int size;

    /**
     * Creates a scenario events.
     */
    public ScenarioEvents() {
        this(64);
    }

    /**
     * Creates a scenario events.
     *
     * @param capacity the capacity
     */
    public ScenarioEvents(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Event capacity must be positive.");
        }
        this.events = new String[capacity];
    }

    /**
     * Runs the emit step.
     *
     * @param event the event
     */
    public void emit(String event) {
        if (event == null || event.length() == 0) {
            throw new IllegalArgumentException("Event name cannot be empty.");
        }
        events[writeIndex] = event;
        writeIndex = (writeIndex + 1) % events.length;
        if (size < events.length) {
            size++;
        }
    }

    /**
     * Runs the clear step.
     */
    public void clear() {
        for (int i = 0; i < events.length; i++) {
            events[i] = null;
        }
        writeIndex = 0;
        size = 0;
    }

    /**
     * Runs the contains step.
     *
     * @param event the event
     * @return true if contains succeeds or is active; false otherwise
     */
    public boolean contains(String event) {
        if (event == null) {
            return false;
        }
        for (int i = 0; i < size; i++) {
            String current = events[indexAt(i)];
            if (event.equals(current)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the latest.
     *
     * @return the latest
     */
    public String latest() {
        if (size == 0) {
            return null;
        }
        int index = writeIndex - 1;
        if (index < 0) {
            index = events.length - 1;
        }
        return events[index];
    }

    /**
     * Returns the recent.
     *
     * @return the recent
     */
    public List<String> recent() {
        if (size == 0) {
            return Collections.emptyList();
        }
        ArrayList<String> recent = new ArrayList<String>(size);
        for (int i = 0; i < size; i++) {
            recent.add(events[indexAt(i)]);
        }
        return Collections.unmodifiableList(recent);
    }

    /**
     * Returns the size.
     *
     * @return the size
     */
    public int size() {
        return size;
    }

    private int indexAt(int order) {
        int start = writeIndex - size;
        if (start < 0) {
            start += events.length;
        }
        return (start + order) % events.length;
    }
}
