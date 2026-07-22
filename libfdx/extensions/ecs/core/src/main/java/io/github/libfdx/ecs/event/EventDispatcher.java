package io.github.libfdx.ecs.event;

import io.github.libfdx.collections.Array;
import io.github.libfdx.collections.IntMap;
import io.github.libfdx.ecs.World;

public final class EventDispatcher {
    private final World world;
    private final IntMap<Array<EventListener>> listeners = new IntMap<>();
    private final Array<QueuedEvent> queue = new Array<>();
    private final Array<Event> pool = new Array<>();
    private boolean flushing;

    public EventDispatcher(World world) {
        this.world = world;
    }

    public Event obtain(int type) {
        Event event;
        if (pool.notEmpty()) {
            event = pool.pop();
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
        Array<EventListener> typedListeners = listeners.get(type);
        if (typedListeners == null) {
            typedListeners = new Array<>();
            listeners.put(type, typedListeners);
        }
        typedListeners.add(listener);
    }

    public void removeListener(int type, EventListener listener) {
        Array<EventListener> typedListeners = listeners.get(type);
        if (typedListeners == null) {
            return;
        }
        typedListeners.removeValue(listener);
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
                Array<EventListener> typedListeners = listeners.get(event.type());
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
                queue.removeRange(0, count - 1);
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
