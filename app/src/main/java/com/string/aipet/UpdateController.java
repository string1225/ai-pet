package com.string.aipet;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Parent-initiated updates, independent of pet saves and learning sessions. */
public final class UpdateController {
    private final Activity activity;
    private final Consumer<Runnable> parentGate;
    private final ManualUpdater updater;
    private final SharedPreferences preferences;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private AlertDialog dialog;
    private ReleasePolicy.Release release;
    private String message = "点击检查更新，查看是否有新版本。\n更新会保留小伙伴和学习记录。";
    private boolean busy, resumed, destroyed, permissionPending;

    public UpdateController(Activity activity, Consumer<Runnable> parentGate) {
        this.activity = activity;
        this.parentGate = parentGate;
        updater = new ManualUpdater(activity);
        preferences = activity.getSharedPreferences("app-update", Context.MODE_PRIVATE);
    }

    public void show() {
        if (destroyed || activity.isFinishing()) return;
        if (dialog != null) { dialog.show(); refresh(); return; }
        dialog = new AlertDialog.Builder(activity)
                .setTitle("应用更新 · 当前 " + BuildConfig.VERSION_NAME)
                .setMessage(message)
                .setPositiveButton("检查更新", null)
                .setNeutralButton("重新检查", null)
                .setNegativeButton("关闭", null).create();
        dialog.setOnDismissListener(d -> dialog = null);
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (busy) return;
            if (!readyVersion().isEmpty()) install();
            else if (hasNewVersion()) download();
            else check();
        });
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> check());
        refresh();
    }

    private File apk() { return new File(activity.getFilesDir(), "updates/update.apk"); }
    private String readyVersion() {
        String ready = preferences.getString("readyVersion", "");
        try {
            if (!ready.isEmpty() && apk().isFile() && ReleasePolicy.Version.parse(ready).compareTo(ReleasePolicy.Version.parse(BuildConfig.VERSION_NAME)) > 0) return ready;
        } catch (IllegalArgumentException ignored) { /* Discard stale metadata. */ }
        if (!ready.isEmpty()) {
            preferences.edit().clear().apply();
            // Only this private update file is removed; pet records are independent.
            apk().delete();
        }
        return "";
    }
    private boolean hasNewVersion() {
        return release != null && release.version.compareTo(ReleasePolicy.Version.parse(BuildConfig.VERSION_NAME)) > 0;
    }
    private void refresh() {
        if (dialog == null || !dialog.isShowing()) return;
        String ready = readyVersion();
        dialog.setMessage(message + (!busy && !ready.isEmpty() ? "\n\n新版 " + ready + " 已下载，点击安装新版。\n安卓会再次请家长确认。" : ""));
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText(busy ? "请稍候" : !ready.isEmpty() ? "安装新版" : hasNewVersion() ? "下载新版" : "检查更新");
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(!busy);
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(!busy);
    }
    private void post(Runnable action) { main.post(() -> { if (!destroyed) action.run(); }); }
    private void check() {
        if (busy) return;
        busy = true; message = "正在检查更新…"; refresh();
        worker.execute(() -> {
            try {
                ReleasePolicy.Release result = updater.check();
                post(() -> {
                    release = result; busy = false;
                    message = result == null ? "还没有正式发布的新版本。" : hasNewVersion()
                            ? "发现新版 " + result.version + "\n\n" + (result.notes.isEmpty() ? "本次更新没有附加说明。" : result.notes)
                            : "当前已经是最新版本。";
                    refresh();
                });
            } catch (Exception e) { failure("检查更新失败", e); }
        });
    }
    private void download() {
        if (busy || !hasNewVersion()) return;
        ReleasePolicy.Release selected = release;
        busy = true; message = "正在下载新版 " + selected.version + " · 0%\n关闭窗口后会继续下载。"; refresh();
        worker.execute(() -> {
            try {
                updater.download(selected, percent -> post(() -> {
                    message = percent == 100 ? "正在校验安装包…" : "正在下载新版 " + selected.version + " · " + percent + "%\n关闭窗口后会继续下载。";
                    refresh();
                }));
                if (!preferences.edit().putString("readyVersion", selected.version.toString())
                        .putString("sha256", selected.asset.digest.substring(7)).putLong("size", selected.asset.size).commit()) throw new IOException("无法保存下载记录，请重试");
                post(() -> { busy = false; message = "下载完成，文件和签名校验通过。\n覆盖安装会保留宠物和学习记录。"; refresh(); });
            } catch (Exception e) { failure("下载失败，请重试", e); }
        });
    }
    private void install() {
        String version = readyVersion();
        if (busy || version.isEmpty()) return;
        if (!activity.getPackageManager().canRequestPackageInstalls()) {
            parentGate.accept(() -> {
                if (!resumed || destroyed) return;
                message = "请在系统设置中开启「允许来自此来源的应用」。\n返回小屋后，再点击安装新版。";
                permissionPending = true;
                try { activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + activity.getPackageName()))); }
                catch (ActivityNotFoundException e) { permissionPending = false; message = "这台设备没有安装权限设置入口，请联系家长检查设备限制。"; show(); }
            });
            dismiss();
            return;
        }
        busy = true; message = "正在重新校验安装包…"; refresh();
        worker.execute(() -> {
            try {
                updater.validateReady(apk(), version, preferences.getString("sha256", ""), preferences.getLong("size", 0));
                post(() -> {
                    busy = false; message = "安装包校验通过，请家长确认安装。"; refresh();
                    // Closing the dialog or leaving the app must never open the installer later.
                    if (!resumed || dialog == null || !dialog.isShowing()) return;
                    dismiss();
                    parentGate.accept(() -> {
                        if (!resumed || destroyed) return;
                        try {
                            activity.startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(UpdateProvider.URI, "application/vnd.android.package-archive")
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
                        } catch (ActivityNotFoundException e) { message = "这台设备没有可用的安装程序，请联系家长。"; show(); }
                    });
                });
            } catch (Exception e) {
                preferences.edit().clear().commit();
                apk().delete();
                failure("安装包不可用，请重新检查更新", e);
            }
        });
    }
    private void failure(String title, Exception error) {
        post(() -> { busy = false; message = title + "\n" + (error.getMessage() == null ? "请检查网络后重试。" : error.getMessage()); refresh(); });
    }
    private void dismiss() { if (dialog != null) dialog.dismiss(); }
    public void onResume() {
        resumed = true;
        if (permissionPending) { permissionPending = false; show(); }
        else refresh();
    }
    public void onPause() { resumed = false; }
    public void destroy() { destroyed = true; worker.shutdownNow(); main.removeCallbacksAndMessages(null); dismiss(); }
}
