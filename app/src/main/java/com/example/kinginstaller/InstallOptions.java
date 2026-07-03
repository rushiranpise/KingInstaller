package com.example.kinginstaller;

class InstallOptions {
    final String installerPackageName;
    final int installReason;
    final int packageSource;
    final boolean allowTestOnly;
    final boolean bypassLowTargetSdk;
    final boolean grantAllPermissions;
    final boolean requestUpdateOwnership;

    InstallOptions(
            String installerPackageName,
            int installReason,
            int packageSource,
            boolean allowTestOnly,
            boolean bypassLowTargetSdk,
            boolean grantAllPermissions,
            boolean requestUpdateOwnership
    ) {
        this.installerPackageName = installerPackageName;
        this.installReason = installReason;
        this.packageSource = packageSource;
        this.allowTestOnly = allowTestOnly;
        this.bypassLowTargetSdk = bypassLowTargetSdk;
        this.grantAllPermissions = grantAllPermissions;
        this.requestUpdateOwnership = requestUpdateOwnership;
    }
}
