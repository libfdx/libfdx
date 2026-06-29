package io.github.libfdx.net.buffer;

import io.github.libfdx.core.FdxException;

/**
 * Stores reusable packet bytes.
 *
 * @author xpenatan
 */
public final class NetBuffer {
    private final NetBufferPool pool;
    private final byte[] bytes;
    private final NetReader reader;
    private final NetWriter writer;
    private int length;
    private int readPosition;
    private int references;
    private boolean inUse;

    NetBuffer(NetBufferPool pool, int capacity) {
        this.pool = pool;
        bytes = new byte[capacity];
        reader = new NetReader(this);
        writer = new NetWriter(this);
    }

    /**
     * Returns the reader.
     *
     * @return the reader
     */
    public NetReader reader() {
        checkInUse();
        return reader;
    }

    /**
     * Returns the writer.
     *
     * @return the writer
     */
    public NetWriter writer() {
        checkInUse();
        return writer;
    }

    /**
     * Clears this buffer for writing.
     *
     * @return this buffer
     */
    public NetBuffer clear() {
        checkInUse();
        length = 0;
        readPosition = 0;
        return this;
    }

    /**
     * Rewinds the reader to the beginning.
     *
     * @return this buffer
     */
    public NetBuffer rewind() {
        checkInUse();
        readPosition = 0;
        return this;
    }

    /**
     * Retains this buffer beyond the current callback.
     *
     * @return this buffer
     */
    public NetBuffer retain() {
        checkInUse();
        references++;
        return this;
    }

    /**
     * Releases this buffer.
     */
    public void release() {
        checkInUse();
        references--;
        if (references < 0) {
            throw new FdxException("Network buffer released too many times");
        }
        if (references == 0) {
            inUse = false;
            length = 0;
            readPosition = 0;
            pool.release(this);
        }
    }

    /**
     * Returns the capacity.
     *
     * @return the capacity
     */
    public int capacity() {
        return bytes.length;
    }

    /**
     * Returns the byte length.
     *
     * @return the length
     */
    public int length() {
        checkInUse();
        return length;
    }

    /**
     * Returns the backing array.
     *
     * @return the backing array
     */
    public byte[] bytes() {
        checkInUse();
        return bytes;
    }

    /**
     * Returns the backing array offset.
     *
     * @return the offset
     */
    public int offset() {
        checkInUse();
        return 0;
    }

    /**
     * Copies bytes into this buffer.
     *
     * @param source the source bytes
     * @param offset the source offset
     * @param length the byte length
     * @return this buffer
     */
    public NetBuffer set(byte[] source, int offset, int length) {
        clear();
        writer.putBytes(source, offset, length);
        rewind();
        return this;
    }

    boolean isInUse() {
        return inUse;
    }

    int readPosition() {
        return readPosition;
    }

    void readPosition(int readPosition) {
        this.readPosition = readPosition;
    }

    byte byteAt(int index) {
        return bytes[index];
    }

    void appendByte(int value) {
        ensureWritable(1);
        bytes[length++] = (byte) value;
    }

    void appendBytes(byte[] source, int offset, int count) {
        if (source == null) {
            throw new FdxException("Network write source cannot be null");
        }
        if (offset < 0 || count < 0 || offset + count > source.length) {
            throw new FdxException("Network write range is invalid");
        }
        ensureWritable(count);
        System.arraycopy(source, offset, bytes, length, count);
        length += count;
    }

    void ensureReadable(int count) {
        checkInUse();
        if (count < 0 || readPosition + count > length) {
            throw new FdxException("Network buffer read exceeds packet length");
        }
    }

    void ensureWritable(int count) {
        checkInUse();
        if (count < 0 || length + count > bytes.length) {
            throw new FdxException("Network buffer write exceeds packet capacity");
        }
    }

    void acquireFromPool() {
        inUse = true;
        references = 1;
        length = 0;
        readPosition = 0;
    }

    private void checkInUse() {
        if (!inUse) {
            throw new FdxException("Network buffer is not acquired");
        }
    }
}
