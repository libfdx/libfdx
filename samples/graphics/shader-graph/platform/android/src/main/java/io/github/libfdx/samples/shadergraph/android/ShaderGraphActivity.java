package io.github.libfdx.samples.shadergraph.android;

import io.github.libfdx.application.ApplicationListener;
import io.github.libfdx.backend.android.AndroidApplicationActivity;
import io.github.libfdx.backend.android.AndroidApplicationConfig;
import io.github.libfdx.backend.android.AndroidGlesProvider;
import io.github.libfdx.samples.shadergraph.ShaderGraphSampleApplication;
import io.github.libfdx.samples.shadergraph.ShaderGraphSampleGraphs.Example;

public final class ShaderGraphActivity extends AndroidApplicationActivity {
    @Override
    protected AndroidApplicationConfig createApplicationConfig() {
        return new AndroidApplicationConfig().title("Shader graph gallery").graphics(new AndroidGlesProvider());
    }

    @Override
    protected ApplicationListener createApplicationListener() {
        return new ShaderGraphSampleApplication(Example.DISSOLVE, false);
    }

}
