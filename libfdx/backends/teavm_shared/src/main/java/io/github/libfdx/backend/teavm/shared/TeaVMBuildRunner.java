package io.github.libfdx.backend.teavm.shared;

import org.teavm.backend.javascript.JSModuleType;
import org.teavm.diagnostics.ProblemProvider;
import org.teavm.tooling.EmptyTeaVMToolLog;
import org.teavm.tooling.TeaVMProblemRenderer;
import org.teavm.tooling.TeaVMSourceFilePolicy;
import org.teavm.tooling.TeaVMTargetType;
import org.teavm.tooling.TeaVMToolLog;
import org.teavm.tooling.builder.BuildException;
import org.teavm.tooling.builder.BuildResult;
import org.teavm.tooling.builder.BuildStrategy;
import org.teavm.tooling.builder.InProcessBuildStrategy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class TeaVMBuildRunner {
    private TeaVMBuildRunner() {
    }

    public static Set<Path> build(Request request, Consumer<BuildStrategy> configureTarget, String failureMessage) {
        TeaVMToolLog actualLog = request.log != null ? request.log : new EmptyTeaVMToolLog();
        BuildStrategy strategy = new InProcessBuildStrategy();
        strategy.init();
        strategy.setLog(actualLog);
        strategy.setTargetType(request.targetType);
        strategy.setMainClass(request.mainClass);
        if (request.entryPointName != null) {
            strategy.setEntryPointName(request.entryPointName);
        }
        strategy.setClassPathEntries(request.classpath.stream().map(Path::toString).toList());
        strategy.setTargetFileName(request.targetFileName);
        strategy.setTargetDirectory(request.targetDirectory.toString());
        strategy.setOptimizationLevel(request.optimization.teaVMOptimizationLevel());
        strategy.setObfuscated(request.obfuscated);
        strategy.setStrict(request.strict);
        strategy.setJsModuleType(JSModuleType.NONE);
        strategy.setDebugInformationGenerated(request.debugInformation);
        strategy.setSourceMapsFileGenerated(request.sourceMaps);
        strategy.setSourceFilesCopied(request.sourceFilesCopied);
        strategy.setSourceFilePolicy(request.sourceFilesCopied
                ? TeaVMSourceFilePolicy.COPY
                : TeaVMSourceFilePolicy.LINK_LOCAL_FILES);
        strategy.setFastDependencyAnalysis(request.fastDependencyAnalysis);
        strategy.setIncremental(request.incremental);
        strategy.setProperties(request.properties);
        strategy.setClassesToPreserve(new String[0]);
        if (request.cacheDirectory != null) {
            strategy.setCacheDirectory(request.cacheDirectory.toString());
        }
        configureTarget.accept(strategy);
        try {
            BuildResult result = strategy.build();
            ProblemProvider problems = result.getProblems();
            TeaVMProblemRenderer.describeProblems(result.getCallGraph(), problems, actualLog);
            if (!problems.getSevereProblems().isEmpty()) {
                throw new BuilderException(failureMessage + ": TeaVM reported "
                        + problems.getSevereProblems().size() + " severe problem(s)");
            }
            return generatedFiles(request.targetDirectory);
        } catch (BuildException error) {
            throw new BuilderException(failureMessage, error);
        } catch (IOException error) {
            throw new BuilderException("Could not inspect TeaVM output in " + request.targetDirectory, error);
        }
    }

    private static Set<Path> generatedFiles(Path targetDirectory) throws IOException {
        if (!Files.exists(targetDirectory)) {
            return Set.of();
        }
        try (Stream<Path> stream = Files.walk(targetDirectory)) {
            return stream.filter(Files::isRegularFile)
                    .map(path -> path.toAbsolutePath().normalize())
                    .collect(Collectors.toUnmodifiableSet());
        }
    }

    public static final class Request {
        private final TeaVMTargetType targetType;
        private final List<Path> classpath;
        private final Path targetDirectory;
        private final Path cacheDirectory;
        private final String mainClass;
        private final String targetFileName;
        private final String entryPointName;
        private final TeaVMOptimization optimization;
        private final Properties properties;
        private final TeaVMToolLog log;
        private final boolean obfuscated;
        private final boolean strict;
        private final boolean debugInformation;
        private final boolean sourceMaps;
        private final boolean sourceFilesCopied;
        private final boolean fastDependencyAnalysis;
        private final boolean incremental;

        public Request(TeaVMTargetType targetType, List<Path> classpath, Path targetDirectory, Path cacheDirectory,
                String mainClass, String targetFileName, String entryPointName, TeaVMOptimization optimization,
                Properties properties, TeaVMToolLog log, boolean obfuscated, boolean strict, boolean debugInformation,
                boolean sourceMaps, boolean sourceFilesCopied, boolean fastDependencyAnalysis, boolean incremental) {
            this.targetType = targetType;
            this.classpath = List.copyOf(classpath);
            this.targetDirectory = targetDirectory;
            this.cacheDirectory = cacheDirectory;
            this.mainClass = mainClass;
            this.targetFileName = targetFileName;
            this.entryPointName = entryPointName;
            this.optimization = optimization;
            this.properties = properties;
            this.log = log;
            this.obfuscated = obfuscated;
            this.strict = strict;
            this.debugInformation = debugInformation;
            this.sourceMaps = sourceMaps;
            this.sourceFilesCopied = sourceFilesCopied;
            this.fastDependencyAnalysis = fastDependencyAnalysis;
            this.incremental = incremental;
        }
    }
}
