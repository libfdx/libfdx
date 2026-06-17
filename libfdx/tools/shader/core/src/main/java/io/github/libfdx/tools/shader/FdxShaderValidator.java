package io.github.libfdx.tools.shader;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.ShaderProfile;
import io.github.libfdx.graphics.ShaderProfileValidator;
import io.github.libfdx.graphics.ShaderValidationResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.stream.Stream;

/**
 * Represents a fdx shader validator.
 *
 * @author xpenatan
 */
public final class FdxShaderValidator {
    private static final String PROFILE_PREFIX = "@fdx.profile";

    private FdxShaderValidator() {
    }

    /**
     * Runs the validate directory step.
     *
     * @param sourceDirectory the source directory
     * @param defaultProfile the default profile
     * @return the validate directory
     */
    public static FdxShaderValidationReport validateDirectory(Path sourceDirectory, ShaderProfile defaultProfile) {
        if (sourceDirectory == null) {
            throw new FdxException("Shader source directory cannot be null");
        }
        if (!Files.isDirectory(sourceDirectory)) {
            return FdxShaderValidationReport.of(new FdxShaderValidationReport.Entry[0]);
        }
        ArrayList<FdxShaderValidationReport.Entry> entries = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(sourceDirectory)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".wgsl"))
                    .sorted()
                    .forEach(path -> entries.add(validateFile(path, defaultProfile)));
        } catch (IOException exception) {
            throw new FdxException("Could not scan shader source directory: " + sourceDirectory, exception);
        }
        return FdxShaderValidationReport.of(entries.toArray(new FdxShaderValidationReport.Entry[0]));
    }

    /**
     * Runs the validate file step.
     *
     * @param path the asset or file path
     * @param defaultProfile the default profile
     * @return the validate file
     */
    public static FdxShaderValidationReport.Entry validateFile(Path path, ShaderProfile defaultProfile) {
        try {
            String source = Files.readString(path, StandardCharsets.UTF_8);
            ShaderProfile profile = profileFromSource(source, defaultProfile);
            ShaderValidationResult result = ShaderProfileValidator.validateWgsl(profile, source);
            return FdxShaderValidationReport.Entry.of(path, profile.id(), result.diagnostics());
        } catch (IOException exception) {
            throw new FdxException("Could not read shader source: " + path, exception);
        }
    }

    /**
     * Runs the profile from source step.
     *
     * @param source the source value
     * @param defaultProfile the default profile
     * @return the profile from source
     */
    public static ShaderProfile profileFromSource(String source, ShaderProfile defaultProfile) {
        ShaderProfile fallback = defaultProfile != null ? defaultProfile : ShaderProfile.PORTABLE_WEBGPU;
        if (source == null || source.length() == 0) {
            return fallback;
        }
        String[] lines = source.split("\\R", 32);
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("//")) {
                trimmed = trimmed.substring(2).trim();
            }
            if (!trimmed.startsWith(PROFILE_PREFIX)) {
                continue;
            }
            String value = trimmed.substring(PROFILE_PREFIX.length()).trim();
            if (value.startsWith("=")) {
                value = value.substring(1).trim();
            }
            return profileFromId(value, fallback);
        }
        return fallback;
    }

    /**
     * Runs the profile from ID step.
     *
     * @param id the identifier
     * @param fallback the fallback
     * @return the profile from ID
     */
    public static ShaderProfile profileFromId(String id, ShaderProfile fallback) {
        return ShaderProfile.fromId(id, fallback);
    }
}
