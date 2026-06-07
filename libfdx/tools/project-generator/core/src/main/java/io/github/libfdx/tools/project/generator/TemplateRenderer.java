package io.github.libfdx.tools.project.generator;

import java.util.Map;

final class TemplateRenderer {
    String render(String template, Map<String, String> values) {
        String result = template != null ? template : "";
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String key = "{{" + entry.getKey() + "}}";
            String value = entry.getValue() != null ? entry.getValue() : "";
            result = result.replace(key, value);
        }
        return result;
    }
}
