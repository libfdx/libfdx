package io.github.libfdx.tools.project.generator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents the result of a project validation operation.
 *
 * @author xpenatan
 */
public final class ProjectValidationResult {
    private final List<String> errors;

    private ProjectValidationResult(List<String> errors) {
        this.errors = Collections.unmodifiableList(new ArrayList<String>(errors));
    }

    /**
     * Validates the settings independent of a particular bundled-sample catalog.
     *
     * @param settings the settings
     * @return the validation result
     */
    public static ProjectValidationResult validate(ProjectGenerationSettings settings) {
        ArrayList<String> errors = new ArrayList<String>();
        if (settings == null) {
            errors.add("Project generation settings cannot be null.");
            return new ProjectValidationResult(errors);
        }
        if (!safeProjectName(settings.projectName())) {
            errors.add("Project name must start with a letter or digit and contain only letters, digits, '-' or '_'.");
        }
        if (!safePackageName(settings.packageName())) {
            errors.add("Package name must contain valid Java identifiers separated by dots.");
        }
        if (settings.sampleId() == null || settings.sampleId().trim().length() == 0) {
            errors.add("A starting point must be selected.");
        }
        if (settings.platforms().isEmpty()) {
            errors.add("Select at least one platform.");
        }
        return new ProjectValidationResult(errors);
    }

    /**
     * Returns whether validation succeeded.
     *
     * @return true when no errors were found
     */
    public boolean valid() {
        return errors.isEmpty();
    }

    /**
     * Returns the validation errors.
     *
     * @return the errors
     */
    public List<String> errors() {
        return errors;
    }

    /**
     * Returns the errors separated by newlines.
     *
     * @return the joined errors
     */
    public String joinedErrors() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < errors.size(); i++) {
            if (i > 0) {
                builder.append('\n');
            }
            builder.append(errors.get(i));
        }
        return builder.toString();
    }

    private static boolean safeProjectName(String value) {
        if (value == null || value.length() == 0 || !Character.isLetterOrDigit(value.charAt(0))) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (!Character.isLetterOrDigit(character) && character != '-' && character != '_') {
                return false;
            }
        }
        return true;
    }

    private static boolean safePackageName(String value) {
        if (value == null || value.length() == 0 || value.startsWith(".") || value.endsWith(".")) {
            return false;
        }
        String[] segments = value.split("\\.", -1);
        for (int segmentIndex = 0; segmentIndex < segments.length; segmentIndex++) {
            String segment = segments[segmentIndex];
            if (segment.length() == 0 || !Character.isJavaIdentifierStart(segment.charAt(0))
                    || javaKeyword(segment)) {
                return false;
            }
            for (int characterIndex = 1; characterIndex < segment.length(); characterIndex++) {
                if (!Character.isJavaIdentifierPart(segment.charAt(characterIndex))) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean javaKeyword(String value) {
        return "abstract".equals(value) || "assert".equals(value) || "boolean".equals(value)
                || "break".equals(value) || "byte".equals(value) || "case".equals(value)
                || "catch".equals(value) || "char".equals(value) || "class".equals(value)
                || "const".equals(value) || "continue".equals(value) || "default".equals(value)
                || "do".equals(value) || "double".equals(value) || "else".equals(value)
                || "enum".equals(value) || "extends".equals(value) || "final".equals(value)
                || "finally".equals(value) || "float".equals(value) || "for".equals(value)
                || "goto".equals(value) || "if".equals(value) || "implements".equals(value)
                || "import".equals(value) || "instanceof".equals(value) || "int".equals(value)
                || "interface".equals(value) || "long".equals(value) || "native".equals(value)
                || "new".equals(value) || "package".equals(value) || "private".equals(value)
                || "protected".equals(value) || "public".equals(value) || "return".equals(value)
                || "short".equals(value) || "static".equals(value) || "strictfp".equals(value)
                || "super".equals(value) || "switch".equals(value) || "synchronized".equals(value)
                || "this".equals(value) || "throw".equals(value) || "throws".equals(value)
                || "transient".equals(value) || "try".equals(value) || "void".equals(value)
                || "volatile".equals(value) || "while".equals(value) || "true".equals(value)
                || "false".equals(value) || "null".equals(value) || "_".equals(value);
    }
}
