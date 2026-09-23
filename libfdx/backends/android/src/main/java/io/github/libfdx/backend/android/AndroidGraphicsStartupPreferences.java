package io.github.libfdx.backend.android;

import android.app.ActivityManager;
import android.app.Application;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Process;
import io.github.libfdx.core.FdxException;

final class AndroidGraphicsStartupPreferences implements AndroidGraphicsStartupRecovery.Store {
    private final SharedPreferences preferences;

    private AndroidGraphicsStartupPreferences(Context context, String key) {
        preferences = context.getSharedPreferences(name(key), Context.MODE_PRIVATE);
        String installation;
        try {
            installation = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).lastUpdateTime
                    + ":" + Build.FINGERPRINT;
        } catch(PackageManager.NameNotFoundException failure) {
            throw new FdxException("Could not identify this installation", failure);
        }
        if(!installation.equals(preferences.getString("installation", ""))) {
            requireCommit(preferences.edit().clear().putString("installation", installation).commit());
        }
    }

    static AndroidGraphicsStartupRecovery create(Context context, String key, int attempts) {
        return new AndroidGraphicsStartupRecovery(new AndroidGraphicsStartupPreferences(context, key),
                (pid, started, now) -> nativeCrash(context, pid, started, now),
                Process.myPid(), System.currentTimeMillis(), attempts);
    }

    static void clear(Context context, String key) {
        requireCommit(context.getSharedPreferences(name(key), Context.MODE_PRIVATE).edit().clear().commit());
    }

    static String name(String key) {
        if(key == null || !key.matches("[A-Za-z0-9_.-]{1,128}")) {
            throw new IllegalArgumentException("Graphics recovery key must contain 1–128 letters, digits, dots, dashes or underscores");
        }
        return "libfdx.graphics.startup." + key;
    }

    private static boolean nativeCrash(Context context, int pid, long started, long now) {
        if(Build.VERSION.SDK_INT < 30) return false;
        ActivityManager manager = (ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
        if(manager == null) return false;
        try {
            for(ApplicationExitInfo exit : manager.getHistoricalProcessExitReasons(context.getPackageName(), pid, 8)) {
                if(exit.getPid() == pid && Application.getProcessName().equals(exit.getProcessName())
                        && exit.getTimestamp() >= started && exit.getTimestamp() <= now
                        && exit.getReason() == ApplicationExitInfo.REASON_CRASH_NATIVE) return true;
            }
        } catch(RuntimeException unavailable) {
            // Missing exit evidence must not turn an unfinished marker into a crash diagnosis.
            return false;
        }
        return false;
    }

    @Override
    public AndroidGraphicsStartupRecovery.State read() {
        return new AndroidGraphicsStartupRecovery.State(preferences.getInt("selected", 0),
                preferences.getInt("pending", -1), preferences.getInt("pid", 0), preferences.getLong("started", 0));
    }

    @Override
    public void write(AndroidGraphicsStartupRecovery.State state) {
        requireCommit(preferences.edit().putInt("selected", state.selected()).putInt("pending", state.pending())
                .putInt("pid", state.pid()).putLong("started", state.started()).commit());
    }

    private static void requireCommit(boolean committed) {
        if(!committed) throw new FdxException("Could not persist graphics startup recovery preferences");
    }
}
