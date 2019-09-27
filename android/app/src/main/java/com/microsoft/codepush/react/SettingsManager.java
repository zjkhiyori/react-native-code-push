package com.microsoft.codepush.react;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.content.SharedPreferences;

public class SettingsManager {

    private SharedPreferences mSettings;

    public SettingsManager(Context applicationContext) {
        mSettings = applicationContext.getSharedPreferences(CodePushConstants.CODE_PUSH_PREFERENCES, 0);
    }

    public JSONArray getFailedUpdates(String pathPrefix) {
        String failedUpdatesString = mSettings.getString(pathPrefix + "_" + CodePushConstants.FAILED_UPDATES_KEY, null);
        if (failedUpdatesString == null) {
            return new JSONArray();
        }

        try {
            return new JSONArray(failedUpdatesString);
        } catch (JSONException e) {
            // Unrecognized data format, clear and replace with expected format.
            JSONArray emptyArray = new JSONArray();
            mSettings.edit().putString(pathPrefix + "_" + CodePushConstants.FAILED_UPDATES_KEY, emptyArray.toString()).commit();
            return emptyArray;
        }
    }

    public JSONObject getPendingUpdate(String pathPrefix) {
        String pendingUpdateString = mSettings.getString(pathPrefix + "_" + CodePushConstants.PENDING_UPDATE_KEY, null);
        if (pendingUpdateString == null) {
            return null;
        }

        try {
            return new JSONObject(pendingUpdateString);
        } catch (JSONException e) {
            // Should not happen.
            CodePushUtils.log("Unable to parse pending update metadata " + pendingUpdateString +
                    " stored in SharedPreferences");
            return null;
        }
    }


    public boolean isFailedHash(String packageHash, String pathPrefix) {
        JSONArray failedUpdates = getFailedUpdates(pathPrefix);
        if (packageHash != null) {
            for (int i = 0; i < failedUpdates.length(); i++) {
                try {
                    JSONObject failedPackage = failedUpdates.getJSONObject(i);
                    String failedPackageHash = failedPackage.getString(CodePushConstants.PACKAGE_HASH_KEY);
                    if (packageHash.equals(failedPackageHash)) {
                        return true;
                    }
                } catch (JSONException e) {
                    throw new CodePushUnknownException("Unable to read failedUpdates data stored in SharedPreferences.", e);
                }
            }
        }

        return false;
    }

    public boolean isPendingUpdate(String packageHash, String pathPrefix) {
        JSONObject pendingUpdate = getPendingUpdate(pathPrefix);

        try {
            return pendingUpdate != null &&
                    !pendingUpdate.getBoolean(CodePushConstants.PENDING_UPDATE_IS_LOADING_KEY) &&
                    (packageHash == null || pendingUpdate.getString(CodePushConstants.PENDING_UPDATE_HASH_KEY).equals(packageHash));
        } catch (JSONException e) {
            throw new CodePushUnknownException("Unable to read pending update metadata in isPendingUpdate.", e);
        }
    }

    public void removeFailedUpdates(String pathPrefix) {
        mSettings.edit().remove(pathPrefix + "_" + CodePushConstants.FAILED_UPDATES_KEY).commit();
    }

    public void removePendingUpdate(String pathPrefix) {
        mSettings.edit().remove(pathPrefix + "_" + CodePushConstants.PENDING_UPDATE_KEY).commit();
    }

    public void saveFailedUpdate(JSONObject failedPackage, String pathPrefix) {
        try {
            if (isFailedHash(failedPackage.getString(CodePushConstants.PACKAGE_HASH_KEY), pathPrefix)) {
                // Do not need to add the package if it is already in the failedUpdates.
                return;
            }
        } catch (JSONException e) {
            throw new CodePushUnknownException("Unable to read package hash from package.", e);
        }

        String failedUpdatesString = mSettings.getString(pathPrefix + "_" + CodePushConstants.FAILED_UPDATES_KEY, null);
        JSONArray failedUpdates;
        if (failedUpdatesString == null) {
            failedUpdates = new JSONArray();
        } else {
            try {
                failedUpdates = new JSONArray(failedUpdatesString);
            } catch (JSONException e) {
                // Should not happen.
                throw new CodePushMalformedDataException("Unable to parse failed updates information " +
                        failedUpdatesString + " stored in SharedPreferences", e);
            }
        }

        failedUpdates.put(failedPackage);
        mSettings.edit().putString(pathPrefix + "_" + CodePushConstants.FAILED_UPDATES_KEY, failedUpdates.toString()).commit();
    }

    public JSONObject getLatestRollbackInfo(String pathPrefix) {
        String latestRollbackInfoString = mSettings.getString(pathPrefix + "_" + CodePushConstants.LATEST_ROLLBACK_INFO_KEY, null);
        if (latestRollbackInfoString == null) {
            return null;
        }

        try {
            return new JSONObject(latestRollbackInfoString);
        } catch (JSONException e) {
            // Should not happen.
            CodePushUtils.log("Unable to parse latest rollback metadata " + latestRollbackInfoString +
                    " stored in SharedPreferences");
            return null;
        }
    }

    public void setLatestRollbackInfo(String packageHash, String pathPrefix) {
        JSONObject latestRollbackInfo = getLatestRollbackInfo(pathPrefix);
        int count = 0;

        if (latestRollbackInfo != null) {
            try {
                String latestRollbackPackageHash = latestRollbackInfo.getString(CodePushConstants.LATEST_ROLLBACK_PACKAGE_HASH_KEY);
                if (latestRollbackPackageHash.equals(packageHash)) {
                    count = latestRollbackInfo.getInt(CodePushConstants.LATEST_ROLLBACK_COUNT_KEY);
                }
            } catch (JSONException e) {
                CodePushUtils.log("Unable to parse latest rollback info.");
            }
        } else {
            latestRollbackInfo = new JSONObject();
        }

        try {
            latestRollbackInfo.put(CodePushConstants.LATEST_ROLLBACK_PACKAGE_HASH_KEY, packageHash);
            latestRollbackInfo.put(CodePushConstants.LATEST_ROLLBACK_TIME_KEY, System.currentTimeMillis());
            latestRollbackInfo.put(CodePushConstants.LATEST_ROLLBACK_COUNT_KEY, count + 1);
            mSettings.edit().putString(pathPrefix + "_" + CodePushConstants.LATEST_ROLLBACK_INFO_KEY, latestRollbackInfo.toString()).commit();
        } catch (JSONException e) {
            throw new CodePushUnknownException("Unable to save latest rollback info.", e);
        }
    }

    public void savePendingUpdate(String packageHash, boolean isLoading, String pathPrefix) {
        JSONObject pendingUpdate = new JSONObject();
        try {
            pendingUpdate.put(CodePushConstants.PENDING_UPDATE_HASH_KEY, packageHash);
            pendingUpdate.put(CodePushConstants.PENDING_UPDATE_IS_LOADING_KEY, isLoading);
            mSettings.edit().putString(pathPrefix + "_" + CodePushConstants.PENDING_UPDATE_KEY, pendingUpdate.toString()).commit();
        } catch (JSONException e) {
            // Should not happen.
            throw new CodePushUnknownException("Unable to save pending update.", e);
        }
    }

}
