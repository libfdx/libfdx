package io.github.libfdx.backend.android;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;
import io.github.libfdx.application.ApplicationListener;

public abstract class AndroidApplicationActivity extends Activity {
    private AndroidApplicationBackend backend;
    private boolean backCallbackRegistered;
    private boolean backKeyConsumed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        backend = new AndroidApplicationBackend();
        backend.attach(this, createApplicationConfig(), createApplicationListener());
        registerBackCallbackIfNeeded();
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

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU && event != null
                && event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && handleBackNavigation()) {
                backKeyConsumed = true;
                return true;
            }
            if (event.getAction() == KeyEvent.ACTION_UP && backKeyConsumed) {
                backKeyConsumed = false;
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private void registerBackCallbackIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || backCallbackRegistered) {
            return;
        }
        getOnBackInvokedDispatcher().registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_OVERLAY,
                new OnBackInvokedCallback() {
                    @Override
                    public void onBackInvoked() {
                        if (!handleBackNavigation()) {
                            finish();
                        }
                    }
                });
        backCallbackRegistered = true;
    }

    private boolean handleBackNavigation() {
        return backend != null && backend.handleBackNavigation();
    }
}
