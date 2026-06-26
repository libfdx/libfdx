package io.github.libfdx.net.buffer;

/**
 * Reads primitive values from a network buffer.
 *
 * @author xpenatan
 */
public final class NetReader {
    private final NetBuffer buffer;

    NetReader(NetBuffer buffer) {
        this.buffer = buffer;
    }

    /**
     * Rewinds this reader.
     *
     * @return this reader
     */
    public NetReader rewind() {
        buffer.rewind();
        return this;
    }

    /**
     * Reads a signed byte.
     *
     * @return the value
     */
    public byte getByte() {
        buffer.ensureReadable(1);
        int position = buffer.readPosition();
        byte value = buffer.byteAt(position);
        buffer.readPosition(position + 1);
        return value;
    }

    /**
     * Reads an unsigned byte.
     *
     * @return the value
     */
    public int getUnsignedByte() {
        return getByte() & 0xff;
    }

    /**
     * Reads a short in big-endian order.
     *
     * @return the value
     */
    public short getShort() {
        int high = getUnsignedByte();
        int low = getUnsignedByte();
        return (short) ((high << 8) | low);
    }

    /**
     * Reads an unsigned short in big-endian order.
     *
     * @return the value
     */
    public int getUnsignedShort() {
        return getShort() & 0xffff;
    }

    /**
     * Reads an int in big-endian order.
     *
     * @return the value
     */
    public int getInt() {
        int b0 = getUnsignedByte();
        int b1 = getUnsignedByte();
        int b2 = getUnsignedByte();
        int b3 = getUnsignedByte();
        return (b0 << 24) | (b1 << 16) | (b2 << 8) | b3;
    }

    /**
     * Reads a long in big-endian order.
     *
     * @return the value
     */
    public long getLong() {
        long high = getInt() & 0xffffffffL;
        long low = getInt() & 0xffffffffL;
        return (high << 32) | low;
    }

    /**
     * Reads a float.
     *
     * @return the value
     */
    public float getFloat() {
        return Float.intBitsToFloat(getInt());
    }

    /**
     * Reads a double.
     *
     * @return the value
     */
    public double getDouble() {
        return Double.longBitsToDouble(getLong());
    }

    /**
     * Reads bytes.
     *
     * @param destination the destination
     * @param offset the destination offset
     * @param length the length
     * @return this reader
     */
    public NetReader getBytes(byte[] destination, int offset, int length) {
        buffer.ensureReadable(length);
        if (destination == null || offset < 0 || length < 0 || offset + length > destination.length) {
            throw new IllegalArgumentException("Invalid destination byte range");
        }
        System.arraycopy(buffer.bytes(), buffer.readPosition(), destination, offset, length);
        buffer.readPosition(buffer.readPosition() + length);
        return this;
    }

    /**
     * Returns remaining readable bytes.
     *
     * @return the remaining bytes
     */
    public int remaining() {
        return buffer.length() - buffer.readPosition();
    }
}
