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
    private static final String[] JAVA_KEYWORDS = {
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
            "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float",
            "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long", "native",
            "new", "package", "private", "protected", "public", "return", "short", "static", "strictfp",
            "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void",
            "volatile", "while"
    };

    private final List<String> errors;

    private ProjectValidationResult(List<String> errors) {
        this.errors = Collections.unmodifiableList(new ArrayList<String>(errors));
    }

    /**
     * Creates a project validation result.
     *
     * @param settings the settings
     * @return a new project validation result
     */
    public static ProjectValidationResult validate(ProjectGenerationSettings settings) {
        ArrayList<String> errors = new ArrayList<String>();
        if (!safeProjectName(settings.projectName())) {
            errors.add("Project name must start with a letter or digit and contain only letters, digits, '-' or '_'.");
        }
        if (!validPackageName(settings.packageName())) {
            errors.add("Package name must contain valid Java package segments.");
        }
        if (!validJavaIdentifier(settings.applicationClassName())) {
            errors.add("ECS project class name must be a valid Java class identifier.");
        }
        if (!validJavaIdentifier(settings.desktopLauncherClassName())) {
            errors.add("Desktop launcher class name must be a valid Java class identifier.");
        }
        if (settings.libfdxVersion().trim().length() == 0) {
            errors.add("libfdx version cannot be empty.");
        }
        if (!settings.desktopPlatform()) {
            errors.add("At least one generated platform must be selected.");
        }
        return new ProjectValidationResult(errors);
    }

    /**
     * Returns the valid.
     *
     * @return true if valid succeeds or is active; false otherwise
     */
    public boolean valid() {
        return errors.isEmpty();
    }

    /**
     * Returns the errors.
     *
     * @return the errors
     */
    public List<String> errors() {
        return errors;
    }

    /**
     * Returns the joined errors.
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

    private static boolean validPackageName(String value) {
        if (value == null || value.length() == 0 || value.startsWith(".") || value.endsWith(".")) {
            return false;
        }
        String[] segments = value.split("\\.");
        for (int i = 0; i < segments.length; i++) {
            if (!validJavaIdentifier(segments[i])) {
                return false;
            }
        }
        return true;
    }

    private static boolean validJavaIdentifier(String value) {
        if (value == null || value.length() == 0 || !Character.isJavaIdentifierStart(value.charAt(0))) {
            return false;
        }
        for (int i = 1; i < value.length(); i++) {
            if (!Character.isJavaIdentifierPart(value.charAt(i))) {
                return false;
            }
        }
        return !isKeyword(value);
    }

    private static boolean isKeyword(String value) {
        for (int i = 0; i < JAVA_KEYWORDS.length; i++) {
            if (JAVA_KEYWORDS[i].equals(value)) {
                return true;
            }
        }
        return false;
    }
}
