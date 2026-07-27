package io.github.libfdx.ui;

import java.util.Arrays;

/**
 * Retained line and curve geometry for custom UI drawing.
 *
 * <p>A path may be built once and drawn repeatedly without allocation. Growing
 * beyond its current capacity reallocates its internal storage, so callers on a
 * steady-state path should construct it with sufficient capacity or warm it up
 * before frame rendering.</p>
 *
 * @author xpenatan
 */
public final class UiPath {
    static final byte MOVE_TO = 1;
    static final byte LINE_TO = 2;
    static final byte QUADRATIC_TO = 3;
    static final byte CUBIC_TO = 4;
    static final byte CLOSE = 5;

    private byte[] commands;
    private float[] coordinates;
    private int commandCount;
    private int coordinateCount;
    private boolean hasCurrentPoint;

    /**
     * Creates a path with reusable default storage.
     */
    public UiPath() {
        this(32, 128);
    }

    /**
     * Creates a path with explicit reusable storage.
     *
     * @param commandCapacity the initial command capacity
     * @param coordinateCapacity the initial float-coordinate capacity
     */
    public UiPath(int commandCapacity, int coordinateCapacity) {
        if (commandCapacity < 1) {
            throw new IllegalArgumentException("UI path command capacity must be positive");
        }
        if (coordinateCapacity < 2) {
            throw new IllegalArgumentException("UI path coordinate capacity must be at least two");
        }
        commands = new byte[commandCapacity];
        coordinates = new float[coordinateCapacity];
    }

    /**
     * Removes all commands while retaining allocated storage.
     *
     * @return this path
     */
    public UiPath clear() {
        commandCount = 0;
        coordinateCount = 0;
        hasCurrentPoint = false;
        return this;
    }

    /**
     * Starts a subpath.
     *
     * @param x the horizontal coordinate
     * @param y the vertical coordinate
     * @return this path
     */
    public UiPath moveTo(float x, float y) {
        requireFinite(x);
        requireFinite(y);
        append(MOVE_TO, 2);
        coordinates[coordinateCount++] = x;
        coordinates[coordinateCount++] = y;
        hasCurrentPoint = true;
        return this;
    }

    /**
     * Adds a straight segment.
     *
     * @param x the destination horizontal coordinate
     * @param y the destination vertical coordinate
     * @return this path
     */
    public UiPath lineTo(float x, float y) {
        requireCurrentPoint();
        requireFinite(x);
        requireFinite(y);
        append(LINE_TO, 2);
        coordinates[coordinateCount++] = x;
        coordinates[coordinateCount++] = y;
        return this;
    }

    /**
     * Adds a quadratic Bezier segment.
     *
     * @param controlX the control horizontal coordinate
     * @param controlY the control vertical coordinate
     * @param x the destination horizontal coordinate
     * @param y the destination vertical coordinate
     * @return this path
     */
    public UiPath quadraticTo(float controlX, float controlY, float x, float y) {
        requireCurrentPoint();
        requireFinite(controlX);
        requireFinite(controlY);
        requireFinite(x);
        requireFinite(y);
        append(QUADRATIC_TO, 4);
        coordinates[coordinateCount++] = controlX;
        coordinates[coordinateCount++] = controlY;
        coordinates[coordinateCount++] = x;
        coordinates[coordinateCount++] = y;
        return this;
    }

    /**
     * Adds a cubic Bezier segment.
     *
     * @param control1X the first control horizontal coordinate
     * @param control1Y the first control vertical coordinate
     * @param control2X the second control horizontal coordinate
     * @param control2Y the second control vertical coordinate
     * @param x the destination horizontal coordinate
     * @param y the destination vertical coordinate
     * @return this path
     */
    public UiPath cubicTo(float control1X, float control1Y, float control2X, float control2Y, float x, float y) {
        requireCurrentPoint();
        requireFinite(control1X);
        requireFinite(control1Y);
        requireFinite(control2X);
        requireFinite(control2Y);
        requireFinite(x);
        requireFinite(y);
        append(CUBIC_TO, 6);
        coordinates[coordinateCount++] = control1X;
        coordinates[coordinateCount++] = control1Y;
        coordinates[coordinateCount++] = control2X;
        coordinates[coordinateCount++] = control2Y;
        coordinates[coordinateCount++] = x;
        coordinates[coordinateCount++] = y;
        return this;
    }

    /**
     * Closes the current subpath.
     *
     * @return this path
     */
    public UiPath close() {
        requireCurrentPoint();
        append(CLOSE, 0);
        return this;
    }

    /**
     * Returns the number of path commands.
     *
     * @return the command count
     */
    public int commandCount() {
        return commandCount;
    }

    /**
     * Returns whether the path contains no commands.
     *
     * @return true when the path is empty
     */
    public boolean isEmpty() {
        return commandCount == 0;
    }

    byte command(int index) {
        return commands[index];
    }

    float coordinate(int index) {
        return coordinates[index];
    }

    private void append(byte command, int coordinateLength) {
        if (commandCount == commands.length) {
            commands = Arrays.copyOf(commands, commands.length * 2);
        }
        int requiredCoordinates = coordinateCount + coordinateLength;
        if (requiredCoordinates > coordinates.length) {
            int nextCapacity = coordinates.length;
            while (nextCapacity < requiredCoordinates) {
                nextCapacity *= 2;
            }
            coordinates = Arrays.copyOf(coordinates, nextCapacity);
        }
        commands[commandCount++] = command;
    }

    private void requireCurrentPoint() {
        if (!hasCurrentPoint) {
            throw new IllegalStateException("UI path must start with moveTo");
        }
    }

    private static void requireFinite(float value) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException("UI path coordinates must be finite");
        }
    }
}
