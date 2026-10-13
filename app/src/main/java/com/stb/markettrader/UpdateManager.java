package com.stb.markettrader;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

public class UpdateManager {

    private static final String RAW_BASE = "https://raw.githubusercontent.com/ms-nicky/meta-treder-stb/main/";
    private static final String UPDATE_URL = RAW_BASE + "update.json";

    public static class UpdateInfo {
        public int versionCode;
        public String versionName;
        public String apkUrl;
        public String apkSha256;
        public String notes;
    }

    public interface UpdateCallback {
        void onUpdate(UpdateInfo info);
        void onResult(String message);
    }

    public interface PairCallback {
        void onPairs(List<String> pairs);
    }

    public static void checkForAppUpdate(final Context context, final UpdateCallback cb) {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final int currentCode = currentVersionCode(context);
                    final UpdateInfo info = parseUpdate(Http.getText(UPDATE_URL));
                    if (info.versionCode > currentCode) {
                        cb.onUpdate(info);
                    } else {
                        cb.onResult("Sudah versi terbaru (v" + info.versionName + ")");
                    }
                } catch (final Exception e) {
                    cb.onResult("Gagal cek update: " + shortErr(e.getMessage()));
                }
            }
        });
        t.start();
    }

    public static void loadRemotePairs(final Context context, final PairCallback cb) {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    List<String> pairs = parsePairs(Http.getText(RAW_BASE + "data/pairs.json"));
                    cb.onPairs(pairs);
                } catch (Exception e) {
                    cb.onPairs(null);
                }
            }
        });
        t.start();
    }

    public static void downloadAndInstall(final Context context, final UpdateInfo info) {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    File dir = context.getExternalFilesDir(null);
                    if (dir == null) dir = context.getFilesDir();
                    File out = new File(dir, "MarketTrader-" + info.versionName + ".apk");

                    Http.download(info.apkUrl, out);

                    if (info.apkSha256 != null && info.apkSha256.length() == 64
                            && !sha256(out).equalsIgnoreCase(info.apkSha256)) {
                        out.delete();
                        throw new Exception("Checksum APK tidak cocok");
                    }

                    final File apk = out;
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            install(context, apk);
                        }
                    });
                } catch (final Exception e) {
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            android.widget.Toast.makeText(context,
                                    "Gagal unduh update: " + shortErr(e.getMessage()),
                                    android.widget.Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        });
        t.start();
    }

    public static void install(Context context, File apk) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.fromFile(apk), "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            android.widget.Toast.makeText(context,
                    "Gagal buka installer: " + shortErr(e.getMessage()),
                    android.widget.Toast.LENGTH_LONG).show();
        }
    }

    private static UpdateInfo parseUpdate(String json) throws Exception {
        JSONObject root = new JSONObject(json);
        JSONObject app = root.getJSONObject("app");
        UpdateInfo info = new UpdateInfo();
        info.versionCode = app.optInt("versionCode", 0);
        info.versionName = app.optString("versionName", "?");
        info.apkUrl = app.optString("apkUrl", "");
        info.apkSha256 = app.optString("apkSha256", "");
        info.notes = app.optString("notes", "Pembaruan tersedia.");
        return info;
    }

    private static List<String> parsePairs(String json) throws Exception {
        JSONObject root = new JSONObject(json);
        JSONArray arr = root.getJSONArray("pairs");
        List<String> list = new ArrayList<String>(arr.length());
        for (int i = 0; i < arr.length(); i++) {
            list.add(arr.getString(i));
        }
        return list;
    }

    private static int currentVersionCode(Context context) {
        try {
            return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionCode;
        } catch (Exception e) {
            return 0;
        }
    }

    private static String sha256(File f) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        FileInputStream in = new FileInputStream(f);
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) {
            md.update(buf, 0, n);
        }
        in.close();
        byte[] d = md.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : d) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static String shortErr(String msg) {
        if (msg == null) return "unknown";
        if (msg.length() > 80) return msg.substring(0, 80);
        return msg;
    }
}