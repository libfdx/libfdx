package io.github.libfdx.graphics;

import io.github.libfdx.core.FdxException;

public final class BufferDescriptor {
    private String label = "";
    private int size;
    private BufferUsage usage = BufferUsage.VERTEX;
    private boolean dynamic = true;

    public static BufferDescriptor vertex(String label, int size) {
        return new BufferDescriptor()
                .label(label)
                .size(size)
                .usage(BufferUsage.VERTEX);
    }

    public static BufferDescriptor staticVertex(String label, int size) {
        return vertex(label, size).dynamic(false);
    }

    public static BufferDescriptor index(String label, int size) {
        return new BufferDescriptor()
                .label(label)
                .size(size)
                .usage(BufferUsage.INDEX);
    }

    public static BufferDescriptor staticIndex(String label, int size) {
        return index(label, size).dynamic(false);
    }

    public String label() {
        return label;
    }

    public BufferDescriptor label(String label) {
        this.label = label != null ? label : "";
        return this;
    }

    public int size() {
        return size;
    }

    public BufferDescriptor size(int size) {
        if (size <= 0) {
            throw new FdxException("Buffer size must be greater than zero");
        }
        this.size = size;
        return this;
    }

    public BufferUsage usage() {
        return usage;
    }

    public BufferDescriptor usage(BufferUsage usage) {
        this.usage = usage != null ? usage : BufferUsage.VERTEX;
        return this;
    }

    public boolean dynamic() {
        return dynamic;
    }

    public BufferDescriptor dynamic(boolean dynamic) {
        this.dynamic = dynamic;
        return this;
    }
}
