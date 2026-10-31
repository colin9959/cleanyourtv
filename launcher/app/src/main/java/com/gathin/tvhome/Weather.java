package com.gathin.tvhome;

import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Current weather for the header, fetched on a background thread and shown as one line under the
 * date ("HEFEI · PARTLY CLOUDY 18~28°C"). Nothing here touches the main thread.
 *
 * <p>Sources, tried in order:
 * <ol>
 *   <li>China Meteorological Administration city feed ({@code weather.com.cn}), by city code
 *       (config.xml, weather_city_code; default 101220101 = Hefei). Fast inside China, plain HTTP.</li>
 *   <li>Open-Meteo by coordinates (config.xml, latitude/longitude), HTTPS, no key. Used when the
 *       CMA feed fails or the city code is empty.</li>
 * </ol>
 *
 * <p>Both feeds are read-only JSON; neither sends any data about the TV.
 */
final class Weather {
    private static final String TAG = "TvHome.Weather";
    private static final int TIMEOUT_MS = 6_000;

    /** WMO weather codes -> short Chinese label, from Open-Meteo's weathercode. */
    static String label(int code) {
        if (code == 0) return "晴";
        if (code == 1) return "晴间多云";
        if (code == 2) return "多云";
        if (code == 3) return "阴";
        if (code == 45 || code == 48) return "雾";
        if (code >= 51 && code <= 57) return "毛毛雨";
        if (code >= 61 && code <= 67) return "雨";
        if (code >= 71 && code <= 77) return "雪";
        if (code >= 80 && code <= 82) return "阵雨";
        if (code == 85 || code == 86) return "阵雪";
        if (code >= 95) return "雷暴";
        return "未知";
    }

    /** One line for the header: city, condition, temperature range. Empty when the feed gave nothing usable. */
    static String line(String city, String condition, String min, String max) {
        StringBuilder b = new StringBuilder();
        if (city != null && !city.isEmpty()) b.append(city).append(" · ");
        if (condition != null && !condition.isEmpty()) b.append(condition).append(' ');
        if (min != null && !min.isEmpty() && max != null && !max.isEmpty()) {
            b.append(min).append('~').append(max);
        } else if (max != null && !max.isEmpty()) {
            b.append(max);
        } else if (min != null && !min.isEmpty()) {
            b.append(min);
        }
        return b.toString().trim();
    }

    /** Fetch current weather. Runs on a background thread; returns an empty string when nothing worked. */
    static String fetch(String cityCode, double latitude, double longitude) {
        if (cityCode != null && !cityCode.trim().isEmpty()) {
            String fromCma = cma(cityCode.trim());
            if (fromCma != null) return fromCma;
        }
        return openMeteo(latitude, longitude);
    }

    /** weather.com.cn city feed: {"weatherinfo":{"city":"合肥","temp1":"18℃","temp2":"28℃","weather":"多云"}}. */
    private static String cma(String code) {
        try {
            JSONObject info = getJson("http://www.weather.com.cn/data/cityinfo/" + code + ".html")
                    .optJSONObject("weatherinfo");
            if (info == null) return null;
            String cond = info.optString("weather", "").trim();
            String min = info.optString("temp1", "").trim();
            String max = info.optString("temp2", "").trim();
            String text = line("", cond, min, max);
            return text.isEmpty() ? null : text;
        } catch (Exception e) {
            Log.w(TAG, "CMA feed failed: " + e.getMessage());
            return null;
        }
    }

    /** Open-Meteo forecast for one day: current temperature and the day's min/max. */
    private static String openMeteo(double latitude, double longitude) {
        if (latitude == 0 && longitude == 0) return null;
        try {
            URL url = new URL("https://api.open-meteo.com/v1/forecast?latitude=" + latitude
                    + "&longitude=" + longitude
                    + "&current_weather=true&daily=temperature_2m_max,temperature_2m_min"
                    + "&timezone=Asia%2FShanghai&forecast_days=1");
            JSONObject root = getJson(url.toString());
            JSONObject current = root.optJSONObject("current_weather");
            JSONObject daily = root.optJSONObject("daily");
            if (current == null || daily == null) return null;
            String cond = label(current.optInt("weathercode", -1));
            String now = Math.round(current.optDouble("temperature", 0)) + "°C";
            double[] max = doubles(daily, "temperature_2m_max");
            double[] min = doubles(daily, "temperature_2m_min");
            String maxS = max.length > 0 ? Math.round(max[0]) + "°C" : "";
            String minS = min.length > 0 ? Math.round(min[0]) + "°C" : "";
            String text = line("", cond, minS, maxS);
            if (text.isEmpty()) text = now;
            return text;
        } catch (Exception e) {
            Log.w(TAG, "Open-Meteo failed: " + e.getMessage());
            return null;
        }
    }

    private static JSONObject getJson(String urlString) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) TvHome");
        conn.setRequestProperty("Accept", "application/json");
        int code = conn.getResponseCode();
        if (code != 200) {
            conn.disconnect();
            throw new java.io.IOException("HTTP " + code);
        }
        InputStream in = conn.getInputStream();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[2048];
            int n;
            while ((n = reader.read(buf)) > 0) sb.append(buf, 0, n);
            String text = sb.toString();
            // The CMA feed is served as JSON with a possible BOM; both parsers tolerate it.
            if (text.startsWith("\uFEFF")) text = text.substring(1);
            return new JSONObject(text);
        } finally {
            conn.disconnect();
        }
    }

    private static double[] doubles(JSONObject o, String key) {
        org.json.JSONArray a = o.optJSONArray(key);
        if (a == null || a.length() == 0) return new double[0];
        double[] out = new double[a.length()];
        for (int i = 0; i < a.length(); i++) out[i] = a.optDouble(i, Double.NaN);
        return out;
    }
}
