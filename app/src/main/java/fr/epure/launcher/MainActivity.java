package fr.epure.launcher;

import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private static final int TILE_W = 272, TILE_H = 153;          // dp, format 16:9
    private static final float FOCUS_SCALE = 1.18f;
    private static final float DIM_ALPHA = 0.55f;                 // applis non sélectionnées
    private static final float BACKDROP_ALPHA = 0.6f;
    private static final long ANIM_MS = 180;
    private static final long CONFIG_EVERY = 10 * 60_000L;
    private static final long WEATHER_EVERY = 30 * 60_000L;
    private static final long UPDATE_EVERY = 6 * 3_600_000L;
    private static final String KEY_SETTINGS = "__reglages__";

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private Config cfg;
    private View root;
    private TextView greetingV, dateV, clockV, weatherV;
    private LinearLayout row;
    private HorizontalScrollView scroll;
    private View ambient, info;
    private ImageView backdropA, backdropB;
    private TextView infoTitle, infoCaption;
    private boolean showingA;
    private String backdropKey;
    private ValueAnimator ambientAnim;
    private GradientDrawable ambientBg;
    private final Map<String, Bitmap> blurCache = new HashMap<>();

    private String appsSig = "";
    private String focusedKey;
    private String weatherText;
    private long lastConfig, lastWeather, lastUpdate;

    private final Runnable clockTick = new Runnable() {
        @Override public void run() {
            updateClock();
            long now = System.currentTimeMillis();
            ui.postDelayed(this, 60_000 - (now % 60_000) + 50);
        }
    };

    // ---------- Cycle de vie ----------

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        cfg = new Config(this);
        root = findViewById(R.id.root);
        greetingV = findViewById(R.id.greeting);
        dateV = findViewById(R.id.date);
        clockV = findViewById(R.id.clock);
        weatherV = findViewById(R.id.weather);
        row = findViewById(R.id.row);
        scroll = findViewById(R.id.row_scroll);
        ambient = findViewById(R.id.ambient);
        backdropA = findViewById(R.id.backdrop_a);
        backdropB = findViewById(R.id.backdrop_b);
        info = findViewById(R.id.info);
        infoTitle = findViewById(R.id.info_title);
        infoCaption = findViewById(R.id.info_caption);

        GradientDrawable scrim = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x99000000, 0x40000000, 0x66000000, 0xE6000000});
        findViewById(R.id.scrim).setBackground(scrim);

        applyConfig();
        rebuildRow(true);

        if (cfg.animations()) {
            root.setAlpha(0f);
            root.animate().alpha(1f).setDuration(450).start();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        ui.removeCallbacks(clockTick);
        clockTick.run();
        startAmbient();
        rebuildRow(false);                         // appli installée / supprimée ?
        long now = SystemClock.elapsedRealtime();
        if (lastConfig == 0 || now - lastConfig > CONFIG_EVERY) fetchConfig(false);
        else if (lastWeather == 0 || now - lastWeather > WEATHER_EVERY) refreshWeather();
    }

    @Override
    protected void onPause() {
        super.onPause();
        ui.removeCallbacks(clockTick);
        if (ambientAnim != null) ambientAnim.cancel();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Bouton Accueil pendant qu'on est déjà là : retour à la première appli
        if (row.getChildCount() > 0) {
            if (cfg.animations()) scroll.smoothScrollTo(0, 0); else scroll.scrollTo(0, 0);
            row.getChildAt(0).requestFocus();
        }
    }

    @Override
    public void onBackPressed() { /* un launcher ne se ferme pas */ }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_SETTINGS) {
            openSettings();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdownNow();
    }

    // ---------- Réglages GitHub ----------

    private void fetchConfig(boolean manual) {
        lastConfig = SystemClock.elapsedRealtime();
        io.execute(() -> {
            String raw = null;
            try { raw = Net.get(Config.url()); } catch (Exception ignored) { }
            final String r = raw;
            ui.post(() -> {
                if (isFinishing()) return;
                if (r != null && cfg.apply(r)) {
                    applyConfig();
                    rebuildRow(true);
                    lastWeather = 0;
                }
                if (manual) Updater.toast(this, r != null ? "Réglages rechargés" : "GitHub injoignable");
                refreshWeather();
                maybeCheckUpdate(false);
            });
        });
    }

    private void applyConfig() {
        ambientBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{
                cfg.color("couleur_fond_haut", 0xFF16171C),
                cfg.color("couleur_fond_bas", 0xFF050506)});
        ambient.setBackground(ambientBg);
        startAmbient();
        weatherV.setVisibility(cfg.bool("afficher_meteo", true) ? View.VISIBLE : View.GONE);
        updateClock();
    }

    // ---------- Heure, accueil, météo ----------

    private void updateClock() {
        Date now = new Date();
        String fmt = cfg.bool("format_24h", true) ? "HH:mm" : "h:mm a";
        clockV.setText(new SimpleDateFormat(fmt, Locale.FRANCE).format(now));
        String d = new SimpleDateFormat("EEEE d MMMM", Locale.FRANCE).format(now);
        dateV.setText(d.substring(0, 1).toUpperCase(Locale.FRANCE) + d.substring(1));
        greetingV.setText(greeting());
        renderWeather();
    }

    private String greeting() {
        String custom = cfg.str("message_accueil", "").trim();
        if (!custom.isEmpty()) return custom;
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String g = (h >= 5 && h < 12) ? "Bonjour" : (h >= 12 && h < 18) ? "Bon après-midi" : "Bonsoir";
        String name = cfg.str("prenom", "").trim();
        return name.isEmpty() ? g : g + ", " + name;
    }

    private void refreshWeather() {
        if (!cfg.bool("afficher_meteo", true)) return;
        long now = SystemClock.elapsedRealtime();
        if (lastWeather != 0 && now - lastWeather < WEATHER_EVERY) return;
        lastWeather = now;
        double lat = cfg.num("latitude", 48.8566), lon = cfg.num("longitude", 2.3522);
        io.execute(() -> {
            String w = Weather.fetch(lat, lon);
            ui.post(() -> {
                if (w != null) weatherText = w; else lastWeather = 0;
                renderWeather();
            });
        });
    }

    private void renderWeather() {
        String city = cfg.str("ville", "").trim();
        if (weatherText == null) { weatherV.setText(city); return; }
        weatherV.setText(city.isEmpty() ? weatherText : weatherText + "  ·  " + city);
    }

    // ---------- Mises à jour ----------

    private void maybeCheckUpdate(boolean manual) {
        long now = SystemClock.elapsedRealtime();
        if (!manual) {
            if (!cfg.bool("maj_auto", true)) return;
            if (lastUpdate != 0 && now - lastUpdate < UPDATE_EVERY) return;
        }
        lastUpdate = now;
        Updater.check(this, manual);
    }

    // ---------- Rangée d'applis ----------

    private void rebuildRow(boolean force) {
        List<Apps.Entry> apps = Apps.load(this, cfg);
        String sig = Apps.signature(apps);
        if (!force && sig.equals(appsSig)) return;
        appsSig = sig;
        String keep = focusedKey;

        row.removeAllViews();
        for (Apps.Entry e : apps) {
            row.addView(makeTile(e.pkg, e.label, e.banner, e.icon, null, v -> launch(e.pkg)));
        }
        row.addView(makeTile(KEY_SETTINGS, "Réglages", null, null, "⚙", v -> openSettings()));

        View target = keep == null ? null : row.findViewWithTag(keep);
        if (target == null && row.getChildCount() > 0) target = row.getChildAt(0);
        if (target != null) target.post(target::requestFocus);
    }

    private View makeTile(String key, String label, Drawable banner, Drawable icon, String glyph,
                          View.OnClickListener click) {
        // Cellule focusable : c'est elle qui grossit
        FrameLayout cell = new FrameLayout(this);
        cell.setTag(key);
        cell.setFocusable(true);
        cell.setClipChildren(false);
        cell.setClipToPadding(false);
        cell.setAlpha(DIM_ALPHA);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.leftMargin = dp(10);
        clp.rightMargin = dp(10);
        cell.setLayoutParams(clp);

        // Liseré blanc posé AUTOUR de la vignette (visible même sur une vignette blanche)
        GradientDrawable border = new GradientDrawable();
        border.setColor(Color.TRANSPARENT);
        border.setCornerRadius(dp(19));
        border.setStroke(dp(3), Color.WHITE);
        border.setAlpha(0);
        cell.setForeground(border);
        cell.setPadding(dp(6), dp(6), dp(6), dp(6));

        // Vignette arrondie
        FrameLayout card = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF1C1C22);
        bg.setCornerRadius(dp(14));
        card.setBackground(bg);
        card.setClipToOutline(true);
        cell.addView(card, new FrameLayout.LayoutParams(dp(TILE_W), dp(TILE_H)));

        if (banner != null) {
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setImageDrawable(banner);
            card.addView(iv, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else if (icon != null) {
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iv.setImageDrawable(icon);
            card.addView(iv, new FrameLayout.LayoutParams(dp(72), dp(72), Gravity.CENTER));
        } else {
            TextView g = new TextView(this);
            g.setText(glyph);
            g.setTextColor(Color.WHITE);
            g.setTextSize(TypedValue.COMPLEX_UNIT_SP, 44);
            g.setGravity(Gravity.CENTER);
            card.addView(g, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }

        cell.setOnClickListener(click);
        cell.setOnFocusChangeListener((v, has) -> {
            float s = has ? FOCUS_SCALE : 1f;
            float a = has ? 1f : DIM_ALPHA;
            float z = has ? dp(20) : 0;
            int ba = has ? 255 : 0;
            cell.animate().cancel();
            card.animate().cancel();
            if (cfg.animations()) {
                DecelerateInterpolator ease = new DecelerateInterpolator();
                cell.animate().scaleX(s).scaleY(s).alpha(a).setDuration(ANIM_MS).setInterpolator(ease).start();
                card.animate().translationZ(z).setDuration(ANIM_MS).start();
                ObjectAnimator.ofInt(border, "alpha", border.getAlpha(), ba).setDuration(ANIM_MS).start();
            } else {
                cell.setScaleX(s);
                cell.setScaleY(s);
                cell.setAlpha(a);
                card.setTranslationZ(z);
                border.setAlpha(ba);
            }
            if (has) {
                focusedKey = key;
                showInfo(key, label);
                showBackdrop(key, banner);
            }
        });
        return cell;
    }

    // ---------- Infos de l'appli sélectionnée ----------

    private void showInfo(String key, String label) {
        String caption = KEY_SETTINGS.equals(key) ? "Animations, mise à jour, paramètres" : caption(key);
        if (!cfg.animations()) {
            infoTitle.setText(label);
            infoCaption.setText(caption);
            return;
        }
        info.animate().cancel();
        info.animate().alpha(0f).setDuration(90).withEndAction(() -> {
            infoTitle.setText(label);
            infoCaption.setText(caption);
            info.animate().alpha(1f).setDuration(160).start();
        }).start();
    }

    private String caption(String pkg) {
        String c = cfg.inMap("sous_titres", pkg);
        if (c != null && !c.isEmpty()) return c;
        switch (pkg) {
            case "com.netflix.ninja":
            case "com.disney.disneyplus":
            case "com.amazon.amazonvideo.livingroom":
                return "Films et séries";
            case "com.google.android.youtube.tv":
                return "Vidéos et chaînes";
            case "com.canal.android.canal":
                return "Chaînes, films et sport";
            case "com.spotify.tv.android":
                return "Musique et podcasts";
            default:
                return "Appuyez sur OK pour ouvrir";
        }
    }

    // ---------- Grand visuel flouté ----------

    private void showBackdrop(String key, Drawable banner) {
        if (key.equals(backdropKey)) return;
        backdropKey = key;
        Bitmap bmp = banner == null ? null : blurred(key, banner);
        ImageView in = showingA ? backdropB : backdropA;
        ImageView out = showingA ? backdropA : backdropB;
        showingA = !showingA;
        in.animate().cancel();
        out.animate().cancel();
        in.setImageBitmap(bmp);
        float target = bmp == null ? 0f : BACKDROP_ALPHA;
        if (cfg.animations()) {
            in.setAlpha(0f);
            in.animate().alpha(target).setDuration(450).start();
            out.animate().alpha(0f).setDuration(450).start();
        } else {
            in.setAlpha(target);
            out.setAlpha(0f);
        }
    }

    /** Flou sans bibliothèque : on réduit l'image à 16x9 puis on l'agrandit en douceur. */
    private Bitmap blurred(String key, Drawable d) {
        Bitmap cached = blurCache.get(key);
        if (cached != null) return cached;
        try {
            Bitmap tiny = Bitmap.createBitmap(16, 9, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(tiny);
            Rect old = d.copyBounds();
            d.setBounds(0, 0, 16, 9);
            d.draw(c);
            d.setBounds(old);
            Bitmap soft = Bitmap.createScaledBitmap(tiny, 192, 108, true);
            tiny.recycle();
            blurCache.put(key, soft);
            return soft;
        } catch (Exception e) {
            return null;
        }
    }

    // ---------- Fond animé ----------

    private void startAmbient() {
        if (ambientBg == null) return;
        if (ambientAnim != null) ambientAnim.cancel();
        int top = cfg.color("couleur_fond_haut", 0xFF16171C);
        int bottom = cfg.color("couleur_fond_bas", 0xFF050506);
        int moodA = cfg.color("couleur_ambiance_1", 0xFF1B2A4A);
        int moodB = cfg.color("couleur_ambiance_2", 0xFF2A1838);
        if (!cfg.animations()) {
            ambientBg.setColors(new int[]{top, bottom});
            return;
        }
        ArgbEvaluator ev = new ArgbEvaluator();
        ambientAnim = ValueAnimator.ofFloat(0f, 1f);
        ambientAnim.setDuration(24_000);
        ambientAnim.setRepeatCount(ValueAnimator.INFINITE);
        ambientAnim.setRepeatMode(ValueAnimator.REVERSE);
        ambientAnim.setInterpolator(new LinearInterpolator());
        ambientAnim.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            ambientBg.setColors(new int[]{
                    (int) ev.evaluate(t, top, moodA),
                    (int) ev.evaluate(t, moodB, bottom)});
        });
        ambientAnim.start();
    }

    private void launch(String pkg) {
        Intent i = Apps.launchIntent(this, pkg);
        if (i == null) { Updater.toast(this, "Impossible d'ouvrir cette appli"); return; }
        try { startActivity(i); } catch (Exception e) { Updater.toast(this, "Impossible d'ouvrir cette appli"); }
    }

    // ---------- Menu Réglages ----------

    private void openSettings() {
        String anim = "Animations : " + (cfg.animations() ? "activées" : "désactivées")
                + (cfg.animationsForcedLocally() ? "" : " (fichier)");
        String[] items = {
                anim,
                "Animations : suivre le fichier GitHub",
                "Rechercher une mise à jour",
                "Recharger les réglages GitHub",
                "Paramètres de la TV",
                "Version " + BuildConfig.VERSION_NAME
        };
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Réglages")
                .setItems(items, (d, which) -> {
                    switch (which) {
                        case 0: cfg.setAnimationsLocal(!cfg.animations()); startAmbient(); break;
                        case 1: cfg.followFileForAnimations(); startAmbient(); break;
                        case 2: maybeCheckUpdate(true); break;
                        case 3: fetchConfig(true); break;
                        case 4:
                            try { startActivity(new Intent(Settings.ACTION_SETTINGS)); } catch (Exception ignored) { }
                            break;
                        default: break;
                    }
                })
                .show();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
