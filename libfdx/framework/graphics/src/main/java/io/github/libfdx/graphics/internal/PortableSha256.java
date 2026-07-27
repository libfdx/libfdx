package io.github.libfdx.graphics.internal;

/**
 * Small portable SHA-256 implementation for framework runtimes that do not provide
 * {@code java.security}.
 *
 * <p>Each instance owns fixed-size working storage and performs no input-sized allocation.
 * Final hexadecimal output is lowercase, matching the JDK SHA-256 representation previously used
 * by the graphics runtime.</p>
 */
public final class PortableSha256 {
    private static final int BLOCK_SIZE = 64;
    private static final int LENGTH_OFFSET = 56;
    private static final char[] HEX = "0123456789abcdef".toCharArray();
    private static final int[] ROUND_CONSTANTS = {
            0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5,
            0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
            0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3,
            0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
            0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc,
            0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
            0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7,
            0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
            0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13,
            0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
            0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3,
            0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
            0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5,
            0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
            0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208,
            0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2
    };

    private final byte[] block = new byte[BLOCK_SIZE];
    private final int[] schedule = new int[64];
    private final int[] state = new int[8];
    private int blockLength;
    private long byteLength;
    private boolean finished;

    /**
     * Creates a fresh SHA-256 accumulator.
     */
    public PortableSha256() {
        state[0] = 0x6a09e667;
        state[1] = 0xbb67ae85;
        state[2] = 0x3c6ef372;
        state[3] = 0xa54ff53a;
        state[4] = 0x510e527f;
        state[5] = 0x9b05688c;
        state[6] = 0x1f83d9ab;
        state[7] = 0x5be0cd19;
    }

    /**
     * Hashes a byte array without copying it.
     *
     * @param bytes the bytes
     * @return the lowercase SHA-256 hexadecimal digest
     */
    public static String hash(byte[] bytes) {
        return new PortableSha256().update(bytes).digestHex();
    }

    /**
     * Hashes the standard UTF-8 encoding of a string without creating an intermediate byte array.
     *
     * @param value the string
     * @return the lowercase SHA-256 hexadecimal digest
     */
    public static String hashUtf8(String value) {
        return new PortableSha256().updateUtf8(value).digestHex();
    }

    /**
     * Adds all bytes in an array without copying it.
     *
     * @param bytes the bytes
     * @return this accumulator
     */
    public PortableSha256 update(byte[] bytes) {
        if (bytes == null) {
            throw new IllegalArgumentException("SHA-256 input cannot be null");
        }
        return update(bytes, 0, bytes.length);
    }

    /**
     * Adds a byte-array range without copying it.
     *
     * @param bytes the bytes
     * @param offset the first byte
     * @param length the byte count
     * @return this accumulator
     */
    public PortableSha256 update(byte[] bytes, int offset, int length) {
        requireActive();
        if (bytes == null || offset < 0 || length < 0 || offset > bytes.length - length) {
            throw new IllegalArgumentException("Invalid SHA-256 input range");
        }
        byteLength += length;
        int inputOffset = offset;
        int remaining = length;
        if (blockLength != 0) {
            int copied = Math.min(remaining, BLOCK_SIZE - blockLength);
            System.arraycopy(bytes, inputOffset, block, blockLength, copied);
            blockLength += copied;
            inputOffset += copied;
            remaining -= copied;
            if (blockLength == BLOCK_SIZE) {
                processBlock(block, 0);
                blockLength = 0;
            }
        }
        while (remaining >= BLOCK_SIZE) {
            processBlock(bytes, inputOffset);
            inputOffset += BLOCK_SIZE;
            remaining -= BLOCK_SIZE;
        }
        if (remaining != 0) {
            System.arraycopy(bytes, inputOffset, block, 0, remaining);
            blockLength = remaining;
        }
        return this;
    }

    /**
     * Adds one byte. Only the low eight bits are used.
     *
     * @param value the byte value
     * @return this accumulator
     */
    public PortableSha256 updateByte(int value) {
        requireActive();
        block[blockLength++] = (byte) value;
        byteLength++;
        if (blockLength == BLOCK_SIZE) {
            processBlock(block, 0);
            blockLength = 0;
        }
        return this;
    }

    /**
     * Adds a big-endian 32-bit value.
     *
     * @param value the value
     * @return this accumulator
     */
    public PortableSha256 updateInt(int value) {
        return updateByte(value >>> 24)
                .updateByte(value >>> 16)
                .updateByte(value >>> 8)
                .updateByte(value);
    }

    /**
     * Adds a big-endian 64-bit value.
     *
     * @param value the value
     * @return this accumulator
     */
    public PortableSha256 updateLong(long value) {
        return updateByte((int) (value >>> 56))
                .updateByte((int) (value >>> 48))
                .updateByte((int) (value >>> 40))
                .updateByte((int) (value >>> 32))
                .updateByte((int) (value >>> 24))
                .updateByte((int) (value >>> 16))
                .updateByte((int) (value >>> 8))
                .updateByte((int) value);
    }

    /**
     * Adds the standard UTF-8 encoding of a string without allocating an encoded byte array.
     * Malformed UTF-16 is replaced with {@code '?'} to match {@code String.getBytes(UTF_8)}.
     *
     * @param value the string
     * @return this accumulator
     */
    public PortableSha256 updateUtf8(String value) {
        requireActive();
        if (value == null) {
            throw new IllegalArgumentException("SHA-256 UTF-8 input cannot be null");
        }
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character <= 0x7f) {
                updateByte(character);
            } else if (character <= 0x7ff) {
                updateByte(0xc0 | character >>> 6);
                updateByte(0x80 | character & 0x3f);
            } else if (Character.isHighSurrogate(character) && i + 1 < value.length()
                    && Character.isLowSurrogate(value.charAt(i + 1))) {
                int codePoint = Character.toCodePoint(character, value.charAt(++i));
                updateByte(0xf0 | codePoint >>> 18);
                updateByte(0x80 | codePoint >>> 12 & 0x3f);
                updateByte(0x80 | codePoint >>> 6 & 0x3f);
                updateByte(0x80 | codePoint & 0x3f);
            } else if (Character.isSurrogate(character)) {
                updateByte('?');
            } else {
                updateByte(0xe0 | character >>> 12);
                updateByte(0x80 | character >>> 6 & 0x3f);
                updateByte(0x80 | character & 0x3f);
            }
        }
        return this;
    }

    /**
     * Adds a big-endian byte-length followed by a string's standard UTF-8 encoding.
     *
     * @param value the string
     * @return this accumulator
     */
    public PortableSha256 updateSizedUtf8(String value) {
        updateInt(utf8Length(value));
        return updateUtf8(value);
    }

    /**
     * Finishes this accumulator and returns its lowercase hexadecimal digest.
     *
     * @return the SHA-256 digest
     */
    public String digestHex() {
        finish();
        char[] hex = new char[64];
        int output = 0;
        for (int value : state) {
            for (int shift = 28; shift >= 0; shift -= 4) {
                hex[output++] = HEX[value >>> shift & 15];
            }
        }
        return new String(hex);
    }

    private void finish() {
        requireActive();
        long bitLength = byteLength << 3;
        block[blockLength++] = (byte) 0x80;
        if (blockLength > LENGTH_OFFSET) {
            while (blockLength < BLOCK_SIZE) {
                block[blockLength++] = 0;
            }
            processBlock(block, 0);
            blockLength = 0;
        }
        while (blockLength < LENGTH_OFFSET) {
            block[blockLength++] = 0;
        }
        for (int shift = 56; shift >= 0; shift -= 8) {
            block[blockLength++] = (byte) (bitLength >>> shift);
        }
        processBlock(block, 0);
        blockLength = 0;
        finished = true;
    }

    private void processBlock(byte[] input, int offset) {
        for (int i = 0; i < 16; i++) {
            int index = offset + i * 4;
            schedule[i] = (input[index] & 255) << 24
                    | (input[index + 1] & 255) << 16
                    | (input[index + 2] & 255) << 8
                    | input[index + 3] & 255;
        }
        for (int i = 16; i < 64; i++) {
            int first = schedule[i - 15];
            int second = schedule[i - 2];
            int sigma0 = Integer.rotateRight(first, 7) ^ Integer.rotateRight(first, 18) ^ first >>> 3;
            int sigma1 = Integer.rotateRight(second, 17) ^ Integer.rotateRight(second, 19) ^ second >>> 10;
            schedule[i] = schedule[i - 16] + sigma0 + schedule[i - 7] + sigma1;
        }

        int a = state[0];
        int b = state[1];
        int c = state[2];
        int d = state[3];
        int e = state[4];
        int f = state[5];
        int g = state[6];
        int h = state[7];
        for (int i = 0; i < 64; i++) {
            int sum1 = Integer.rotateRight(e, 6) ^ Integer.rotateRight(e, 11) ^ Integer.rotateRight(e, 25);
            int choice = e & f ^ ~e & g;
            int temporary1 = h + sum1 + choice + ROUND_CONSTANTS[i] + schedule[i];
            int sum0 = Integer.rotateRight(a, 2) ^ Integer.rotateRight(a, 13) ^ Integer.rotateRight(a, 22);
            int majority = a & b ^ a & c ^ b & c;
            int temporary2 = sum0 + majority;
            h = g;
            g = f;
            f = e;
            e = d + temporary1;
            d = c;
            c = b;
            b = a;
            a = temporary1 + temporary2;
        }
        state[0] += a;
        state[1] += b;
        state[2] += c;
        state[3] += d;
        state[4] += e;
        state[5] += f;
        state[6] += g;
        state[7] += h;
    }

    private static int utf8Length(String value) {
        if (value == null) {
            throw new IllegalArgumentException("SHA-256 UTF-8 input cannot be null");
        }
        long length = 0;
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character <= 0x7f || Character.isSurrogate(character)) {
                if (Character.isHighSurrogate(character) && i + 1 < value.length()
                        && Character.isLowSurrogate(value.charAt(i + 1))) {
                    length += 4;
                    i++;
                } else {
                    length++;
                }
            } else if (character <= 0x7ff) {
                length += 2;
            } else {
                length += 3;
            }
        }
        if (length > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("SHA-256 sized UTF-8 input is too large");
        }
        return (int) length;
    }

    private void requireActive() {
        if (finished) {
            throw new IllegalStateException("SHA-256 digest is already finished");
        }
    }
}
