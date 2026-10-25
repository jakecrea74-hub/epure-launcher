package fr.epure.launcher;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;

import java.text.Collator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Liste des applis installées, triées selon settings.json. */
final class Apps {
    private Apps() {}

    static final class Entry {
        String pkg;
        String label;
        Drawable banner;
        Drawable icon;
    }

    static List<Entry> load(Context ctx, Config cfg) {
        PackageManager pm = ctx.getPackageManager();
        LinkedHashMap<String, Entry> map = new LinkedHashMap<>();
        String self = ctx.getPackageName();
        add(pm, map, Intent.CATEGORY_LEANBACK_LAUNCHER, self);
        if (cfg.bool("inclure_applis_non_tv", false)) add(pm, map, Intent.CATEGORY_LAUNCHER, self);

        Set<String> hidden = new HashSet<>(cfg.list("applis_masquees"));
        List<String> order = cfg.list("ordre_applis");
        List<Entry> out = new ArrayList<>();
        for (Entry e : map.values()) if (!hidden.contains(e.pkg)) out.add(e);

        Collator col = Collator.getInstance(Locale.FRANCE);
        col.setStrength(Collator.PRIMARY);
        out.sort((a, b) -> {
            int ia = order.indexOf(a.pkg), ib = order.indexOf(b.pkg);
            if (ia < 0) ia = Integer.MAX_VALUE;
            if (ib < 0) ib = Integer.MAX_VALUE;
            if (ia != ib) return Integer.compare(ia, ib);
            return col.compare(a.label, b.label);
        });
        return out;
    }

    static String signature(List<Entry> list) {
        StringBuilder sb = new StringBuilder();
        for (Entry e : list) sb.append(e.pkg).append(';');
        return sb.toString();
    }

    static Intent launchIntent(Context ctx, String pkg) {
        PackageManager pm = ctx.getPackageManager();
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(pkg);
        if (i != null) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return i;
    }

    private static void add(PackageManager pm, LinkedHashMap<String, Entry> map, String cat, String self) {
        Intent q = new Intent(Intent.ACTION_MAIN).addCategory(cat);
        for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
            ActivityInfo ai = ri.activityInfo;
            if (ai == null || ai.packageName.equals(self) || map.containsKey(ai.packageName)) continue;
            Entry e = new Entry();
            e.pkg = ai.packageName;
            e.label = String.valueOf(ri.loadLabel(pm));
            Drawable b = ai.loadBanner(pm);
            if (b == null && ai.applicationInfo != null) b = ai.applicationInfo.loadBanner(pm);
            e.banner = b;
            if (b == null) e.icon = ri.loadIcon(pm);
            map.put(e.pkg, e);
        }
    }
}
