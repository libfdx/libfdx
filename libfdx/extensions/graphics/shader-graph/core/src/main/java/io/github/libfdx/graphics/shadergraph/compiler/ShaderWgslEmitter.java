package io.github.libfdx.graphics.shadergraph.compiler;

import io.github.libfdx.graphics.shadergraph.ir.ShaderIrFunction;
import io.github.libfdx.graphics.shadergraph.ir.ShaderIrInstruction;
import io.github.libfdx.graphics.shadergraph.ir.ShaderIrModule;
import io.github.libfdx.graphics.shadergraph.ir.ShaderIrOutput;
import io.github.libfdx.graphics.shadergraph.ir.ShaderIrValue;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphBarrierScope;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphId;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphKind;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphLiteral;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphParameter;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphResource;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphStageSemantic;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphType;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphTypeKind;
import io.github.libfdx.graphics.shadergraph.model.ShaderStructField;
import io.github.libfdx.graphics.shadergraph.model.ShaderStructType;
import io.github.libfdx.graphics.shader.reflection.ShaderSamplerKind;
import io.github.libfdx.graphics.shader.reflection.ShaderResourceAccess;
import io.github.libfdx.graphics.shader.reflection.ShaderScalarType;
import io.github.libfdx.graphics.shader.reflection.ShaderTextureDimension;
import io.github.libfdx.graphics.shader.reflection.ShaderTextureSampleType;
import io.github.libfdx.graphics.shader.reflection.ShaderValueKind;
import io.github.libfdx.graphics.shader.reflection.ShaderValueType;

import java.util.ArrayList;
import java.util.List;

/**
 * Canonical WGSL emitter for typed graph IR.
 */
final class ShaderWgslEmitter {
    Result emit(ShaderIrModule module) {
        return emit(module, true);
    }

    Result emitLibrary(ShaderIrModule module) {
        return emit(module, false);
    }

    private Result emit(ShaderIrModule module, boolean entryPoints) {
        Writer writer = new Writer();
        List<ShaderStructType> structures = collectStructures(module);
        if (usesF16(module)) {
            writer.line("enable f16;");
            writer.line("");
        }
        for (ShaderStructType structure : structures) {
            emitStructure(writer, structure);
        }
        for (ShaderIrFunction function : module.functions()) {
            if (function.outputs().length > 1) {
                emitResultStructure(writer, function);
            }
        }
        emitResources(writer, module);
        for (ShaderIrFunction function : module.functions()) {
            emitFunction(writer, function);
        }
        if (entryPoints) {
            emitValidationEntryPoints(writer, module.root());
        }
        return new Result(writer.source.toString(),
                writer.spans.toArray(ShaderSourceSpan[]::new));
    }

    private static void emitStructure(Writer writer, ShaderStructType structure) {
        writer.line("struct " + typeName(structure.id()) + " {");
        for (ShaderStructField field : structure.fields()) {
            writer.line("  " + symbol(field.id()) + ": " + type(field.type()) + ",");
        }
        writer.line("}");
        writer.line("");
    }

    private static void emitResultStructure(Writer writer,
            ShaderIrFunction function) {
        writer.line("struct " + resultType(function) + " {");
        for (ShaderIrOutput output : function.outputs()) {
            writer.line("  " + symbol(output.id()) + ": "
                    + type(output.value().type()) + ",");
        }
        writer.line("}");
        writer.line("");
    }

    private static void emitResources(Writer writer, ShaderIrModule module) {
        List<String> declarations = new ArrayList<>();
        for (ShaderIrFunction function : module.functions()) {
            for (ShaderGraphResource resource : function.resources()) {
                String declaration = resourceDeclaration(resource);
                if (!declarations.contains(declaration)) {
                    declarations.add(declaration);
                }
            }
        }
        declarations.sort(null);
        for (String declaration : declarations) {
            writer.line(declaration);
        }
        if (!declarations.isEmpty()) {
            writer.line("");
        }
    }

    private static void emitFunction(Writer writer, ShaderIrFunction function) {
        StringBuilder signature = new StringBuilder("fn ")
                .append(functionName(function.graphId())).append('(');
        ShaderGraphParameter[] parameters = function.parameters();
        for (int i = 0; i < parameters.length; i++) {
            if (i > 0) {
                signature.append(", ");
            }
            signature.append(parameterSymbol(parameters[i].id())).append(": ")
                    .append(type(parameters[i].type()));
        }
        signature.append(") -> ");
        if (function.outputs().length == 1) {
            signature.append(type(function.outputs()[0].value().type()));
        } else {
            signature.append(resultType(function));
        }
        signature.append(" {");
        writer.line(signature.toString());

        List<ValueSymbol> symbols = new ArrayList<>();
        for (ShaderIrInstruction instruction : function.instructions()) {
            int firstLine = writer.lineNumber();
            emitInstruction(writer, function, instruction, symbols);
            writer.spans.add(new ShaderSourceSpan(firstLine,
                    writer.lineNumber() - 1, instruction.graphId(),
                    instruction.nodeId(), instruction.portId()));
        }

        ShaderIrOutput[] outputs = function.outputs();
        if (outputs.length == 1) {
            writer.line("  return " + find(symbols, outputs[0].value()) + ";");
        } else {
            StringBuilder result = new StringBuilder("  return ")
                    .append(resultType(function)).append('(');
            for (int i = 0; i < outputs.length; i++) {
                if (i > 0) {
                    result.append(", ");
                }
                result.append(find(symbols, outputs[i].value()));
            }
            writer.line(result.append(");").toString());
        }
        writer.line("}");
        writer.line("");
    }

    private static void emitInstruction(Writer writer, ShaderIrFunction function,
            ShaderIrInstruction instruction, List<ValueSymbol> symbols) {
        String result = valueSymbol(instruction.result().id());
        ShaderIrValue[] operands = instruction.operands();
        String expression;
        switch (instruction.opcode()) {
            case CONSTANT -> {
                ShaderGraphLiteral literal =
                        instruction.property("literal").literalValue();
                writer.line("  let " + result + ": "
                        + type(instruction.result().type()) + " = "
                        + literal(literal) + ";");
                symbols.add(new ValueSymbol(instruction.result(), result));
                return;
            }
            case PARAMETER -> {
                String parameter = instruction.property("parameter").stringValue();
                writer.line("  // parameter " + parameter);
                symbols.add(new ValueSymbol(instruction.result(),
                        parameterSymbol(ShaderGraphId.of(parameter))));
                return;
            }
            case RESOURCE -> {
                String resource = instruction.property("resource").stringValue();
                ShaderGraphResource declaration =
                        resource(function, ShaderGraphId.of(resource));
                writer.line("  // resource " + resource);
                symbols.add(new ValueSymbol(instruction.result(),
                        resourceSymbol(declaration)));
                return;
            }
            case ADD -> expression = binary(symbols, operands, "+");
            case SUBTRACT -> expression = binary(symbols, operands, "-");
            case MULTIPLY -> expression = binary(symbols, operands, "*");
            case DIVIDE -> expression = binary(symbols, operands, "/");
            case MINIMUM -> expression = call(symbols, operands, "min");
            case MAXIMUM -> expression = call(symbols, operands, "max");
            case NEGATE -> expression = "(-" + find(symbols, operands[0]) + ")";
            case ABSOLUTE -> expression = call(symbols, operands, "abs");
            case NORMALIZE -> expression = call(symbols, operands, "normalize");
            case DOT -> expression = call(symbols, operands, "dot");
            case CROSS -> expression = call(symbols, operands, "cross");
            case CLAMP -> expression = call(symbols, operands, "clamp");
            case LERP -> expression = call(symbols, operands, "mix");
            case CONSTRUCT, CONVERT -> expression =
                    type(instruction.result().type()) + '('
                            + join(symbols, operands) + ')';
            case MEMBER -> expression = find(symbols, operands[0]) + '.'
                    + safeMember(instruction.property("member").stringValue());
            case BRANCH -> {
                writer.line("  var " + result + ": "
                        + type(instruction.result().type()) + ";");
                writer.line("  if (" + find(symbols, operands[0]) + ") {");
                writer.line("    " + result + " = " + find(symbols, operands[1]) + ";");
                writer.line("  } else {");
                writer.line("    " + result + " = " + find(symbols, operands[2]) + ";");
                writer.line("  }");
                symbols.add(new ValueSymbol(instruction.result(), result));
                return;
            }
            case SWITCH -> {
                writer.line("  var " + result + ": "
                        + type(instruction.result().type()) + " = "
                        + find(symbols, operands[1]) + ";");
                writer.line("  switch (" + find(symbols, operands[0]) + ") {");
                long[] cases = instruction.property("cases").integerValues();
                boolean unsigned = operands[0].type().valueType().scalarType()
                        == ShaderScalarType.U32;
                for (int i = 0; i < cases.length; i++) {
                    writer.line("    case " + cases[i] + (unsigned ? "u" : "")
                            + ": { " + result + " = "
                            + find(symbols, operands[i + 2]) + "; }");
                }
                writer.line("    default: {}");
                writer.line("  }");
                symbols.add(new ValueSymbol(instruction.result(), result));
                return;
            }
            case LOOP -> {
                long iterations = instruction.property("iterations").integerValue();
                writer.line("  var " + result + ": "
                        + type(instruction.result().type()) + " = "
                        + find(symbols, operands[0]) + ";");
                writer.line("  for (var fdx_i: u32 = 0u; fdx_i < "
                        + iterations + "u; fdx_i = fdx_i + 1u) {");
                writer.line("    " + result + " = " + result + " + "
                        + find(symbols, operands[1]) + ";");
                writer.line("  }");
                symbols.add(new ValueSymbol(instruction.result(), result));
                return;
            }
            case TEXTURE_SAMPLE -> expression = call(symbols, operands,
                    "textureSample");
            case FUNCTION_CALL -> expression = functionName(ShaderGraphId.of(
                    instruction.property("function").stringValue())) + '('
                    + join(symbols, operands) + ')';
            case DERIVATIVE_X -> expression = call(symbols, operands, "dpdx");
            case DERIVATIVE_Y -> expression = call(symbols, operands, "dpdy");
            case CUSTOM_FUNCTION -> {
                String[] arguments = new String[operands.length];
                for (int i = 0; i < arguments.length; i++) {
                    arguments[i] = find(symbols, operands[i]);
                }
                expression = ShaderCustomWgslExpression.emit(
                        instruction.property("body").stringValue(), arguments);
            }
            case DISCARD -> {
                writer.line("  if (" + find(symbols, operands[0]) + ") {");
                writer.line("    discard;");
                writer.line("  }");
                expression = find(symbols, operands[0]);
            }
            case ATOMIC_ADD -> expression = "atomicAdd(&"
                    + indexed(symbols, operands) + ", "
                    + find(symbols, operands[2]) + ')';
            case STORAGE_LOAD -> {
                ShaderGraphType resourceType = operands[0].type();
                if (resourceType.kind()
                        == ShaderGraphTypeKind.STORAGE_TEXTURE) {
                    expression = "textureLoad("
                            + find(symbols, operands[0]) + ", "
                            + find(symbols, operands[1]) + ')';
                } else if (atomicElement(resourceType)) {
                    expression = "atomicLoad(&"
                            + indexed(symbols, operands) + ')';
                } else {
                    expression = indexed(symbols, operands);
                }
            }
            case STORAGE_STORE -> {
                ShaderGraphType resourceType = operands[0].type();
                String stored = find(symbols, operands[2]);
                if (resourceType.kind()
                        == ShaderGraphTypeKind.STORAGE_TEXTURE) {
                    writer.line("  textureStore("
                            + find(symbols, operands[0]) + ", "
                            + find(symbols, operands[1]) + ", "
                            + stored + ");");
                } else if (atomicElement(resourceType)) {
                    writer.line("  atomicStore(&"
                            + indexed(symbols, operands) + ", "
                            + stored + ");");
                } else {
                    writer.line("  " + indexed(symbols, operands) + " = "
                            + stored + ";");
                }
                expression = stored;
            }
            case BARRIER -> {
                ShaderGraphBarrierScope scope =
                        ShaderGraphBarrierScope.valueOf(
                                instruction.property("scope").stringValue()
                                        .trim().toUpperCase());
                if (scope == ShaderGraphBarrierScope.WORKGROUP
                        || scope == ShaderGraphBarrierScope
                                .WORKGROUP_AND_STORAGE) {
                    writer.line("  workgroupBarrier();");
                }
                if (scope == ShaderGraphBarrierScope.STORAGE
                        || scope == ShaderGraphBarrierScope
                                .WORKGROUP_AND_STORAGE) {
                    writer.line("  storageBarrier();");
                }
                expression = find(symbols, operands[0]);
            }
            default -> throw new IllegalStateException(
                    "Unhandled shader IR opcode: " + instruction.opcode());
        }
        writer.line("  let " + result + ": " + type(instruction.result().type())
                + " = " + expression + ";");
        symbols.add(new ValueSymbol(instruction.result(), result));
    }

    private static void emitValidationEntryPoints(Writer writer,
            ShaderIrFunction root) {
        if (root.kind() == ShaderGraphKind.COMPUTE) {
            emitComputeValidationEntryPoint(writer, root);
            return;
        }
        writer.line("struct FdxGraphVaryings {");
        writer.line("  @builtin(position) position: vec4<f32>,");
        writer.line("  @location(0) uv: vec2<f32>,");
        writer.line("}");
        writer.line("");
        writer.line("@vertex");
        writer.line("fn fdx_graph_vertex(@builtin(vertex_index) vertex_index: u32)"
                + " -> FdxGraphVaryings {");
        writer.line("  var positions = array<vec2<f32>, 3>(");
        writer.line("      vec2<f32>(-1.0, -1.0), vec2<f32>(3.0, -1.0),"
                + " vec2<f32>(-1.0, 3.0));");
        writer.line("  var output: FdxGraphVaryings;");
        writer.line("  let position = positions[vertex_index];");
        writer.line("  output.position = vec4<f32>(position, 0.0, 1.0);");
        writer.line("  output.uv = position * 0.5 + vec2<f32>(0.5);");
        writer.line("  return output;");
        writer.line("}");
        writer.line("");
        writer.line("@fragment");
        writer.line("fn fdx_graph_fragment(input: FdxGraphVaryings)"
                + " -> @location(0) vec4<f32> {");
        StringBuilder call = new StringBuilder("  let result = ")
                .append(functionName(root.graphId())).append('(');
        ShaderGraphParameter[] parameters = root.parameters();
        for (int i = 0; i < parameters.length; i++) {
            if (i > 0) {
                call.append(", ");
            }
            call.append(defaultArgument(parameters[i]));
        }
        writer.line(call.append(");").toString());
        writer.line("  return " + colorExpression(root) + ";");
        writer.line("}");
    }

    private static void emitComputeValidationEntryPoint(Writer writer,
            ShaderIrFunction root) {
        StringBuilder signature = new StringBuilder(
                "@compute @workgroup_size(1)\nfn fdx_graph_compute(");
        List<String> builtins = new ArrayList<>();
        for (ShaderGraphParameter parameter : root.parameters()) {
            String builtin = computeBuiltin(parameter.semantic());
            if (builtin != null && !builtins.contains(builtin)) {
                if (!builtins.isEmpty()) {
                    signature.append(", ");
                }
                builtins.add(builtin);
                signature.append("@builtin(").append(builtin).append(") ")
                        .append(builtin).append(": ")
                        .append(type(parameter.type()));
            }
        }
        writer.line(signature.append(") {").toString());
        StringBuilder call = new StringBuilder("  let fdx_result = ")
                .append(functionName(root.graphId())).append('(');
        ShaderGraphParameter[] parameters = root.parameters();
        for (int i = 0; i < parameters.length; i++) {
            if (i > 0) {
                call.append(", ");
            }
            String builtin = computeBuiltin(parameters[i].semantic());
            call.append(builtin != null ? builtin
                    : defaultArgument(parameters[i]));
        }
        writer.line(call.append(");").toString());
        writer.line("}");
    }

    private static String colorExpression(ShaderIrFunction root) {
        ShaderIrOutput[] outputs = root.outputs();
        if (outputs.length == 1) {
            ShaderGraphType type = outputs[0].value().type();
            if (isVector(type, 4)) {
                return "result";
            }
            if (isVector(type, 3)) {
                return "vec4<f32>(result, 1.0)";
            }
            if (isVector(type, 2)) {
                return "vec4<f32>(result, 0.0, 1.0)";
            }
            if (isFloatScalar(type)) {
                return "vec4<f32>(result, result, result, 1.0)";
            }
            return "vec4<f32>(0.0, 0.0, 0.0, 1.0)";
        }
        int color = outputIndex(outputs, "basecolor", "base_color", "color");
        int alpha = outputIndex(outputs, "alpha");
        String colorValue = color >= 0
                ? "result." + symbol(outputs[color].id())
                : "vec3<f32>(0.0)";
        if (color >= 0 && isVector(outputs[color].value().type(), 4)) {
            colorValue += ".rgb";
        } else if (color >= 0 && isFloatScalar(outputs[color].value().type())) {
            colorValue = "vec3<f32>(" + colorValue + ")";
        }
        String alphaValue = alpha >= 0
                ? "result." + symbol(outputs[alpha].id()) : "1.0";
        return "vec4<f32>(" + colorValue + ", " + alphaValue + ")";
    }

    private static String defaultArgument(ShaderGraphParameter parameter) {
        if ("uv0".equalsIgnoreCase(parameter.semantic())
                && isVector(parameter.type(), 2)) {
            return "input.uv";
        }
        return parameter.defaultValue() != null
                ? literal(parameter.defaultValue()) : zero(parameter.type());
    }

    private static String binary(List<ValueSymbol> symbols,
            ShaderIrValue[] operands, String operator) {
        return '(' + find(symbols, operands[0]) + ' ' + operator + ' '
                + find(symbols, operands[1]) + ')';
    }

    private static String call(List<ValueSymbol> symbols,
            ShaderIrValue[] operands, String function) {
        return function + '(' + join(symbols, operands) + ')';
    }

    private static String indexed(List<ValueSymbol> symbols,
            ShaderIrValue[] operands) {
        return find(symbols, operands[0]) + '['
                + find(symbols, operands[1]) + ']';
    }

    private static String join(List<ValueSymbol> symbols,
            ShaderIrValue[] operands) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < operands.length; i++) {
            if (i > 0) {
                result.append(", ");
            }
            result.append(find(symbols, operands[i]));
        }
        return result.toString();
    }

    private static String find(List<ValueSymbol> symbols, ShaderIrValue value) {
        for (ValueSymbol symbol : symbols) {
            if (symbol.value().id().equals(value.id())) {
                return symbol.symbol();
            }
        }
        throw new IllegalStateException("Missing emitted shader value " + value.id());
    }

    private static ShaderGraphResource resource(ShaderIrFunction function,
            ShaderGraphId id) {
        for (ShaderGraphResource resource : function.resources()) {
            if (resource.id().equals(id)) {
                return resource;
            }
        }
        throw new IllegalStateException("Missing emitted shader resource " + id);
    }

    static String literal(ShaderGraphLiteral literal) {
        ShaderGraphType type = literal.type();
        if (type.kind() == ShaderGraphTypeKind.VALUE
                && type.valueType().kind() == ShaderValueKind.SCALAR) {
            return scalarLiteral(type.valueType().scalarType(), literal.bits());
        }
        StringBuilder result = new StringBuilder(type(type)).append('(');
        for (int i = 0; i < literal.elementCount(); i++) {
            if (i > 0) {
                result.append(", ");
            }
            result.append(literal(literal.element(i)));
        }
        return result.append(')').toString();
    }

    private static String scalarLiteral(ShaderScalarType scalar, long bits) {
        return switch (scalar) {
            case BOOL -> bits == 0 ? "false" : "true";
            case I32 -> Integer.toString((int) bits);
            case U32 -> Long.toUnsignedString(bits & 0xffffffffL) + 'u';
            case F32 -> floatLiteral(Float.intBitsToFloat((int) bits));
            case F16 -> floatLiteral(Float.intBitsToFloat((int) bits)) + 'h';
            default -> throw new IllegalStateException(
                    "Unsupported WGSL graph scalar literal: " + scalar);
        };
    }

    private static String floatLiteral(float value) {
        if (Float.floatToRawIntBits(value) == Float.floatToRawIntBits(-0.0f)) {
            return "-0.0";
        }
        String result = Float.toString(value);
        if (result.indexOf('.') < 0 && result.indexOf('e') < 0
                && result.indexOf('E') < 0) {
            result += ".0";
        }
        return result;
    }

    static String zero(ShaderGraphType type) {
        if (type.kind() == ShaderGraphTypeKind.VALUE
                && type.valueType().kind() == ShaderValueKind.SCALAR) {
            return switch (type.valueType().scalarType()) {
                case BOOL -> "false";
                case U32 -> "0u";
                case I32 -> "0";
                case F16 -> "0.0h";
                default -> "0.0";
            };
        }
        return type(type) + "()";
    }

    static String type(ShaderGraphType type) {
        return switch (type.kind()) {
            case VALUE -> type(type.valueType());
            case STRUCT -> typeName(type.structType().id());
            case TEXTURE -> textureType(type);
            case SAMPLER -> type.samplerKind() == ShaderSamplerKind.COMPARISON
                    ? "sampler_comparison" : "sampler";
            case STORAGE_BUFFER -> "array<"
                    + type(type.elementType()) + '>';
            case STORAGE_TEXTURE -> storageTextureType(type);
            case WORKGROUP_ARRAY -> "array<"
                    + type(type.elementType()) + ", "
                    + type.elementCount() + '>';
        };
    }

    private static String type(ShaderValueType type) {
        return switch (type.kind()) {
            case SCALAR -> scalarType(type.scalarType());
            case VECTOR -> "vec" + type.rows() + '<'
                    + scalarType(type.scalarType()) + '>';
            case MATRIX -> "mat" + type.columns() + 'x' + type.rows() + '<'
                    + scalarType(type.scalarType()) + '>';
            case ARRAY -> "array<" + type(type.elementType())
                    + (type.arrayCount() < 0 ? ">"
                            : ", " + type.arrayCount() + '>');
            case ATOMIC -> "atomic<" + scalarType(type.scalarType()) + '>';
            case STRUCT, BUFFER -> sanitize(type.typeName());
            case UNKNOWN -> throw new IllegalStateException(
                    "Unknown graph value type cannot be emitted");
        };
    }

    private static String scalarType(ShaderScalarType type) {
        return switch (type) {
            case BOOL -> "bool";
            case I32 -> "i32";
            case U32 -> "u32";
            case F32 -> "f32";
            case F16 -> "f16";
            default -> throw new IllegalStateException(
                    "Unsupported WGSL scalar type: " + type);
        };
    }

    private static String textureType(ShaderGraphType type) {
        String dimension = switch (type.textureDimension()) {
            case D1 -> "1d";
            case D2 -> "2d";
            case D2_ARRAY -> "2d_array";
            case CUBE -> "cube";
            case CUBE_ARRAY -> "cube_array";
            case D3 -> "3d";
            default -> throw new IllegalStateException(
                    "Unsupported graph texture dimension: "
                            + type.textureDimension());
        };
        if (type.textureSampleType() == ShaderTextureSampleType.DEPTH) {
            return "texture_depth_" + dimension;
        }
        String scalar = switch (type.textureSampleType()) {
            case SINT -> "i32";
            case UINT -> "u32";
            default -> "f32";
        };
        return type.multisampled()
                ? "texture_multisampled_" + dimension + '<' + scalar + '>'
                : "texture_" + dimension + '<' + scalar + '>';
    }

    private static String storageTextureType(ShaderGraphType type) {
        String format = type.storageFormat().name().toLowerCase()
                .replace("_", "");
        String access = switch (type.resourceAccess()) {
            case READ -> "read";
            case WRITE -> "write";
            case READ_WRITE -> "read_write";
            default -> throw new IllegalStateException(
                    "Storage texture access is not explicit");
        };
        return "texture_storage_2d<" + format + ", " + access + '>';
    }

    private static List<ShaderStructType> collectStructures(ShaderIrModule module) {
        List<ShaderStructType> result = new ArrayList<>();
        for (ShaderIrFunction function : module.functions()) {
            for (ShaderGraphParameter parameter : function.parameters()) {
                collect(result, parameter.type());
            }
            for (ShaderIrInstruction instruction : function.instructions()) {
                collect(result, instruction.result().type());
                for (ShaderIrValue operand : instruction.operands()) {
                    collect(result, operand.type());
                }
            }
            for (ShaderGraphResource resource : function.resources()) {
                collect(result, resource.type());
            }
        }
        result.sort((left, right) -> left.id().compareTo(right.id()));
        return result;
    }

    private static void collect(List<ShaderStructType> result,
            ShaderGraphType type) {
        if (type.kind() == ShaderGraphTypeKind.STORAGE_BUFFER
                || type.kind() == ShaderGraphTypeKind.WORKGROUP_ARRAY) {
            collect(result, type.elementType());
            return;
        }
        if (type.kind() != ShaderGraphTypeKind.STRUCT) {
            return;
        }
        for (ShaderStructType existing : result) {
            if (existing.id().equals(type.structType().id())) {
                if (!existing.equals(type.structType())) {
                    throw new IllegalStateException(
                            "Conflicting shader structure definitions: "
                                    + existing.id());
                }
                return;
            }
        }
        result.add(type.structType());
        for (ShaderStructField field : type.structType().fields()) {
            collect(result, field.type());
        }
    }

    private static boolean usesF16(ShaderIrModule module) {
        for (ShaderIrFunction function : module.functions()) {
            for (ShaderGraphParameter parameter : function.parameters()) {
                if (usesF16(parameter.type())) {
                    return true;
                }
            }
            for (ShaderIrInstruction instruction : function.instructions()) {
                if (usesF16(instruction.result().type())) {
                    return true;
                }
            }
            for (ShaderGraphResource resource : function.resources()) {
                if (usesF16(resource.type())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean usesF16(ShaderGraphType type) {
        if (type.kind() == ShaderGraphTypeKind.VALUE) {
            ShaderValueType value = type.valueType();
            return value.scalarType() == ShaderScalarType.F16
                    || value.elementType() != null
                            && usesF16(ShaderGraphType.value(value.elementType()));
        }
        if (type.kind() == ShaderGraphTypeKind.STRUCT) {
            for (ShaderStructField field : type.structType().fields()) {
                if (usesF16(field.type())) {
                    return true;
                }
            }
        }
        if (type.kind() == ShaderGraphTypeKind.STORAGE_BUFFER
                || type.kind() == ShaderGraphTypeKind.WORKGROUP_ARRAY) {
            return usesF16(type.elementType());
        }
        return false;
    }

    static String functionName(ShaderGraphId id) {
        return "fdx_graph_" + sanitize(id.value());
    }

    static String resultType(ShaderIrFunction function) {
        return "FdxResult_" + sanitize(function.graphId().value());
    }

    private static String typeName(ShaderGraphId id) {
        return "FdxType_" + sanitize(id.value());
    }

    private static String parameterSymbol(ShaderGraphId id) {
        return "fdx_p_" + sanitize(id.value());
    }

    private static String resourceSymbol(int group, int binding) {
        return "fdx_resource_" + group + '_' + binding;
    }

    private static String resourceSymbol(ShaderGraphResource resource) {
        return resource.bound()
                ? resourceSymbol(resource.group(), resource.binding())
                : "fdx_workgroup_" + sanitize(resource.id().value());
    }

    private static String resourceDeclaration(
            ShaderGraphResource resource) {
        ShaderGraphType type = resource.type();
        if (type.kind() == ShaderGraphTypeKind.WORKGROUP_ARRAY) {
            return "var<workgroup> " + resourceSymbol(resource) + ": "
                    + type(type) + ";";
        }
        String prefix = "@group(" + resource.group() + ") @binding("
                + resource.binding() + ") ";
        if (type.kind() == ShaderGraphTypeKind.STORAGE_BUFFER) {
            String access = type.resourceAccess()
                    == ShaderResourceAccess.READ ? "read" : "read_write";
            return prefix + "var<storage, " + access + "> "
                    + resourceSymbol(resource) + ": " + type(type) + ";";
        }
        return prefix + "var " + resourceSymbol(resource) + ": "
                + type(type) + ";";
    }

    private static boolean atomicElement(ShaderGraphType resource) {
        return (resource.kind() == ShaderGraphTypeKind.STORAGE_BUFFER
                || resource.kind() == ShaderGraphTypeKind.WORKGROUP_ARRAY)
                && resource.elementType().kind()
                        == ShaderGraphTypeKind.VALUE
                && resource.elementType().valueType().kind()
                        == ShaderValueKind.ATOMIC;
    }

    private static String computeBuiltin(String semantic) {
        if (!ShaderGraphStageSemantic.builtin(semantic)) {
            return null;
        }
        return switch (ShaderGraphStageSemantic.builtinName(semantic)) {
            case "global_invocation_id" -> "global_invocation_id";
            case "local_invocation_id" -> "local_invocation_id";
            case "local_invocation_index" -> "local_invocation_index";
            case "workgroup_id" -> "workgroup_id";
            case "num_workgroups" -> "num_workgroups";
            default -> null;
        };
    }

    private static String valueSymbol(ShaderGraphId id) {
        return "fdx_v_" + sanitize(id.value());
    }

    static String symbol(ShaderGraphId id) {
        return "fdx_" + sanitize(id.value());
    }

    private static String safeMember(String value) {
        if (value != null && !value.isEmpty()
                && value.chars().allMatch(character ->
                        character == 'x' || character == 'y'
                                || character == 'z' || character == 'w')) {
            return value;
        }
        return symbol(ShaderGraphId.of(value));
    }

    private static String sanitize(String value) {
        StringBuilder result = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            result.append(character >= 'a' && character <= 'z'
                    || character >= 'A' && character <= 'Z'
                    || character >= '0' && character <= '9'
                    || character == '_' ? character : '_');
        }
        return result.toString();
    }

    private static boolean isVector(ShaderGraphType type, int width) {
        return type.kind() == ShaderGraphTypeKind.VALUE
                && type.valueType().kind() == ShaderValueKind.VECTOR
                && type.valueType().scalarType() == ShaderScalarType.F32
                && type.valueType().componentCount() == width;
    }

    private static boolean isFloatScalar(ShaderGraphType type) {
        return type.kind() == ShaderGraphTypeKind.VALUE
                && type.valueType().kind() == ShaderValueKind.SCALAR
                && type.valueType().scalarType() == ShaderScalarType.F32;
    }

    private static int outputIndex(ShaderIrOutput[] outputs, String... names) {
        for (int i = 0; i < outputs.length; i++) {
            String id = outputs[i].id().value().replace("-", "")
                    .replace("_", "").toLowerCase();
            for (String name : names) {
                if (id.equals(name.replace("_", "").toLowerCase())) {
                    return i;
                }
            }
        }
        return -1;
    }

    record Result(String source, ShaderSourceSpan[] sourceMap) {
    }

    private record ValueSymbol(ShaderIrValue value, String symbol) {
    }

    private static final class Writer {
        private final StringBuilder source = new StringBuilder();
        private final List<ShaderSourceSpan> spans = new ArrayList<>();
        private int line = 1;

        void line(String value) {
            source.append(value).append('\n');
            line++;
        }

        int lineNumber() {
            return line;
        }
    }
}
