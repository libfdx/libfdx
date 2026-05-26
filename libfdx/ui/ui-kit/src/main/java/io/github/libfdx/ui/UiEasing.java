package io.github.libfdx.ui;

public final class UiEasing {
    private static final int LINEAR = 0;
    private static final int IN_QUAD = 1;
    private static final int OUT_QUAD = 2;
    private static final int IN_CUBIC = 3;
    private static final int OUT_CUBIC = 4;
    private static final int OUT_BACK = 5;
    private static final int OUT_ELASTIC = 6;
    private static final int STEP_END = 7;

    private final int type;

    private UiEasing(int type) {
        this.type = type;
    }

    public static UiEasing linear() {
        return new UiEasing(LINEAR);
    }

    public static UiEasing inQuad() {
        return new UiEasing(IN_QUAD);
    }

    public static UiEasing outQuad() {
        return new UiEasing(OUT_QUAD);
    }

    public static UiEasing inCubic() {
        return new UiEasing(IN_CUBIC);
    }

    public static UiEasing outCubic() {
        return new UiEasing(OUT_CUBIC);
    }

    public static UiEasing outBack() {
        return new UiEasing(OUT_BACK);
    }

    public static UiEasing outElastic() {
        return new UiEasing(OUT_ELASTIC);
    }

    public static UiEasing stepEnd() {
        return new UiEasing(STEP_END);
    }

    public float apply(float progress) {
        float t = clamp(progress);
        switch (type) {
            case IN_QUAD:
                return t * t;
            case OUT_QUAD:
                return 1.0f - (1.0f - t) * (1.0f - t);
            case IN_CUBIC:
                return t * t * t;
            case OUT_CUBIC:
                return 1.0f - (float) Math.pow(1.0f - t, 3.0);
            case OUT_BACK:
                float c1 = 1.70158f;
                float c3 = c1 + 1.0f;
                return 1.0f + c3 * (float) Math.pow(t - 1.0f, 3.0) + c1 * (float) Math.pow(t - 1.0f, 2.0);
            case OUT_ELASTIC:
                if (t == 0.0f || t == 1.0f) {
                    return t;
                }
                return (float) (Math.pow(2.0, -10.0f * t) * Math.sin((t * 10.0f - 0.75f) * ((2.0 * Math.PI) / 3.0))
                        + 1.0);
            case STEP_END:
                return t >= 1.0f ? 1.0f : 0.0f;
            case LINEAR:
            default:
                return t;
        }
    }

    private float clamp(float value) {
        if (value < 0.0f) {
            return 0.0f;
        }
        if (value > 1.0f) {
            return 1.0f;
        }
        return value;
    }
}
