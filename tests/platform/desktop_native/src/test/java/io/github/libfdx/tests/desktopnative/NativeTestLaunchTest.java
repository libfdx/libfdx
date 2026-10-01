package io.github.libfdx.tests.desktopnative;

import io.github.libfdx.testsupport.desktopc.DesktopCTestLauncherArgs;
import io.github.libfdx.testsupport.nativeprocess.NativeTestLaunchArguments;
import io.github.libfdx.testsupport.nativeprocess.NativeTestLaunchHandler;
import io.github.libfdx.testsupport.nativeprocess.RecordingProcesses;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

final class NativeTestLaunchTest {
    @Test
    void forwardsLauncherOptionsWithoutChooserIdentityOrFiniteFrameLimit() {
        String[] names = {"libfdx.test.name", "libfdx.test.frames", "libfdx.test.width", "libfdx.test.height",
                "libfdx.test.vsync", "libfdx.test.uiScale", "libfdx.test.uiDebugLines", "libfdx.test.modelAsset"};
        String[] old = new String[names.length];
        for (int i = 0; i < names.length; i++) old[i] = System.getProperty(names[i]);
        try {
            System.setProperty(names[0], "selector");
            System.setProperty(names[1], "99");
            System.setProperty(names[2], "1024");
            System.setProperty(names[3], "768");
            System.setProperty(names[4], "false");
            System.setProperty(names[5], "1.5");
            System.setProperty(names[6], "true");
            System.setProperty(names[7], "models/caf\u00e9 with spaces.glb");
            String[] child = NativeTestLaunchArguments.forTest("CircleTest");
            assertEquals("CircleTest", child[0]);
            assertEquals("0", child[1]);
            var tokens = java.util.List.of(child);
            assertTrue(tokens.contains("-Dlibfdx.test.width=1024"));
            assertTrue(tokens.contains("-Dlibfdx.test.height=768"));
            assertTrue(tokens.contains("-Dlibfdx.test.vsync=false"));
            assertTrue(tokens.contains("-Dlibfdx.test.uiScale=1.5"));
            assertTrue(tokens.contains("-Dlibfdx.test.uiDebugLines=true"));
            assertTrue(tokens.contains("-Dlibfdx.test.modelAsset=models/caf\u00e9 with spaces.glb"));
            assertFalse(tokens.stream().anyMatch(value -> value.startsWith("-Dlibfdx.test.name=")
                    || value.startsWith("-Dlibfdx.test.frames=")));
            System.clearProperty(names[0]); System.clearProperty(names[1]);
            var parsed = DesktopCTestLauncherArgs.apply(child);
            assertEquals("CircleTest", parsed.testName());
            assertEquals(0, parsed.frames());
            assertEquals(1024, parsed.width("CircleTest"));
            assertEquals("auto", NativeTestLaunchArguments.forTest("auto")[0]);
            assertThrows(IllegalArgumentException.class, () -> NativeTestLaunchArguments.forTest("selector"));
        } finally {
            for (int i = 0; i < names.length; i++) {
                if (old[i] == null) System.clearProperty(names[i]); else System.setProperty(names[i], old[i]);
            }
        }
    }

    @Test
    void encodesUtf8TokensIncludingQuotesEmptyArgumentsAndTrailingBackslashes() {
        String[] tokens = {"spaces here", "quote\"inside", "tail\\\\", "", "caf\u00e9 \u2603"};
        byte[] encoded = NativeTestLaunchArguments.encode(tokens);
        var decoded = new ArrayList<String>();
        int start = 0;
        for (int i = 0; i < encoded.length; i++) {
            if (encoded[i] == 0) {
                decoded.add(new String(encoded, start, i - start, StandardCharsets.UTF_8));
                start = i + 1;
            }
        }
        assertArrayEquals(tokens, decoded.toArray(new String[0]));
        assertThrows(IllegalArgumentException.class, () -> NativeTestLaunchArguments.encode(new String[] {"bad\0token"}));
    }

    @Test
    void eachSelectionLaunchesAChildAndTrackingIsDisposedOnce() {
        var processes = new RecordingProcesses();
        var handler = new NativeTestLaunchHandler("gl", processes);
        assertFalse(handler.hasActiveLaunch());
        assertTrue(handler.launch("CircleTest", "gl"));
        assertTrue(handler.hasActiveLaunch());
        assertTrue(handler.launch("FontTest", "gl"));
        assertEquals(2, processes.launches.size());
        assertFalse(handler.launch("CircleTest", "vulkan"));
        processes.result = 5;
        assertFalse(handler.launch("CircleTest", "gl"));
        handler.dispose(); handler.dispose();
        assertEquals(1, processes.disposals);
        assertFalse(handler.hasActiveLaunch());
        assertFalse(handler.launch("CircleTest", "gl"));
    }
}
