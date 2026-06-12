package io.github.libfdx.graphics;

import io.github.libfdx.core.FdxException;

/**
 * Describes the values used to create or identify a buffer.
 *
 * @author xpenatan
 */
public final class BufferDescriptor {
    private String label = "";
    private int size;
    private BufferUsage usage = BufferUsage.VERTEX;
    private boolean dynamic = true;

    /**
     * Creates a buffer descriptor.
     *
     * @param label the debug label
     * @param size the size
     * @return a new buffer descriptor
     */
    public static BufferDescriptor vertex(String label, int size) {
        return new BufferDescriptor()
                .label(label)
                .size(size)
                .usage(BufferUsage.VERTEX);
    }

    /**
     * Creates a buffer descriptor.
     *
     * @param label the debug label
     * @param size the size
     * @return a new buffer descriptor
     */
    public static BufferDescriptor staticVertex(String label, int size) {
        return vertex(label, size).dynamic(false);
    }

    /**
     * Creates a buffer descriptor.
     *
     * @param label the debug label
     * @param size the size
     * @return a new buffer descriptor
     */
    public static BufferDescriptor index(String label, int size) {
        return new BufferDescriptor()
                .label(label)
                .size(size)
                .usage(BufferUsage.INDEX);
    }

    /**
     * Creates a buffer descriptor.
     *
     * @param label the debug label
     * @param size the size
     * @return a new buffer descriptor
     */
    public static BufferDescriptor staticIndex(String label, int size) {
        return index(label, size).dynamic(false);
    }

    /**
     * Returns the label.
     *
     * @return the label
     */
    public String label() {
        return label;
    }

    /**
     * Sets the label and returns this buffer descriptor.
     *
     * @param label the debug label
     * @return this buffer descriptor for chaining
     */
    public BufferDescriptor label(String label) {
        this.label = label != null ? label : "";
        return this;
    }

    /**
     * Returns the size.
     *
     * @return the size
     */
    public int size() {
        return size;
    }

    /**
     * Sets the size and returns this buffer descriptor.
     *
     * @param size the size
     * @return this buffer descriptor for chaining
     */
    public BufferDescriptor size(int size) {
        if (size <= 0) {
            throw new FdxException("Buffer size must be greater than zero");
        }
        this.size = size;
        return this;
    }

    /**
     * Returns the usage.
     *
     * @return the usage
     */
    public BufferUsage usage() {
        return usage;
    }

    /**
     * Sets the usage and returns this buffer descriptor.
     *
     * @param usage the usage
     * @return this buffer descriptor for chaining
     */
    public BufferDescriptor usage(BufferUsage usage) {
        this.usage = usage != null ? usage : BufferUsage.VERTEX;
        return this;
    }

    /**
     * Returns the dynamic.
     *
     * @return true if dynamic succeeds or is active; false otherwise
     */
    public boolean dynamic() {
        return dynamic;
    }

    /**
     * Sets the dynamic and returns this buffer descriptor.
     *
     * @param dynamic the dynamic
     * @return this buffer descriptor for chaining
     */
    public BufferDescriptor dynamic(boolean dynamic) {
        this.dynamic = dynamic;
        return this;
    }
}
