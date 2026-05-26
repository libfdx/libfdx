package io.github.libfdx.tests;

import io.github.libfdx.application.ApplicationListener;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.tests.graphics.CircleTest;
import io.github.libfdx.tests.graphics.ModelBatchTest;
import io.github.libfdx.tests.graphics.ReadbackTest;
import io.github.libfdx.tests.graphics.SquareTest;
import io.github.libfdx.tests.graphics.SpriteBatchTest;
import io.github.libfdx.tests.graphics.TextureTest;
import io.github.libfdx.tests.graphics.TriangleTest;
import io.github.libfdx.tests.ui.UiKitTest;

import java.util.Locale;

public final class TestSelector {
    public interface TestFactory {
        ApplicationListener create(long exitAfterFrames);
    }

    public static final class TestDescriptor {
        private final String name;
        private final String displayName;
        private final String category;
        private final int defaultWidth;
        private final int defaultHeight;
        private final TestFactory factory;

        private TestDescriptor(String name, String displayName, String category, int defaultWidth, int defaultHeight,
                TestFactory factory) {
            this.name = name;
            this.displayName = displayName;
            this.category = category;
            this.defaultWidth = defaultWidth;
            this.defaultHeight = defaultHeight;
            this.factory = factory;
        }

        public String name() {
            return name;
        }

        public String displayName() {
            return displayName;
        }

        public String category() {
            return category;
        }

        public int defaultWidth() {
            return defaultWidth;
        }

        public int defaultHeight() {
            return defaultHeight;
        }

        ApplicationListener create(long exitAfterFrames) {
            return factory.create(exitAfterFrames);
        }
    }

    private static final String TRIANGLE = "triangle";
    private static final String SQUARE = "square";
    private static final String CIRCLE = "circle";
    private static final String TEXTURE = "texture";
    private static final String SPRITE = "sprite";
    private static final String MODEL = "model";
    private static final String READBACK = "readback";
    private static final String UI = "ui";
    public static final String SELECTOR_NAME = "selector";
    public static final String AUTO_TEST_NAME = "auto";
    public static final String DEFAULT_TEST_NAME = UI;
    private static final TestDescriptor[] TESTS = {
            descriptor(TRIANGLE, "Triangle", "Shapes", 640, 480, new TestFactory() {
                @Override
                public ApplicationListener create(long exitAfterFrames) {
                    return new TriangleTest(exitAfterFrames);
                }
            }),
            descriptor(SQUARE, "Square", "Shapes", 640, 480, new TestFactory() {
                @Override
                public ApplicationListener create(long exitAfterFrames) {
                    return new SquareTest(exitAfterFrames);
                }
            }),
            descriptor(CIRCLE, "Circle", "Shapes", 640, 480, new TestFactory() {
                @Override
                public ApplicationListener create(long exitAfterFrames) {
                    return new CircleTest(exitAfterFrames);
                }
            }),
            descriptor(TEXTURE, "Texture", "Graphics 2D", 640, 480, new TestFactory() {
                @Override
                public ApplicationListener create(long exitAfterFrames) {
                    return new TextureTest(exitAfterFrames);
                }
            }),
            descriptor(SPRITE, "Sprite batch", "Graphics 2D", 640, 480, new TestFactory() {
                @Override
                public ApplicationListener create(long exitAfterFrames) {
                    return new SpriteBatchTest(exitAfterFrames);
                }
            }),
            descriptor(MODEL, "Model batch", "Graphics 3D", 640, 480, new TestFactory() {
                @Override
                public ApplicationListener create(long exitAfterFrames) {
                    return new ModelBatchTest(exitAfterFrames);
                }
            }),
            descriptor(READBACK, "Readback", "Graphics", 640, 480, new TestFactory() {
                @Override
                public ApplicationListener create(long exitAfterFrames) {
                    return new ReadbackTest(exitAfterFrames);
                }
            }),
            descriptor(UI, "UI kit", "UI", 1440, 1000, new TestFactory() {
                @Override
                public ApplicationListener create(long exitAfterFrames) {
                    return new UiKitTest(exitAfterFrames);
                }
            })
    };

    private TestSelector() {
    }

    public static ApplicationListener create(String name, long exitAfterFrames) {
        TestDescriptor descriptor = descriptor(name);
        if (descriptor != null) {
            return descriptor.create(exitAfterFrames);
        }
        throw new FdxException("Unknown test '" + name + "'. Available tests: " + availableTests());
    }

    public static TestDescriptor[] descriptors() {
        return TESTS.clone();
    }

    public static TestDescriptor descriptor(String name) {
        String testName = normalize(name);
        for (int i = 0; i < TESTS.length; i++) {
            if (TESTS[i].name.equals(testName)) {
                return TESTS[i];
            }
        }
        return null;
    }

    public static boolean contains(String name) {
        return descriptor(name) != null;
    }

    public static String[] testNames() {
        String[] names = new String[TESTS.length];
        for (int i = 0; i < TESTS.length; i++) {
            names[i] = TESTS[i].name;
        }
        return names;
    }

    public static String availableTests() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < TESTS.length; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(TESTS[i].name);
        }
        return builder.toString();
    }

    public static int defaultWidth(String name) {
        TestDescriptor descriptor = descriptor(name);
        return descriptor != null ? descriptor.defaultWidth() : 640;
    }

    public static int defaultHeight(String name) {
        TestDescriptor descriptor = descriptor(name);
        return descriptor != null ? descriptor.defaultHeight() : 480;
    }

    public static String normalize(String name) {
        if (name == null || name.trim().length() == 0) {
            return DEFAULT_TEST_NAME;
        }
        return name.trim().toLowerCase(Locale.ROOT);
    }

    private static TestDescriptor descriptor(String name, String displayName, String category, int defaultWidth,
            int defaultHeight, TestFactory factory) {
        return new TestDescriptor(name, displayName, category, defaultWidth, defaultHeight, factory);
    }
}
