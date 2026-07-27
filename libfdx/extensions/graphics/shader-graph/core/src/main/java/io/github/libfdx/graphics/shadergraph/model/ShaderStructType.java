package io.github.libfdx.graphics.shadergraph.model;

import io.github.libfdx.core.FdxException;

import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable ordered graph structure definition.
 */
public final class ShaderStructType {
    private final ShaderGraphId id;
    private final ShaderStructField[] fields;

    private ShaderStructType(ShaderGraphId id, ShaderStructField[] fields) {
        if (id == null || fields == null || fields.length == 0) {
            throw new FdxException("Shader structure requires an ID and at least one field");
        }
        this.id = id;
        this.fields = fields.clone();
        for (int i = 0; i < this.fields.length; i++) {
            ShaderStructField field = this.fields[i];
            if (field == null) {
                throw new FdxException("Shader structure field cannot be null");
            }
            for (int j = 0; j < i; j++) {
                if (this.fields[j].id().equals(field.id())) {
                    throw new FdxException("Duplicate shader structure field: " + field.id());
                }
            }
        }
    }

    public static ShaderStructType of(String id, ShaderStructField... fields) {
        return new ShaderStructType(ShaderGraphId.of(id), fields);
    }

    public ShaderGraphId id() {
        return id;
    }

    public int fieldCount() {
        return fields.length;
    }

    public ShaderStructField field(int index) {
        return fields[index];
    }

    public ShaderStructField field(ShaderGraphId fieldId) {
        for (ShaderStructField field : fields) {
            if (field.id().equals(fieldId)) {
                return field;
            }
        }
        return null;
    }

    public ShaderStructField[] fields() {
        return fields.clone();
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof ShaderStructType other
                && id.equals(other.id) && Arrays.equals(fields, other.fields);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, Arrays.hashCode(fields));
    }
}
