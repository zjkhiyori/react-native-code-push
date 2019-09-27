package com.microsoft.codepush.react;

import com.facebook.react.ReactInstanceManager;
import com.facebook.react.ReactPackage;
import com.facebook.react.bridge.JavaScriptModule;
import com.facebook.react.bridge.NativeModule;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.devsupport.DevInternalSettings;
import com.facebook.react.devsupport.interfaces.DevSupportManager;
import com.facebook.react.uimanager.ViewManager;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class CodePush implements ReactPackage {

//    private static boolean sIsRunningBinaryVersion = false;
    private HashMap<String, Boolean> sIsRunningBinaryVersionArr = new HashMap<>();
//    private static boolean sNeedToReportRollback = false;
    private HashMap<String, Boolean> sNeedToReportRollbackArr = new HashMap<>();
    private static boolean sTestConfigurationFlag = false;
    private static String sAppVersion = null;

//    private boolean mDidUpdate = false;
    private HashMap<String, Boolean> mDidUpdateArr = new HashMap<>();

//    private String mAssetsBundleFileName;

    // Helper classes.
    private CodePushUpdateManager mUpdateManager;
    private CodePushTelemetryManager mTelemetryManager;
    private SettingsManager mSettingsManager;

    // Config properties.
    private String mDeploymentKey;
    private static String mServerUrl = "https://codepush.azurewebsites.net/";

    private Context mContext;
    private final boolean mIsDebugMode;

    private static String mPublicKey;

    private static ReactInstanceHolder mReactInstanceHolder;
    private static CodePush mCurrentInstance;

    public CodePush(String deploymentKey, Context context) {
        this(deploymentKey, context, false);
    }

    public static String getServiceUrl() {
        return mServerUrl;
    }

    public CodePush(String deploymentKey, Context context, boolean isDebugMode) {
        mContext = context.getApplicationContext();

        mUpdateManager = new CodePushUpdateManager(context.getFilesDir().getAbsolutePath());
        mTelemetryManager = new CodePushTelemetryManager(mContext);
        mDeploymentKey = deploymentKey;
        mIsDebugMode = isDebugMode;
        mSettingsManager = new SettingsManager(mContext);

        if (sAppVersion == null) {
            try {
                PackageInfo pInfo = mContext.getPackageManager().getPackageInfo(mContext.getPackageName(), 0);
                sAppVersion = pInfo.versionName;
            } catch (PackageManager.NameNotFoundException e) {
                throw new CodePushUnknownException("Unable to get package info for " + mContext.getPackageName(), e);
            }
        }

        mCurrentInstance = this;

        clearDebugCacheIfNeeded(null, CodePushConstants.CODE_PUSH_COMMON_BUNDLE_FOLDER_PREFIX);
        initializeUpdateAfterRestart(CodePushConstants.CODE_PUSH_COMMON_BUNDLE_FOLDER_PREFIX);
    }

    public CodePush(String deploymentKey, Context context, boolean isDebugMode, String serverUrl) {
        this(deploymentKey, context, isDebugMode);
        mServerUrl = serverUrl;
    }

    public CodePush(String deploymentKey, Context context, boolean isDebugMode, int publicKeyResourceDescriptor) {
        this(deploymentKey, context, isDebugMode);

        mPublicKey = getPublicKeyByResourceDescriptor(publicKeyResourceDescriptor);
    }

    public CodePush(String deploymentKey, Context context, boolean isDebugMode, String serverUrl, Integer publicKeyResourceDescriptor) {
        this(deploymentKey, context, isDebugMode);

        if (publicKeyResourceDescriptor != null) {
            mPublicKey = getPublicKeyByResourceDescriptor(publicKeyResourceDescriptor);
        }

        mServerUrl = serverUrl;
    }

    private String getPublicKeyByResourceDescriptor(int publicKeyResourceDescriptor){
        String publicKey;
        try {
            publicKey = mContext.getString(publicKeyResourceDescriptor);
        } catch (Resources.NotFoundException e) {
            throw new CodePushInvalidPublicKeyException(
                    "Unable to get public key, related resource descriptor " +
                            publicKeyResourceDescriptor +
                            " can not be found", e
            );
        }

        if (publicKey.isEmpty()) {
            throw new CodePushInvalidPublicKeyException("Specified public key is empty");
        }
        return publicKey;
    }

    public void clearDebugCacheIfNeeded(ReactInstanceManager instanceManager, String pathPrefix) {
        boolean isLiveReloadEnabled = false;

        // Use instanceManager for checking if we use LiveRelaod mode. In this case we should not remove ReactNativeDevBundle.js file
        // because we get error with trying to get this after reloading. Issue: https://github.com/Microsoft/react-native-code-push/issues/1272
        if (instanceManager != null) {
            DevSupportManager devSupportManager = instanceManager.getDevSupportManager();
            if (devSupportManager != null) {
                DevInternalSettings devInternalSettings = (DevInternalSettings)devSupportManager.getDevSettings();
                isLiveReloadEnabled = devInternalSettings.isReloadOnJSChangeEnabled();
            }
        }

        if (mIsDebugMode && mSettingsManager.isPendingUpdate(null, pathPrefix) && !isLiveReloadEnabled) {
            // This needs to be kept in sync with https://github.com/facebook/react-native/blob/master/ReactAndroid/src/main/java/com/facebook/react/devsupport/DevSupportManager.java#L78
            File cachedDevBundle = new File(mContext.getFilesDir(), "ReactNativeDevBundle.js");
            if (cachedDevBundle.exists()) {
                cachedDevBundle.delete();
            }
        }
    }

    public boolean didUpdate(String pathPrefix) {
//        return mDidUpdate;
        return mDidUpdateArr.get(pathPrefix) == null ? false : mDidUpdateArr.get(pathPrefix);
    }

    public String getAppVersion() {
        return sAppVersion;
    }

//    public String getAssetsBundleFileName() {
//        return mAssetsBundleFileName;
//    }

    public String getPublicKey() {
        return mPublicKey;
    }

    long getBinaryResourcesModifiedTime() {
        try {
            String packageName = this.mContext.getPackageName();
            int codePushApkBuildTimeId = this.mContext.getResources().getIdentifier(CodePushConstants.CODE_PUSH_APK_BUILD_TIME_KEY, "string", packageName);
            // replace double quotes needed for correct restoration of long value from strings.xml
            // https://github.com/Microsoft/cordova-plugin-code-push/issues/264
            String codePushApkBuildTime = this.mContext.getResources().getString(codePushApkBuildTimeId).replaceAll("\"","");
            return Long.parseLong(codePushApkBuildTime);
        } catch (Exception e) {
            throw new CodePushUnknownException("Error in getting binary resources modified time", e);
        }
    }

    public String getPackageFolder(String pathPrefix) {
        JSONObject codePushLocalPackage = mUpdateManager.getCurrentPackage(pathPrefix);
        if (codePushLocalPackage == null) {
            return null;
        }
        return mUpdateManager.getPackageFolderPath(codePushLocalPackage.optString("packageHash"), pathPrefix);
    }

    @Deprecated
    public static String getBundleUrl() {
        return getJSBundleFile();
    }

    @Deprecated
    public static String getBundleUrl(String assetsBundleFileName) {
        return getJSBundleFile(assetsBundleFileName, "");
    }

    public Context getContext() {
        return mContext;
    }

    public String getDeploymentKey() {
        return mDeploymentKey;
    }

    public static String getJSBundleFile() {
        return CodePush.getJSBundleFile(CodePushConstants.DEFAULT_JS_BUNDLE_NAME, "");
    }

    public static String getCommonJSBundleFile() {
        return CodePush.getJSBundleFile(CodePushConstants.DEFAULT_COMMON_JS_BUNDLE_NAME, CodePushConstants.CODE_PUSH_COMMON_BUNDLE_FOLDER_PREFIX);
    }

    public static String getJSBundleFile(String assetsBundleFileName, String pathPrefix) {
        if (mCurrentInstance == null) {
            throw new CodePushNotInitializedException("A CodePush instance has not been created yet. Have you added it to your app's list of ReactPackages?");
        }

        return mCurrentInstance.getJSBundleFileInternal(assetsBundleFileName, pathPrefix);
    }

    public String getJSBundleFileInternal(String assetsBundleFileName, String pathPrefix) {
//        this.mAssetsBundleFileName = assetsBundleFileName;
        String binaryJsBundleUrl = CodePushConstants.ASSETS_BUNDLE_PREFIX + assetsBundleFileName;

        String packageFilePath = null;
        try {
            packageFilePath = mUpdateManager.getCurrentPackageBundlePath(assetsBundleFileName, pathPrefix);
        } catch (CodePushMalformedDataException e) {
            // We need to recover the app in case 'codepush.json' is corrupted
            CodePushUtils.log(e.getMessage());
            clearUpdates(pathPrefix);
        }

        if (packageFilePath == null) {
            // There has not been any downloaded updates.
            CodePushUtils.logBundleUrl(binaryJsBundleUrl, pathPrefix);
//            sIsRunningBinaryVersion = true;
            sIsRunningBinaryVersionArr.put(pathPrefix, true);
            return binaryJsBundleUrl;
        }

        JSONObject packageMetadata = this.mUpdateManager.getCurrentPackage(pathPrefix);
        if (isPackageBundleLatest(packageMetadata)) {
            CodePushUtils.logBundleUrl(packageFilePath, pathPrefix);
//            sIsRunningBinaryVersion = false;
            sIsRunningBinaryVersionArr.put(pathPrefix, false);
            return packageFilePath;
        } else {
            // The binary version is newer.
//            this.mDidUpdate = false;
            mDidUpdateArr.put(pathPrefix, false);
            if (!this.mIsDebugMode || hasBinaryVersionChanged(packageMetadata)) {
                this.clearUpdates(pathPrefix);
            }

            CodePushUtils.logBundleUrl(binaryJsBundleUrl, pathPrefix);
//            sIsRunningBinaryVersion = true;
            sIsRunningBinaryVersionArr.put(pathPrefix, true);
            return binaryJsBundleUrl;
        }
    }

    public String getServerUrl() {
        return mServerUrl;
    }

    void initializeUpdateAfterRestart(String pathPrefix) {
        // Reset the state which indicates that
        // the app was just freshly updated.
//        mDidUpdate = false;
        mDidUpdateArr.put(pathPrefix, false);

        JSONObject pendingUpdate = mSettingsManager.getPendingUpdate(pathPrefix);
        if (pendingUpdate != null) {
            JSONObject packageMetadata = this.mUpdateManager.getCurrentPackage(pathPrefix);
            if (packageMetadata == null || !isPackageBundleLatest(packageMetadata) && hasBinaryVersionChanged(packageMetadata)) {
                CodePushUtils.log("Skipping initializeUpdateAfterRestart(), binary version is newer");
                return;
            }

            try {
                boolean updateIsLoading = pendingUpdate.getBoolean(CodePushConstants.PENDING_UPDATE_IS_LOADING_KEY);
                if (updateIsLoading) {
                    // Pending update was initialized, but notifyApplicationReady was not called.
                    // Therefore, deduce that it is a broken update and rollback.
                    CodePushUtils.log("Update did not finish loading the last time, rolling back to a previous version.");
//                    sNeedToReportRollback = true;
                    sNeedToReportRollbackArr.put(pathPrefix, true);
                    rollbackPackage(pathPrefix);
                } else {
                    // There is in fact a new update running for the first
                    // time, so update the local state to ensure the client knows.
//                    mDidUpdate = true;
                    mDidUpdateArr.put(pathPrefix, true);

                    // Mark that we tried to initialize the new update, so that if it crashes,
                    // we will know that we need to rollback when the app next starts.
                    mSettingsManager.savePendingUpdate(pendingUpdate.getString(CodePushConstants.PENDING_UPDATE_HASH_KEY),
                            /* isLoading */true, pathPrefix);
                }
            } catch (JSONException e) {
                // Should not happen.
                throw new CodePushUnknownException("Unable to read pending update metadata stored in SharedPreferences", e);
            }
        }
    }

    void invalidateCurrentInstance() {
        mCurrentInstance = null;
    }

    boolean isDebugMode() {
        return mIsDebugMode;
    }

    boolean isRunningBinaryVersion(String bundleFileName, String pathPrefix) {
//        return sIsRunningBinaryVersion;
        // init sIsRunningBinaryVersion params
        getJSBundleFileInternal(bundleFileName, pathPrefix);
        return sIsRunningBinaryVersionArr.get(pathPrefix) == null ? false : sIsRunningBinaryVersionArr.get(pathPrefix);
    }

    private boolean isPackageBundleLatest(JSONObject packageMetadata) {
        try {
            Long binaryModifiedDateDuringPackageInstall = null;
            String binaryModifiedDateDuringPackageInstallString = packageMetadata.optString(CodePushConstants.BINARY_MODIFIED_TIME_KEY, null);
            if (binaryModifiedDateDuringPackageInstallString != null) {
                binaryModifiedDateDuringPackageInstall = Long.parseLong(binaryModifiedDateDuringPackageInstallString);
            }
            String packageAppVersion = packageMetadata.optString("appVersion", null);
            long binaryResourcesModifiedTime = this.getBinaryResourcesModifiedTime();
            return binaryModifiedDateDuringPackageInstall != null &&
                    binaryModifiedDateDuringPackageInstall == binaryResourcesModifiedTime &&
                    (isUsingTestConfiguration() || sAppVersion.equals(packageAppVersion));
        } catch (NumberFormatException e) {
            throw new CodePushUnknownException("Error in reading binary modified date from package metadata", e);
        }
    }

    private boolean hasBinaryVersionChanged(JSONObject packageMetadata) {
        String packageAppVersion = packageMetadata.optString("appVersion", null);
        return !sAppVersion.equals(packageAppVersion);
    }

    boolean needToReportRollback(String pathPrefix) {
//        return sNeedToReportRollback;
        return sNeedToReportRollbackArr.get(pathPrefix) == null ? false : sNeedToReportRollbackArr.get(pathPrefix);
    }

    public static void overrideAppVersion(String appVersionOverride) {
        sAppVersion = appVersionOverride;
    }

    private void rollbackPackage(String pathPrefix) {
        JSONObject failedPackage = mUpdateManager.getCurrentPackage(pathPrefix);
        mSettingsManager.saveFailedUpdate(failedPackage, pathPrefix);
        mUpdateManager.rollbackPackage(pathPrefix);
        mSettingsManager.removePendingUpdate(pathPrefix);
    }

    public void setNeedToReportRollback(boolean needToReportRollback, String pathPrefix) {
//        CodePush.sNeedToReportRollback = needToReportRollback;
        sNeedToReportRollbackArr.put(pathPrefix, needToReportRollback);
    }

    /* The below 3 methods are used for running tests.*/
    public static boolean isUsingTestConfiguration() {
        return sTestConfigurationFlag;
    }

    public void setDeploymentKey(String deploymentKey) {
        mDeploymentKey = deploymentKey;
    }

    public static void setUsingTestConfiguration(boolean shouldUseTestConfiguration) {
        sTestConfigurationFlag = shouldUseTestConfiguration;
    }

    public void clearUpdates(String pathPrefix) {
        mUpdateManager.clearUpdates(pathPrefix);
        mSettingsManager.removePendingUpdate(pathPrefix);
        mSettingsManager.removeFailedUpdates(pathPrefix);
    }

    public static void setReactInstanceHolder(ReactInstanceHolder reactInstanceHolder) {
        mReactInstanceHolder = reactInstanceHolder;
    }

    static ReactInstanceManager getReactInstanceManager() {
        if (mReactInstanceHolder == null) {
            return null;
        }
        return mReactInstanceHolder.getReactInstanceManager();
    }

    @Override
    public List<NativeModule> createNativeModules(ReactApplicationContext reactApplicationContext) {
        CodePushNativeModule codePushModule = new CodePushNativeModule(reactApplicationContext, this, mUpdateManager, mTelemetryManager, mSettingsManager);
        CodePushDialog dialogModule = new CodePushDialog(reactApplicationContext);

        List<NativeModule> nativeModules = new ArrayList<>();
        nativeModules.add(codePushModule);
        nativeModules.add(dialogModule);
        return nativeModules;
    }

    // Deprecated in RN v0.47.
    public List<Class<? extends JavaScriptModule>> createJSModules() {
        return new ArrayList<>();
    }

    @Override
    public List<ViewManager> createViewManagers(ReactApplicationContext reactApplicationContext) {
        return new ArrayList<>();
    }
}
