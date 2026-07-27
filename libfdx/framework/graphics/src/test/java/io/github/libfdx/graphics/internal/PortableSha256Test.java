package io.github.libfdx.graphics.internal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class PortableSha256Test {
    @Test
    void matchesPublishedSha256VectorsAcrossPaddingBoundaries() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                PortableSha256.hash(new byte[0]));
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                PortableSha256.hashUtf8("abc"));
        assertEquals("248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
                PortableSha256.hashUtf8(
                        "abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq"));
    }

    @Test
    void supportsIncrementalRangesAndLongInputWithoutInputSizedWorkingStorage() {
        byte[] bytes = new byte[256];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) i;
        }
        PortableSha256 ranged = new PortableSha256()
                .update(bytes, 0, 1)
                .update(bytes, 1, 63)
                .update(bytes, 64, 65)
                .update(bytes, 129, 127);
        assertEquals("40aff2e9d2d8922e47afd4648e6967497158785fbd1da870e7110266bf944880",
                ranged.digestHex());

        PortableSha256 millionA = new PortableSha256();
        for (int i = 0; i < 1_000_000; i++) {
            millionA.updateByte('a');
        }
        assertEquals("cdc76e5c9914fb9281a1c7e284d73e67f1809a48a497200e046d39ccc7112cd0",
                millionA.digestHex());
    }

    @Test
    void utf8AndSizedUtf8MatchThePreviousJdkEncodingContract() {
        String value = "libFDX 🐆 shader π";
        assertEquals("556dc291fa9f16ea0eb407ff967db7aba78abe493cdc7c003c72165354623878",
                PortableSha256.hashUtf8(value));
        assertEquals("30c2975dd4af6adc9f4855cfeaa18140b69a7e71089f8c178eacd6038d05079f",
                new PortableSha256().updateSizedUtf8(value).digestHex());
        assertEquals("8a8de823d5ed3e12746a62ef169bcf372be0ca44f0a1236abc35df05d96928e1",
                PortableSha256.hashUtf8("\ud800"));
    }

    @Test
    void rejectsInvalidRangesAndReuseAfterFinalization() {
        assertThrows(IllegalArgumentException.class, () ->
                new PortableSha256().update(new byte[1], 1, 1));
        PortableSha256 digest = new PortableSha256();
        digest.digestHex();
        assertThrows(IllegalStateException.class, digest::digestHex);
        assertThrows(IllegalStateException.class, () -> digest.updateByte(0));
    }
}
