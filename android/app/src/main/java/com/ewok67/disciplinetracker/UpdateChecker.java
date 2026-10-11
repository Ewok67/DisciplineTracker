package com.ewok67.disciplinetracker;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Checks the GitHub latest release for a newer APK and offers it for install.
 *
 * Flow: GET /repos/{owner}/{repo}/releases/latest -> compare tag semver with
 * BuildConfig.VERSION_NAME -> download the .apk asset -> hand it to the
 * system package installer via FileProvider.
 *
 * Notes:
 *  - Unauthenticated GitHub API allows 60 requests/hour; checks are throttled
 *   to at most once every 6 hours.
 *  - Android never allows a fully silent install for sideloaded apps; the
 *   system installer always asks for final confirmation.
 */
public class UpdateChecker {

    private static final String REPO_OWNER = "Ewok67";
    private static final String REPO_NAME = "DisciplineTracker";
    private static final String API_URL =
            "https://api.github.com/repos/" + REPO_OWNER + "/" + REPO_NAME + "/releases/latest";

    private static final String PREFS = "updater_prefs";
    private static final String KEY_LAST_CHECK = "last_check_ms";
    private static final long THROTTLE_MS = 6L * 60L * 60L * 1000L; // 6 hours

    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 30000;

    public static void checkForUpdate(final Activity activity) {
        final SharedPreferences prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        final long now = System.currentTimeMillis();
        if (now - prefs.getLong(KEY_LAST_CHECK, 0L) < THROTTLE_MS) {
            return;
        }
        prefs.edit().putLong(KEY_LAST_CHECK, now).apply();

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    JSONObject release = getJson(API_URL);
                    String tag = release.optString("tag_name", "");
                    if (tag.isEmpty() || !isNewer(tag, BuildConfig.VERSION_NAME)) {
                        return; // up to date
                    }

                    String apkUrl = null;
                    JSONArray assets = release.optJSONArray("assets");
                    if (assets != null) {
                        for (int i = 0; i < assets.length(); i++) {
                            JSONObject asset = assets.getJSONObject(i);
                            String name = asset.optString("name", "");
                            if (name.endsWith(".apk")) {
                                apkUrl = asset.optString("browser_download_url", null);
                                break;
                            }
                        }
                    }
                    if (apkUrl == null) {
                        return;
                    }

                    File apk = downloadApk(activity, apkUrl);
                    if (apk == null || apk.length() == 0) {
                        return;
                    }
                    promptInstall(activity, apk);
                } catch (Exception e) {
                    // Network/API failure — stay silent, retry on next launch.
                }
            }
        }).start();
    }

    /** Compares "v1.2.3" style tags against the installed version. */
    static boolean isNewer(String remoteTag, String localVersion) {
        int[] remote = parseVersion(remoteTag);
        int[] local = parseVersion(localVersion);
        for (int i = 0; i < 3; i++) {
            if (remote[i] != local[i]) {
                return remote[i] > local[i];
            }
        }
        return false;
    }

    static int[] parseVersion(String version) {
        String cleaned = version.trim();
        if (cleaned.startsWith("v") || cleaned.startsWith("V")) {
            cleaned = cleaned.substring(1);
        }
        String[] parts = cleaned.split("\\.");
        int[] out = {0, 0, 0};
        for (int i = 0; i < Math.min(3, parts.length); i++) {
            try {
                out[i] = Integer.parseInt(parts[i].replaceAll("[^0-9]", ""));
            } catch (NumberFormatException ignored) {
                out[i] = 0;
            }
        }
        return out;
    }

    private static JSONObject getJson(String urlStr) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(CONNECT_TIMEOUT_MS);
        conn.setRequestProperty("Accept", "application/vnd.github+json");
        conn.setRequestProperty("User-Agent", "DisciplineTracker-Android");
        try {
            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new Exception("HTTP " + conn.getResponseCode());
            }
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            return new JSONObject(sb.toString());
        } finally {
            conn.disconnect();
        }
    }

    private static File downloadApk(Context context, String urlStr) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", "DisciplineTracker-Android");
        File out = new File(context.getCacheDir(), "update.apk");
        try {
            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return null;
            }
            InputStream in = conn.getInputStream();
            FileOutputStream fos = new FileOutputStream(out);
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                fos.write(buffer, 0, read);
            }
            fos.close();
            in.close();
            return out;
        } finally {
            conn.disconnect();
        }
    }

    private static void promptInstall(final Activity activity, final File apk) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                try {
                    // Android 8+ requires the user to grant install permission per app
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                            && !activity.getPackageManager().canRequestPackageInstalls()) {
                        Toast.makeText(activity,
                                "Allow this app to install updates, then reopen it",
                                Toast.LENGTH_LONG).show();
                        Intent settingsIntent = new Intent(
                                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:" + activity.getPackageName()));
                        activity.startActivity(settingsIntent);
                        return;
                    }
                    Uri apkUri = FileProvider.getUriForFile(activity,
                            activity.getPackageName() + ".provider", apk);
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setDataAndType(apkUri,
                            "application/vnd.android.package-archive");
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    activity.startActivity(intent);
                    Toast.makeText(activity, "Update ready — tap Install",
                            Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    Toast.makeText(activity, "Update downloaded — open it manually",
                            Toast.LENGTH_LONG).show();
                }
            }
        });
    }
}
