package io.github.libfdx.backend.cshared;

import org.teavm.backend.c.TeaVMCHost;
import org.teavm.model.FieldReference;
import org.teavm.model.MethodReference;
import org.teavm.model.Program;
import org.teavm.model.ValueType;
import org.teavm.model.Variable;
import org.teavm.model.instructions.BinaryInstruction;
import org.teavm.model.instructions.BinaryOperation;
import org.teavm.model.instructions.BranchingCondition;
import org.teavm.model.instructions.BranchingInstruction;
import org.teavm.model.instructions.ExitInstruction;
import org.teavm.model.instructions.GetFieldInstruction;
import org.teavm.model.instructions.IntegerConstantInstruction;
import org.teavm.model.instructions.InvocationType;
import org.teavm.model.instructions.InvokeInstruction;
import org.teavm.model.instructions.MonitorExitInstruction;
import org.teavm.model.instructions.NumericOperandType;
import org.teavm.model.instructions.PutFieldInstruction;
import org.teavm.vm.spi.TeaVMHost;
import org.teavm.vm.spi.TeaVMPlugin;

/** Compatibility fixes for the compiler and class library used by the desktop C backend. */
public final class CCompatibilityPlugin implements TeaVMPlugin {
    @Override
    public void install(TeaVMHost host) {
        if (host.getExtension(TeaVMCHost.class) == null) return;
        host.add(
                (type, context) -> {
                    for (var method : type.getMethods()) {
                        var program = method.getProgram();
                        if (program == null) continue;
                        if (type.getName().equals("org.teavm.runtime.GC")) {
                            if (method.getName().equals("markObjectData")) markMonitor(program);
                            if (method.getName().equals("updatePointers")) relocateMonitor(program);
                            if (method.getName().equals("processDirectBuffers"))
                                fixBufferLiveness(program);
                            if (method.getName().equals("freeBufferContent"))
                                fixBufferRelease(program);
                        }
                        if ((type.getName().equals("java.lang.Object")
                                        || type.getName()
                                                .equals("org.teavm.classlib.java.lang.TObject"))
                                && method.getName().equals("cloneLowLevel")) {
                            fixArrayCloneSize(program);
                        }
                        for (int block = 0; block < program.basicBlockCount(); block++) {
                            for (var instruction : program.basicBlockAt(block)) {
                                if (instruction instanceof InvokeInstruction call
                                        && call.getMethod()
                                                .getClassName()
                                                .equals(
                                                        CGarbageCollectionSupport.class
                                                                .getName())) {
                                    bindCollectorCall(call);
                                }
                                if (type.getName()
                                                .equals(
                                                        "org.teavm.backend.c.runtime.fs.CVirtualFileAccessor")
                                        && method.getName().equals("skip")
                                        && instruction instanceof InvokeInstruction call
                                        && call.getMethod()
                                                .getClassName()
                                                .equals(
                                                        "org.teavm.backend.c.runtime.fs.CFileSystem")
                                        && call.getMethod().getName().equals("seek")) {
                                    // InputStream.skip is relative to the current position.
                                    // Keep absolute seek unchanged and retain the library's error
                                    // behavior.
                                    var mode = program.createVariable();
                                    var relative = new IntegerConstantInstruction();
                                    relative.setReceiver(mode);
                                    relative.setConstant(1);
                                    relative.setLocation(call.getLocation());
                                    call.insertPrevious(relative);
                                    var arguments =
                                            call.getArguments()
                                                    .toArray(org.teavm.model.Variable[]::new);
                                    arguments[1] = mode;
                                    call.setArguments(arguments);
                                }
                                if (!(instruction instanceof MonitorExitInstruction exit)) continue;
                                // The 0.16.0-dev-5 data-flow builder marks the exception slot,
                                // instead of this monitor operand, as escaping. An explicit
                                // use preserves its dependency node and is inlined away later.
                                var keep = new InvokeInstruction();
                                keep.setType(InvocationType.SPECIAL);
                                keep.setMethod(
                                        new MethodReference(
                                                CMonitorSupport.class,
                                                "keep",
                                                Object.class,
                                                void.class));
                                keep.setArguments(exit.getObjectRef());
                                keep.setLocation(exit.getLocation());
                                exit.insertPrevious(keep);
                            }
                        }
                    }
                });
    }

    private static void fixBufferLiveness(Program program) {
        var headers = new java.util.HashMap<Variable, Variable>();
        var markedMasks = new java.util.HashSet<Variable>();
        for (var block : program.getBasicBlocks()) {
            for (var instruction : block) {
                if (instruction instanceof GetFieldInstruction field
                        && field.getField().getFieldName().equals("classReference"))
                    headers.put(field.getReceiver(), field.getInstance());
                if (instruction instanceof IntegerConstantInstruction constant
                        && constant.getConstant() == Integer.MIN_VALUE)
                    markedMasks.add(constant.getReceiver());
            }
        }
        for (var block : program.getBasicBlocks()) {
            for (var instruction : block) {
                if (instruction instanceof BinaryInstruction binary
                        && binary.getOperation() == BinaryOperation.AND
                        && headers.containsKey(binary.getFirstOperand())
                        && markedMasks.contains(binary.getSecondOperand())) {
                    // Old-generation buffers remain live during a young collection.
                    var live = new InvokeInstruction();
                    live.setType(InvocationType.SPECIAL);
                    live.setMethod(
                            new MethodReference(
                                    org.teavm.runtime.GC.class,
                                    "isMarked",
                                    org.teavm.runtime.RuntimeObject.class,
                                    boolean.class));
                    live.setArguments(headers.get(binary.getFirstOperand()));
                    live.setReceiver(binary.getReceiver());
                    live.setLocation(binary.getLocation());
                    binary.replace(live);
                }
                if (instruction instanceof PutFieldInstruction field
                        && field.getField().getFieldName().equals("nextRef")) {
                    // Rebuilt weak-list links must participate in pointer relocation.
                    var dirty = new InvokeInstruction();
                    dirty.setType(InvocationType.SPECIAL);
                    dirty.setMethod(
                            new MethodReference(
                                    org.teavm.runtime.GC.class,
                                    "makeInvalid",
                                    org.teavm.runtime.RuntimeObject.class,
                                    void.class));
                    dirty.setArguments(field.getInstance());
                    dirty.setLocation(field.getLocation());
                    field.insertNext(dirty);
                }
            }
        }
    }

    private static void fixBufferRelease(Program program) {
        for (var block : program.getBasicBlocks()) {
            if (!(block.getLastInstruction() instanceof BranchingInstruction branch)) continue;
            if (branch.getCondition() != BranchingCondition.NULL
                    && branch.getCondition() != BranchingCondition.NOT_NULL) continue;
            var nullTarget =
                    branch.getCondition() == BranchingCondition.NULL
                            ? branch.getConsequent()
                            : branch.getAlternative();
            for (var instruction : nullTarget) {
                if (instruction instanceof InvokeInstruction call
                        && call.getMethod().getClassName().equals("org.teavm.runtime.GC")
                        && call.getMethod().getName().equals("free")
                        && call.getArguments().get(0) == branch.getOperand()) {
                    branch.setCondition(
                            branch.getCondition() == BranchingCondition.NULL
                                    ? BranchingCondition.NOT_NULL
                                    : BranchingCondition.NULL);
                    break;
                }
            }
        }
    }

    private static void bindCollectorCall(InvokeInstruction call) {
        String name = call.getMethod().getName();
        String target =
                switch (name) {
                    case "enqueueMonitor" -> "enqueueMark";
                    case "relocation" -> "getRelocation";
                    case "relocated" -> "updatePointer";
                    default -> null;
                };
        if (target == null) return;
        var signature = call.getMethod().getSignature();
        if (name.equals("relocation"))
            signature[signature.length - 1] = ValueType.object("org.teavm.runtime.Relocation");
        call.setMethod(new MethodReference("org.teavm.runtime.GC", target, signature));
    }

    private static void markMonitor(Program program) {
        var monitorIsYoung = program.createVariable();
        var call = new InvokeInstruction();
        call.setType(InvocationType.SPECIAL);
        call.setMethod(
                new MethodReference(
                        CGarbageCollectionSupport.class,
                        "markMonitor",
                        org.teavm.runtime.RuntimeObject.class,
                        boolean.class));
        call.setArguments(program.variableAt(1));
        call.setReceiver(monitorIsYoung);
        program.basicBlockAt(0).getFirstInstruction().insertPrevious(call);
        for (var block : program.getBasicBlocks()) {
            if (!(block.getLastInstruction() instanceof ExitInstruction exit)) continue;
            var combined = program.createVariable();
            var either = new BinaryInstruction(BinaryOperation.OR, NumericOperandType.INT);
            either.setFirstOperand(exit.getValueToReturn());
            either.setSecondOperand(monitorIsYoung);
            either.setReceiver(combined);
            exit.insertPrevious(either);
            exit.setValueToReturn(combined);
        }
    }

    private static void relocateMonitor(Program program) {
        var call = new InvokeInstruction();
        call.setType(InvocationType.SPECIAL);
        call.setMethod(
                new MethodReference(
                        CGarbageCollectionSupport.class,
                        "relocateMonitor",
                        org.teavm.runtime.RuntimeObject.class,
                        void.class));
        call.setArguments(program.variableAt(2));
        program.basicBlockAt(0).getFirstInstruction().insertPrevious(call);
    }

    private static void fixArrayCloneSize(Program program) {
        Variable arrayClass = null;
        for (int block = 0; block < program.basicBlockCount(); block++) {
            for (var instruction : program.basicBlockAt(block)) {
                if (instruction instanceof InvokeInstruction call
                        && call.getMethod().getClassName().equals("org.teavm.runtime.RuntimeClass")
                        && call.getMethod().getName().equals("getClass")) {
                    arrayClass = call.getReceiver();
                }
            }
        }
        if (arrayClass == null) return;
        for (int block = 0; block < program.basicBlockCount(); block++) {
            for (var instruction : program.basicBlockAt(block)) {
                if (!(instruction instanceof InvokeInstruction call)
                        || !call.getMethod().getClassName().equals("org.teavm.runtime.RuntimeClass")
                        || !call.getMethod().getName().equals("isPrimitive")
                        || call.getArguments().size() != 1
                        || call.getArguments().get(0) != arrayClass) continue;
                // The affected class library tests the array class, which is never
                // primitive, and copies pointer-sized elements over a smaller allocation.
                // Match that exact operand so a corrected library remains unchanged.
                var component = program.createVariable();
                var load = new GetFieldInstruction();
                load.setInstance(arrayClass);
                load.setField(new FieldReference("org.teavm.runtime.RuntimeClass", "itemType"));
                load.setFieldType(ValueType.object("org.teavm.runtime.RuntimeClass"));
                load.setReceiver(component);
                load.setLocation(call.getLocation());
                call.insertPrevious(load);
                call.setArguments(component);
            }
        }
    }
}
