package com.example.kinginstaller;

class InstallOptions {
    final String installerPackageName;
    final int installReason;
    final int packageSource;
    final int targetUserId;
    final boolean allowTestOnly;
    final boolean bypassLowTargetSdk;
    final boolean grantAllPermissions;
    final boolean requestUpdateOwnership;
    final boolean installForAllUsers;
    final boolean allowDowngrade;
    final boolean allowRestrictedPermissions;
    final boolean disableVerification;
    final boolean enableRollback;
    final boolean fromAdb;
    final boolean bypassPlayProtect;
    final boolean privateSpaceInstall;

    InstallOptions(
            String installerPackageName,
            int installReason,
            int packageSource,
            int targetUserId,
            boolean allowTestOnly,
            boolean bypassLowTargetSdk,
            boolean grantAllPermissions,
            boolean requestUpdateOwnership,
            boolean installForAllUsers,
            boolean allowDowngrade,
            boolean allowRestrictedPermissions,
            boolean disableVerification,
            boolean enableRollback,
            boolean fromAdb,
            boolean bypassPlayProtect,
            boolean privateSpaceInstall
    ) {
        this.installerPackageName = installerPackageName;
        this.installReason = installReason;
        this.packageSource = packageSource;
        this.targetUserId = targetUserId;
        this.allowTestOnly = allowTestOnly;
        this.bypassLowTargetSdk = bypassLowTargetSdk;
        this.grantAllPermissions = grantAllPermissions;
        this.requestUpdateOwnership = requestUpdateOwnership;
        this.installForAllUsers = installForAllUsers;
        this.allowDowngrade = allowDowngrade;
        this.allowRestrictedPermissions = allowRestrictedPermissions;
        this.disableVerification = disableVerification;
        this.enableRollback = enableRollback;
        this.fromAdb = fromAdb;
        this.bypassPlayProtect = bypassPlayProtect;
        this.privateSpaceInstall = privateSpaceInstall;
    }

    InstallOptions withoutPrivilegedOptions() {
        return new InstallOptions(
                installerPackageName,
                installReason,
                packageSource,
                0,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false
        );
    }
}
