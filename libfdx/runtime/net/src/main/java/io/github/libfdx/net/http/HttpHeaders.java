package io.github.libfdx.net.http;

import io.github.libfdx.core.FdxException;

/**
 * Stores HTTP headers.
 *
 * @author xpenatan
 */
public final class HttpHeaders {
    private String[] names;
    private String[] values;
    private int size;

    /**
     * Creates headers.
     */
    public HttpHeaders() {
        this(8);
    }

    /**
     * Creates headers.
     *
     * @param capacity the initial capacity
     */
    public HttpHeaders(int capacity) {
        int actualCapacity = Math.max(1, capacity);
        names = new String[actualCapacity];
        values = new String[actualCapacity];
    }

    /**
     * Adds a header value.
     *
     * @param name the name
     * @param value the value
     * @return this headers object
     */
    public HttpHeaders add(String name, String value) {
        checkName(name);
        ensureCapacity(1);
        names[size] = name;
        values[size] = value != null ? value : "";
        size++;
        return this;
    }

    /**
     * Sets a header value after removing existing values for the name.
     *
     * @param name the name
     * @param value the value
     * @return this headers object
     */
    public HttpHeaders set(String name, String value) {
        remove(name);
        return add(name, value);
    }

    /**
     * Removes all values for a header name.
     *
     * @param name the name
     * @return this headers object
     */
    public HttpHeaders remove(String name) {
        checkName(name);
        for (int i = 0; i < size;) {
            if (name.equalsIgnoreCase(names[i])) {
                int move = size - i - 1;
                if (move > 0) {
                    System.arraycopy(names, i + 1, names, i, move);
                    System.arraycopy(values, i + 1, values, i, move);
                }
                size--;
                names[size] = null;
                values[size] = null;
            } else {
                i++;
            }
        }
        return this;
    }

    /**
     * Returns the first value for a header name.
     *
     * @param name the name
     * @return the first value, or null
     */
    public String first(String name) {
        checkName(name);
        for (int i = 0; i < size; i++) {
            if (name.equalsIgnoreCase(names[i])) {
                return values[i];
            }
        }
        return null;
    }

    /**
     * Returns the number of stored header values.
     *
     * @return the size
     */
    public int size() {
        return size;
    }

    /**
     * Returns the header name at an index.
     *
     * @param index the index
     * @return the name
     */
    public String nameAt(int index) {
        checkIndex(index);
        return names[index];
    }

    /**
     * Returns the header value at an index.
     *
     * @param index the index
     * @return the value
     */
    public String valueAt(int index) {
        checkIndex(index);
        return values[index];
    }

    private void ensureCapacity(int additional) {
        int required = size + additional;
        if (required <= names.length) {
            return;
        }
        int newCapacity = Math.max(required, names.length * 2);
        String[] newNames = new String[newCapacity];
        String[] newValues = new String[newCapacity];
        System.arraycopy(names, 0, newNames, 0, size);
        System.arraycopy(values, 0, newValues, 0, size);
        names = newNames;
        values = newValues;
    }

    private void checkIndex(int index) {
        if (index < 0 || index >= size) {
            throw new FdxException("Header index out of range: " + index);
        }
    }

    private static void checkName(String name) {
        if (name == null || name.isEmpty()) {
            throw new FdxException("Header name cannot be empty");
        }
    }
}
