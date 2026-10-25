package fr.epure.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;

/** Mise à jour intégrée : lit la dernière version publiée sur GitHub et propose de l'installer. */
final class Updater {
    private Updater() {}

    private static volatile boolean busy;

    static void check(Activity a, boolean manual) {
        if (busy) return;
        busy = true;
        new Thread(() -> {
            try {
                JSONObject rel = new JSONObject(Net.get(
                        "https://api.github.com/repos/" + BuildConfig.GITHUB_REPO + "/releases/latest"));
                String digits = rel.optString("tag_name", "").replaceAll("\\D", "");
                int remote = digits.isEmpty() ? 0 : Integer.parseInt(digits);
                String apkUrl = null;
                JSONArray assets = rel.optJSONArray("assets");
                if (assets != null) for (int i = 0; i < assets.length(); i++) {
                    JSONObject as = assets.getJSONObject(i);
                    if (as.optString("name").endsWith(".apk")) apkUrl = as.optString("browser_download_url");
                }
                String notes = rel.optString("body", "");
                if (notes.length() > 300) notes = notes.substring(0, 300) + "…";
                final String url = apkUrl, msg = notes;
                if (remote > BuildConfig.VERSION_CODE && url != null) {
                    a.runOnUiThread(() -> offer(a, "1.0." + remote, msg, url));
                } else if (manual) {
                    a.runOnUiThread(() -> toast(a, "Vous avez la dernière version (" + BuildConfig.VERSION_NAME + ")"));
                }
            } catch (Exception e) {
                if (manual) a.runOnUiThread(() -> toast(a, "Impossible de vérifier les mises à jour"));
            } finally {
                busy = false;
            }
        }).start();
    }

    private static void offer(Activity a, String version, String notes, String url) {
        if (a.isFinishing()) return;
        new AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Nouvelle version " + version)
                .setMessage(notes.isEmpty() ? "Une mise à jour d'Épure est disponible." : notes)
                .setPositiveButton("Installer", (d, w) -> install(a, url))
                .setNegativeButton("Plus tard", null)
                .show();
    }

    private static void install(Activity a, String url) {
        if (Build.VERSION.SDK_INT >= 26 && !a.getPackageManager().canRequestPackageInstalls()) {
            toast(a, "Autorisez Épure à installer des applis, puis relancez la mise à jour");
            try {
                a.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + a.getPackageName())));
            } catch (ActivityNotFoundException ignored) { }
            return;
        }
        toast(a, "Téléchargement de la mise à jour…");
        new Thread(() -> {
            try {
                PackageInstaller pi = a.getPackageManager().getPackageInstaller();
                PackageInstaller.SessionParams p =
                        new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                int id = pi.createSession(p);
                HttpURLConnection c = Net.open(url);
                try (PackageInstaller.Session s = pi.openSession(id)) {
                    long len = c.getContentLengthLong();
                    try (InputStream in = c.getInputStream();
                         OutputStream out = s.openWrite("epure.apk", 0, len > 0 ? len : -1)) {
                        byte[] buf = new byte[65536];
                        int n;
                        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                        s.fsync(out);
                    }
                    int flags = PendingIntent.FLAG_UPDATE_CURRENT
                            | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
                    PendingIntent done = PendingIntent.getBroadcast(a, id,
                            new Intent(a, InstallReceiver.class), flags);
                    s.commit(done.getIntentSender());
                } finally {
                    c.disconnect();
                }
            } catch (Exception e) {
                a.runOnUiThread(() -> toast(a, "Échec du téléchargement : " + e.getMessage()));
            }
        }).start();
    }

    static void toast(Activity a, String s) {
        Toast.makeText(a, s, Toast.LENGTH_LONG).show();
    }
}
