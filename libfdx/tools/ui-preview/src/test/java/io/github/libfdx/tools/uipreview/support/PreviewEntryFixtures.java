package io.github.libfdx.tools.uipreview.support;

import io.github.libfdx.tools.uipreview.UiPreviewContext;
import io.github.libfdx.tools.uipreview.UiPreviewEntry;
import io.github.libfdx.ui.UiContent;

/** Compiled fixtures for discovery without loading or initializing entries. */
public final class PreviewEntryFixtures {
    public abstract static class Base implements UiPreviewEntry {
        public UiContent create(UiPreviewContext context) {
            return ui -> {};
        }

    }

    public static final class Discovered extends Base {
        static {
            if (!Boolean.getBoolean("preview.fixture.allowInitialization")) {
                throw new AssertionError("Discovery must not initialize preview entries");
            }
        }
    }

    public static final class PrivateConstructor extends Base {
        private PrivateConstructor() {
        }

    }

    public static final class NeedsArgument extends Base {
        public NeedsArgument(String argument) {
        }

    }

    public static final class OverridesMain extends Base {
        @Override
        public void main(String[] args) {
            throw new AssertionError("Discovery must not select an overridden entry point");
        }

    }
}
