package io.github.libfdx.graphics.g3d;

import io.github.libfdx.math.Color;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Environment3D {
    private Color ambientColor = new Color(0.03f, 0.03f, 0.03f, 1.0f);
    private final ArrayList<Light> lights = new ArrayList<Light>();

    public Environment3D ambientColor(Color ambientColor) {
        this.ambientColor = ambientColor != null ? ambientColor : Color.BLACK;
        return this;
    }

    public Environment3D add(Light light) {
        if (light != null) {
            lights.add(light);
        }
        return this;
    }

    public Environment3D clearLights() {
        lights.clear();
        return this;
    }

    public Color ambientColor() {
        return ambientColor;
    }

    public List<Light> lights() {
        return Collections.unmodifiableList(lights);
    }
}
