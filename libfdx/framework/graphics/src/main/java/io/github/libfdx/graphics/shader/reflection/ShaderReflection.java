package io.github.libfdx.graphics.shader.reflection;

import io.github.libfdx.graphics.VertexLayout;
import io.github.libfdx.graphics.shader.ShaderOverride;
import io.github.libfdx.graphics.shader.ShaderProfile;
import io.github.libfdx.graphics.shader.ShaderStage;
import io.github.libfdx.graphics.shader.target.ShaderSemanticOverlay;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.internal.PortableSha256;
import io.github.libfdx.runtime.core.shader.RuntimeShaderReflection;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;

/**
 * Immutable provider-neutral shader interface manifest.
 *
 * <p>The original {@link #of(ShaderBinding[], ShaderAttribute[])} factory remains available for
 * handwritten compatibility metadata, but it deliberately produces an incomplete manifest.
 * Physical-layout operations must require {@link #complete()}.</p>
 */
public final class ShaderReflection {
    private static final ShaderBinding[] EMPTY_BINDINGS = new ShaderBinding[0];
    private static final ShaderAttribute[] EMPTY_ATTRIBUTES = new ShaderAttribute[0];
    private static final ShaderEntryPoint[] EMPTY_ENTRY_POINTS = new ShaderEntryPoint[0];
    private static final String[] EMPTY_CAPABILITIES = new String[0];
    private static final ShaderReflection EMPTY = new Builder(ShaderProfile.PORTABLE_WEBGPU)
            .complete(false)
            .build();

    private final boolean complete;
    private final ShaderProfile profile;
    private final ShaderEntryPoint[] entryPoints;
    private final ShaderBinding[] bindings;
    private final ShaderAttribute[] attributes;
    private final String[] requiredCapabilities;
    private final String physicalHash;
    private final String fullHash;

    private ShaderReflection(Builder builder) {
        complete = builder.complete;
        profile = builder.profile != null ? builder.profile : ShaderProfile.PORTABLE_WEBGPU;
        entryPoints = cloneAndRequire(builder.entryPoints, EMPTY_ENTRY_POINTS, "entry point");
        bindings = cloneAndRequire(builder.bindings, EMPTY_BINDINGS, "binding");
        attributes = cloneAndRequire(builder.attributes, EMPTY_ATTRIBUTES, "attribute");
        requiredCapabilities = builder.requiredCapabilities != null
                ? builder.requiredCapabilities.clone() : EMPTY_CAPABILITIES;
        canonicalizeAndValidate();
        physicalHash = hash(false);
        fullHash = hash(true);
    }

    /**
     * Returns the shared empty, incomplete manifest.
     *
     * @return the empty manifest
     */
    public static ShaderReflection empty() {
        return EMPTY;
    }

    /**
     * Creates explicitly incomplete compatibility reflection for handwritten shaders.
     *
     * @param bindings the coarse bindings
     * @param attributes the host vertex attributes
     * @return the incomplete reflection
     */
    public static ShaderReflection of(ShaderBinding[] bindings, ShaderAttribute[] attributes) {
        return builder(ShaderProfile.PORTABLE_WEBGPU)
                .bindings(bindings)
                .attributes(attributes)
                .complete(false)
                .build();
    }

    /**
     * Creates a complete interface manifest.
     *
     * @param profile the required portability profile
     * @param entryPoints the entry points
     * @param bindings the complete resources
     * @param requiredCapabilities the required capability/extension IDs
     * @return the complete reflection
     */
    public static ShaderReflection complete(ShaderProfile profile, ShaderEntryPoint[] entryPoints,
            ShaderBinding[] bindings, String[] requiredCapabilities) {
        return builder(profile)
                .entryPoints(entryPoints)
                .bindings(bindings)
                .requiredCapabilities(requiredCapabilities)
                .complete(true)
                .build();
    }

    /**
     * Decodes a complete FDXI runtime reflection using the portable WebGPU profile.
     *
     * @param runtimeReflection the opaque runtime payload
     * @return the complete graphics manifest
     */
    public static ShaderReflection fromRuntime(RuntimeShaderReflection runtimeReflection) {
        return ShaderReflectionDecoder.decode(runtimeReflection, ShaderProfile.PORTABLE_WEBGPU);
    }

    /**
     * Decodes a complete FDXI runtime reflection with an explicit framework profile.
     *
     * @param runtimeReflection the opaque runtime payload
     * @param profile the framework portability profile
     * @return the complete graphics manifest
     */
    public static ShaderReflection fromRuntime(RuntimeShaderReflection runtimeReflection, ShaderProfile profile) {
        return ShaderReflectionDecoder.decode(runtimeReflection, profile);
    }

    /**
     * Creates a manifest builder.
     *
     * @param profile the required portability profile
     * @return the builder
     */
    public static Builder builder(ShaderProfile profile) {
        return new Builder(profile);
    }

    public boolean complete() {
        return complete;
    }

    public ShaderProfile profile() {
        return profile;
    }

    public ShaderEntryPoint[] entryPoints() {
        return entryPoints.clone();
    }

    public int entryPointCount() {
        return entryPoints.length;
    }

    public ShaderEntryPoint entryPoint(int index) {
        return entryPoints[index];
    }

    public ShaderEntryPoint findEntryPoint(ShaderStage stage, String name) {
        for (ShaderEntryPoint entryPoint : entryPoints) {
            if (entryPoint.stage() == stage && entryPoint.name().equals(name)) {
                return entryPoint;
            }
        }
        return null;
    }

    public ShaderEntryPoint requireEntryPoint(ShaderStage stage, String name) {
        ShaderEntryPoint entryPoint = findEntryPoint(stage, name);
        if (entryPoint == null) {
            throw new FdxException("Unknown shader entry point: " + stage + ' ' + name);
        }
        return entryPoint;
    }

    public ShaderBinding[] bindings() {
        return bindings.clone();
    }

    public int bindingCount() {
        return bindings.length;
    }

    public ShaderBinding binding(int index) {
        return bindings[index];
    }

    public ShaderBinding findBinding(int group, int binding) {
        for (ShaderBinding value : bindings) {
            if (value.group() == group && value.binding() == binding) {
                return value;
            }
        }
        return null;
    }

    public ShaderBinding requireBinding(int group, int binding) {
        ShaderBinding value = findBinding(group, binding);
        if (value == null) {
            throw new FdxException("Unknown shader binding: group " + group + " binding " + binding);
        }
        return value;
    }

    /**
     * Returns the compatibility host vertex attributes. Complete source reflection cannot infer
     * normalized host formats, so pipeline {@link VertexLayout} remains authoritative.
     *
     * @return the compatibility attributes
     */
    public ShaderAttribute[] attributes() {
        return attributes.clone();
    }

    public String[] requiredCapabilities() {
        return requiredCapabilities.clone();
    }

    public String physicalHash() {
        return physicalHash;
    }

    public String fullHash() {
        return fullHash;
    }

    /**
     * Compares only physical shader ABI facts, excluding framework semantic aliases and ownership.
     *
     * @param other the other manifest
     * @return whether the physical interfaces are equal
     */
    public boolean physicallyEquivalent(ShaderReflection other) {
        if (other == null || complete != other.complete
                || !Arrays.equals(requiredCapabilities, other.requiredCapabilities)
                || entryPoints.length != other.entryPoints.length || bindings.length != other.bindings.length) {
            return false;
        }
        for (int i = 0; i < entryPoints.length; i++) {
            if (!physicalEntryPointEquals(entryPoints[i], other.entryPoints[i])) {
                return false;
            }
        }
        for (int i = 0; i < bindings.length; i++) {
            if (!physicalBindingEquals(bindings[i], other.bindings[i])) {
                return false;
            }
        }
        return true;
    }

    /**
     * Applies validated framework semantics without changing the Tint-proven physical ABI.
     *
     * @param overlay the typed semantic overlay
     * @return a new complete reflection
     */
    public ShaderReflection withSemanticOverlay(ShaderSemanticOverlay overlay) {
        if (!complete) {
            throw new FdxException("Shader semantic overlays require complete reflection");
        }
        if (overlay == null || overlay.bindingCount() == 0) {
            return this;
        }
        ShaderBinding[] updated = bindings.clone();
        for (int overlayIndex = 0; overlayIndex < overlay.bindingCount(); overlayIndex++) {
            ShaderBindingSemantic semantic = overlay.binding(overlayIndex);
            int bindingIndex = findBindingIndex(semantic.group(), semantic.binding());
            if (bindingIndex < 0) {
                throw new FdxException("Shader semantic overlay targets an unknown binding: group "
                        + semantic.group() + " binding " + semantic.binding());
            }
            ShaderBinding source = updated[bindingIndex];
            ShaderParameterLayout layout = applyParameterSemantics(source, semantic);
            updated[bindingIndex] = source.withMetadata(semantic.stableId(), semantic.domain(),
                    semantic.updateFrequency(), layout);
        }
        ShaderReflection result = builder(profile)
                .entryPoints(entryPoints)
                .bindings(updated)
                .attributes(attributes)
                .requiredCapabilities(requiredCapabilities)
                .complete(true)
                .build();
        if (!physicalHash.equals(result.physicalHash)) {
            throw new FdxException("Shader semantic overlay changed the physical interface");
        }
        return result;
    }

    /**
     * Returns the number of distinct sampled/depth/external texture resources used by selected
     * render entry points.
     *
     * @param vertexEntryPoint the vertex entry-point name
     * @param fragmentEntryPoint the fragment entry-point name
     * @return the sampled texture count
     */
    public int sampledTextureCount(String vertexEntryPoint, String fragmentEntryPoint) {
        if (!complete) {
            int count = 0;
            for (ShaderBinding binding : bindings) {
                if (binding.type() == ShaderBindingType.TEXTURE) {
                    count++;
                }
            }
            return count;
        }
        ShaderEntryPoint vertex = requireEntryPoint(ShaderStage.VERTEX, vertexEntryPoint);
        ShaderEntryPoint fragment = requireEntryPoint(ShaderStage.FRAGMENT, fragmentEntryPoint);
        boolean[] used = new boolean[bindings.length];
        markSampledTextures(vertex, used);
        markSampledTextures(fragment, used);
        int count = 0;
        for (boolean value : used) {
            if (value) {
                count++;
            }
        }
        return count;
    }

    private void markSampledTextures(ShaderEntryPoint entryPoint, boolean[] used) {
        for (int i = 0; i < entryPoint.resourceCount(); i++) {
            ShaderResourceUse use = entryPoint.resource(i);
            int index = findBindingIndex(use.group(), use.binding());
            if (index >= 0 && isSampledTexture(bindings[index].resourceKind())) {
                used[index] = true;
            }
        }
    }

    private static boolean isSampledTexture(ShaderResourceKind kind) {
        return kind == ShaderResourceKind.SAMPLED_TEXTURE || kind == ShaderResourceKind.MULTISAMPLED_TEXTURE
                || kind == ShaderResourceKind.DEPTH_TEXTURE
                || kind == ShaderResourceKind.DEPTH_MULTISAMPLED_TEXTURE
                || kind == ShaderResourceKind.EXTERNAL_TEXTURE;
    }

    private ShaderParameterLayout applyParameterSemantics(ShaderBinding binding, ShaderBindingSemantic semantic) {
        if (semantic.parameterCount() == 0) {
            return binding.bufferLayout();
        }
        ShaderParameterLayout source = binding.bufferLayout();
        if (source == null) {
            throw new FdxException("Shader semantic overlay contains members for a non-buffer binding: "
                    + binding.name());
        }
        boolean[] found = new boolean[semantic.parameterCount()];
        ShaderParameter[] roots = new ShaderParameter[source.parameterCount()];
        for (int i = 0; i < roots.length; i++) {
            ShaderParameter root = source.parameter(i);
            roots[i] = applyParameterSemantics(root, canonicalParameterPath("", root), semantic, found);
        }
        for (int i = 0; i < found.length; i++) {
            if (!found[i]) {
                throw new FdxException("Shader semantic overlay targets an unknown parameter path: "
                        + semantic.parameter(i).path());
            }
        }
        return ShaderParameterLayout.of(source.minimumBindingSize(), source.alignment(), roots);
    }

    private ShaderParameter applyParameterSemantics(ShaderParameter source, String canonicalPath,
            ShaderBindingSemantic semantic, boolean[] found) {
        String stableId = source.stableId();
        ShaderParameterDomain domain = source.domain();
        ShaderUpdateFrequency frequency = source.updateFrequency();
        for (int i = 0; i < semantic.parameterCount(); i++) {
            ShaderParameterSemantic parameterSemantic = semantic.parameter(i);
            if (parameterSemantic.path().equals(canonicalPath)) {
                found[i] = true;
                stableId = parameterSemantic.stableId();
                domain = parameterSemantic.domain();
                frequency = parameterSemantic.updateFrequency();
            }
        }
        ShaderParameter[] members = new ShaderParameter[source.memberCount()];
        for (int i = 0; i < members.length; i++) {
            ShaderParameter member = source.member(i);
            members[i] = applyParameterSemantics(member, canonicalParameterPath(canonicalPath, member), semantic,
                    found);
        }
        return source.withMetadata(stableId, domain, frequency, members);
    }

    private static String canonicalParameterPath(String parentPath, ShaderParameter parameter) {
        String sourcePath = parameter.sourcePath();
        if (parentPath.isEmpty() || sourcePath.startsWith(parentPath)
                || sourcePath.indexOf('.') >= 0 || sourcePath.contains("[]")) {
            return sourcePath;
        }
        return parentPath + '.' + sourcePath;
    }

    private static boolean physicalBindingEquals(ShaderBinding first, ShaderBinding second) {
        if (first.group() != second.group() || first.binding() != second.binding()
                || first.resourceKind() != second.resourceKind()
                || !first.visibility().equals(second.visibility()) || first.access() != second.access()
                || first.bindingArrayCount() != second.bindingArrayCount()
                || first.minimumBindingSize() != second.minimumBindingSize()
                || first.sizeWithoutPadding() != second.sizeWithoutPadding()
                || first.alignment() != second.alignment()
                || first.textureDimension() != second.textureDimension()
                || first.textureSampleType() != second.textureSampleType()
                || first.samplerKind() != second.samplerKind()
                || first.storageFormat() != second.storageFormat()
                || first.inputAttachmentIndex() != second.inputAttachmentIndex()) {
            return false;
        }
        ShaderParameterLayout firstLayout = first.bufferLayout();
        ShaderParameterLayout secondLayout = second.bufferLayout();
        return firstLayout == null ? secondLayout == null : firstLayout.physicallyEquivalent(secondLayout);
    }

    private static boolean physicalEntryPointEquals(ShaderEntryPoint first, ShaderEntryPoint second) {
        if (!first.name().equals(second.name()) || first.stage() != second.stage()
                || first.workgroupSizeKind() != second.workgroupSizeKind()
                || first.workgroupX() != second.workgroupX()
                || first.workgroupY() != second.workgroupY()
                || first.workgroupZ() != second.workgroupZ()
                || first.builtinMask() != second.builtinMask()
                || first.clipDistanceSize() != second.clipDistanceSize()
                || !physicalVariablesEqual(first.inputs(), second.inputs())
                || !physicalVariablesEqual(first.outputs(), second.outputs())
                || first.overrideCount() != second.overrideCount()
                || first.resourceCount() != second.resourceCount()) {
            return false;
        }
        for (int i = 0; i < first.overrideCount(); i++) {
            ShaderOverride firstOverride = first.override(i);
            ShaderOverride secondOverride = second.override(i);
            if (firstOverride.id() != secondOverride.id()
                    || firstOverride.type() != secondOverride.type()
                    || firstOverride.initialized() != secondOverride.initialized()
                    || firstOverride.explicitId() != secondOverride.explicitId()) {
                return false;
            }
        }
        for (int i = 0; i < first.resourceCount(); i++) {
            if (!first.resource(i).equals(second.resource(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean physicalVariablesEqual(ShaderStageVariable[] first, ShaderStageVariable[] second) {
        if (first.length != second.length) {
            return false;
        }
        for (int i = 0; i < first.length; i++) {
            ShaderStageVariable firstVariable = first[i];
            ShaderStageVariable secondVariable = second[i];
            if (firstVariable.location() != secondVariable.location()
                    || firstVariable.color() != secondVariable.color()
                    || firstVariable.blendSource() != secondVariable.blendSource()
                    || !firstVariable.valueType().equals(secondVariable.valueType())
                    || firstVariable.interpolation() != secondVariable.interpolation()
                    || firstVariable.sampling() != secondVariable.sampling()) {
                return false;
            }
        }
        return true;
    }

    private int findBindingIndex(int group, int binding) {
        for (int i = 0; i < bindings.length; i++) {
            if (bindings[i].group() == group && bindings[i].binding() == binding) {
                return i;
            }
        }
        return -1;
    }

    private void canonicalizeAndValidate() {
        Arrays.sort(entryPoints, Comparator.<ShaderEntryPoint>comparingInt(value -> stageTag(value.stage()))
                .thenComparing(ShaderEntryPoint::name));
        Arrays.sort(bindings, Comparator.comparingInt(ShaderBinding::group)
                .thenComparingInt(ShaderBinding::binding));
        Arrays.sort(attributes, Comparator.comparingInt(ShaderAttribute::location));
        Arrays.sort(requiredCapabilities);
        for (int i = 0; i < entryPoints.length; i++) {
            if (i > 0 && entryPoints[i - 1].stage() == entryPoints[i].stage()
                    && entryPoints[i - 1].name().equals(entryPoints[i].name())) {
                throw new FdxException("Duplicate shader entry point: " + entryPoints[i].stage() + ' '
                        + entryPoints[i].name());
            }
        }
        for (int i = 0; i < bindings.length; i++) {
            if (complete && !bindings[i].complete()) {
                throw new FdxException("Complete shader reflection contains an incomplete binding: "
                        + bindings[i].name());
            }
            if (i > 0 && bindings[i - 1].group() == bindings[i].group()
                    && bindings[i - 1].binding() == bindings[i].binding()) {
                throw new FdxException("Duplicate shader binding: group " + bindings[i].group() + " binding "
                        + bindings[i].binding());
            }
        }
        for (int i = 0; i < requiredCapabilities.length; i++) {
            String capability = requiredCapabilities[i];
            if (capability == null || capability.trim().isEmpty()) {
                throw new FdxException("Shader required capability cannot be empty");
            }
            if (i > 0 && capability.equals(requiredCapabilities[i - 1])) {
                throw new FdxException("Duplicate shader required capability: " + capability);
            }
        }
        if (complete) {
            if (entryPoints.length == 0) {
                throw new FdxException("Complete shader reflection must contain an entry point");
            }
            validateResourceUses();
        }
    }

    private void validateResourceUses() {
        for (ShaderEntryPoint entryPoint : entryPoints) {
            for (int i = 0; i < entryPoint.resourceCount(); i++) {
                ShaderResourceUse use = entryPoint.resource(i);
                ShaderBinding binding = findBinding(use.group(), use.binding());
                if (binding == null) {
                    throw new FdxException("Shader entry point " + entryPoint.name()
                            + " references an unknown resource: group " + use.group() + " binding "
                            + use.binding());
                }
                if (!binding.visibility().contains(entryPoint.stage())) {
                    throw new FdxException("Shader resource visibility omits entry-point stage: "
                            + entryPoint.name() + " resource " + binding.name());
                }
                if (use.minimumBindingSize() > binding.minimumBindingSize()) {
                    throw new FdxException("Shader entry-point minimum binding size exceeds aggregate resource size: "
                            + entryPoint.name() + " resource " + binding.name());
                }
            }
        }
    }

    private String hash(boolean full) {
        PortableSha256 digest = new PortableSha256();
        ShaderParameterLayout.updateInt(digest, complete ? 1 : 0);
        if (full) {
            ShaderParameterLayout.updateString(digest, profile.id());
        }
        ShaderParameterLayout.updateInt(digest, entryPoints.length);
        for (ShaderEntryPoint entryPoint : entryPoints) {
            hashEntryPoint(digest, entryPoint, full);
        }
        ShaderParameterLayout.updateInt(digest, bindings.length);
        for (ShaderBinding binding : bindings) {
            hashBinding(digest, binding, full);
        }
        ShaderParameterLayout.updateInt(digest, requiredCapabilities.length);
        for (String capability : requiredCapabilities) {
            ShaderParameterLayout.updateString(digest, capability);
        }
        if (full) {
            ShaderParameterLayout.updateInt(digest, attributes.length);
            for (ShaderAttribute attribute : attributes) {
                ShaderParameterLayout.updateInt(digest, attribute.location());
                ShaderParameterLayout.updateString(digest, attribute.name());
                ShaderParameterLayout.updateString(digest, attribute.format().name());
            }
        }
        return digest.digestHex();
    }

    private static void hashEntryPoint(PortableSha256 digest, ShaderEntryPoint entryPoint, boolean full) {
        ShaderParameterLayout.updateString(digest, entryPoint.name());
        ShaderParameterLayout.updateInt(digest, stageTag(entryPoint.stage()));
        ShaderParameterLayout.updateString(digest, entryPoint.workgroupSizeKind().name());
        ShaderParameterLayout.updateInt(digest, entryPoint.workgroupX());
        ShaderParameterLayout.updateInt(digest, entryPoint.workgroupY());
        ShaderParameterLayout.updateInt(digest, entryPoint.workgroupZ());
        ShaderParameterLayout.updateLong(digest, entryPoint.builtinMask());
        ShaderParameterLayout.updateInt(digest, entryPoint.clipDistanceSize());
        hashVariables(digest, entryPoint.inputs(), full);
        hashVariables(digest, entryPoint.outputs(), full);
        ShaderParameterLayout.updateInt(digest, entryPoint.overrideCount());
        for (int i = 0; i < entryPoint.overrideCount(); i++) {
            ShaderOverride override = entryPoint.override(i);
            if (full) {
                ShaderParameterLayout.updateString(digest, override.name());
            }
            ShaderParameterLayout.updateInt(digest, override.id());
            ShaderParameterLayout.updateString(digest, override.type().name());
            ShaderParameterLayout.updateInt(digest, override.initialized() ? 1 : 0);
            ShaderParameterLayout.updateInt(digest, override.explicitId() ? 1 : 0);
        }
        ShaderParameterLayout.updateInt(digest, entryPoint.resourceCount());
        for (int i = 0; i < entryPoint.resourceCount(); i++) {
            ShaderResourceUse use = entryPoint.resource(i);
            ShaderParameterLayout.updateInt(digest, use.group());
            ShaderParameterLayout.updateInt(digest, use.binding());
            ShaderParameterLayout.updateLong(digest, use.minimumBindingSize());
        }
    }

    private static void hashVariables(PortableSha256 digest, ShaderStageVariable[] variables, boolean full) {
        ShaderParameterLayout.updateInt(digest, variables.length);
        for (ShaderStageVariable variable : variables) {
            if (full) {
                ShaderParameterLayout.updateString(digest, variable.name());
                ShaderParameterLayout.updateString(digest, variable.variableName());
            }
            ShaderParameterLayout.updateInt(digest, variable.location());
            ShaderParameterLayout.updateInt(digest, variable.color());
            ShaderParameterLayout.updateInt(digest, variable.blendSource());
            ShaderParameterLayout.updateType(digest, variable.valueType());
            ShaderParameterLayout.updateString(digest, variable.interpolation().name());
            ShaderParameterLayout.updateString(digest, variable.sampling().name());
        }
    }

    private static void hashBinding(PortableSha256 digest, ShaderBinding binding, boolean full) {
        ShaderParameterLayout.updateInt(digest, binding.group());
        ShaderParameterLayout.updateInt(digest, binding.binding());
        if (full) {
            ShaderParameterLayout.updateString(digest, binding.stableId());
            ShaderParameterLayout.updateString(digest, binding.name());
        }
        ShaderParameterLayout.updateString(digest, binding.resourceKind().name());
        ShaderParameterLayout.updateInt(digest, binding.visibility().mask());
        ShaderParameterLayout.updateString(digest, binding.access().name());
        ShaderParameterLayout.updateLong(digest, binding.bindingArrayCount());
        ShaderParameterLayout.updateLong(digest, binding.minimumBindingSize());
        ShaderParameterLayout.updateLong(digest, binding.sizeWithoutPadding());
        ShaderParameterLayout.updateLong(digest, binding.alignment());
        ShaderParameterLayout.updateString(digest, binding.textureDimension().name());
        ShaderParameterLayout.updateString(digest, binding.textureSampleType().name());
        ShaderParameterLayout.updateString(digest, binding.samplerKind().name());
        ShaderParameterLayout.updateInt(digest, binding.storageFormatTag());
        ShaderParameterLayout.updateLong(digest, binding.inputAttachmentIndex());
        ShaderParameterLayout layout = binding.bufferLayout();
        if (layout == null) {
            ShaderParameterLayout.updateInt(digest, 0);
        } else {
            ShaderParameterLayout.updateInt(digest, 1);
            hashParameters(digest, layout.parameters(), full);
        }
        if (full) {
            ShaderParameterLayout.updateString(digest, binding.domain().name());
            ShaderParameterLayout.updateString(digest, binding.updateFrequency().name());
        }
    }

    private static void hashParameters(PortableSha256 digest, ShaderParameter[] parameters, boolean full) {
        ShaderParameterLayout.updateInt(digest, parameters.length);
        for (ShaderParameter parameter : parameters) {
            if (full) {
                ShaderParameterLayout.updateString(digest, parameter.stableId());
                ShaderParameterLayout.updateString(digest, parameter.name());
                ShaderParameterLayout.updateString(digest, parameter.domain().name());
                ShaderParameterLayout.updateString(digest, parameter.updateFrequency().name());
            }
            ShaderParameterLayout.updateType(digest, parameter.valueType());
            ShaderParameterLayout.updateLong(digest, parameter.byteOffset());
            ShaderParameterLayout.updateLong(digest, parameter.occupiedSize());
            ShaderParameterLayout.updateLong(digest, parameter.minimumRequiredSize());
            ShaderParameterLayout.updateLong(digest, parameter.alignment());
            ShaderParameterLayout.updateLong(digest, parameter.arrayStride());
            ShaderParameterLayout.updateLong(digest, parameter.matrixStride());
            hashParameters(digest, parameter.members(), full);
        }
    }

    private static int stageTag(ShaderStage stage) {
        return switch (stage) {
            case VERTEX -> 1;
            case FRAGMENT -> 2;
            case COMPUTE -> 3;
        };
    }

    private static <T> T[] cloneAndRequire(T[] values, T[] empty, String label) {
        T[] result = values != null ? values.clone() : empty;
        for (T value : result) {
            if (value == null) {
                throw new FdxException("Shader reflection " + label + " cannot be null");
            }
        }
        return result;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof ShaderReflection other)) {
            return false;
        }
        return complete == other.complete && profile == other.profile
                && Arrays.equals(entryPoints, other.entryPoints) && Arrays.equals(bindings, other.bindings)
                && Arrays.equals(attributes, other.attributes)
                && Arrays.equals(requiredCapabilities, other.requiredCapabilities);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(complete, profile);
        result = 31 * result + Arrays.hashCode(entryPoints);
        result = 31 * result + Arrays.hashCode(bindings);
        result = 31 * result + Arrays.hashCode(attributes);
        return 31 * result + Arrays.hashCode(requiredCapabilities);
    }

    /**
     * Builds complete or explicitly incomplete shader manifests.
     */
    public static final class Builder {
        private final ShaderProfile profile;
        private boolean complete = true;
        private ShaderEntryPoint[] entryPoints = EMPTY_ENTRY_POINTS;
        private ShaderBinding[] bindings = EMPTY_BINDINGS;
        private ShaderAttribute[] attributes = EMPTY_ATTRIBUTES;
        private String[] requiredCapabilities = EMPTY_CAPABILITIES;

        private Builder(ShaderProfile profile) {
            this.profile = profile != null ? profile : ShaderProfile.PORTABLE_WEBGPU;
        }

        public Builder complete(boolean complete) {
            this.complete = complete;
            return this;
        }

        public Builder entryPoints(ShaderEntryPoint... entryPoints) {
            this.entryPoints = entryPoints != null ? entryPoints.clone() : EMPTY_ENTRY_POINTS;
            return this;
        }

        public Builder bindings(ShaderBinding... bindings) {
            this.bindings = bindings != null ? bindings.clone() : EMPTY_BINDINGS;
            return this;
        }

        public Builder attributes(ShaderAttribute... attributes) {
            this.attributes = attributes != null ? attributes.clone() : EMPTY_ATTRIBUTES;
            return this;
        }

        public Builder requiredCapabilities(String... requiredCapabilities) {
            this.requiredCapabilities = requiredCapabilities != null
                    ? requiredCapabilities.clone() : EMPTY_CAPABILITIES;
            return this;
        }

        public ShaderReflection build() {
            return new ShaderReflection(this);
        }
    }
}
