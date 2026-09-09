package io.github.libfdx.maps;

import io.github.libfdx.collections.Array;
import io.github.libfdx.core.FdxException;

/** Application-owned authoring properties in insertion order; imported cached data is borrowed. */
public final class MapProperties {
    private final Array<MapProperty> values = new Array<MapProperty>(0);
    public MapProperties put(MapProperty property) {
        if (property == null) { throw new FdxException("Property cannot be null"); }
        for (int i = 0; i < values.size(); i++) {
            if (values.get(i).name().equals(property.name())) { values.set(i, property); return this; }
        }
        values.add(property); return this;
    }
    /** Returns the property, or null when absent. */
    public MapProperty find(String name) {
        for (int i = 0; i < values.size(); i++) {
            if (values.get(i).name().equals(name)) { return values.get(i); }
        }
        return null;
    }
    public MapProperty get(String name) {
        MapProperty result = find(name);
        if (result == null) { throw new FdxException("Missing map property: " + name); }
        return result;
    }
    public int size() { return values.size(); }
    public MapProperty property(int index) { return values.get(index); }
}
