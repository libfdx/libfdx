package io.github.libfdx.backend.web.tooling;

import java.io.File;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.util.Arrays;
import org.teavm.tooling.TeaVMTool;
import org.teavm.tooling.TeaVMTargetType;

/** Build-only compiler; not part of the backend's published Java API. */
public final class PreparationWorkerCompiler {
    public static void main(String[] args) throws Exception {
        var classpath = Arrays.stream(args[1].split(java.util.regex.Pattern.quote(File.pathSeparator)))
                .map(File::new).toList();
        var urls = new java.net.URL[classpath.size()];
        for (int i = 0; i < urls.length; i++) urls[i] = classpath.get(i).toURI().toURL();
        try (var loader = new URLClassLoader(urls, PreparationWorkerCompiler.class.getClassLoader())) {
            var tool = new TeaVMTool();
            tool.setClassLoader(loader); tool.setClassPath(classpath);
            String main = args.length > 2 ? args[2] : "io.github.libfdx.backend.web.internal.PreparationWorkerMain";
            String file = args.length > 3 ? args[3] : "io/github/libfdx/backend/web/internal/worker.js";
            File output = new File(args[0], file);
            Files.createDirectories(output.toPath().getParent());
            tool.setMainClass(main);
            tool.setEntryPointName("main"); tool.setTargetType(TeaVMTargetType.JAVASCRIPT);
            tool.setTargetDirectory(output.getParentFile()); tool.setTargetFileName(output.getName());
            tool.setObfuscated(true); tool.generate();
            if (!tool.getProblemProvider().getSevereProblems().isEmpty())
                throw new IllegalStateException(main + " worker compilation failed: " + tool.getProblemProvider().getSevereProblems());
        }
    }
}

