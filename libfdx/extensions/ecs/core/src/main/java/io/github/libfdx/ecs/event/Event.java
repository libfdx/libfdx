package io.github.libfdx.ecs.event;

public final class Event {
    private int type;
    private int intValue;
    private long longValue;
    private float floatValue;
    private double doubleValue;
    private boolean booleanValue;
    private Object object;

    public int type() {
        return type;
    }

    public Event type(int type) {
        this.type = type;
        return this;
    }

    public int intValue() {
        return intValue;
    }

    public Event intValue(int value) {
        intValue = value;
        return this;
    }

    public long longValue() {
        return longValue;
    }

    public Event longValue(long value) {
        longValue = value;
        return this;
    }

    public float floatValue() {
        return floatValue;
    }

    public Event floatValue(float value) {
        floatValue = value;
        return this;
    }

    public double doubleValue() {
        return doubleValue;
    }

    public Event doubleValue(double value) {
        doubleValue = value;
        return this;
    }

    public boolean booleanValue() {
        return booleanValue;
    }

    public Event booleanValue(boolean value) {
        booleanValue = value;
        return this;
    }

    public Object object() {
        return object;
    }

    public Event object(Object value) {
        object = value;
        return this;
    }

    public void reset() {
        type = 0;
        intValue = 0;
        longValue = 0L;
        floatValue = 0.0f;
        doubleValue = 0.0;
        booleanValue = false;
        object = null;
    }
}
