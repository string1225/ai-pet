package com.string.aipet;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Bundle;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;

/** Inspect real signing metadata on an installed old release without changing its data. */
public final class UpdateCompatibilityInstrumentation extends Instrumentation {
    private Bundle arguments;
    @Override public void onCreate(Bundle args) { super.onCreate(args); arguments=args; start(); }
    private static int modern(PackageInfo p) {
        return p!=null&&p.signingInfo!=null&&p.signingInfo.getApkContentsSigners()!=null?p.signingInfo.getApkContentsSigners().length:0;
    }
    private static int legacy(PackageInfo p) { return p!=null&&p.signatures!=null?p.signatures.length:0; }
    @Override public void onStart() {
        Bundle out=new Bundle();StringBuilder log=new StringBuilder();File copy=null;
        try {
            PackageManager pm=getTargetContext().getPackageManager();
            File source=new File(arguments.getString("apk","/data/local/tmp/ai-pet-v0.2.0.apk"));
            copy=new File(getTargetContext().getFilesDir(),"signing-probe.apk");
            Files.copy(source.toPath(),copy.toPath(),StandardCopyOption.REPLACE_EXISTING);
            for(int flags:new int[]{PackageManager.GET_SIGNING_CERTIFICATES,PackageManager.GET_SIGNATURES,PackageManager.GET_SIGNING_CERTIFICATES|PackageManager.GET_SIGNATURES}) {
                PackageInfo installed=pm.getPackageInfo(getTargetContext().getPackageName(),flags);
                PackageInfo archive=pm.getPackageArchiveInfo(copy.getAbsolutePath(),flags);
                log.append("flags=").append(flags).append(" installed modern=").append(modern(installed)).append(" legacy=").append(legacy(installed))
                    .append(" archive modern=").append(modern(archive)).append(" legacy=").append(legacy(archive)).append('\n');
                if(archive==null)throw new AssertionError("Archive cannot be parsed");
            }
            int flags=PackageManager.GET_SIGNING_CERTIFICATES|PackageManager.GET_SIGNATURES;
            PackageInfo installed=pm.getPackageInfo(getTargetContext().getPackageName(),flags);
            PackageInfo archive=pm.getPackageArchiveInfo(copy.getAbsolutePath(),flags);
            if(modern(installed)<1||modern(archive)<1)throw new AssertionError("Missing certificate metadata for the updater");
            Signature a=installed.signingInfo.getApkContentsSigners()[0],b=archive.signingInfo.getApkContentsSigners()[0];
            if(!a.equals(b))throw new AssertionError("Certificate does not match the installed release");
            String expected=arguments.getString("expectedVersion");
            if(expected!=null){
                byte[] digest=MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(copy.toPath()));StringBuilder sha=new StringBuilder();for(byte value:digest)sha.append(String.format(java.util.Locale.ROOT,"%02x",value&255));
                new ManualUpdater(getTargetContext()).validateReady(copy,expected,sha.toString(),copy.length());
                log.append("PASS: actual updater accepts the newer fixed-signature APK and full digest\n");
            }
            out.putString("stream",log+"PASS: APK certificate metadata matches the installed release\n");finish(Activity.RESULT_OK,out);
        } catch(Throwable e) { out.putString("stream",log+"FAIL: "+android.util.Log.getStackTraceString(e));finish(Activity.RESULT_CANCELED,out); }
        finally { if(copy!=null)copy.delete(); }
    }
}
