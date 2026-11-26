package io.github.libfdx.graphics.shadergraph.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Immutable editor geometry for one wire. Pins use graph coordinates and run in order from the source.
 * Each pin controls its incoming segment. {@code straightEnd} controls the final segment to the destination.
 */
public record ShaderGraphWireRoute(List<Pin> pins, boolean straightEnd) {
    public static final ShaderGraphWireRoute DEFAULT = new ShaderGraphWireRoute(List.of(), false);

    public ShaderGraphWireRoute {
        pins = List.copyOf(pins);
    }

    /** Identity of one connection. A destination is either a node port or a graph output ID. */
    public record Wire(ShaderEndpoint source, ShaderEndpoint target, ShaderGraphId output) {
        static final Comparator<Wire> ORDER = Comparator.comparing(Wire::source)
                .thenComparing(Wire::target, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Wire::output, Comparator.nullsLast(Comparator.naturalOrder()));

        public Wire {
            Objects.requireNonNull(source);
            if ((target == null) == (output == null)) throw new IllegalArgumentException("Wire requires one destination");
        }

        public static Wire of(ShaderEdge edge) {
            return new Wire(edge.source(), edge.target(), null);
        }

        public static Wire of(ShaderGraphOutput output) {
            return new Wire(output.source(), null, output.id());
        }

        public Wire renameNode(String oldId, ShaderGraphId newId) {
            return new Wire(rename(source, oldId, newId), rename(target, oldId, newId), output);
        }

        private static ShaderEndpoint rename(ShaderEndpoint endpoint, String oldId, ShaderGraphId newId) {
            return endpoint != null && endpoint.nodeId().value().equals(oldId)
                    ? ShaderEndpoint.of(newId, endpoint.portId()) : endpoint;
        }

    }

    /** Rotation is measured clockwise from right, following the wire from source to destination. */
    public record Pin(float x, float y, boolean straightIn, float rotationDegrees) {
        public Pin(float x, float y, boolean straightIn) {
            this(x, y, straightIn, 0);
        }

        public Pin {
            if (!Float.isFinite(x) || !Float.isFinite(y)) throw new IllegalArgumentException("Invalid wire pin position");
            if (!Float.isFinite(rotationDegrees)) throw new IllegalArgumentException("Invalid wire pin rotation");
            rotationDegrees %= 360;
            if (rotationDegrees > 180) rotationDegrees -= 360;
            if (rotationDegrees <= -180) rotationDegrees += 360;
            if (rotationDegrees == 0) rotationDegrees = 0; // Canonicalize negative zero for equality and history.
        }

    }

    public boolean straight(int segment) {
        return segment == pins.size() ? straightEnd : pins.get(segment).straightIn();
    }

    /** Splits a segment at the pin, keeping its routing style on both sides. */
    public ShaderGraphWireRoute insert(int segment, float x, float y) {
        var next = new ArrayList<>(pins);
        next.add(segment, new Pin(x, y, straight(segment)));
        return new ShaderGraphWireRoute(next, straightEnd);
    }

    public ShaderGraphWireRoute move(int index, float x, float y) {
        var next = new ArrayList<>(pins);
        var pin = pins.get(index);
        next.set(index, new Pin(x, y, pin.straightIn(), pin.rotationDegrees()));
        return new ShaderGraphWireRoute(next, straightEnd);
    }

    public ShaderGraphWireRoute withRotation(int index, float degrees) {
        var pin = pins.get(index);
        var rotated = new Pin(pin.x(), pin.y(), pin.straightIn(), degrees);
        if (rotated.equals(pin)) return this;
        var next = new ArrayList<>(pins);
        next.set(index, rotated);
        return new ShaderGraphWireRoute(next, straightEnd);
    }

    public ShaderGraphWireRoute remove(int index) {
        var next = new ArrayList<>(pins);
        next.remove(index);
        return new ShaderGraphWireRoute(next, straightEnd);
    }

    public ShaderGraphWireRoute withStraight(int segment, boolean straight) {
        if (segment == pins.size()) return new ShaderGraphWireRoute(pins, straight);
        var next = new ArrayList<>(pins);
        var pin = pins.get(segment);
        next.set(segment, new Pin(pin.x(), pin.y(), straight, pin.rotationDegrees()));
        return new ShaderGraphWireRoute(next, straightEnd);
    }

}
