package io.github.libfdx.net.buffer;

/**
 * Writes primitive values to a network buffer.
 *
 * @author xpenatan
 */
public final class NetWriter {
    private final NetBuffer buffer;

    NetWriter(NetBuffer buffer) {
        this.buffer = buffer;
    }

    /**
     * Writes a byte.
     *
     * @param value the value
     * @return this writer
     */
    public NetWriter putByte(int value) {
        buffer.appendByte(value);
        return this;
    }

    /**
     * Writes a short in big-endian order.
     *
     * @param value the value
     * @return this writer
     */
    public NetWriter putShort(int value) {
        buffer.appendByte((value >>> 8) & 0xff);
        buffer.appendByte(value & 0xff);
        return this;
    }

    /**
     * Writes an int in big-endian order.
     *
     * @param value the value
     * @return this writer
     */
    public NetWriter putInt(int value) {
        buffer.appendByte((value >>> 24) & 0xff);
        buffer.appendByte((value >>> 16) & 0xff);
        buffer.appendByte((value >>> 8) & 0xff);
        buffer.appendByte(value & 0xff);
        return this;
    }

    /**
     * Writes a long in big-endian order.
     *
     * @param value the value
     * @return this writer
     */
    public NetWriter putLong(long value) {
        putInt((int) (value >>> 32));
        putInt((int) value);
        return this;
    }

    /**
     * Writes a float.
     *
     * @param value the value
     * @return this writer
     */
    public NetWriter putFloat(float value) {
        return putInt(Float.floatToIntBits(value));
    }

    /**
     * Writes a double.
     *
     * @param value the value
     * @return this writer
     */
    public NetWriter putDouble(double value) {
        return putLong(Double.doubleToLongBits(value));
    }

    /**
     * Writes bytes.
     *
     * @param bytes the bytes
     * @param offset the offset
     * @param length the length
     * @return this writer
     */
    public NetWriter putBytes(byte[] bytes, int offset, int length) {
        buffer.appendBytes(bytes, offset, length);
        return this;
    }

    /**
     * Returns the number of written bytes.
     *
     * @return the position
     */
    public int position() {
        return buffer.length();
    }
}
