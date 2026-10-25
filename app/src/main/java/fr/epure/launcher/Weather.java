package fr.epure.launcher;

import org.json.JSONObject;

import java.util.Locale;

/** Météo via Open-Meteo (gratuit, sans clé). */
final class Weather {
    private Weather() {}

    /** Renvoie par ex. "☀  14°" ou null en cas d'échec. */
    static String fetch(double lat, double lon) {
        try {
            String url = String.format(Locale.US,
                    "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                            + "&current=temperature_2m,weather_code&timezone=auto", lat, lon);
            JSONObject cur = new JSONObject(Net.get(url)).getJSONObject("current");
            long t = Math.round(cur.getDouble("temperature_2m"));
            return symbol(cur.optInt("weather_code", -1)) + "  " + t + "°";
        } catch (Exception e) {
            return null;
        }
    }

    private static String symbol(int c) {
        if (c == 0) return "☀";
        if (c == 1 || c == 2) return "⛅";
        if (c == 3) return "☁";
        if (c == 45 || c == 48) return "🌫";
        if ((c >= 71 && c <= 77) || c == 85 || c == 86) return "❄";
        if (c >= 95) return "⛈";
        if (c >= 51) return "🌧";
        return "";
    }
}
