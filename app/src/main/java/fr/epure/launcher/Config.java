package fr.epure.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Réglages lus depuis settings.json sur GitHub (relu au démarrage),
 * avec une copie locale pour fonctionner hors ligne.
 */
final class Config {
    private static final String KEY_JSON = "config_json";
    private static final String KEY_ANIM_LOCAL = "anim_local";

    private final SharedPreferences prefs;
    private JSONObject json;

    Config(Context c) {
        prefs = c.getSharedPreferences("epure", Context.MODE_PRIVATE);
        json = parse(prefs.getString(KEY_JSON, "{}"));
    }

    static String url() {
        return "https://raw.githubusercontent.com/" + BuildConfig.GITHUB_REPO
                + "/main/settings.json?t=" + System.currentTimeMillis();
    }

    /** Applique un nouveau contenu ; renvoie true si quelque chose a changé. */
    boolean apply(String raw) {
        JSONObject j = parse(raw);
        if (j.length() == 0) return false;
        String s = j.toString();
        if (s.equals(json.toString())) return false;
        json = j;
        prefs.edit().putString(KEY_JSON, s).apply();
        return true;
    }

    String str(String k, String def) {
        String v = json.optString(k, def);
        return v == null ? def : v;
    }

    boolean bool(String k, boolean def) { return json.optBoolean(k, def); }

    double num(String k, double def) { return json.optDouble(k, def); }

    int color(String k, int def) {
        try { return Color.parseColor(str(k, "")); } catch (Exception e) { return def; }
    }

    List<String> list(String k) {
        List<String> out = new ArrayList<>();
        JSONArray a = json.optJSONArray(k);
        if (a != null) for (int i = 0; i < a.length(); i++) {
            String v = a.optString(i, "").trim();
            if (!v.isEmpty()) out.add(v);
        }
        return out;
    }

    /** Lit une valeur texte dans un objet du fichier, ex. "sous_titres": {"com.netflix.ninja": "..."}. */
    String inMap(String obj, String key) {
        JSONObject o = json.optJSONObject(obj);
        return o == null ? null : o.optString(key, null);
    }

    /** Le choix fait sur la TV (menu Réglages) a priorité sur le fichier. */
    boolean animations() {
        if (prefs.contains(KEY_ANIM_LOCAL)) return prefs.getBoolean(KEY_ANIM_LOCAL, true);
        return bool("animations", true);
    }

    void setAnimationsLocal(boolean on) { prefs.edit().putBoolean(KEY_ANIM_LOCAL, on).apply(); }

    void followFileForAnimations() { prefs.edit().remove(KEY_ANIM_LOCAL).apply(); }

    boolean animationsForcedLocally() { return prefs.contains(KEY_ANIM_LOCAL); }

    private static JSONObject parse(String s) {
        try { return new JSONObject(s); } catch (Exception e) { return new JSONObject(); }
    }
}
