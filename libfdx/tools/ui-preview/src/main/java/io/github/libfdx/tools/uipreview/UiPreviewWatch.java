package io.github.libfdx.tools.uipreview;

import java.io.IOException;
import java.io.Writer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/** External build/preview supervisor. Launches ordinary Java main classes; never loads user classes. */
public final class UiPreviewWatch {
    private final Path modelFile;
    private final Function<PreviewLaunch, List<String>> buildCommand;
    private final List<String> workerJvmArguments;
    private PreviewLaunch launch;
    private Process build;
    private Worker current;
    private Worker candidate;
    private long generation;
    private boolean pending;
    private long changedAt;
    private long fingerprint;
    private volatile boolean stopping;
    private Path failedDirectory;
    private boolean cleaned;
    private String lastEntryRequest = "";
    private String requestedEntry = "";
    private boolean switchPending;

    private record Worker(Process process, Path directory, long generation, long started, String requestedTarget,
                          List<String> classpath, List<String> entries, String main) { }

    UiPreviewWatch(Path modelFile, Function<PreviewLaunch, List<String>> buildCommand,
                   List<String> workerJvmArguments) throws IOException {
        this.modelFile = modelFile.toAbsolutePath().normalize();
        this.buildCommand = buildCommand;
        this.workerJvmArguments = List.copyOf(workerJvmArguments);
        launch = new PreviewLaunch(this.modelFile);
    }

    /**
     * Starts the watcher from an existing desktop launcher. JVM options selected by the
     * caller apply to each replacement worker, after the configured options.
     *
     * @param modelFile the launch metadata prepared by Gradle
     * @param workerJvmArguments additional JVM options for every worker
     * @return the running watcher process
     * @throws IOException if the watcher cannot be started
     */
    public static Process start(Path modelFile, List<String> workerJvmArguments) throws IOException {
        Path model = modelFile.toAbsolutePath().normalize();
        PreviewLaunch launch = new PreviewLaunch(model);
        Files.createDirectories(launch.session);
        Path controller = launch.session.resolve("controller.jar");
        try (var channel = FileChannel.open(launch.session.resolve("controller.lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE); var lock = channel.tryLock()) {
            if (lock == null) throw new IOException("A UI preview is already running for this module");
            Files.copy(launch.controllerClasspath(), controller, StandardCopyOption.REPLACE_EXISTING);
        }

        List<String> command = new ArrayList<>(List.of(launch.java, "-classpath", controller.toString(),
                UiPreviewWatch.class.getName(), model.toString()));
        command.addAll(workerJvmArguments);
        return new ProcessBuilder(command).directory(launch.projectDirectory.toFile())
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(launch.session.resolve("watch.log").toFile()))
                .start();
    }

    /** Tool entry point. The first argument is launch metadata, followed by optional worker JVM options. */
    public static void main(String[] args) throws Exception {
        if (args.length == 0) throw new IllegalArgumentException("Usage: UiPreviewWatch <launch.properties> [worker JVM options]");
        UiPreviewWatch watch = new UiPreviewWatch(Path.of(args[0]), PreviewLaunch::buildCommand,
                Arrays.asList(args).subList(1, args.length));
        Files.createDirectories(watch.launch.session);
        try (var channel = FileChannel.open(watch.launch.session.resolve("controller.lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE); var lock = channel.tryLock()) {
            if (lock == null) throw new IllegalStateException("A UI preview is already running for this module");
            Thread shutdown = new Thread(() -> { watch.stopping = true; watch.cleanup(); }, "ui-preview-shutdown");
            Runtime.getRuntime().addShutdownHook(shutdown);
            try { watch.run(); }
            finally { watch.stopping = true; watch.cleanup(); Runtime.getRuntime().removeShutdownHook(shutdown); }
        }
    }

    void run() throws Exception {
        Files.deleteIfExists(launch.session.resolve("stop.request"));
        fingerprint = PreviewSources.fingerprint(launch.strings("watch"));
        Files.writeString(launch.session.resolve("controller.ready"), Long.toString(ProcessHandle.current().pid()));
        status("starting", "Starting preview");
        startCandidate(null);
        while (!stopping && !Files.exists(launch.session.resolve("stop.request"))) {
            if (current != null && !current.process.isAlive()) {
                int exit = current.process.exitValue();
                if (exit == 0) break; // Closing the preview also closes the watcher.
                retainFailure(current); current = null;
                status("runtime-failed", "Preview exited " + exit + "; edit a source file to retry; see " + failedDirectory.resolve("preview.log"));
            }
            long next = PreviewSources.fingerprint(launch.strings("watch"));
            if (next != fingerprint) {
                fingerprint = next; pending = true; changedAt = System.nanoTime();
            }
            readEntryRequest();
            pollCandidate();
            if (build != null && !build.isAlive()) {
                int exit = build.exitValue(); build = null;
                if (exit != 0) status("build-failed", "Compilation failed (exit " + exit + "); previous preview retained; see build.log");
                else if (pending) status("queued", "Newer edits pending; compiling latest sources");
                else {
                    launch = new PreviewLaunch(modelFile);
                    startCandidate(null);
                }
            }
            if (pending && build == null && candidate == null
                    && TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - changedAt) >= launch.milliseconds("debounceMillis", 300)) {
                pending = false;
                build = new ProcessBuilder(buildCommand.apply(launch)).directory(launch.projectDirectory.toFile())
                        .redirectErrorStream(true).redirectOutput(launch.session.resolve("build.log").toFile()).start();
                status("building", "Compiling changed sources; current preview remains active");
            }
            if (switchPending && !pending && build == null && candidate == null && current != null) {
                startCandidate(current);
            }
            Thread.sleep(launch.milliseconds("pollMillis", 300));
        }
        status("stopped", "Preview closed");
    }

    private void startCandidate(Worker snapshot) throws IOException {
        Path directory = launch.session.resolve("generation-" + ProcessHandle.current().pid() + "-" + (++generation));
        PreviewSnapshot.deleteOwned(launch.session, directory);
        try {
            List<String> classpath = snapshot == null ? PreviewSnapshot.copy(launch, directory)
                    : PreviewSnapshot.copy(snapshot.classpath, snapshot.directory, directory);
            List<String> entries = List.of();
            String main = launch.main;
            String target = "";
            List<String> arguments = new ArrayList<>(workerJvmArguments);
            if (main.isEmpty()) {
                entries = snapshot == null ? PreviewEntries.discover(classpath, directory) : snapshot.entries;
                main = chooseEntry(entries);
                if (main.isEmpty()) {
                    PreviewSnapshot.deleteOwned(launch.session, directory);
                    switchPending = false;
                    return;
                }
                Path catalog = directory.resolve("entries.txt");
                Files.write(catalog, entries);
                arguments.add("-D" + PreviewEntries.CATALOG_PROPERTY + "=" + catalog);
                arguments.add("-D" + PreviewEntries.CLASS_PROPERTY + "=" + main);
            } else {
                target = currentTarget();
                arguments.add("-D" + PreviewSessionFiles.RESTORED_TARGET + "=" + target);
            }
            Process process = new ProcessBuilder(PreviewSnapshot.workerCommand(launch, classpath, directory,
                    generation, arguments, main)).directory(launch.workingDirectory.toFile()).redirectErrorStream(true)
                    .redirectOutput(directory.resolve("preview.log").toFile()).start();
            candidate = new Worker(process, directory, generation, System.nanoTime(), target, classpath, entries, main);
            switchPending = false;
            status("starting", "Starting generation " + generation + " (" + main + ")");
        } catch (IOException | IllegalArgumentException failure) {
            if (candidate != null) throw failure;
            PreviewSnapshot.deleteOwned(launch.session, directory);
            switchPending = false;
            status("runtime-failed", "Cannot prepare preview worker: " + failure);
        }
    }

    private String chooseEntry(List<String> entries) throws IOException {
        if (entries.isEmpty()) {
            status("target-unavailable", "No UiPreviewEntry classes found in compiled project outputs");
            return "";
        }
        if (current != null) {
            if (entries.contains(requestedEntry)) return requestedEntry;
            return entries.contains(current.main) ? current.main : entries.getFirst();
        }
        String initial = "";
        var options = new ArrayList<>(launch.strings("jvmArguments"));
        options.addAll(workerJvmArguments);
        String prefix = "-Dlibfdx.uiPreview.target=";
        for (String option : options) {
            if (option.startsWith(prefix)) initial = option.substring(prefix.length());
        }
        if (initial.isEmpty()) return entries.getFirst();
        if (entries.contains(initial)) return initial;
        status("target-unavailable", "No UiPreviewEntry named " + initial);
        return "";
    }

    private void readEntryRequest() throws IOException {
        if (current == null || current.entries.isEmpty()) return;
        Path file = current.directory.resolve(PreviewEntries.REQUEST_FILE);
        if (!Files.isRegularFile(file)) return;
        String request = Files.readString(file);
        if (request.equals(lastEntryRequest)) return;
        lastEntryRequest = request;
        int split = request.indexOf('\n');
        if (split < 0) return;
        String entry = request.substring(split + 1);
        if (current.entries.contains(entry)) {
            requestedEntry = entry;
            switchPending = true;
        }
    }

    private String currentTarget() throws IOException {
        return current == null ? "" : PreviewSessionFiles.read(current.directory);
    }

    private void pollCandidate() throws IOException, InterruptedException {
        if (candidate == null) return;
        if (Files.exists(candidate.directory.resolve("ready"))) {
            if (pending) { stop(candidate); delete(candidate); candidate = null; return; }
            if (!candidate.entries.isEmpty() && switchPending && candidate.entries.contains(requestedEntry)
                    && !candidate.main.equals(requestedEntry)) {
                Worker superseded = candidate;
                candidate = null;
                try { startCandidate(superseded); }
                finally { stop(superseded); delete(superseded); }
                return;
            }
            if (candidate.entries.isEmpty() && !candidate.requestedTarget.equals(currentTarget())) {
                stop(candidate);
                delete(candidate);
                candidate = null;
                startCandidate(null);
                return;
            }
            Worker previous = current;
            current = candidate; candidate = null;
            lastEntryRequest = "";
            requestedEntry = current.main;
            switchPending = false;
            if (previous != null) { stop(previous); delete(previous); }
            status("running", "Preview generation " + current.generation + " ready");
        } else if (!candidate.process.isAlive()) {
            int exit = candidate.process.exitValue();
            if (exit == 0 && candidate.entries.isEmpty()) { stopping = true; return; }
            long failedGeneration = candidate.generation;
            retainFailure(candidate); candidate = null;
            status("runtime-failed", "Generation " + failedGeneration + " exited " + exit
                    + "; previous preview retained; see " + failedDirectory.resolve("preview.log"));
        } else if (TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - candidate.started)
                > launch.milliseconds("startupTimeoutMillis", 30_000)) {
            stop(candidate);
            retainFailure(candidate); candidate = null;
            status("runtime-failed", "Preview startup timed out; previous preview retained; see " + failedDirectory.resolve("preview.log"));
        }
    }

    private void retainFailure(Worker worker) {
        if (failedDirectory != null && !failedDirectory.equals(worker.directory)) {
            try { PreviewSnapshot.deleteOwned(launch.session, failedDirectory); }
            catch (IOException error) { System.err.println("[ui-preview] Cannot remove failed snapshot: " + error); }
        }
        failedDirectory = worker.directory;
    }

    private void status(String state, String message) throws IOException {
        Properties status = new Properties();
        status.setProperty("state", state); status.setProperty("message", message);
        status.setProperty("updated", Instant.now().toString());
        status.setProperty("controllerPid", Long.toString(ProcessHandle.current().pid()));
        status.setProperty("generation", Long.toString(current != null ? current.generation : 0));
        status.setProperty("workerPid", Long.toString(current != null ? current.process.pid() : 0));
        status.setProperty("candidatePid", Long.toString(candidate != null ? candidate.process.pid() : 0));
        status.setProperty("buildPid", Long.toString(build != null ? build.pid() : 0));
        if (current != null) status.setProperty("workerDirectory", current.directory.toString());
        if (candidate != null) status.setProperty("candidateDirectory", candidate.directory.toString());
        if (failedDirectory != null) status.setProperty("failedDirectory", failedDirectory.toString());
        Path temporary = launch.session.resolve("status.tmp");
        try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) { status.store(writer, null); }
        PreviewSessionFiles.replace(temporary, launch.session.resolve("status.properties"));
        System.out.println("[ui-preview] " + state + ": " + message);
        System.out.flush();
    }

    private void stop(Worker worker) {
        try {
            Files.writeString(worker.directory.resolve("stop.request"), "stop\n");
            if (worker.process.waitFor(5, TimeUnit.SECONDS)) return;
        } catch (IOException ignored) { }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        worker.process.destroy();
        try { if (!worker.process.waitFor(1, TimeUnit.SECONDS)) worker.process.destroyForcibly().waitFor(5, TimeUnit.SECONDS); }
        catch (InterruptedException interrupted) { worker.process.destroyForcibly(); Thread.currentThread().interrupt(); }
    }

    private void delete(Worker worker) {
        try { PreviewSnapshot.deleteOwned(launch.session, worker.directory); }
        catch (IOException error) { System.err.println("[ui-preview] Cannot remove old snapshot: " + error); }
    }

    synchronized void cleanup() {
        if (cleaned) return;
        cleaned = true;
        if (build != null) {
            build.destroy();
            try { if (!build.waitFor(2, TimeUnit.SECONDS)) build.destroyForcibly().waitFor(2, TimeUnit.SECONDS); }
            catch (InterruptedException interrupted) { build.destroyForcibly(); Thread.currentThread().interrupt(); }
            build = null;
        }
        if (candidate != null) { stop(candidate); delete(candidate); candidate = null; }
        if (current != null) { stop(current); delete(current); current = null; }
        try { status("stopped", "Preview watcher stopped"); Files.deleteIfExists(launch.session.resolve("controller.ready")); }
        catch (IOException error) { System.err.println("[ui-preview] Cannot write final status: " + error); }
    }

}
