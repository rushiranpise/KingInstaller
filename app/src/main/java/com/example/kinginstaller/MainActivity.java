package com.example.kinginstaller;

import android.app.Activity;
import android.content.ClipData;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.content.pm.InstallSourceInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.FileProvider;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.radiobutton.MaterialRadioButton;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.lsposed.hiddenapibypass.HiddenApiBypass;

import rikka.shizuku.Shizuku;
import rikka.shizuku.ShizukuProvider;
import rikka.sui.Sui;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "KingInstaller";
    private static final String PLAY_STORE_PACKAGE = "com.android.vending";
    private static final String GOOGLE_PACKAGE_INSTALLER = "com.google.android.packageinstaller";
    private static final String TEMP_DIR_NAME = "apk";
    private static final int SHIZUKU_PERMISSION_REQUEST = 8201;
    private static final String APK_MIME_TYPE = "application/vnd.android.package-archive";
    private static final String PREF_THEME_MODE = "theme_mode";
    private static final String PREF_INSTALL_ROUTE = "install_route";
    private static final String STATE_SELECTED_URI = "selected_uri";
    private static final long ROOT_COMMAND_TIMEOUT_MS = 1500;
    private static final int PACKAGE_VISIBILITY_CHECK_ATTEMPTS = 8;
    private static final long PACKAGE_VISIBILITY_RETRY_DELAY_MS = 500;
    private static final int THEME_AUTO = 0;
    private static final int THEME_LIGHT = 1;
    private static final int THEME_DARK = 2;
    private static final int ROUTE_NORMAL_NO_ROOT = 0;
    private static final int ROUTE_NORMAL_SHIZUKU = 1;
    private static final int ROUTE_NORMAL_ROOT = 2;
    private static final int ROUTE_OEM_NO_ROOT = 3;
    private static final int ROUTE_OEM_ROOT = 4;
    private static final String[] ROOT_MANAGER_PACKAGES = {
            "com.topjohnwu.magisk",
            "io.github.huskydg.magisk",
            "me.weishu.kernelsu",
            "io.github.rifsxd.ksunext",
            "me.bmax.apatch",
            "eu.chainfire.supersu",
            "com.noshufou.android.su",
            "com.koushikdutta.superuser",
            "com.thirdparty.superuser",
            "com.kingroot.kinguser",
            "com.kingo.root"
    };
    private static final String[] ROOT_BINARY_PATHS = {
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/su/bin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/system_ext/bin/su",
            "/vendor/bin/su",
            "/product/bin/su",
            "/odm/bin/su",
            "/data/local/su",
            "/data/local/bin/su",
            "/data/local/xbin/su",
            "/debug_ramdisk/su"
    };
    private static final String[] ROOT_ARTIFACT_PATHS = {
            "/data/adb/magisk",
            "/data/adb/ksu",
            "/data/adb/ksud",
            "/data/adb/ap",
            "/data/adb/apd",
            "/data/adb/modules",
            "/metadata/adb/magisk",
            "/metadata/adb/ksu",
            "/metadata/adb/ap"
    };
    private static final String[] ROOT_PROPERTY_KEYS = {
            "ro.magisk.version",
            "ro.kernelsu.version",
            "ro.ksu.version",
            "ro.apatch.version",
            "init.svc.ksud",
            "init.svc.apd"
    };

    private EditText pathEdit;
    private TextView statusText;
    private TextView apkInfoText;
    private RadioGroup modeGroup;
    private MaterialRadioButton normalNoRootMode;
    private MaterialRadioButton normalShizukuMode;
    private MaterialRadioButton normalRootMode;
    private MaterialRadioButton oemNoRootMode;
    private MaterialRadioButton oemRootMode;
    private Button installButton;
    private Button openButton;

    private ApkSet selectedApkSet;
    private Uri selectedSourceUri;
    private String selectedAppName;
    private String selectedPackageName;
    private String selectedLabel;
    private boolean forceRootEnabled;
    private boolean pendingInstallVerification;
    private boolean updatingMode;
    private int selectedInstallRoute = ROUTE_NORMAL_NO_ROOT;
    private int verificationSequence;
    private KingShizukuInstaller shizukuInstaller;

    private final ActivityResultLauncher<String[]> apkPicker =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) {
                    persistSelectedUriPermission(uri);
                    handleSelectedApk(uri);
                }
            });

    private final Shizuku.OnRequestPermissionResultListener shizukuPermissionListener =
            (requestCode, grantResult) -> {
                if (requestCode != SHIZUKU_PERMISSION_REQUEST) return;
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    setInstallRoute(ROUTE_NORMAL_SHIZUKU);
                    setStatus(getString(R.string.shizuku_permission_requested));
                } else {
                    setInstallRoute(ROUTE_NORMAL_NO_ROOT);
                    setStatus(getString(R.string.shizuku_not_ready));
                }
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        applySavedTheme();
        DynamicColors.applyToActivitiesIfAvailable(getApplication());
        super.onCreate(savedInstanceState);
        enableHiddenApiAccess();
        setContentView(R.layout.activity_main);
        MaterialToolbar toolbar = findViewById(R.id.topAppBar);
        setSupportActionBar(toolbar);

        initViews();
        initShizuku();
        bindControls();
        restoreModeState();
        updateGooglePackageInstallerStatus();
        if (restoreRetainedSelection()) {
            verifySelectedPackage();
        } else if (savedInstanceState != null && savedInstanceState.getString(STATE_SELECTED_URI) != null) {
            handleSelectedApk(Uri.parse(savedInstanceState.getString(STATE_SELECTED_URI)));
        } else {
            handleIncomingIntent(getIntent());
        }
    }

    private void enableHiddenApiAccess() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return;
        try {
            HiddenApiBypass.addHiddenApiExemptions("L");
        } catch (Throwable error) {
            Log.w(TAG, "Hidden API bypass initialization failed", error);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingIntent(intent);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (selectedSourceUri != null) {
            outState.putString(STATE_SELECTED_URI, selectedSourceUri.toString());
        }
    }

    @Override
    public Object onRetainCustomNonConfigurationInstance() {
        if (selectedApkSet == null) return null;
        return new RetainedSelection(
                selectedApkSet,
                selectedSourceUri,
                selectedAppName,
                selectedPackageName,
                selectedLabel
        );
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (pendingInstallVerification) {
            verifySelectedPackageWithRetry();
        } else {
            verifySelectedPackage();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener);
        } catch (Throwable ignored) {
        }
        if (!isChangingConfigurations()) {
            selectedApkSet = null;
            clearTempFile();
        }
    }

    private void initViews() {
        pathEdit = findViewById(R.id.pathTextEdit);
        statusText = findViewById(R.id.textViewError);
        apkInfoText = findViewById(R.id.textViewApkInfo);
        modeGroup = findViewById(R.id.modeGroup);
        normalNoRootMode = findViewById(R.id.radioNormalNoRoot);
        normalShizukuMode = findViewById(R.id.radioNormalShizuku);
        normalRootMode = findViewById(R.id.radioNormalRoot);
        oemNoRootMode = findViewById(R.id.radioOemNoRoot);
        oemRootMode = findViewById(R.id.radioOemRoot);
        installButton = findViewById(R.id.installButton);
        openButton = findViewById(R.id.openButton);
        updateOpenButton(false);
    }

    private void initShizuku() {
        try {
            boolean isSui = Sui.init(getPackageName());
            if (!isSui) {
                ShizukuProvider.requestBinderForNonProviderProcess(this);
            }
            Shizuku.addRequestPermissionResultListener(shizukuPermissionListener);
            shizukuInstaller = new KingShizukuInstaller(getApplication());
        } catch (Throwable error) {
            Log.w(TAG, "Shizuku initialization failed", error);
        }
    }

    private void bindControls() {
        findViewById(R.id.selectButton).setOnClickListener(v -> showFileChooser());

        findViewById(R.id.site_annexhack).setOnClickListener(v -> openUrl("https://inceptive.ru"));

        modeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (updatingMode) return;
            handleRouteSelection(routeFromCheckedId(checkedId));
        });

        installButton.setOnClickListener(v -> {
            if (!ensureApkSelected()) return;
            if (isShizukuRouteSelected()) {
                installAsShizuku();
            } else if (isRootRouteSelected()) {
                installAsRoot();
            } else {
                installAsKing();
            }
        });

        openButton.setOnClickListener(v -> openSelectedPackage());

        findViewById(R.id.resetButton).setOnClickListener(v -> openGooglePackageInstallerSettings());
    }

    private void restoreModeState() {
        SharedPreferences prefs = getPreferences(Activity.MODE_PRIVATE);
        int route = prefs.contains(PREF_INSTALL_ROUTE)
                ? prefs.getInt(PREF_INSTALL_ROUTE, ROUTE_NORMAL_NO_ROOT)
                : legacyRouteFromPrefs(prefs);
        if (route == ROUTE_NORMAL_SHIZUKU && !ensureShizukuReady(false)) {
            route = ROUTE_NORMAL_NO_ROOT;
        }
        if (isRootRoute(route) && !isDeviceRooted()) {
            route = fallbackRouteFor(route);
        }
        setInstallRoute(route);
    }

    private int legacyRouteFromPrefs(SharedPreferences prefs) {
        if (prefs.getBoolean("shizuku_trick_value", false)) {
            return ROUTE_NORMAL_SHIZUKU;
        }
        if (prefs.getBoolean("root_trick_value", false)) {
            return ROUTE_NORMAL_ROOT;
        }
        return prefs.getBoolean("oppo_trick_value", false)
                ? ROUTE_OEM_NO_ROOT
                : ROUTE_NORMAL_NO_ROOT;
    }

    private void handleRouteSelection(int route) {
        if (route == ROUTE_NORMAL_SHIZUKU && !ensureShizukuReady(true)) {
            setInstallRoute(selectedInstallRoute);
            return;
        }
        if (isRootRoute(route)) {
            if (!isDeviceRooted()) {
                Toast.makeText(getBaseContext(), R.string.device_not_rooted, Toast.LENGTH_SHORT).show();
                setInstallRoute(fallbackRouteFor(route));
                return;
            }
            if (isGooglePackageExist() && !forceRootEnabled) {
                setStatus(getString(R.string.root_method_warning));
                setInstallRoute(fallbackRouteFor(route));
                forceRootEnabled = true;
                return;
            }
            forceRootEnabled = true;
        }
        setInstallRoute(route);
    }

    private void setInstallRoute(int route) {
        selectedInstallRoute = normalizeInstallRoute(route);
        updatingMode = true;
        normalNoRootMode.setChecked(selectedInstallRoute == ROUTE_NORMAL_NO_ROOT);
        normalShizukuMode.setChecked(selectedInstallRoute == ROUTE_NORMAL_SHIZUKU);
        normalRootMode.setChecked(selectedInstallRoute == ROUTE_NORMAL_ROOT);
        oemNoRootMode.setChecked(selectedInstallRoute == ROUTE_OEM_NO_ROOT);
        oemRootMode.setChecked(selectedInstallRoute == ROUTE_OEM_ROOT);
        updatingMode = false;
        updateOemAliasState();
        saveModeState();
        updateInstallButtonLabel();
        Log.d(TAG, "install route=" + selectedInstallRoute);
    }

    private void updateInstallButtonLabel() {
        if (installButton == null) return;
        if (selectedInstallRoute == ROUTE_OEM_ROOT) {
            installButton.setText(R.string.install_with_oem_root);
        } else if (selectedInstallRoute == ROUTE_NORMAL_ROOT) {
            installButton.setText(R.string.install_with_root);
        } else if (selectedInstallRoute == ROUTE_NORMAL_SHIZUKU) {
            installButton.setText(R.string.install_with_shizuku);
        } else if (selectedInstallRoute == ROUTE_OEM_NO_ROOT) {
            installButton.setText(R.string.install_with_oem);
        } else {
            installButton.setText(R.string.install_with_normal);
        }
    }

    private void saveModeState() {
        getPreferences(Activity.MODE_PRIVATE).edit()
                .putInt(PREF_INSTALL_ROUTE, selectedInstallRoute)
                .putBoolean("oppo_trick_value", isOemRouteSelected())
                .putBoolean("root_trick_value", isRootRouteSelected())
                .putBoolean("shizuku_trick_value", isShizukuRouteSelected())
                .apply();
    }

    private int routeFromCheckedId(int checkedId) {
        if (checkedId == R.id.radioNormalShizuku) return ROUTE_NORMAL_SHIZUKU;
        if (checkedId == R.id.radioNormalRoot) return ROUTE_NORMAL_ROOT;
        if (checkedId == R.id.radioOemNoRoot) return ROUTE_OEM_NO_ROOT;
        if (checkedId == R.id.radioOemRoot) return ROUTE_OEM_ROOT;
        return ROUTE_NORMAL_NO_ROOT;
    }

    private int normalizeInstallRoute(int route) {
        if (route >= ROUTE_NORMAL_NO_ROOT && route <= ROUTE_OEM_ROOT) {
            return route;
        }
        return ROUTE_NORMAL_NO_ROOT;
    }

    private int fallbackRouteFor(int route) {
        return route == ROUTE_OEM_ROOT ? ROUTE_OEM_NO_ROOT : ROUTE_NORMAL_NO_ROOT;
    }

    private boolean isRootRoute(int route) {
        return route == ROUTE_NORMAL_ROOT || route == ROUTE_OEM_ROOT;
    }

    private boolean isRootRouteSelected() {
        return isRootRoute(selectedInstallRoute);
    }

    private boolean isShizukuRouteSelected() {
        return selectedInstallRoute == ROUTE_NORMAL_SHIZUKU;
    }

    private boolean isOemRouteSelected() {
        return selectedInstallRoute == ROUTE_OEM_NO_ROOT || selectedInstallRoute == ROUTE_OEM_ROOT;
    }

    private void updateGooglePackageInstallerStatus() {
        if (isGooglePackageExist()) {
            setStatus(getString(R.string.google_package_installer_is_installed));
        } else {
            setStatus(getString(R.string.missing_google_package_installer));
        }
    }

    public boolean isGooglePackageExist() {
        try {
            getPackageManager().getPackageInfo(GOOGLE_PACKAGE_INSTALLER, PackageManager.GET_META_DATA);
            return true;
        } catch (PackageManager.NameNotFoundException ignored) {
            return false;
        }
    }

    public void updateOemAliasState() {
        ComponentName oppoTrickFlagged =
                new ComponentName(getPackageName(), getPackageName() + ".OppoTrick");
        getPackageManager().setComponentEnabledSetting(
                oppoTrickFlagged,
                isOemRouteSelected()
                        ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
        );
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.user_info_menu, menu);
        updateThemeMenuItem(menu.findItem(R.id.action_theme));
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_theme) {
            cycleThemeMode();
            return true;
        }
        if (item.getItemId() == R.id.action_search) {
            openUrl("https://gitlab.com/annexhack/king-installer");
            return true;
        }
        if (item.getItemId() == R.id.action_search2) {
            openUrl("https://github.com/fcaronte/KingInstaller");
            return true;
        }
        if (item.getItemId() == R.id.action_search3) {
            openUrl("https://github.com/Rikj000/KingInstaller");
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void applySavedTheme() {
        AppCompatDelegate.setDefaultNightMode(appCompatNightModeFor(getThemeMode()));
    }

    private void cycleThemeMode() {
        int nextMode = (getThemeMode() + 1) % 3;
        getPreferences(Activity.MODE_PRIVATE).edit()
                .putInt(PREF_THEME_MODE, nextMode)
                .apply();
        AppCompatDelegate.setDefaultNightMode(appCompatNightModeFor(nextMode));
        invalidateOptionsMenu();
    }

    private int getThemeMode() {
        return getPreferences(Activity.MODE_PRIVATE).getInt(PREF_THEME_MODE, THEME_AUTO);
    }

    private int appCompatNightModeFor(int themeMode) {
        if (themeMode == THEME_LIGHT) {
            return AppCompatDelegate.MODE_NIGHT_NO;
        }
        if (themeMode == THEME_DARK) {
            return AppCompatDelegate.MODE_NIGHT_YES;
        }
        return AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
    }

    private void updateThemeMenuItem(MenuItem item) {
        if (item == null) return;
        int titleRes;
        int themeMode = getThemeMode();
        if (themeMode == THEME_LIGHT) {
            titleRes = R.string.theme_light;
        } else if (themeMode == THEME_DARK) {
            titleRes = R.string.theme_dark;
        } else {
            titleRes = R.string.theme_auto;
        }
        item.setTitle(titleRes);
        item.setContentDescription(getString(titleRes));
        Drawable icon = item.getIcon();
        if (icon != null) {
            icon.mutate().setTint(resolveThemeColor(com.google.android.material.R.attr.colorOnSurface));
        }
    }

    private int resolveThemeColor(int attr) {
        TypedValue value = new TypedValue();
        getTheme().resolveAttribute(attr, value, true);
        if (value.resourceId != 0) {
            return getColor(value.resourceId);
        }
        return value.data;
    }

    private void installAsRoot() {
        markInstallVerificationPending();
        setInstallButtonsEnabled(false);
        new Thread(() -> {
            try {
                StreamLogs logs = runSuWithCmd(buildRootInstallCommand());
                runOnUiThread(() -> {
                    setInstallButtonsEnabled(true);
                    if (!logs.getErrorStreamLog().isEmpty()) {
                        pendingInstallVerification = false;
                        setStatus(logs.getStreamLogsWithLabels());
                    } else {
                        setStatus(logs.getStreamLogsWithLabels());
                        verifySelectedPackageWithRetry();
                    }
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    pendingInstallVerification = false;
                    setInstallButtonsEnabled(true);
                    setStatus(getString(R.string.install_failed, error.toString()));
                });
            }
        }).start();
    }

    private String buildRootInstallCommand() {
        StringBuilder command = new StringBuilder();
        if (selectedApkSet.isSingleApk()) {
            command.append("pm install -t -i ")
                    .append(shellQuote(PLAY_STORE_PACKAGE))
                    .append(" -r ")
                    .append(shellQuote(selectedApkSet.getApkFiles().get(0).getAbsolutePath()));
        } else {
            command.append("pm install-multiple -t -i ")
                    .append(shellQuote(PLAY_STORE_PACKAGE))
                    .append(" -r");
            for (File apkFile : selectedApkSet.getApkFiles()) {
                command.append(' ').append(shellQuote(apkFile.getAbsolutePath()));
            }
        }
        return command.append(" && ")
                .append(buildSetInstallerCommand(selectedPackageName))
                .toString();
    }

    private String buildSetInstallerCommand(String packageName) {
        return "(cmd package set-installer " + shellQuote(packageName) + " " +
                shellQuote(PLAY_STORE_PACKAGE) + " 2>&1 || " +
                "pm set-installer " + shellQuote(packageName) + " " +
                shellQuote(PLAY_STORE_PACKAGE) + " 2>&1 || true)";
    }

    private void installAsShizuku() {
        if (!ensureShizukuReady(true)) return;
        markInstallVerificationPending();
        setInstallButtonsEnabled(false);
        new Thread(() -> {
            try {
                if (shizukuInstaller == null) {
                    shizukuInstaller = new KingShizukuInstaller(getApplication());
                }
                shizukuInstaller.install(selectedApkSet.getApkFiles(), selectedPackageName);
                runOnUiThread(() -> {
                    setInstallButtonsEnabled(true);
                    Toast.makeText(this, R.string.shizuku_install_success, Toast.LENGTH_SHORT).show();
                    verifySelectedPackageWithRetry();
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    pendingInstallVerification = false;
                    setInstallButtonsEnabled(true);
                    setStatus(getString(R.string.install_failed, error.getMessage() == null ? error.toString() : error.getMessage()));
                });
            }
        }).start();
    }

    private void installAsKing() {
        if (!selectedApkSet.isSingleApk()) {
            installSplitSessionWithUserAction();
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
            Uri fileUri = FileProvider.getUriForFile(
                    getApplicationContext(),
                    getPackageName() + ".provider",
                    selectedApkSet.getApkFiles().get(0)
            );
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setDataAndType(fileUri, APK_MIME_TYPE);
            intent.setClipData(ClipData.newRawUri("apk", fileUri));
            intent.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true);
            intent.putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME, PLAY_STORE_PACKAGE);
            grantReadPermissionToInstallers(intent, fileUri);
            setStatus(getString(R.string.install_started_no_root));
            markInstallVerificationPending();
            if (isOemRouteSelected()) {
                Intent chooser = Intent.createChooser(intent, getString(R.string.choose_package_installer));
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                chooser.setClipData(ClipData.newRawUri("apk", fileUri));
                startActivity(chooser);
            } else {
                startActivity(intent);
            }
        } catch (Exception error) {
            pendingInstallVerification = false;
            setStatus(getString(R.string.install_failed, error.toString()));
        }
    }

    private void installSplitSessionWithUserAction() {
        markInstallVerificationPending();
        setInstallButtonsEnabled(false);
        new Thread(() -> {
            PackageInstaller packageInstaller = getPackageManager().getPackageInstaller();
            int sessionId = -1;
            PackageInstaller.Session session = null;
            boolean committed = false;
            try {
                PackageInstaller.SessionParams params =
                        new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                try {
                    params.setAppPackageName(selectedPackageName);
                } catch (Throwable ignored) {
                }
                params.setInstallReason(PackageManager.INSTALL_REASON_USER);

                sessionId = packageInstaller.createSession(params);
                session = packageInstaller.openSession(sessionId);
                ApkSessionWriter.writeApks(session, selectedApkSet.getApkFiles());

                CountDownLatch latch = new CountDownLatch(1);
                AtomicReference<SessionInstallResult> resultRef = new AtomicReference<>();
                IntentSender sender = createIntentSender(intent -> {
                    int status = intent.getIntExtra(
                            PackageInstaller.EXTRA_STATUS,
                            PackageInstaller.STATUS_FAILURE
                    );
                    if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                        Object confirmation = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                        if (confirmation instanceof Intent) {
                            runOnUiThread(() -> {
                                Intent confirmationIntent = (Intent) confirmation;
                                confirmationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                startActivity(confirmationIntent);
                            });
                        }
                        return;
                    }
                    String message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
                    resultRef.set(new SessionInstallResult(status, message));
                    latch.countDown();
                });

                session.commit(sender);
                committed = true;
                if (!latch.await(5, TimeUnit.MINUTES)) {
                    throw new IOException("Timed out waiting for package installer result");
                }

                SessionInstallResult result = resultRef.get();
                if (result == null) {
                    throw new IOException("No package installer result was returned");
                }
                if (result.status != PackageInstaller.STATUS_SUCCESS) {
                    throw new IOException(result.message == null ? "Package installer failed" : result.message);
                }
                runOnUiThread(() -> {
                    setInstallButtonsEnabled(true);
                    verifySelectedPackageWithRetry();
                });
            } catch (Exception error) {
                pendingInstallVerification = false;
                if (!committed && sessionId != -1) {
                    try {
                        packageInstaller.abandonSession(sessionId);
                    } catch (Throwable ignored) {
                    }
                }
                runOnUiThread(() -> {
                    setInstallButtonsEnabled(true);
                    setStatus(getString(
                            R.string.install_failed,
                            error.getMessage() == null ? error.toString() : error.getMessage()
                    ));
                });
            } finally {
                if (session != null) {
                    try {
                        session.close();
                    } catch (Throwable ignored) {
                    }
                }
            }
        }).start();
        setStatus(getString(R.string.split_install_started));
    }

    private void grantReadPermissionToInstallers(Intent intent, Uri apkUri) {
        List<ResolveInfo> installers = getPackageManager()
                .queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
        for (ResolveInfo installer : installers) {
            if (installer.activityInfo == null || installer.activityInfo.packageName == null) continue;
            grantUriPermission(
                    installer.activityInfo.packageName,
                    apkUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
            );
        }
    }

    private void showFileChooser() {
        try {
            apkPicker.launch(new String[]{
                    "application/vnd.android.package-archive",
                    "application/zip",
                    "application/x-zip-compressed",
                    "application/octet-stream",
                    "*/*"
            });
        } catch (ActivityNotFoundException error) {
            Toast.makeText(this, "Please install a File Manager.", Toast.LENGTH_SHORT).show();
        }
    }

    private void handleIncomingIntent(Intent intent) {
        if (intent == null) return;
        Uri uri = null;
        if (ApkReceiverActivity.ACTION_OPEN_APK.equals(intent.getAction())) {
            Object extra = intent.getParcelableExtra(ApkReceiverActivity.EXTRA_APK_URI);
            if (extra instanceof Uri) {
                uri = (Uri) extra;
            }
        } else if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            uri = intent.getData();
        } else if (Intent.ACTION_SEND.equals(intent.getAction())) {
            Object stream = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (stream instanceof Uri) {
                uri = (Uri) stream;
            }
        }
        if (uri != null) {
            persistSelectedUriPermission(uri);
            handleSelectedApk(uri);
        }
    }

    private boolean restoreRetainedSelection() {
        Object retained = getLastCustomNonConfigurationInstance();
        if (!(retained instanceof RetainedSelection)) {
            return false;
        }
        RetainedSelection selection = (RetainedSelection) retained;
        selectedApkSet = selection.apkSet;
        selectedSourceUri = selection.sourceUri;
        selectedAppName = selection.appName;
        selectedPackageName = selection.packageName;
        selectedLabel = selection.label;
        renderSelectedApkInfo();
        return true;
    }

    private void handleSelectedApk(Uri uri) {
        try {
            selectedApkSet = null;
            clearTempFile();
            selectedApkSet = ApkSetExtractor.fromUri(this, uri, TEMP_DIR_NAME);
            selectedSourceUri = uri;
            selectedAppName = selectedApkSet.getAppName();
            selectedPackageName = selectedApkSet.getPackageName();
            selectedLabel = selectedApkSet.getLabel();
            renderSelectedApkInfo();
            verifySelectedPackage();
        } catch (Exception error) {
            selectedApkSet = null;
            selectedSourceUri = null;
            selectedAppName = null;
            selectedPackageName = null;
            selectedLabel = null;
            pathEdit.setText("");
            apkInfoText.setText(R.string.no_apk_selected);
            updateOpenButton(false);
            setStatus(getString(
                    R.string.install_failed,
                    error.getMessage() == null ? error.toString() : error.getMessage()
            ));
        }
    }

    private void renderSelectedApkInfo() {
        if (selectedApkSet == null) return;
        pathEdit.setText(selectedLabel);
        updateOpenButton(false);
        String appName = selectedAppName == null ? selectedLabel : selectedAppName;
        String version = selectedApkSet.getVersionName() == null
                ? getString(R.string.install_source_unknown)
                : selectedApkSet.getVersionName();
        int apkCount = selectedApkSet.getApkCount();
        apkInfoText.setText(apkCount > 1
                ? getString(R.string.apk_info_bundle, appName, selectedPackageName, version, apkCount)
                : getString(R.string.apk_info_single, appName, selectedPackageName, version));
    }

    private void persistSelectedUriPermission(Uri uri) {
        if (uri == null || !"content".equals(uri.getScheme())) return;
        try {
            getContentResolver().takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
            );
        } catch (SecurityException | IllegalArgumentException ignored) {
        }
    }

    private boolean ensureApkSelected() {
        if (selectedApkSet == null
                || selectedApkSet.getApkFiles().isEmpty()
                || selectedPackageName == null) {
            Toast.makeText(this, R.string.select_a_file, Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    private void verifySelectedPackage() {
        if (selectedPackageName == null) {
            updateOpenButton(false);
            return;
        }
        verifyInstallSource(selectedPackageName);
    }

    private void verifySelectedPackageWithRetry() {
        if (selectedPackageName == null) return;
        String packageName = selectedPackageName;
        int sequence = ++verificationSequence;
        new Thread(() -> {
            for (int attempt = 0; attempt < PACKAGE_VISIBILITY_CHECK_ATTEMPTS; attempt++) {
                if (isPackageVisible(packageName)) {
                    break;
                }
                try {
                    Thread.sleep(PACKAGE_VISIBILITY_RETRY_DELAY_MS);
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            runOnUiThread(() -> {
                if (sequence != verificationSequence || !packageName.equals(selectedPackageName)) {
                    return;
                }
                pendingInstallVerification = false;
                verifyInstallSource(packageName);
            });
        }).start();
    }

    private boolean isPackageVisible(String packageName) {
        try {
            getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException ignored) {
            return false;
        }
    }

    private void markInstallVerificationPending() {
        pendingInstallVerification = true;
        updateOpenButton(false);
    }

    private void verifyInstallSource(String packageName) {
        try {
            PackageManager pm = getPackageManager();
            pm.getPackageInfo(packageName, 0);
            updateOpenButton(true);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                InstallSourceInfo info = pm.getInstallSourceInfo(packageName);
                setStatus(getString(
                        R.string.install_source_status,
                        packageName,
                        displaySource(info.getInstallingPackageName())
                ));
            } else {
                setStatus(getString(
                        R.string.install_source_status_legacy,
                        packageName,
                        displaySource(pm.getInstallerPackageName(packageName))
                ));
            }
        } catch (PackageManager.NameNotFoundException ignored) {
            updateOpenButton(false);
            setStatus(getString(R.string.install_source_not_installed, packageName));
        } catch (Exception error) {
            updateOpenButton(false);
            setStatus(error.toString());
        }
    }

    private void openSelectedPackage() {
        if (selectedPackageName == null) {
            Toast.makeText(this, R.string.select_a_file, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Intent launchIntent = getPackageManager().getLaunchIntentForPackage(selectedPackageName);
            if (launchIntent == null) {
                if (!isPackageVisible(selectedPackageName)) {
                    updateOpenButton(false);
                    setStatus(getString(R.string.install_source_not_installed, selectedPackageName));
                } else {
                    setStatus(getString(R.string.open_installed_app_unavailable));
                }
                return;
            }
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(launchIntent);
        } catch (Exception error) {
            setStatus(error.toString());
        }
    }

    private void updateOpenButton(boolean enabled) {
        if (openButton != null) {
            openButton.setEnabled(enabled);
        }
    }

    private String displaySource(String packageName) {
        if (packageName == null || packageName.trim().isEmpty()) {
            return getString(R.string.install_source_unknown);
        }
        if (PLAY_STORE_PACKAGE.equals(packageName)) {
            return "Google Play Store (" + packageName + ")";
        }
        if ("com.android.vending".equals(packageName)) {
            return "Google Play Store (" + packageName + ")";
        }
        if ("com.android.shell".equals(packageName)) {
            return "Android shell (" + packageName + ")";
        }
        return packageName;
    }

    private boolean ensureShizukuReady(boolean requestPermission) {
        try {
            if (Shizuku.isPreV11()) {
                setStatus(getString(R.string.shizuku_not_ready));
                return false;
            }
            if (!Shizuku.pingBinder()) {
                setStatus(getString(R.string.shizuku_not_ready));
                return false;
            }
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                return true;
            }
            if (requestPermission && !Shizuku.shouldShowRequestPermissionRationale()) {
                Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST);
                setStatus(getString(R.string.shizuku_permission_requested));
            } else {
                setStatus(getString(R.string.shizuku_not_ready));
            }
            return false;
        } catch (Throwable error) {
            setStatus(getString(R.string.shizuku_not_ready));
            return false;
        }
    }

    private void openGooglePackageInstallerSettings() {
        if (!isGooglePackageExist()) {
            setStatus(getString(R.string.missing_google_package_installer));
            return;
        }
        try {
            Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + GOOGLE_PACKAGE_INSTALLER));
            startActivity(intent);
        } catch (Exception error) {
            setStatus(error.toString());
        }
    }

    public final void clearTempFile() {
        File dir = new File(getFilesDir(), TEMP_DIR_NAME);
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (selectedApkSet != null && selectedApkSet.getStorageRoot().equals(file)) continue;
            ApkSetExtractor.deleteRecursive(file);
        }
    }

    private void setInstallButtonsEnabled(boolean enabled) {
        installButton.setEnabled(enabled);
    }

    private void setStatus(String message) {
        statusText.setText(message == null ? "" : message);
    }

    private void openUrl(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse(url));
            startActivity(intent);
        } catch (Exception error) {
            setStatus(error.toString());
        }
    }

    private static class RetainedSelection {
        final ApkSet apkSet;
        final Uri sourceUri;
        final String appName;
        final String packageName;
        final String label;

        RetainedSelection(
                ApkSet apkSet,
                Uri sourceUri,
                String appName,
                String packageName,
                String label
        ) {
            this.apkSet = apkSet;
            this.sourceUri = sourceUri;
            this.appName = appName;
            this.packageName = packageName;
            this.label = label;
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
            java.lang.reflect.Constructor<IntentSender> ctor =
                    IntentSender.class.getDeclaredConstructor(android.content.IIntentSender.class);
            ctor.setAccessible(true);
            return ctor.newInstance(binder);
        } catch (ReflectiveOperationException error) {
            throw new RuntimeException(error);
        }
    }

    private static class SessionInstallResult {
        final int status;
        final String message;

        SessionInstallResult(int status, String message) {
            this.status = status;
            this.message = message;
        }
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    public static StreamLogs runSuWithCmd(String cmd) {
        StreamLogs streamLogs = new StreamLogs();
        streamLogs.setOutputStreamLog(cmd);

        try {
            Process su = Runtime.getRuntime().exec("su");
            try (DataOutputStream outputStream = new DataOutputStream(su.getOutputStream());
                 InputStream inputStream = su.getInputStream();
                 InputStream errorStream = su.getErrorStream()) {
                outputStream.writeBytes(cmd + "\n");
                outputStream.flush();
                outputStream.writeBytes("exit\n");
                outputStream.flush();

                try {
                    su.waitFor();
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                }
                streamLogs.setInputStreamLog(readStream(inputStream));
                streamLogs.setErrorStreamLog(readStream(errorStream));
            }
        } catch (IOException error) {
            streamLogs.setErrorStreamLog(error.toString());
        }

        return streamLogs;
    }

    public static String readStream(InputStream is) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int length;
        while ((length = is.read(buffer)) != -1) {
            byteArrayOutputStream.write(buffer, 0, length);
        }
        return byteArrayOutputStream.toString("UTF-8");
    }

    private boolean isDeviceRooted() {
        return hasRootBuildTags()
                || hasKnownRootManagerPackage()
                || hasRootBinary()
                || hasRootArtifact()
                || hasRootSystemProperty()
                || canFindSu();
    }

    private static boolean hasRootBuildTags() {
        String buildTags = Build.TAGS;
        return buildTags != null && buildTags.contains("test-keys");
    }

    private boolean hasKnownRootManagerPackage() {
        PackageManager packageManager = getPackageManager();
        for (String packageName : ROOT_MANAGER_PACKAGES) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0));
                } else {
                    packageManager.getPackageInfo(packageName, 0);
                }
                return true;
            } catch (PackageManager.NameNotFoundException ignored) {
            } catch (Throwable error) {
                Log.d(TAG, "Root manager package check failed for " + packageName, error);
            }
        }
        return false;
    }

    private static boolean hasRootBinary() {
        for (String path : ROOT_BINARY_PATHS) {
            File file = new File(path);
            if (file.exists() && (file.canExecute() || path.endsWith(".apk") || path.endsWith("/su"))) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasRootArtifact() {
        for (String path : ROOT_ARTIFACT_PATHS) {
            if (new File(path).exists()) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasRootSystemProperty() {
        StringBuilder command = new StringBuilder();
        for (String key : ROOT_PROPERTY_KEYS) {
            command.append("getprop ").append(key).append("; ");
        }
        String output = runCommandForOutput(new String[]{"sh", "-c", command.toString()}, 1000);
        for (String value : output.split("\\R")) {
            value = value.trim();
            if (!value.isEmpty() && !"stopped".equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    private static boolean canFindSu() {
        return commandSucceeds(new String[]{
                "sh",
                "-c",
                "command -v su >/dev/null 2>&1 || which su >/dev/null 2>&1 || su -v >/dev/null 2>&1 || su -V >/dev/null 2>&1"
        });
    }

    private static boolean commandSucceeds(String[] command) {
        Process process = null;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).start();
            if (!process.waitFor(ROOT_COMMAND_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (Throwable ignored) {
            return false;
        } finally {
            if (process != null) process.destroy();
        }
    }

    private static String runCommandForOutput(String[] command, long timeoutMs) {
        Process process = null;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).start();
            if (!process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                return "";
            }
            if (process.exitValue() != 0) {
                return "";
            }
            return readStream(process.getInputStream());
        } catch (Throwable ignored) {
            return "";
        } finally {
            if (process != null) process.destroy();
        }
    }
}
