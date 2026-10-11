package com.string.aipet;

import android.app.Activity;
import android.app.Instrumentation;
import android.net.Uri;
import android.os.Bundle;
import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;

/** Real APK/package-manager checks on a disposable emulator, without a test framework dependency. */
public final class UpdateInstrumentation extends Instrumentation {
    private int checks;
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            runChecks();
            result.putString("stream", "PASS: " + checks + " updater checks\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            result.putString("stream", "FAIL: " + android.util.Log.getStackTraceString(error));
            finish(Activity.RESULT_CANCELED, result);
        }
    }
    private void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); checks++; }
    private void scenario(String name) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(BuildConfig.UPDATE_TEST_URL.replace("/latest", "/control?scenario=" + name)).openConnection();
        c.setConnectTimeout(3000); c.setReadTimeout(3000);
        try { if (c.getResponseCode() != 200) throw new AssertionError("Fixture unavailable"); c.getInputStream().close(); }
        finally { c.disconnect(); }
    }
    private void refused(ManualUpdater updater, String scenario, String expected) throws Exception {
        scenario(scenario);
        boolean rejected = false;
        try { updater.download(updater.check(), ignored -> {}); }
        catch (Exception e) { rejected = e.getMessage().contains(expected); }
        check(rejected, scenario + " must be refused: " + expected);
        check(!new File(getTargetContext().getFilesDir(), "updates/update.apk").exists(), scenario + " must not leave an installable APK");
        check(!new File(getTargetContext().getFilesDir(), "updates/download.part.apk").exists(), scenario + " partial download must be removed");
    }
    private void runChecks() throws Exception {
        check(BuildConfig.DEBUG && BuildConfig.VERSION_CODE == 1 && !BuildConfig.UPDATE_TEST_URL.isEmpty(), "Requires version 1 emulator fixture build");
        File apk = new File(getTargetContext().getFilesDir(), "updates/update.apk");
        Files.deleteIfExists(apk.toPath());
        getTargetContext().getSharedPreferences("app-update", 0).edit().clear().commit();
        ManualUpdater updater = new ManualUpdater(getTargetContext());
        for (String name : new String[]{"missing_apk", "draft", "unavailable"}) {
            scenario(name);
            boolean rejected = false;
            try { updater.check(); } catch (Exception e) { rejected = true; }
            check(rejected, name + " metadata refused");
        }
        refused(updater, "bad_digest", "校验失败");
        refused(updater, "bad_signer", "签名");
        refused(updater, "old", "版本");
        refused(updater, "foreign", "包名");
        scenario("good");
        ReleasePolicy.Release release = updater.check();
        final int[] progress = {-1};
        File downloaded = updater.download(release, value -> {
            if (value < progress[0] || value > 100) throw new AssertionError("Invalid download progress");
            progress[0] = value;
        });
        check(downloaded.isFile() && progress[0] == 100, "Valid APK fully downloaded");
        updater.validateReady(downloaded, release.version.toString(), release.asset.digest.substring(7), release.asset.size);
        check(true, "Installed certificate matches candidate certificate");
        boolean readonly = false;
        try (android.os.ParcelFileDescriptor ignored = getTargetContext().getContentResolver().openFileDescriptor(UpdateProvider.URI, "w")) { }
        catch (java.io.FileNotFoundException expected) { readonly = true; }
        check(readonly, "APK provider refuses writes");
        boolean traversal = false;
        try (android.os.ParcelFileDescriptor ignored = getTargetContext().getContentResolver().openFileDescriptor(Uri.parse("content://com.string.aipet.updates/../shared_prefs/pet_home.xml"), "r")) { }
        catch (java.io.FileNotFoundException expected) { traversal = true; }
        check(traversal, "APK provider refuses other files");
        byte[] original = Files.readAllBytes(downloaded.toPath());
        original[original.length / 2] ^= 1;
        Files.write(downloaded.toPath(), original);
        boolean tampered = false;
        try { updater.validateReady(downloaded, release.version.toString(), release.asset.digest.substring(7), release.asset.size); }
        catch (Exception expected) { tampered = expected.getMessage().contains("已改变"); }
        check(tampered, "Persisted APK modification refused immediately before install");
        Files.delete(downloaded.toPath());
        scenario("good");
    }
}
