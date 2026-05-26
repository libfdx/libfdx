package io.github.libfdx.ui;

import io.github.libfdx.files.FileHandle;
import io.github.libfdx.files.FileSystem;
import java.nio.charset.StandardCharsets;

public final class UiSkinLoader {
    private UiSkinLoader() {
    }

    public static UiTheme load(FileSystem files, String path) {
        if (files == null) {
            return UiTheme.dark();
        }
        FileHandle file = files.internal(path);
        String text = file.readString(StandardCharsets.UTF_8).join();
        return parse(UiTheme.dark(), text);
    }

    public static UiTheme parse(UiTheme base, String text) {
        UiTheme theme = base != null ? base : UiTheme.dark();
        if (text == null) {
            return theme;
        }
        String[] lines = text.split("\\r?\\n");
        for (int i = 0; i < lines.length; i++) {
            String line = stripComment(lines[i]).trim();
            if (line.length() == 0) {
                continue;
            }
            int equals = line.indexOf('=');
            if (equals <= 0) {
                continue;
            }
            String key = line.substring(0, equals).trim();
            String value = line.substring(equals + 1).trim();
            theme = apply(theme, key, value);
        }
        return theme;
    }

    private static UiTheme apply(UiTheme theme, String key, String value) {
        if (!key.startsWith("style.")) {
            return theme;
        }
        String rest = key.substring("style.".length());
        int dot = rest.indexOf('.');
        if (dot <= 0) {
            return theme;
        }
        String styleName = rest.substring(0, dot);
        String property = rest.substring(dot + 1);
        UiStyle style = theme.style(styleName);
        if (style == null) {
            style = UiStyle.style();
        }
        if ("background".equals(property)) {
            style = style.background(UiDrawable.color(color(value)));
        } else if ("foreground".equals(property)) {
            style = style.foreground(UiDrawable.color(color(value)));
        } else if ("text".equals(property)) {
            style = style.text(style.textStyle().color(color(value)));
        } else if ("padding".equals(property)) {
            style = style.padding(insets(value));
        } else if ("ninepatch".equals(property)) {
            style = style.background(UiDrawable.ninePatch(value));
        }
        return theme.style(styleName, style);
    }

    private static UiColor color(String value) {
        String hex = value;
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }
        if (hex.length() == 6) {
            hex = hex + "ff";
        }
        long rgba = Long.parseLong(hex, 16);
        return UiColor.rgba8888((int) rgba);
    }

    private static UiInsets insets(String value) {
        String[] parts = value.split(",");
        if (parts.length == 1) {
            return UiInsets.of(number(parts[0]));
        }
        if (parts.length == 2) {
            return UiInsets.of(number(parts[0]), number(parts[1]));
        }
        if (parts.length >= 4) {
            return UiInsets.of(number(parts[0]), number(parts[1]), number(parts[2]), number(parts[3]));
        }
        return UiInsets.ZERO;
    }

    private static float number(String value) {
        return Float.parseFloat(value.trim());
    }

    private static String stripComment(String line) {
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '#' && (i == 0 || Character.isWhitespace(line.charAt(i - 1)))) {
                return line.substring(0, i);
            }
        }
        return line;
    }
}
