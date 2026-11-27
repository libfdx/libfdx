package io.github.libfdx.backend.web;

import java.util.Arrays;
import org.teavm.dependency.AbstractDependencyListener;
import org.teavm.dependency.DependencyAgent;
import org.teavm.jso.impl.JSOPlugin;
import org.teavm.model.ClassHolder;
import org.teavm.model.ClassHolderTransformerContext;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodReference;
import org.teavm.model.Program;
import org.teavm.model.ValueType;
import org.teavm.model.instructions.ConstructInstruction;
import org.teavm.model.instructions.ExitInstruction;
import org.teavm.model.instructions.InvocationType;
import org.teavm.model.instructions.InvokeInstruction;
import org.teavm.model.instructions.StringConstantInstruction;
import org.teavm.platform.metadata.ResourceArray;
import org.teavm.platform.plugin.MetadataRegistration;
import org.teavm.vm.spi.Before;
import org.teavm.vm.spi.TeaVMHost;
import org.teavm.vm.spi.TeaVMPlugin;

/**
 * Represents a web tea VM plugin.
 *
 * @author xpenatan
 */
@Before(JSOPlugin.class)
public final class WebTeaVMPlugin implements TeaVMPlugin {
    /**
     * Runs the install step.
     *
     * @param host the host
     */
    @Override
    public void install(TeaVMHost host) {
        host.add((cls, context) -> {
            if (cls.getName().equals("com.github.xpenatan.jParser.loader.JParserLibraryLoader")) {
                bindModuleInitialization(cls, context);
            }
            bindPreparationDefaults(cls);
            if (cls.getName().equals("io.github.libfdx.backend.web.WebRuntimeShaderCompiler")) {
                var method = cls.getMethod(new MethodDescriptor("compiledIdentity", ValueType.object("java.lang.String")));
                var program = new Program(); program.createVariable();
                var result = program.createVariable(); var block = program.createBasicBlock();
                var value = new StringConstantInstruction();
                value.setConstant(TeaVMAssetProperties.compilerIdentity(host.getProperties()));
                value.setReceiver(result); block.add(value);
                var exit = new ExitInstruction(); exit.setValueToReturn(result); block.add(exit);
                method.setProgram(program);
            }
        });
        // The loader transform must precede JS interop. Look up the metadata service after all plugins install.
        host.add(new AbstractDependencyListener() {
            @Override
            public void started(DependencyAgent agent) {
                MetadataRegistration registration = agent.getService(MetadataRegistration.class);
                if (registration != null) {
                    registration.register(new MethodReference(WebGeneratedAssets.class, "assets", ResourceArray.class),
                            new WebAssetMetadataGenerator());
                }
            }

        });
    }

    /** Forward module factory rejections to jParser's existing script-load listener when WebGPU is packaged. */
    private static void bindModuleInitialization(ClassHolder cls, ClassHolderTransformerContext context) {
        String bridge = "io.github.libfdx.graphics.wgpu.internal.WebModuleInitialization";
        context.getIncrementalCache().addDependencies(cls.getName(), bridge);
        if (context.getHierarchy().getClassSource().get(bridge) == null) return;

        var listenerType = ValueType.object("com.github.xpenatan.jParser.loader.JParserLibraryLoaderListener");
        var nameType = ValueType.parse(String.class);
        var initialize = new MethodReference(cls.getName(), "initializeModule", nameType, ValueType.VOID);
        var checked = new MethodReference(bridge, "initialize", nameType, listenerType, ValueType.VOID);
        for (var method : cls.getMethods()) {
            var program = method.getProgram();
            int listener = Arrays.asList(method.getParameterTypes()).indexOf(listenerType);
            if (program == null || listener < 0) continue;
            for (var block : program.getBasicBlocks()) {
                for (var instruction : block) {
                    if (instruction instanceof InvokeInstruction call && initialize.equals(call.getMethod())) {
                        call.setArguments(call.getArguments().get(0), program.variableAt(listener + 1));
                        call.setMethod(checked);
                    }
                }
            }
        }
    }

    /** Bind optional framework hooks only when their owning classes are present in a web build.
     * Generated code calls the backend directly; no runtime discovery or registry is needed.
     * String type names keep the compiler adapter usable without the optional G3D artifact. */
    private static void bindPreparationDefaults(ClassHolder cls) {
        var context = ValueType.object("io.github.libfdx.assets.AssetLoadContext");
        String worker = "io.github.libfdx.backend.web.WebAssetPreparation";
        switch (cls.getName()) {
            case "io.github.libfdx.graphics.meshoptimizer.ModelLodExecutors" -> {
                var method = cls.getMethod(new MethodDescriptor("create", ValueType.INTEGER,
                        ValueType.object("io.github.libfdx.graphics.meshoptimizer.ModelLodExecutor")));
                var program = new Program(); program.createVariable();
                var count = program.createVariable(); var result = program.createVariable();
                var block = program.createBasicBlock();
                String type = "io.github.libfdx.backend.web.WebModelLodExecutor";
                var create = new ConstructInstruction(); create.setType(type); create.setReceiver(result); block.add(create);
                var init = new InvokeInstruction(); init.setType(InvocationType.SPECIAL);
                init.setMethod(new MethodReference(type, "<init>", ValueType.INTEGER, ValueType.VOID));
                init.setInstance(result); init.setArguments(count); block.add(init);
                var exit = new ExitInstruction(); exit.setValueToReturn(result); block.add(exit);
                method.setProgram(program);
            }
            case "io.github.libfdx.backend.web.WebModelLodSource" -> {
                var method = cls.getMethod(new MethodDescriptor("source", ValueType.object("java.lang.String")));
                var program = new Program(); program.createVariable();
                var result = program.createVariable(); var block = program.createBasicBlock();
                var source = new StringConstantInstruction(); source.setConstant(WebModelLodSource.source());
                source.setReceiver(result); block.add(source);
                var exit = new ExitInstruction(); exit.setValueToReturn(result); block.add(exit);
                method.setProgram(program);
            }
            case "io.github.libfdx.backend.web.WebWorkerSource" -> {
                var method = cls.getMethod(new MethodDescriptor("source", ValueType.object("java.lang.String")));
                var program = new Program(); program.createVariable();
                var result = program.createVariable(); var block = program.createBasicBlock();
                var source = new StringConstantInstruction(); source.setConstant(WebWorkerSource.source());
                source.setReceiver(result); block.add(source);
                var exit = new ExitInstruction(); exit.setValueToReturn(result); block.add(exit);
                method.setProgram(program);
            }
            case "io.github.libfdx.assets.loaders.ImageDecoder" -> bindManagerWorker(cls,
                    new MethodDescriptor("platformDecoder", context,
                            ValueType.object("io.github.libfdx.assets.loaders.ImageDecoder")), worker, context);
            case "io.github.libfdx.graphics.g3d.G3DAssetLoaders" -> bindManagerWorker(cls,
                    new MethodDescriptor("defaultMipmaps", context,
                            ValueType.object("io.github.libfdx.graphics.TextureMipmapPreparer")), worker, context);
            case "io.github.libfdx.graphics.g3d.ModelShaderPlan" -> {
                var method = cls.getMethod(new MethodDescriptor("defaultSources",
                        ValueType.object("io.github.libfdx.graphics.g3d.StandardPbrSourcePreparer$Owned")));
                if (method == null) throw new IllegalStateException("Missing ModelShaderPlan.defaultSources hook");
                String sourceWorker = "io.github.libfdx.backend.web.WebPbrSourcePreparation";
                var program = new Program(); program.createVariable();
                var result = program.createVariable(); var block = program.createBasicBlock();
                var create = new ConstructInstruction(); create.setType(sourceWorker); create.setReceiver(result);
                block.add(create);
                var init = new InvokeInstruction(); init.setType(InvocationType.SPECIAL);
                init.setMethod(new MethodReference(sourceWorker, "<init>", ValueType.VOID)); init.setInstance(result);
                block.add(init);
                var exit = new ExitInstruction(); exit.setValueToReturn(result); block.add(exit);
                method.setProgram(program);
            }
            default -> { }
        }
    }

    private static void bindManagerWorker(ClassHolder cls, MethodDescriptor hook, String worker, ValueType context) {
        var method = cls.getMethod(hook);
        if (method == null) throw new IllegalStateException("Missing preparation hook: " + cls.getName() + "." + hook);
        var program = new Program(); program.createVariable();
        var argument = program.createVariable(); var result = program.createVariable();
        var block = program.createBasicBlock();
        var call = new InvokeInstruction(); call.setType(InvocationType.SPECIAL);
        call.setMethod(new MethodReference(worker, "forManager", context, ValueType.object(worker)));
        call.setArguments(argument); call.setReceiver(result); block.add(call);
        var exit = new ExitInstruction(); exit.setValueToReturn(result); block.add(exit);
        method.setProgram(program);
    }
}
