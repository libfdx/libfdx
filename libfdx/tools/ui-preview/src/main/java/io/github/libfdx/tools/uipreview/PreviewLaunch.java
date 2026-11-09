package io.github.libfdx.tools.uipreview;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/** Data-only launch model written by the Gradle integration; it never describes generated code. */
final class PreviewLaunch {
    final Properties values;
    final Path session;
    final Path workingDirectory;
    final Path projectDirectory;
    final String java;
    final String main;

    PreviewLaunch(Path file) throws IOException {
        values = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { values.load(reader); }
        if (!"1".equals(required("formatVersion"))) throw new IOException("Unsupported preview launch format");
        session = Path.of(required("sessionDirectory")).toAbsolutePath().normalize();
        workingDirectory = Path.of(required("workingDirectory")).toAbsolutePath().normalize();
        projectDirectory = Path.of(required("projectDirectory")).toAbsolutePath().normalize();
        java = required("javaExecutable");
        main = values.getProperty("mainClass", "").strip();
        if (!main.isEmpty() && !main.matches("[\\p{javaJavaIdentifierStart}][\\p{javaJavaIdentifierPart}]*(\\.[\\p{javaJavaIdentifierStart}][\\p{javaJavaIdentifierPart}]*)*")) {
            throw new IOException("Invalid preview main class: " + main);
        }
        if (strings("classpath").isEmpty()) throw new IOException("Preview classpath is empty");
    }

    private String required(String key) throws IOException {
        String value = values.getProperty(key);
        if (value == null || value.isBlank()) throw new IOException("Missing preview property: " + key);
        return value;
    }

    Path controllerClasspath() throws IOException {
        return Path.of(required("controllerClasspath"));
    }

    List<String> strings(String name) {
        int count = Integer.parseInt(values.getProperty(name + ".count", "0"));
        if (count < 0 || count > 100_000) throw new IllegalArgumentException("Invalid " + name + " count");
        ArrayList<String> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String value = values.getProperty(name + "." + i);
            if (value == null) throw new IllegalArgumentException("Missing " + name + "." + i);
            result.add(value);
        }
        return result;
    }

    long milliseconds(String name, long fallback) {
        long value = Long.parseLong(values.getProperty(name, Long.toString(fallback)));
        if (value < 1) throw new IllegalArgumentException(name + " must be positive");
        return value;
    }

    List<String> buildCommand() {
        ArrayList<String> command = new ArrayList<>();
        command.add(java);
        command.add("-classpath");
        command.add(projectDirectory.resolve("gradle/wrapper/gradle-wrapper.jar").toString());
        command.add("org.gradle.wrapper.GradleWrapperMain");
        command.add("--project-dir"); command.add(projectDirectory.toString());
        command.addAll(strings("buildArguments"));
        command.add(values.getProperty("prepareTask"));
        return command;
    }

}
