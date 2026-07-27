package io.github.libfdx.graphics.shadergraph.ui;

import io.github.libfdx.graphics.shader.target.ShaderArtifactEncoding;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphId;
import io.github.libfdx.graphics.GraphicsFeature;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraph;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphComputeTechniquePass;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphComputeVariant;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphDiagnostic;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphOutput;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphParameter;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphResource;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphStaticValue;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphTechniquePass;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphVariant;
import io.github.libfdx.graphics.shadergraph.node.ShaderNode;
import io.github.libfdx.graphics.shadergraph.node.ShaderNodeProperty;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Complete inspector snapshot for graph declarations, programs, stage linkage,
 * passes, variants, capabilities, diagnostics, and translated artifacts.
 */
public final class ShaderGraphEditorInspectorModel {
    private final ShaderGraphEditorInspectorSection[] sections;

    private ShaderGraphEditorInspectorModel(
            ShaderGraphEditorInspectorSection[] sections) {
        this.sections = sections;
    }

    public static ShaderGraphEditorInspectorModel inspect(
            ShaderGraphEditorSession session,
            ShaderGraphEditorCompileSettings settings) {
        if (session == null || settings == null) {
            throw new IllegalArgumentException(
                    "Shader graph editor inspector input is incomplete");
        }
        List<ShaderGraphEditorInspectorSection> sections =
                new ArrayList<>();
        addDocument(sections, session);
        addGraph(sections, session.activeGraph(),
                session.selectedNodeId());
        addContainer(sections, session.document());
        addCompile(sections, settings, session.latestCompilation());
        return new ShaderGraphEditorInspectorModel(
                sections.toArray(
                        ShaderGraphEditorInspectorSection[]::new));
    }

    public ShaderGraphEditorInspectorSection[] sections() {
        return sections.clone();
    }

    private static void addDocument(
            List<ShaderGraphEditorInspectorSection> sections,
            ShaderGraphEditorSession session) {
        ShaderGraphEditorDocument document = session.document();
        sections.add(section("document", "Document",
                field("document.type", "Type", document.type().name(),
                        ShaderGraphEditorInspectorKind.ENUM, false),
                field("document.id", "ID", document.id(),
                        ShaderGraphEditorInspectorKind.TEXT, false),
                field("document.hash", "Semantic hash",
                        document.semanticHash(),
                        ShaderGraphEditorInspectorKind.SUMMARY, false),
                field("document.graphs", "Embedded graphs",
                        Integer.toString(document.graphs().length),
                        ShaderGraphEditorInspectorKind.NUMBER, false),
                field("document.semantic-revision", "Semantic revision",
                        Long.toString(session.semanticRevision()),
                        ShaderGraphEditorInspectorKind.NUMBER, false),
                field("document.layout-revision", "Layout revision",
                        Long.toString(session.layoutRevision()),
                        ShaderGraphEditorInspectorKind.NUMBER, false)));
    }

    private static void addGraph(
            List<ShaderGraphEditorInspectorSection> sections,
            ShaderGraph graph, String selectedNodeId) {
        sections.add(section("graph", "Graph",
                field("graph.id", "ID", graph.id().value(),
                        ShaderGraphEditorInspectorKind.TEXT, false),
                field("graph.kind", "Kind", graph.kind().name(),
                        ShaderGraphEditorInspectorKind.ENUM, false),
                field("graph.format", "Format version",
                        Integer.toString(graph.formatVersion()),
                        ShaderGraphEditorInspectorKind.NUMBER, false),
                field("graph.nodes", "Nodes",
                        Integer.toString(graph.nodes().length),
                        ShaderGraphEditorInspectorKind.NUMBER, false),
                field("graph.edges", "Connections",
                        Integer.toString(graph.edges().length),
                        ShaderGraphEditorInspectorKind.NUMBER, false)));

        List<ShaderGraphEditorInspectorField> parameters =
                new ArrayList<>();
        for (ShaderGraphParameter parameter : graph.parameters()) {
            parameters.add(field("parameter." + parameter.id(),
                    parameter.id().value(),
                    parameter.kind() + " - " + parameter.type()
                            + semantic(parameter.semantic()),
                    ShaderGraphEditorInspectorKind.TYPE, true));
        }
        sections.add(section("parameters", "Parameters", parameters));

        List<ShaderGraphEditorInspectorField> resources = new ArrayList<>();
        for (ShaderGraphResource resource : graph.resources()) {
            resources.add(field("resource." + resource.id(),
                    resource.id().value(),
                    resource.type() + (resource.bound()
                            ? " - group " + resource.group() + ", binding "
                                    + resource.binding()
                            : " - workgroup"),
                    ShaderGraphEditorInspectorKind.TYPE, true));
        }
        sections.add(section("resources", "Resources", resources));

        List<ShaderGraphEditorInspectorField> outputs = new ArrayList<>();
        for (ShaderGraphOutput output : graph.outputs()) {
            outputs.add(field("output." + output.id(),
                    output.id().value(),
                    output.type() + " <- " + output.source()
                            + semantic(output.semantic()),
                    ShaderGraphEditorInspectorKind.REFERENCE, true));
        }
        sections.add(section("outputs", "Outputs", outputs));

        if (selectedNodeId != null && !selectedNodeId.isEmpty()) {
            ShaderNode node = graph.node(
                    io.github.libfdx.graphics.shadergraph.model.ShaderGraphId
                            .of(selectedNodeId));
            if (node != null) {
                List<ShaderGraphEditorInspectorField> fields =
                        new ArrayList<>();
                fields.add(field("node.id", "ID", node.id().value(),
                        ShaderGraphEditorInspectorKind.TEXT, true));
                fields.add(field("node.definition", "Definition",
                        node.definitionId() + "@"
                                + node.definitionVersion(),
                        ShaderGraphEditorInspectorKind.REFERENCE, false));
                fields.add(field("node.inputs", "Inputs",
                        Integer.toString(node.inputs().length),
                        ShaderGraphEditorInspectorKind.NUMBER, false));
                fields.add(field("node.outputs", "Outputs",
                        Integer.toString(node.outputs().length),
                        ShaderGraphEditorInspectorKind.NUMBER, false));
                for (ShaderNodeProperty property : node.properties()) {
                    fields.add(field("node.property." + property.id(),
                            property.id().value(), value(property),
                            ShaderGraphEditorInspectorKind.TEXT, true));
                }
                sections.add(section("selected-node", "Selected Node",
                        fields));
            }
        }
    }

    private static void addContainer(
            List<ShaderGraphEditorInspectorSection> sections,
            ShaderGraphEditorDocument document) {
        if (document.program() != null) {
            sections.add(section("program", "Stage Linkage",
                    field("program.vertex-entry", "Vertex entry point",
                            document.program().vertexEntryPoint(),
                            ShaderGraphEditorInspectorKind.TEXT, true),
                    field("program.fragment-entry",
                            "Fragment entry point",
                            document.program().fragmentEntryPoint(),
                            ShaderGraphEditorInspectorKind.TEXT, true),
                    field("program.material-binding", "Material binding",
                            document.program().materialGroup() + ":"
                                    + document.program().materialBinding(),
                            ShaderGraphEditorInspectorKind.REFERENCE, true)));
        } else if (document.computeProgram() != null) {
            sections.add(section("compute-program", "Compute Entry Point",
                    field("compute.entry", "Entry point",
                            document.computeProgram().entryPoint(),
                            ShaderGraphEditorInspectorKind.TEXT, true),
                    field("compute.workgroup", "Workgroup",
                            document.computeProgram().workgroupX() + " x "
                                    + document.computeProgram().workgroupY()
                                    + " x "
                                    + document.computeProgram().workgroupZ(),
                            ShaderGraphEditorInspectorKind.NUMBER, true)));
        } else if (document.technique() != null) {
            sections.add(section("technique", "Render Technique",
                    field("technique.passes", "Passes",
                            Integer.toString(
                                    document.technique().passes().length),
                            ShaderGraphEditorInspectorKind.NUMBER, false),
                    field("technique.variants", "Variants",
                            document.technique().variantCount() + " / "
                                    + document.technique().maxVariants(),
                            ShaderGraphEditorInspectorKind.NUMBER, true)));
            for (ShaderGraphTechniquePass pass
                    : document.technique().passes()) {
                addPass(sections, pass);
            }
        } else if (document.computeTechnique() != null) {
            sections.add(section("compute-technique", "Compute Technique",
                    field("compute-technique.passes", "Passes",
                            Integer.toString(document.computeTechnique()
                                    .passes().length),
                            ShaderGraphEditorInspectorKind.NUMBER, false),
                    field("compute-technique.variants", "Variants",
                            document.computeTechnique().variantCount() + " / "
                                    + document.computeTechnique()
                                            .maxVariants(),
                            ShaderGraphEditorInspectorKind.NUMBER, true)));
            for (ShaderGraphComputeTechniquePass pass
                    : document.computeTechnique().passes()) {
                addPass(sections, pass);
            }
        }
    }

    private static void addPass(
            List<ShaderGraphEditorInspectorSection> sections,
            ShaderGraphTechniquePass pass) {
        List<ShaderGraphEditorInspectorField> fields = new ArrayList<>();
        fields.add(field("pass.target", "Target layout",
                pass.pipelineState().targetLayout().structuralKey(),
                ShaderGraphEditorInspectorKind.SUMMARY, true));
        fields.add(field("pass.primitive", "Primitive",
                pass.pipelineState().primitive().topology().name(),
                ShaderGraphEditorInspectorKind.ENUM, true));
        fields.add(field("pass.samples", "Samples",
                Integer.toString(pass.pipelineState().multisample().count()),
                ShaderGraphEditorInspectorKind.NUMBER, true));
        fields.add(field("pass.vertex-layouts", "Vertex layouts",
                Integer.toString(
                        pass.pipelineState().vertexLayouts().length),
                ShaderGraphEditorInspectorKind.NUMBER, true));
        fields.add(field("pass.default", "Default variant",
                displayKey(pass.defaultVariantKey()),
                ShaderGraphEditorInspectorKind.REFERENCE, true));
        for (ShaderGraphVariant variant : pass.variants()) {
            fields.add(field("variant." + displayKey(variant.key()),
                    "Variant " + displayKey(variant.key()),
                    variant.sourceProgram().id() + " - profiles "
                            + Arrays.toString(variant.profiles())
                            + " - features "
                            + Arrays.toString(variant.features())
                            + " - switches "
                            + staticValues(variant.staticValues())
                            + fallback(variant.fallbackKey()),
                    ShaderGraphEditorInspectorKind.SUMMARY, true));
        }
        sections.add(section("render-pass." + pass.passId(),
                "Render Pass - " + pass.passId(), fields));
    }

    private static void addPass(
            List<ShaderGraphEditorInspectorSection> sections,
            ShaderGraphComputeTechniquePass pass) {
        List<ShaderGraphEditorInspectorField> fields = new ArrayList<>();
        fields.add(field("pass.default", "Default variant",
                displayKey(pass.defaultVariantKey()),
                ShaderGraphEditorInspectorKind.REFERENCE, true));
        for (ShaderGraphComputeVariant variant : pass.variants()) {
            fields.add(field("variant." + displayKey(variant.key()),
                    "Variant " + displayKey(variant.key()),
                    variant.sourceProgram().id() + " - "
                            + variant.sourceProgram().entryPoint()
                            + " - workgroup "
                            + variant.sourceProgram().workgroupX() + "x"
                            + variant.sourceProgram().workgroupY() + "x"
                            + variant.sourceProgram().workgroupZ()
                            + " - profiles "
                            + Arrays.toString(variant.profiles())
                            + " - features "
                            + Arrays.toString(variant.features())
                            + " - switches "
                            + staticValues(variant.staticValues())
                            + fallback(variant.fallbackKey()),
                    ShaderGraphEditorInspectorKind.SUMMARY, true));
        }
        sections.add(section("compute-pass." + pass.passId(),
                "Compute Pass - " + pass.passId(), fields));
    }

    private static void addCompile(
            List<ShaderGraphEditorInspectorSection> sections,
            ShaderGraphEditorCompileSettings settings,
            ShaderGraphEditorCompilation compilation) {
        List<ShaderGraphEditorInspectorField> fields = new ArrayList<>();
        fields.add(field("compile.profile", "Profile",
                settings.profile().name(),
                ShaderGraphEditorInspectorKind.ENUM, true));
        fields.add(field("compile.target", "Target",
                settings.target().value(),
                ShaderGraphEditorInspectorKind.ENUM, true));
        fields.add(field("compile.format", "Format",
                settings.format().id(),
                ShaderGraphEditorInspectorKind.ENUM, true));
        fields.add(field("compile.environment", "Environment",
                settings.environment().id(),
                ShaderGraphEditorInspectorKind.ENUM, true));
        fields.add(field("compile.compiler", "Compiler",
                settings.compiler() != null
                        ? settings.compiler().value() : "automatic",
                ShaderGraphEditorInspectorKind.REFERENCE, true));
        fields.add(field("compile.verifier", "Verifier",
                settings.verifier() != null
                        ? settings.verifier().value() : "automatic",
                ShaderGraphEditorInspectorKind.REFERENCE, true));
        fields.add(field("compile.preview", "Preview",
                settings.previewMode().name(),
                ShaderGraphEditorInspectorKind.ENUM, true));
        if (settings.capabilities() == null) {
            fields.add(field("compile.capabilities", "Provider",
                    "No provider selected",
                    ShaderGraphEditorInspectorKind.SUMMARY, false));
        } else {
            List<String> features = new ArrayList<>();
            for (GraphicsFeature feature : GraphicsFeature.values()) {
                if (settings.capabilities().supports(feature)) {
                    features.add(feature.name());
                }
            }
            fields.add(field("compile.capabilities", "Provider features",
                    features.toString(),
                    ShaderGraphEditorInspectorKind.SUMMARY, false));
        }
        sections.add(section("compile", "Target & Capability", fields));

        if (compilation != null) {
            List<ShaderGraphEditorInspectorField> result =
                    new ArrayList<>();
            result.add(field("result.success", "Status",
                    compilation.success() ? "Success" : "Failed",
                    ShaderGraphEditorInspectorKind.SUMMARY, false));
            for (ShaderGraphDiagnostic diagnostic
                    : compilation.diagnostics()) {
                result.add(field("diagnostic." + result.size(),
                        diagnostic.severity() + " - "
                                + diagnostic.code(),
                        diagnostic.message(),
                        ShaderGraphEditorInspectorKind.SUMMARY, false));
            }
            for (ShaderGraphEditorArtifact artifact
                    : compilation.artifacts()) {
                result.add(field("artifact." + result.size(),
                        artifact.stage() + " - " + artifact.formatId(),
                        artifact.targetId() + " / "
                                + artifact.environmentId() + " / "
                                + artifact.compilerId()
                                + (artifact.verified()
                                        ? " - verified"
                                        : " - unverified"),
                        artifact.encoding()
                                == io.github.libfdx.graphics.shader.target.ShaderArtifactEncoding.TEXT
                                        ? ShaderGraphEditorInspectorKind.CODE
                                        : ShaderGraphEditorInspectorKind.SUMMARY,
                        false));
            }
            sections.add(section("compilation", "Compilation", result));
        }
    }

    private static ShaderGraphEditorInspectorSection section(String id,
            String title, ShaderGraphEditorInspectorField... fields) {
        return new ShaderGraphEditorInspectorSection(id, title, fields);
    }

    private static ShaderGraphEditorInspectorSection section(String id,
            String title, List<ShaderGraphEditorInspectorField> fields) {
        return section(id, title,
                fields.toArray(ShaderGraphEditorInspectorField[]::new));
    }

    private static ShaderGraphEditorInspectorField field(String id,
            String label, String value,
            ShaderGraphEditorInspectorKind kind, boolean editable) {
        return new ShaderGraphEditorInspectorField(id, label, value, kind,
                editable);
    }

    private static String value(ShaderNodeProperty property) {
        return switch (property.kind()) {
            case STRING -> property.stringValue();
            case INTEGER -> Long.toString(property.integerValue());
            case BOOLEAN -> Boolean.toString(property.booleanValue());
            case TYPE -> property.typeValue().toString();
            case LITERAL -> property.literalValue().toString();
            case ID_LIST -> Arrays.toString(property.idValues());
            case INTEGER_LIST -> Arrays.toString(
                    property.integerValues());
        };
    }

    private static String semantic(String value) {
        return value != null && !value.isEmpty() ? " - " + value : "";
    }

    private static String staticValues(ShaderGraphStaticValue[] values) {
        if (values.length == 0) {
            return "[]";
        }
        StringBuilder result = new StringBuilder("[");
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                result.append(", ");
            }
            result.append(values[i].parameterId()).append('=')
                    .append(values[i].boolValue());
        }
        return result.append(']').toString();
    }

    private static String fallback(String value) {
        return value != null ? " - fallback " + displayKey(value) : "";
    }

    private static String displayKey(String value) {
        return value == null || value.isEmpty() ? "<default>" : value;
    }
}
