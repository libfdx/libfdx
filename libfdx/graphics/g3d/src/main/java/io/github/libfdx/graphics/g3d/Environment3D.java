package io.github.libfdx.graphics.g3d;

import io.github.libfdx.math.Color;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents an environment3 d.
 *
 * @author xpenatan
 */
public final class Environment3D {
    private Color ambientColor = new Color(0.03f, 0.03f, 0.03f, 1.0f);
    private final ArrayList<Light> lights = new ArrayList<Light>();

    /**
     * Sets the ambient color and returns this environment3 d.
     *
     * @param ambientColor the ambient color
     * @return this environment3 d for chaining
     */
    public Environment3D ambientColor(Color ambientColor) {
        this.ambientColor = ambientColor != null ? ambientColor : Color.BLACK;
        return this;
    }

    /**
     * Sets the add and returns this environment3 d.
     *
     * @param light the light
     * @return this environment3 d for chaining
     */
    public Environment3D add(Light light) {
        if (light != null) {
            lights.add(light);
        }
        return this;
    }

    /**
     * Returns the clear lights.
     *
     * @return this environment3 d for chaining
     */
    public Environment3D clearLights() {
        lights.clear();
        return this;
    }

    /**
     * Returns the ambient color.
     *
     * @return the ambient color
     */
    public Color ambientColor() {
        return ambientColor;
    }

    /**
     * Returns the lights.
     *
     * @return the lights
     */
    public List<Light> lights() {
        return Collections.unmodifiableList(lights);
    }
}
