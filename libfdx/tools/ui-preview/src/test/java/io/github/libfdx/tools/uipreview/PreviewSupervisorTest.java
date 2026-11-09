package io.github.libfdx.tools.uipreview;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

final class PreviewSupervisorTest {
    private static final String FIXTURE = "io.github.libfdx.tools.uipreview.support.PreviewProcessFixture";

    @Test
    void failedBuildStartupAndTimeoutRetainPreviousPreviewAndRecover() throws Exception {
        try (Session session = new Session()) {
            Properties first = session.await(status -> state(status, "running") && generation(status) == 1);
            long oldPid = Long.parseLong(first.getProperty("workerPid"));
            Path old = Path.of(first.getProperty("workerDirectory"));
            assertEquals("one", Files.readString(old.resolve("observed.txt")));
            assertEquals("selected Vulkan", Files.readString(old.resolve("observed-option.txt")));

            Files.writeString(session.root.resolve("build-fail"), "fail");
            session.edit("two");
            Properties failed = session.await(status -> state(status, "build-failed"));
            assertEquals(oldPid, Long.parseLong(failed.getProperty("workerPid")));
            assertTrue(ProcessHandle.of(oldPid).orElseThrow().isAlive());
            assertEquals("one", Files.readString(old.resolve("classpath-0/content.txt")));

            Files.delete(session.root.resolve("build-fail"));
            Files.writeString(session.root.resolve("runtime-fail"), "fail");
            session.edit("three");
            failed = session.await(status -> state(status, "runtime-failed"));
            assertEquals(oldPid, Long.parseLong(failed.getProperty("workerPid")));
            assertTrue(ProcessHandle.of(oldPid).orElseThrow().isAlive());

            Files.delete(session.root.resolve("runtime-fail"));
            session.edit("four");
            Properties recovered = session.await(status -> state(status, "running") && generation(status) >= 3);
            Path recoveredDirectory = Path.of(recovered.getProperty("workerDirectory"));
            assertEquals("four", Files.readString(recoveredDirectory.resolve("observed.txt")));
            assertEquals("selected Vulkan", Files.readString(recoveredDirectory.resolve("observed-option.txt")));
            assertFalse(ProcessHandle.of(oldPid).map(ProcessHandle::isAlive).orElse(false));
            assertFalse(Files.exists(old));
            String retainedPid = recovered.getProperty("workerPid");

            Files.writeString(session.root.resolve("no-ready"), "wait");
            session.edit("five");
            failed = session.await(status -> state(status, "runtime-failed") && status.getProperty("message").contains("timed out"));
            assertEquals(retainedPid, failed.getProperty("workerPid"));
            Files.delete(session.root.resolve("no-ready"));
            session.edit("six");
            recovered = session.await(status -> state(status, "running") && generation(status) >= 5);
            assertEquals("six", Files.readString(Path.of(recovered.getProperty("workerDirectory")).resolve("observed.txt")));
            Files.writeString(session.root.resolve("close-window"), "close");
            session.thread.join(5_000);
            assertFalse(session.thread.isAlive(), "Closing the worker should also stop its supervisor");
            assertFalse(Files.exists(session.directory.resolve("controller.ready")));
        }
    }

    @Test
    void editsDuringCompilationAreQueuedAndStopTerminatesAnActiveBuild() throws Exception {
        try (Session session = new Session()) {
            session.await(status -> state(status, "running"));
            Files.writeString(session.root.resolve("build-gate"), "hold");
            session.edit("intermediate");
            session.await(status -> state(status, "building"));
            session.edit("latest source and resource data");
            // Let the watcher observe the edit while the finite build is still held.
            Thread.sleep(200);
            Files.delete(session.root.resolve("build-gate"));
            Properties latest = session.await(status -> state(status, "running") && generation(status) >= 2);
            assertEquals("latest source and resource data", Files.readString(
                    Path.of(latest.getProperty("workerDirectory")).resolve("observed.txt")));
            long worker = Long.parseLong(latest.getProperty("workerPid"));
            Files.writeString(session.root.resolve("build-gate"), "hold");
            session.edit("stop while building");
            Properties building = session.await(status -> state(status, "building"));
            long build = Long.parseLong(building.getProperty("buildPid"));
            session.close();
            assertFalse(ProcessHandle.of(worker).map(ProcessHandle::isAlive).orElse(false));
            assertFalse(ProcessHandle.of(build).map(ProcessHandle::isAlive).orElse(false));
        }
    }

    @Test
    void refreshStartsTheLatestTargetWhenSelectionChangesDuringStartup() throws Exception {
        try (Session session = new Session()) {
            Properties first = session.await(status -> state(status, "running"));
            Path previous = Path.of(first.getProperty("workerDirectory"));
            Path selection = previous.resolve(PreviewSessionFiles.FILE_NAME);
            PreviewSessionFiles.write(selection, "editor");

            Path gate = session.root.resolve("ready-gate");
            Files.writeString(gate, "hold candidate startup");
            session.edit("new composition");
            Properties starting = session.await(status -> state(status, "starting") && generation(status) == 1);
            Path candidate = Path.of(starting.getProperty("candidateDirectory"));
            PreviewSessionFiles.write(selection, "inventory");
            Files.delete(gate);

            Properties refreshed = session.await(status -> state(status, "running") && generation(status) == 3);
            Path worker = Path.of(refreshed.getProperty("workerDirectory"));
            assertEquals("inventory", PreviewSessionFiles.read(worker));
            assertEquals("new composition", Files.readString(worker.resolve("observed.txt")));
            assertFalse(Files.exists(candidate));
        }
    }

    @Test
    void snapshotsIsolateMutableOutputsAndDeletionStaysInsideSession() throws Exception {
        try (Session session = new Session(false)) {
            PreviewLaunch launch = new PreviewLaunch(session.model);
            Path snapshot = session.directory.resolve("copy");
            List<String> paths = PreviewSnapshot.copy(launch, snapshot);
            Files.writeString(session.root.resolve("classes/content.txt"), "new compiled output");
            assertEquals("one", Files.readString(Path.of(paths.getFirst()).resolve("content.txt")));
            assertThrows(java.io.IOException.class, () -> PreviewSnapshot.deleteOwned(session.directory, session.root));
            assertThrows(java.io.IOException.class, () -> PreviewSnapshot.deleteOwned(session.directory, session.directory));
            assertTrue(Files.exists(session.root.resolve("classes/content.txt")));
            List<String> command = PreviewSnapshot.workerCommand(launch, paths, snapshot, 42, List.of(), launch.main);
            assertTrue(command.contains(FIXTURE));
            assertTrue(command.contains("-Dlibfdx.uiPreview.generation=42"));
            assertTrue(command.contains("-Dlibfdx.uiPreview.assets.0=" + session.root.resolve("assets with spaces")));
        }
    }

    @Test
    void sourceFingerprintIncludesNewRemovedAndSameLengthEditedFiles() throws Exception {
        Path root = temporary();
        try {
            List<String> roots = List.of(root.resolve("new-directory").toString());
            long missing = PreviewSources.fingerprint(roots);
            Path file = root.resolve("new-directory/data.txt");
            Files.createDirectories(file.getParent()); Files.writeString(file, "old");
            long added = PreviewSources.fingerprint(roots);
            assertNotEquals(missing, added);
            Files.writeString(file, "new");
            Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis() + 10_000));
            assertNotEquals(added, PreviewSources.fingerprint(roots));
            Files.delete(file); Files.delete(file.getParent());
            assertEquals(missing, PreviewSources.fingerprint(roots));
        } finally { PreviewSnapshot.deleteOwned(root.getParent(), root); }
    }

    private static boolean state(Properties status, String value) { return value.equals(status.getProperty("state")); }

    private static long generation(Properties status) { return Long.parseLong(status.getProperty("generation", "0")); }

    private static Path temporary() throws Exception {
        Path root = Path.of("build/tmp/preview-supervisor").toAbsolutePath();
        Files.createDirectories(root); return Files.createTempDirectory(root, "case-");
    }

    private static void values(Properties model, String name, List<String> values) {
        model.setProperty(name + ".count", Integer.toString(values.size()));
        for (int i = 0; i < values.size(); i++) model.setProperty(name + "." + i, values.get(i));
    }

    private static final class Session implements AutoCloseable {
        final Path root, directory, model;
        final UiPreviewWatch watch;
        final Thread thread;
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        boolean closed;
        Session() throws Exception { this(true); }

        Session(boolean start) throws Exception {
            root = temporary(); directory = root.resolve("session"); model = root.resolve("launch.properties");
            Files.createDirectories(directory); Files.createDirectories(root.resolve("classes"));
            Files.writeString(root.resolve("classes/content.txt"), "one"); Files.writeString(root.resolve("next.txt"), "one");
            String java = Path.of(System.getProperty("java.home"), "bin", "java" + (File.separatorChar == '\\' ? ".exe" : "")).toString();
            String fixtureClasspath = System.getProperty("preview.testClasses");
            Properties settings = new Properties();
            settings.setProperty("formatVersion", "1"); settings.setProperty("javaExecutable", java);
            settings.setProperty("mainClass", FIXTURE); settings.setProperty("prepareTask", ":prepare");
            settings.setProperty("workingDirectory", root.toString()); settings.setProperty("projectDirectory", root.toString());
            settings.setProperty("sessionDirectory", directory.toString());
            settings.setProperty("pollMillis", "30"); settings.setProperty("debounceMillis", "50");
            settings.setProperty("startupTimeoutMillis", "2000");
            values(settings, "classpath", List.of(root.resolve("classes").toString(), fixtureClasspath));
            values(settings, "assets", List.of(root.resolve("assets with spaces").toString()));
            values(settings, "jvmArguments", List.of("-Dfixture.control=" + root, "-Dfixture.selection=configured GL"));
            values(settings, "arguments", List.of("worker")); values(settings, "watch", List.of(root.resolve("next.txt").toString()));
            try (var writer = Files.newBufferedWriter(model)) { settings.store(writer, null); }
            watch = new UiPreviewWatch(model, ignored -> List.of(java, "-Dfixture.control=" + root,
                    "-classpath", fixtureClasspath, FIXTURE, "build"), List.of("-Dfixture.selection=selected Vulkan"));
            thread = new Thread(() -> {
                try { watch.run(); } catch (Throwable error) { failure.set(error); }
                finally { watch.cleanup(); }
            });
            if (start) thread.start();
        }

        void edit(String value) throws Exception { Files.writeString(root.resolve("next.txt"), value); }

        Properties await(Predicate<Properties> predicate) throws Exception {
            long deadline = System.nanoTime() + 12_000_000_000L;
            Properties status = new Properties();
            do {
                if (failure.get() != null) throw new AssertionError("Watcher failed", failure.get());
                Path file = directory.resolve("status.properties");
                if (Files.exists(file)) {
                    status = new Properties();
                    try (Reader reader = Files.newBufferedReader(file)) { status.load(reader); }
                    if (predicate.test(status)) return status;
                }
                Thread.sleep(20);
            } while (System.nanoTime() < deadline);
            throw new AssertionError("Timed out; latest status: " + status);
        }

        public void close() throws Exception {
            if (closed) return;
            closed = true;
            Files.writeString(directory.resolve("stop.request"), "stop");
            if (thread.isAlive()) thread.join(7_000);
            assertFalse(thread.isAlive(), "Supervisor failed to stop");
            watch.cleanup();
            if (failure.get() != null) throw new AssertionError("Watcher failed", failure.get());
            PreviewSnapshot.deleteOwned(root.getParent(), root);
        }

    }
}
