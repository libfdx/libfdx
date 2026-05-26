package io.github.libfdx.backend.android;

import android.app.Activity;
import android.os.Bundle;
import io.github.libfdx.application.ApplicationListener;

public abstract class AndroidApplicationActivity extends Activity {
    private AndroidApplicationBackend backend;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        backend = new AndroidApplicationBackend();
        backend.attach(this, createApplicationConfig(), createApplicationListener());
    }

    protected abstract AndroidApplicationConfig createApplicationConfig();

    protected abstract ApplicationListener createApplicationListener();

    @Override
    protected void onResume() {
        super.onResume();
        if (backend != null) {
            backend.resume();
        }
    }

    @Override
    protected void onPause() {
        if (backend != null) {
            backend.pause();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (backend != null) {
            backend.dispose();
            backend = null;
        }
        super.onDestroy();
    }

    protected AndroidApplicationBackend backend() {
        return backend;
    }
}
