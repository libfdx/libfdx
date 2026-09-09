package io.github.libfdx.graphics.d3d12;

import io.github.libfdx.core.FdxException;

import java.io.IOException;
import java.io.InputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.SymbolLookup;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Properties;

/** Extracts the pinned provider resources. Native libraries remain loaded for process lifetime. */
final class D3D12DxcLibrary {
    private static final String ROOT = "/libfdx-dxc/windows-x64/";
    final SymbolLookup symbols;
    final String version;
    final String compilerIdentity;

    D3D12DxcLibrary() {
        try {
            if (!System.getProperty("os.name", "").startsWith("Windows")
                    || !(System.getProperty("os.arch", "").equals("amd64")
                    || System.getProperty("os.arch", "").equals("x86_64"))) {
                throw new FdxException("The Direct3D 12 DXC distribution requires Windows x64");
            }
            Properties properties = new Properties();
            try (InputStream input = resource("distribution.properties")) { properties.load(input); }
            version = properties.getProperty("version");
            compilerIdentity = version + ":" + properties.getProperty("dxcompiler.dll") + ":" + properties.getProperty("dxil.dll");
            Path directory = Path.of(System.getProperty("java.io.tmpdir"), "libfdx-dxc", version,
                    properties.getProperty("dxcompiler.dll")).toAbsolutePath();
            Files.createDirectories(directory);
            extract(directory, "dxil.dll", properties.getProperty("dxil.dll"));
            Path compiler = extract(directory, "dxcompiler.dll", properties.getProperty("dxcompiler.dll"));
            // DXC finds the matching validator beside itself; never manually unload either DLL.
            symbols = SymbolLookup.libraryLookup(compiler, Arena.global());
        } catch (IOException | RuntimeException error) {
            throw new FdxException("Could not load the packaged Direct3D 12 DXC compiler. "
                    + "Use the complete d3d12_core artifact and a writable java.io.tmpdir.", error);
        }
    }

    static InputStream resource(String name) {
        InputStream input = D3D12DxcLibrary.class.getResourceAsStream(ROOT + name);
        if (input == null) throw new FdxException("Missing packaged DXC resource: " + ROOT + name);
        return input;
    }

    private static Path extract(Path directory, String name, String expected) throws IOException {
        if (expected == null || !expected.matches("[0-9a-f]{64}")) {
            throw new FdxException("Invalid packaged DXC checksum for " + name);
        }
        Path target = directory.resolve(name);
        if (Files.isRegularFile(target) && expected.equals(checksum(target))) return target;
        Path temporary = Files.createTempFile(directory, name, ".tmp");
        try {
            try (InputStream input = resource(name)) { Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING); }
            if (!expected.equals(checksum(temporary))) throw new FdxException("Packaged DXC checksum mismatch: " + name);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException error) {
                // Another process may have published and loaded the same verified distribution.
                if (!Files.isRegularFile(target) || !expected.equals(checksum(target))) throw error;
            }
            return target;
        } finally { Files.deleteIfExists(temporary); }
    }

    private static String checksum(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(path)) {
                byte[] buffer = new byte[65536];
                for (int count; (count = input.read(buffer)) != -1;) digest.update(buffer, 0, count);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException error) { throw new AssertionError(error); }
    }
}
