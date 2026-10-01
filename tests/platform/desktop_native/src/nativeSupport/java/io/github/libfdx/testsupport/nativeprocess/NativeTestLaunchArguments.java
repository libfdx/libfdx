package io.github.libfdx.testsupport.nativeprocess;

import io.github.libfdx.testsupport.TestSelector;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

/** Arguments for another test in the same graphics executable; chooser identity is never inherited. */
public final class NativeTestLaunchArguments {
    private static final String[] FORWARDED_PROPERTIES = {
            "libfdx.test.vsync", "libfdx.test.width", "libfdx.test.height", "libfdx.test.maximized",
            "libfdx.test.foregroundFps", "libfdx.test.fpsLogSeconds", "libfdx.test.modelAsset",
            "libfdx.test.uiScale", "libfdx.test.safeArea", "libfdx.test.uiDebugLines", "libfdx.test.uiSection"
    };

    private NativeTestLaunchArguments() {}

    public static String[] forTest(String testName) {
        if (testName == null || testName.isEmpty() || testName.equals(TestSelector.SELECTOR_NAME)) {
            throw new IllegalArgumentException("A child launch requires a selected test");
        }
        var args = new ArrayList<String>();
        args.add(testName);
        args.add("0");
        for (String name : FORWARDED_PROPERTIES) {
            String value = System.getProperty(name);
            if (value != null && !value.trim().isEmpty()) args.add("-D" + name + "=" + value.trim());
        }
        return args.toArray(new String[0]);
    }

    public static byte[] encode(String[] arguments) {
        var tokens = new byte[arguments.length][];
        int size = 0;
        for (int i = 0; i < arguments.length; i++) {
            if (arguments[i].indexOf('\0') >= 0) throw new IllegalArgumentException("Argument contains NUL");
            tokens[i] = arguments[i].getBytes(StandardCharsets.UTF_8);
            size = Math.addExact(size, Math.addExact(tokens[i].length, 1));
        }
        byte[] packed = new byte[size];
        int offset = 0;
        for (byte[] token : tokens) {
            System.arraycopy(token, 0, packed, offset, token.length);
            offset += token.length + 1;
        }
        return packed;
    }
}
