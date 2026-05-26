package io.github.libfdx.validation.scenario;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ScenarioEvents {
    private final String[] events;
    private int writeIndex;
    private int size;

    public ScenarioEvents() {
        this(64);
    }

    public ScenarioEvents(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Event capacity must be positive.");
        }
        this.events = new String[capacity];
    }

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

    public void clear() {
        for (int i = 0; i < events.length; i++) {
            events[i] = null;
        }
        writeIndex = 0;
        size = 0;
    }

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
