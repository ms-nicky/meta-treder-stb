package com.stb.markettrader;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class YahooApi {

    private static final String BASE = "https://query1.finance.yahoo.com/v8/finance/chart/";

    public static boolean isYahoo(String pair) {
        return pair != null && pair.contains("/");
    }

    public static boolean isMetal(String pair) {
        return pair != null && (pair.startsWith("XAU/") || pair.startsWith("XAG/")
                || pair.startsWith("XPT/") || pair.startsWith("XPD/"));
    }

    public static String toYahooSymbol(String pair) {
        if (pair.startsWith("XAU/")) return "GC=F";
        if (pair.startsWith("XAG/")) return "SI=F";
        if (pair.startsWith("XPT/")) return "PL=F";
        if (pair.startsWith("XPD/")) return "PA=F";
        return pair.replace("/", "") + "=X";
    }

    private static String yahooInterval(String interval) {
        if ("1m".equals(interval)) return "1m";
        if ("5m".equals(interval)) return "5m";
        if ("15m".equals(interval)) return "15m";
        if ("1h".equals(interval) || "4h".equals(interval)) return "60m";
        return "1d";
    }

    private static String yahooRange(String interval) {
        if ("1m".equals(interval)) return "1d";
        if ("5m".equals(interval)) return "5d";
        if ("15m".equals(interval)) return "5d";
        if ("1h".equals(interval)) return "1mo";
        if ("4h".equals(interval)) return "3mo";
        return "6mo";
    }

    public static List<Kline> getKlines(String pair, String interval) throws Exception {
        return parseKlines(fetchChart(pair, yahooInterval(interval), yahooRange(interval)));
    }

    public static Ticker getQuote(String pair) throws Exception {
        JSONObject result = fetchChart(pair, "5m", "1d");
        JSONObject meta = result.getJSONObject("meta");
        List<Kline> bars = parseKlines(result);

        double price = meta.optDouble("regularMarketPrice", Double.NaN);
        if (Double.isNaN(price) && !bars.isEmpty()) price = bars.get(bars.size() - 1).close;
        double prev = meta.optDouble("chartPreviousClose", Double.NaN);
        if (Double.isNaN(prev)) prev = meta.optDouble("previousClose", price);

        double high = meta.optDouble("regularMarketDayHigh", Double.NaN);
        double low = meta.optDouble("regularMarketDayLow", Double.NaN);
        double vol = meta.optDouble("regularMarketVolume", Double.NaN);
        if (Double.isNaN(high) || Double.isNaN(low) || Double.isNaN(vol)) {
            double h = Double.NaN, l = Double.NaN, sv = 0;
            for (Kline k : bars) {
                if (Double.isNaN(h) || k.high > h) h = k.high;
                if (Double.isNaN(l) || k.low < l) l = k.low;
                sv += k.volume;
            }
            if (Double.isNaN(high)) high = h;
            if (Double.isNaN(low)) low = l;
            if (Double.isNaN(vol)) vol = sv;
        }

        Ticker t = new Ticker();
        t.lastPrice = price;
        t.highPrice = Double.isNaN(high) ? price : high;
        t.lowPrice = Double.isNaN(low) ? price : low;
        t.volume = Double.isNaN(vol) ? 0 : vol;
        t.quoteVolume = 0;
        t.priceChange = price - prev;
        t.priceChangePercent = prev > 0 ? ((price - prev) / prev) * 100.0 : 0;
        return t;
    }

    private static JSONObject fetchChart(String pair, String interval, String range) throws Exception {
        String sym = toYahooSymbol(pair);
        String url = BASE + sym + "?interval=" + interval + "&range=" + range;
        JSONObject root = new JSONObject(Http.getText(url));
        JSONObject chart = root.getJSONObject("chart");
        if (chart.has("error") && !chart.isNull("error")) {
            throw new Exception("Tidak ada data");
        }
        JSONArray results = chart.optJSONArray("result");
        if (results == null || results.length() == 0) {
            throw new Exception("Tidak ada data");
        }
        return results.getJSONObject(0);
    }

    private static List<Kline> parseKlines(JSONObject result) throws Exception {
        List<Kline> list = new ArrayList<Kline>();
        JSONArray ts = result.optJSONArray("timestamp");
        JSONObject indicators = result.optJSONObject("indicators");
        if (ts == null || indicators == null) return list;
        JSONArray quotes = indicators.optJSONArray("quote");
        if (quotes == null || quotes.length() == 0) return list;
        JSONObject q = quotes.getJSONObject(0);
        JSONArray open = q.optJSONArray("open");
        JSONArray high = q.optJSONArray("high");
        JSONArray low = q.optJSONArray("low");
        JSONArray close = q.optJSONArray("close");
        JSONArray vol = q.optJSONArray("volume");
        for (int i = 0; i < ts.length(); i++) {
            if (close == null || close.isNull(i)) continue;
            double c = close.getDouble(i);
            double o = open != null && !open.isNull(i) ? open.getDouble(i) : c;
            double h = high != null && !high.isNull(i) ? high.getDouble(i) : Math.max(o, c);
            double l = low != null && !low.isNull(i) ? low.getDouble(i) : Math.min(o, c);
            double v = vol != null && !vol.isNull(i) ? vol.getDouble(i) : 0;
            list.add(new Kline(ts.getLong(i) * 1000L, o, h, l, c, v));
        }
        return list;
    }
}