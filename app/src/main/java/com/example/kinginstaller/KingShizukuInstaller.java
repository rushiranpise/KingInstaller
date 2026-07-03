package com.example.kinginstaller;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.IPackageInstaller;
import android.content.pm.IPackageInstallerSession;
import android.content.pm.IPackageManager;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Process;
import android.os.RemoteException;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.lsposed.hiddenapibypass.HiddenApiBypass;

import rikka.shizuku.Shizuku;
import rikka.shizuku.ShizukuBinderWrapper;
import rikka.shizuku.ShizukuProvider;
import rikka.shizuku.SystemServiceHelper;
import rikka.sui.Sui;

class KingShizukuInstaller {
    static final String PACKAGE_NAME = "moe.shizuku.privileged.api";

    private static final String TAG = "KingShizukuInstaller";
    private static final String PLAY_STORE_PACKAGE = "com.android.vending";
    private static final int INSTALL_REPLACE_EXISTING = 0x00000002;

    private final Application app;

    KingShizukuInstaller(Application app) {
        this.app = app;
        enableHiddenApiAccess();
        boolean isSui = Sui.init(app.getPackageName());
        if (!isSui) {
            try {
                ShizukuProvider.requestBinderForNonProviderProcess(app);
            } catch (Throwable ignored) {
            }
        }
    }

    private static void enableHiddenApiAccess() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return;
        try {
            HiddenApiBypass.addHiddenApiExemptions("L");
        } catch (Throwable error) {
            Log.w(TAG, "Hidden API bypass initialization failed", error);
        }
    }

    @SuppressLint("RequestInstallPackagesPolicy")
    InstallResult install(File sourceFile, String expectedPackage) throws Exception {
        return install(Collections.singletonList(sourceFile), expectedPackage);
    }

    @SuppressLint("RequestInstallPackagesPolicy")
    InstallResult install(List<File> apkFiles, String expectedPackage) throws Exception {
        if (apkFiles == null || apkFiles.isEmpty()) {
            throw new IOException("No APK files to install");
        }
        IPackageManager packageManager = obtainPackageManager();
        IPackageInstaller packageInstaller = obtainPackageInstaller(packageManager);
        boolean isRoot = getShizukuUid() == 0;
        int userId = isRoot ? Process.myUid() / 100000 : 0;
        String attributionTag = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? app.getAttributionTag() : null;

        PackageInstaller wrappedInstaller = createPackageInstaller(
                packageInstaller,
                PLAY_STORE_PACKAGE,
                attributionTag,
                userId
        );

        PackageInstaller.SessionParams params =
                new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        try {
            params.setAppPackageName(expectedPackage);
        } catch (Throwable ignored) {
        }
        params.setInstallReason(PackageManager.INSTALL_REASON_USER);
        setSessionInstallerPackageName(params);
        setSessionPackageSource(params);
        if (Build.VERSION.SDK_INT >= 34) {
            params.setRequestUpdateOwnership(true);
        }
        applyReplaceExistingFlag(params);

        int sessionId = wrappedInstaller.createSession(params);
        IPackageInstallerSession sessionBinder = IPackageInstallerSession.Stub.asInterface(
                new ShizukuBinderWrapper(packageInstaller.openSession(sessionId).asBinder())
        );
        PackageInstaller.Session session = createSession(sessionBinder);

        try {
            ApkSessionWriter.writeApks(session, apkFiles);

            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<InstallResult> resultRef = new AtomicReference<>();
            IntentSender sender = createIntentSender(intent -> {
                int status = intent.getIntExtra(
                        PackageInstaller.EXTRA_STATUS,
                        PackageInstaller.STATUS_FAILURE
                );
                String message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
                resultRef.set(new InstallResult(status, message));
                latch.countDown();
            });

            session.commit(sender);
            if (!latch.await(5, TimeUnit.MINUTES)) {
                throw new IOException("Timed out waiting for Shizuku install result");
            }

            InstallResult result = resultRef.get();
            if (result == null) {
                throw new IOException("No Shizuku install result was returned");
            }
            if (result.status != PackageInstaller.STATUS_SUCCESS) {
                throw new InstallerOperationException(result.status, result.message);
            }
            return result;
        } finally {
            try {
                session.close();
            } catch (Throwable ignored) {
            }
        }
    }

    private IPackageManager obtainPackageManager() throws IOException {
        IBinder binder = SystemServiceHelper.getSystemService("package");
        if (binder == null) {
            throw new IOException("Package service unavailable");
        }
        return IPackageManager.Stub.asInterface(new ShizukuBinderWrapper(binder));
    }

    private IPackageInstaller obtainPackageInstaller(IPackageManager manager) throws IOException {
        try {
            IPackageInstaller installer = manager.getPackageInstaller();
            return IPackageInstaller.Stub.asInterface(new ShizukuBinderWrapper(installer.asBinder()));
        } catch (RemoteException error) {
            throw new IOException(error);
        }
    }

    private static void setSessionInstallerPackageName(PackageInstaller.SessionParams params) {
        try {
            params.setInstallerPackageName(PLAY_STORE_PACKAGE);
        } catch (Throwable ignored) {
        }
    }

    private static void setSessionPackageSource(PackageInstaller.SessionParams params) {
        try {
            params.setPackageSource(PackageInstaller.PACKAGE_SOURCE_STORE);
        } catch (Throwable ignored) {
        }
    }

    private int getShizukuUid() {
        try {
            return Shizuku.getUid();
        } catch (Throwable ignored) {
            return -1;
        }
    }

    private static PackageInstaller createPackageInstaller(
            IPackageInstaller remote,
            String installerPackageName,
            String installerAttributionTag,
            int userId
    ) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Constructor<PackageInstaller> ctor = PackageInstaller.class.getDeclaredConstructor(
                        IPackageInstaller.class,
                        String.class,
                        String.class,
                        int.class
                );
                ctor.setAccessible(true);
                return ctor.newInstance(remote, installerPackageName, installerAttributionTag, userId);
            } else {
                Constructor<PackageInstaller> ctor = PackageInstaller.class.getDeclaredConstructor(
                        IPackageInstaller.class,
                        String.class,
                        int.class
                );
                ctor.setAccessible(true);
                return ctor.newInstance(remote, installerPackageName, userId);
            }
        } catch (ReflectiveOperationException error) {
            throw new RuntimeException(error);
        }
    }

    private static PackageInstaller.Session createSession(IPackageInstallerSession remote) {
        try {
            Constructor<PackageInstaller.Session> ctor =
                    PackageInstaller.Session.class.getDeclaredConstructor(IPackageInstallerSession.class);
            ctor.setAccessible(true);
            return ctor.newInstance(remote);
        } catch (ReflectiveOperationException error) {
            throw new RuntimeException(error);
        }
    }

    @SuppressLint("DiscouragedPrivateApi")
    private static void applyReplaceExistingFlag(PackageInstaller.SessionParams params) {
        try {
            Field field = PackageInstaller.SessionParams.class.getDeclaredField("installFlags");
            field.setAccessible(true);
            field.setInt(params, field.getInt(params) | INSTALL_REPLACE_EXISTING);
        } catch (Throwable ignored) {
        }
    }

    private interface IntentCallback {
        void onIntent(Intent intent);
    }

    private static IntentSender createIntentSender(IntentCallback callback) {
        android.content.IIntentSender.Stub binder = new android.content.IIntentSender.Stub() {
            @Override
            public int send(
                    int code,
                    Intent intent,
                    String resolvedType,
                    android.content.IIntentReceiver finishedReceiver,
                    String requiredPermission,
                    Bundle options
            ) {
                if (intent != null) callback.onIntent(intent);
                return 0;
            }

            @Override
            public void send(
                    int code,
                    Intent intent,
                    String resolvedType,
                    IBinder whitelistToken,
                    android.content.IIntentReceiver finishedReceiver,
                    String requiredPermission,
                    Bundle options
            ) {
                if (intent != null) callback.onIntent(intent);
            }
        };

        try {
            Constructor<IntentSender> ctor =
                    IntentSender.class.getDeclaredConstructor(android.content.IIntentSender.class);
            ctor.setAccessible(true);
            return ctor.newInstance(binder);
        } catch (ReflectiveOperationException error) {
            throw new RuntimeException(error);
        }
    }

    static class InstallResult {
        final int status;
        final String message;

        InstallResult(int status, String message) {
            this.status = status;
            this.message = message;
        }
    }

    static class InstallerOperationException extends Exception {
        final int status;

        InstallerOperationException(int status, String message) {
            super(message);
            this.status = status;
        }
    }
}
