package io.github.libfdx.ecs.schema;

import io.github.libfdx.ecs.component.Component;

/** A core typed, allocation-free property bridge for one component type. */
public final class EcsPropertyDescriptor<T extends Component> {
    private final String id;
    private final String name;
    private final Class<T> ownerType;
    private final EcsPropertyKind kind;
    private final int elements;
    private final String[] enumValues;
    private final Object accessor;

    private EcsPropertyDescriptor(
            String id,
            String name,
            Class<T> ownerType,
            EcsPropertyKind kind,
            int elements,
            String[] enumValues,
            Object accessor) {
        this.id = requireText(id, "id");
        this.name = requireText(name, "name");
        if (ownerType == null || kind == null || accessor == null) {
            throw new IllegalArgumentException("ownerType, kind, and accessor cannot be null.");
        }
        this.ownerType = ownerType;
        this.kind = kind;
        this.elements = elements;
        this.enumValues = enumValues;
        this.accessor = accessor;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public Class<T> ownerType() {
        return ownerType;
    }

    public EcsPropertyKind kind() {
        return kind;
    }

    public int elements() {
        return elements;
    }

    public int enumValueCount() {
        return enumValues == null ? 0 : enumValues.length;
    }

    public String enumValue(int index) {
        if (enumValues == null || index < 0 || index >= enumValues.length) {
            throw new IndexOutOfBoundsException("index=" + index + ", size=" + enumValueCount());
        }
        return enumValues[index];
    }

    @SuppressWarnings("unchecked")
    public boolean booleanValue(Component component) {
        requireKind(EcsPropertyKind.BOOLEAN);
        return ((BooleanAccessor<T>) accessor).get(ownerType.cast(component));
    }

    @SuppressWarnings("unchecked")
    public void booleanValue(Component component, boolean value) {
        requireKind(EcsPropertyKind.BOOLEAN);
        ((BooleanAccessor<T>) accessor).set(ownerType.cast(component), value);
    }

    @SuppressWarnings("unchecked")
    public int intValue(Component component) {
        if (kind != EcsPropertyKind.INTEGER && kind != EcsPropertyKind.ENUM) {
            throw wrongKind("integer or enum");
        }
        return ((IntAccessor<T>) accessor).get(ownerType.cast(component));
    }

    @SuppressWarnings("unchecked")
    public void intValue(Component component, int value) {
        if (kind != EcsPropertyKind.INTEGER && kind != EcsPropertyKind.ENUM) {
            throw wrongKind("integer or enum");
        }
        if (kind == EcsPropertyKind.ENUM && (value < 0 || value >= enumValues.length)) {
            throw new IllegalArgumentException("Enum index out of range for " + id + ": " + value);
        }
        ((IntAccessor<T>) accessor).set(ownerType.cast(component), value);
    }

    @SuppressWarnings("unchecked")
    public float floatValue(Component component, int element) {
        requireFloatElement(element);
        return ((FloatAccessor<T>) accessor).get(ownerType.cast(component), element);
    }

    @SuppressWarnings("unchecked")
    public void floatValue(Component component, int element, float value) {
        requireFloatElement(element);
        ((FloatAccessor<T>) accessor).set(ownerType.cast(component), element, value);
    }

    @SuppressWarnings("unchecked")
    public String textValue(Component component) {
        if (kind != EcsPropertyKind.TEXT && kind != EcsPropertyKind.ASSET) {
            throw wrongKind("text or asset");
        }
        return ((TextAccessor<T>) accessor).get(ownerType.cast(component));
    }

    @SuppressWarnings("unchecked")
    public void textValue(Component component, String value) {
        if (kind != EcsPropertyKind.TEXT && kind != EcsPropertyKind.ASSET) {
            throw wrongKind("text or asset");
        }
        ((TextAccessor<T>) accessor).set(ownerType.cast(component), value == null ? "" : value);
    }

    @SuppressWarnings("unchecked")
    public long entityReference(Component component) {
        requireKind(EcsPropertyKind.ENTITY_REFERENCE);
        return ((LongAccessor<T>) accessor).get(ownerType.cast(component));
    }

    @SuppressWarnings("unchecked")
    public void entityReference(Component component, long value) {
        requireKind(EcsPropertyKind.ENTITY_REFERENCE);
        if (value < 0L) {
            throw new IllegalArgumentException("Entity references must be zero or positive.");
        }
        ((LongAccessor<T>) accessor).set(ownerType.cast(component), value);
    }

    public static <T extends Component> EcsPropertyDescriptor<T> booleanProperty(
            String id, String name, Class<T> ownerType, BooleanAccessor<T> accessor) {
        return new EcsPropertyDescriptor<>(id, name, ownerType, EcsPropertyKind.BOOLEAN, 1, null, accessor);
    }

    public static <T extends Component> EcsPropertyDescriptor<T> integerProperty(
            String id, String name, Class<T> ownerType, IntAccessor<T> accessor) {
        return new EcsPropertyDescriptor<>(id, name, ownerType, EcsPropertyKind.INTEGER, 1, null, accessor);
    }

    public static <T extends Component> EcsPropertyDescriptor<T> enumProperty(
            String id, String name, Class<T> ownerType, String[] values, IntAccessor<T> accessor) {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("Enum values cannot be empty.");
        }
        String[] copy = values.clone();
        for (int i = 0; i < copy.length; i++) {
            copy[i] = requireText(copy[i], "enum value");
        }
        return new EcsPropertyDescriptor<>(id, name, ownerType, EcsPropertyKind.ENUM, 1, copy, accessor);
    }

    public static <T extends Component> EcsPropertyDescriptor<T> floatProperty(
            String id, String name, Class<T> ownerType, FloatAccessor<T> accessor) {
        return floatProperty(id, name, ownerType, EcsPropertyKind.FLOAT, 1, accessor);
    }

    public static <T extends Component> EcsPropertyDescriptor<T> vectorProperty(
            String id, String name, Class<T> ownerType, int elements, FloatAccessor<T> accessor) {
        EcsPropertyKind kind = switch (elements) {
            case 2 -> EcsPropertyKind.VECTOR2;
            case 3 -> EcsPropertyKind.VECTOR3;
            case 4 -> EcsPropertyKind.VECTOR4;
            default -> throw new IllegalArgumentException("Vector elements must be 2, 3, or 4.");
        };
        return floatProperty(id, name, ownerType, kind, elements, accessor);
    }

    public static <T extends Component> EcsPropertyDescriptor<T> colorProperty(
            String id, String name, Class<T> ownerType, int elements, FloatAccessor<T> accessor) {
        EcsPropertyKind kind = switch (elements) {
            case 3 -> EcsPropertyKind.COLOR3;
            case 4 -> EcsPropertyKind.COLOR4;
            default -> throw new IllegalArgumentException("Color elements must be 3 or 4.");
        };
        return floatProperty(id, name, ownerType, kind, elements, accessor);
    }

    public static <T extends Component> EcsPropertyDescriptor<T> textProperty(
            String id, String name, Class<T> ownerType, TextAccessor<T> accessor) {
        return new EcsPropertyDescriptor<>(id, name, ownerType, EcsPropertyKind.TEXT, 1, null, accessor);
    }

    public static <T extends Component> EcsPropertyDescriptor<T> assetProperty(
            String id, String name, Class<T> ownerType, TextAccessor<T> accessor) {
        return new EcsPropertyDescriptor<>(id, name, ownerType, EcsPropertyKind.ASSET, 1, null, accessor);
    }

    public static <T extends Component> EcsPropertyDescriptor<T> entityReferenceProperty(
            String id, String name, Class<T> ownerType, LongAccessor<T> accessor) {
        return new EcsPropertyDescriptor<>(id, name, ownerType, EcsPropertyKind.ENTITY_REFERENCE, 1, null, accessor);
    }

    private static <T extends Component> EcsPropertyDescriptor<T> floatProperty(
            String id,
            String name,
            Class<T> ownerType,
            EcsPropertyKind kind,
            int elements,
            FloatAccessor<T> accessor) {
        return new EcsPropertyDescriptor<>(id, name, ownerType, kind, elements, null, accessor);
    }

    private void requireFloatElement(int element) {
        if (kind != EcsPropertyKind.FLOAT
                && kind != EcsPropertyKind.VECTOR2
                && kind != EcsPropertyKind.VECTOR3
                && kind != EcsPropertyKind.VECTOR4
                && kind != EcsPropertyKind.COLOR3
                && kind != EcsPropertyKind.COLOR4) {
            throw wrongKind("float, vector, or color");
        }
        if (element < 0 || element >= elements) {
            throw new IndexOutOfBoundsException("element=" + element + ", size=" + elements);
        }
    }

    private void requireKind(EcsPropertyKind expected) {
        if (kind != expected) {
            throw wrongKind(expected.name().toLowerCase());
        }
    }

    private IllegalStateException wrongKind(String expected) {
        return new IllegalStateException("Property " + id + " is " + kind + ", expected " + expected + ".");
    }

    private static String requireText(String value, String label) {
        if (value == null || value.trim().length() == 0) {
            throw new IllegalArgumentException(label + " cannot be blank.");
        }
        return value.trim();
    }

    public interface BooleanAccessor<T extends Component> {
        boolean get(T component);

        void set(T component, boolean value);
    }

    public interface IntAccessor<T extends Component> {
        int get(T component);

        void set(T component, int value);
    }

    public interface FloatAccessor<T extends Component> {
        float get(T component, int element);

        void set(T component, int element, float value);
    }

    public interface TextAccessor<T extends Component> {
        String get(T component);

        void set(T component, String value);
    }

    public interface LongAccessor<T extends Component> {
        long get(T component);

        void set(T component, long value);
    }
}
