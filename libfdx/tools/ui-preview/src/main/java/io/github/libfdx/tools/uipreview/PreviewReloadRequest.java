package io.github.libfdx.tools.uipreview;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

/** Atomic controller/host handoff. Paths refer only to completed, immutable preview snapshots. */
record PreviewReloadRequest(long revision, List<String> classpath, List<String> entries) {
    static final String ENABLED = "libfdx.uiPreview.fastReload";
    static final String REQUEST = "reload.properties";
    static final String RESPONSE = "reload-response.properties";
    static final String AVAILABLE = "reload-ready";

    void write(Path file) throws IOException {
        Properties values = new Properties();
        values.setProperty("revision", Long.toString(revision));
        list(values, "classpath", classpath);
        list(values, "entries", entries);
        write(file, values);
    }

    static PreviewReloadRequest read(Path file) throws IOException {
        Properties values = readValues(file);
        return new PreviewReloadRequest(Long.parseLong(values.getProperty("revision")),
                list(values, "classpath"), list(values, "entries"));
    }

    static void respond(Path directory, long revision, String state, String message) throws IOException {
        Properties values = new Properties();
        values.setProperty("revision", Long.toString(revision));
        values.setProperty("state", state);
        values.setProperty("message", message);
        write(directory.resolve(RESPONSE), values);
    }

    static Properties readValues(Path file) throws IOException {
        Properties values = new Properties();
        try (var reader = Files.newBufferedReader(file)) { values.load(reader); }
        return values;
    }

    static void write(Path file, Properties values) throws IOException {
        Path temporary = Files.createTempFile(file.getParent(), "reload-", ".tmp");
        try {
            try (var writer = Files.newBufferedWriter(temporary)) { values.store(writer, null); }
            PreviewSessionFiles.replace(temporary, file);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void list(Properties values, String key, List<String> list) {
        values.setProperty(key + ".count", Integer.toString(list.size()));
        for (int i = 0; i < list.size(); i++) values.setProperty(key + "." + i, list.get(i));
    }

    private static List<String> list(Properties values, String key) {
        return java.util.stream.IntStream.range(0, Integer.parseInt(values.getProperty(key + ".count")))
                .mapToObj(i -> values.getProperty(key + "." + i)).toList();
    }

}
