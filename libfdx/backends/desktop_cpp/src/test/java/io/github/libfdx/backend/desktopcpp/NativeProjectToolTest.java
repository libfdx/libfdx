package io.github.libfdx.backend.desktopcpp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.github.xpenatan.jnative.BuildType;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class NativeProjectToolTest {
    @Test
    void rejectsUnsupportedRequestVersionsAndActionsBeforeBuilding() {
        Properties request = new Properties();
        assertThrows(IllegalArgumentException.class, () -> NativeProjectTool.execute(request));
        request.setProperty("formatVersion", "1");
        request.setProperty("action", "unknown");
        assertThrows(IllegalArgumentException.class, () -> NativeProjectTool.execute(request));
    }

    @Test
    void preservesBuildTypeToolchainAndExactCmakeArguments() {
        Properties request = compilerRequest();
        request.setProperty("cmakeArguments.count", "1");
        request.setProperty("cmakeArguments.0", "-DUSER_PATH=C:/a directory/with spaces");
        request.setProperty("cmakeBuildArguments.count", "2");
        request.setProperty("cmakeBuildArguments.0", "--parallel");
        request.setProperty("cmakeBuildArguments.1", "3");
        var configured = NativeProjectTool.compiler(request)
                .mainClass("example.Main")
                .classpath(Path.of("build/classes"))
                .buildRoot(Path.of("build/native-tool-test"))
                .request();

        assertEquals(BuildType.RELEASE, configured.buildType());
        assertEquals("custom cmake", configured.nativeOptions().cmake());
        assertEquals("Ninja", configured.nativeOptions().generator());
        assertEquals(Duration.ofMinutes(9), configured.nativeOptions().timeout());
        assertEquals(List.of("-DUSER_PATH=C:/a directory/with spaces"), configured.nativeOptions().cmakeArguments());
        assertEquals(List.of("--parallel", "3"), configured.nativeOptions().cmakeBuildArguments());
    }

    @Test
    void rejectsInvalidCompilerOptionsBeforeInvokingCmake() {
        Properties request = compilerRequest();
        request.setProperty("buildTimeoutMinutes", "0");
        assertThrows(IllegalArgumentException.class, () -> NativeProjectTool.compiler(request));
        request.setProperty("buildTimeoutMinutes", "9");
        request.setProperty("cmakeArguments.count", "-1");
        assertThrows(IllegalArgumentException.class, () -> NativeProjectTool.compiler(request));
        request.setProperty("cmakeArguments.count", "1");
        assertThrows(IllegalArgumentException.class, () -> NativeProjectTool.compiler(request));
    }

    private static Properties compilerRequest() {
        Properties request = new Properties();
        request.setProperty("buildType", "release");
        request.setProperty("buildTimeoutMinutes", "9");
        request.setProperty("cmakeExecutable", "custom cmake");
        request.setProperty("generator", "Ninja");
        request.setProperty("cmakeArguments.count", "0");
        request.setProperty("cmakeBuildArguments.count", "0");
        return request;
    }
}
