package io.github.libfdx.backend.web;

import org.teavm.model.MethodReference;
import org.teavm.model.ClassHolder;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.Program;
import org.teavm.model.ValueType;
import org.teavm.model.instructions.ConstructInstruction;
import org.teavm.model.instructions.ExitInstruction;
import org.teavm.model.instructions.InvocationType;
import org.teavm.model.instructions.InvokeInstruction;
import org.teavm.model.instructions.StringConstantInstruction;
import org.teavm.platform.metadata.ResourceArray;
import org.teavm.platform.plugin.MetadataRegistration;
import org.teavm.vm.spi.TeaVMHost;
import org.teavm.vm.spi.TeaVMPlugin;

/**
 * Represents a web tea VM plugin.
 *
 * @author xpenatan
 */
public final class WebTeaVMPlugin implements TeaVMPlugin {
    /**
     * Runs the install step.
     *
     * @param host the host
     */
    @Override
    public void install(TeaVMHost host) {
        host.add((cls, context) -> {
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
        MetadataRegistration registration = host.getService(MetadataRegistration.class);
        if (registration != null) {
            registration.register(new MethodReference(WebGeneratedAssets.class, "assets", ResourceArray.class),
                    new WebAssetMetadataGenerator());
        }
    }

    /** Bind optional framework hooks only when their owning classes are present in a web build.
     * Generated code calls the backend directly; no runtime discovery or registry is needed.
     * String type names keep the compiler adapter usable without the optional G3D artifact. */
    private static void bindPreparationDefaults(ClassHolder cls) {
        var context = ValueType.object("io.github.libfdx.assets.AssetLoadContext");
        String worker = "io.github.libfdx.backend.web.WebAssetPreparation";
        switch (cls.getName()) {
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
