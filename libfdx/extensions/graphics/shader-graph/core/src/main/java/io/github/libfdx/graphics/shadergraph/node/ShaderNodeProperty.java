package io.github.libfdx.graphics.shadergraph.node;

import io.github.libfdx.graphics.shadergraph.model.ShaderGraphId;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphLiteral;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphType;
import io.github.libfdx.core.FdxException;

import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable typed semantic property stored by a node.
 */
public final class ShaderNodeProperty implements Comparable<ShaderNodeProperty> {
    private final ShaderGraphId id;
    private final ShaderNodePropertyKind kind;
    private final String stringValue;
    private final long integerValue;
    private final boolean booleanValue;
    private final ShaderGraphType typeValue;
    private final ShaderGraphLiteral literalValue;
    private final ShaderGraphId[] idValues;
    private final long[] integerValues;

    private ShaderNodeProperty(ShaderGraphId id, ShaderNodePropertyKind kind,
            String stringValue, long integerValue, boolean booleanValue,
            ShaderGraphType typeValue, ShaderGraphLiteral literalValue,
            ShaderGraphId[] idValues, long[] integerValues) {
        if (id == null || kind == null) {
            throw new FdxException("Shader node property requires an ID and kind");
        }
        this.id = id;
        this.kind = kind;
        this.stringValue = stringValue;
        this.integerValue = integerValue;
        this.booleanValue = booleanValue;
        this.typeValue = typeValue;
        this.literalValue = literalValue;
        this.idValues = idValues != null ? idValues.clone() : new ShaderGraphId[0];
        this.integerValues = integerValues != null ? integerValues.clone() : new long[0];
    }

    public static ShaderNodeProperty string(String id, String value) {
        if (value == null) {
            throw new FdxException("Shader node string property cannot be null");
        }
        return new ShaderNodeProperty(ShaderGraphId.of(id),
                ShaderNodePropertyKind.STRING, value, 0, false, null, null,
                null, null);
    }

    public static ShaderNodeProperty integer(String id, long value) {
        return new ShaderNodeProperty(ShaderGraphId.of(id),
                ShaderNodePropertyKind.INTEGER, null, value, false, null, null,
                null, null);
    }

    public static ShaderNodeProperty bool(String id, boolean value) {
        return new ShaderNodeProperty(ShaderGraphId.of(id),
                ShaderNodePropertyKind.BOOLEAN, null, 0, value, null, null,
                null, null);
    }

    public static ShaderNodeProperty type(String id, ShaderGraphType value) {
        if (value == null) {
            throw new FdxException("Shader node type property cannot be null");
        }
        return new ShaderNodeProperty(ShaderGraphId.of(id),
                ShaderNodePropertyKind.TYPE, null, 0, false, value, null,
                null, null);
    }

    public static ShaderNodeProperty literal(String id, ShaderGraphLiteral value) {
        if (value == null) {
            throw new FdxException("Shader node literal property cannot be null");
        }
        return new ShaderNodeProperty(ShaderGraphId.of(id),
                ShaderNodePropertyKind.LITERAL, null, 0, false, null, value,
                null, null);
    }

    public static ShaderNodeProperty ids(String id, ShaderGraphId... values) {
        ShaderGraphId[] checked = values != null ? values.clone() : new ShaderGraphId[0];
        for (ShaderGraphId value : checked) {
            if (value == null) {
                throw new FdxException("Shader node ID-list property cannot contain null");
            }
        }
        return new ShaderNodeProperty(ShaderGraphId.of(id),
                ShaderNodePropertyKind.ID_LIST, null, 0, false, null, null,
                checked, null);
    }

    public static ShaderNodeProperty integers(String id, long... values) {
        return new ShaderNodeProperty(ShaderGraphId.of(id),
                ShaderNodePropertyKind.INTEGER_LIST, null, 0, false, null, null,
                null, values != null ? values : new long[0]);
    }

    public ShaderGraphId id() {
        return id;
    }

    public ShaderNodePropertyKind kind() {
        return kind;
    }

    public String stringValue() {
        require(ShaderNodePropertyKind.STRING);
        return stringValue;
    }

    public long integerValue() {
        require(ShaderNodePropertyKind.INTEGER);
        return integerValue;
    }

    public boolean booleanValue() {
        require(ShaderNodePropertyKind.BOOLEAN);
        return booleanValue;
    }

    public ShaderGraphType typeValue() {
        require(ShaderNodePropertyKind.TYPE);
        return typeValue;
    }

    public ShaderGraphLiteral literalValue() {
        require(ShaderNodePropertyKind.LITERAL);
        return literalValue;
    }

    public ShaderGraphId[] idValues() {
        require(ShaderNodePropertyKind.ID_LIST);
        return idValues.clone();
    }

    public long[] integerValues() {
        require(ShaderNodePropertyKind.INTEGER_LIST);
        return integerValues.clone();
    }

    @Override
    public int compareTo(ShaderNodeProperty other) {
        return id.compareTo(other.id);
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof ShaderNodeProperty other
                && id.equals(other.id) && kind == other.kind
                && Objects.equals(stringValue, other.stringValue)
                && integerValue == other.integerValue
                && booleanValue == other.booleanValue
                && Objects.equals(typeValue, other.typeValue)
                && Objects.equals(literalValue, other.literalValue)
                && Arrays.equals(idValues, other.idValues)
                && Arrays.equals(integerValues, other.integerValues);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, kind, stringValue, integerValue, booleanValue,
                typeValue, literalValue, Arrays.hashCode(idValues),
                Arrays.hashCode(integerValues));
    }

    private void require(ShaderNodePropertyKind expected) {
        if (kind != expected) {
            throw new FdxException("Shader node property " + id + " is " + kind
                    + ", not " + expected);
        }
    }
}
