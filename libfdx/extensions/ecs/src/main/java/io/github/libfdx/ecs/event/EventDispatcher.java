package io.github.libfdx.ecs.event;

import io.github.libfdx.ecs.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public final class EventDispatcher {
    private final World world;
    private final Map<Integer, ArrayList<EventListener>> listeners = new HashMap<>();
    private final ArrayList<QueuedEvent> queue = new ArrayList<>();
    private final ArrayList<Event> pool = new ArrayList<>();
    private boolean flushing;

    public EventDispatcher(World world) {
        this.world = world;
    }

    public Event obtain(int type) {
        Event event;
        int last = pool.size() - 1;
        if (last >= 0) {
            event = pool.remove(last);
        } else {
            event = new Event();
        }
        event.type(type);
        return event;
    }

    public void dispatch(Event event) {
        dispatch(event, null, null);
    }

    public void dispatch(Event event, Runnable processed) {
        dispatch(event, null, processed);
    }

    public void dispatch(Event event, EventListener listener) {
        dispatch(event, listener, null);
    }

    public void dispatch(Event event, EventListener listener, Runnable processed) {
        if (event == null) {
            throw new IllegalArgumentException("event cannot be null.");
        }
        queue.add(new QueuedEvent(event, listener, processed));
    }

    public void addListener(int type, EventListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener cannot be null.");
        }
        listeners.computeIfAbsent(type, ignored -> new ArrayList<>()).add(listener);
    }

    public void removeListener(int type, EventListener listener) {
        ArrayList<EventListener> typedListeners = listeners.get(type);
        if (typedListeners == null) {
            return;
        }
        typedListeners.remove(listener);
        if (typedListeners.isEmpty()) {
            listeners.remove(type);
        }
    }

    public void flush() {
        if (flushing) {
            throw new IllegalStateException("Event flushing is already active.");
        }
        flushing = true;
        try {
            int count = queue.size();
            for (int i = 0; i < count; i++) {
                QueuedEvent queued = queue.get(i);
                Event event = queued.event;
                ArrayList<EventListener> typedListeners = listeners.get(event.type());
                if (typedListeners != null) {
                    for (int listenerIndex = 0; listenerIndex < typedListeners.size(); listenerIndex++) {
                        typedListeners.get(listenerIndex).onEvent(world, event);
                    }
                }
                if (queued.listener != null) {
                    queued.listener.onEvent(world, event);
                }
                if (queued.processed != null) {
                    queued.processed.run();
                }
                event.reset();
                pool.add(event);
            }
            if (count > 0) {
                queue.subList(0, count).clear();
            }
        } finally {
            flushing = false;
        }
    }

    public int queuedCount() {
        return queue.size();
    }

    public void clear() {
        for (int i = 0; i < queue.size(); i++) {
            Event event = queue.get(i).event;
            event.reset();
            pool.add(event);
        }
        queue.clear();
        listeners.clear();
        for (int i = 0; i < pool.size(); i++) {
            pool.get(i).reset();
        }
    }

    private static final class QueuedEvent {
        final Event event;
        final EventListener listener;
        final Runnable processed;

        QueuedEvent(Event event, EventListener listener, Runnable processed) {
            this.event = event;
            this.listener = listener;
            this.processed = processed;
        }
    }
}
