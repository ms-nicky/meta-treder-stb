package com.stb.markettrader;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class BinanceApi {

    private static final String[] HOSTS = {
            "https://data-api.binance.vision",
            "https://api.binance.com",
            "https://api1.binance.com",
            "https://api2.binance.com",
            "https://api3.binance.com"
    };

    private static int lastGoodHostIdx = 0;

    public interface HostFoundCallback {
        void onHost(String host);
    }

    private static HostFoundCallback hostCallback;

    public static void setHostCallback(HostFoundCallback cb) {
        hostCallback = cb;
    }

    public static List<Kline> getKlines(String symbol, String interval, int limit) throws Exception {
        Exception last = null;
        for (int idx : hostOrder()) {
            String host = HOSTS[idx];
            try {
                String url = host + "/api/v3/klines?symbol=" + symbol + "&interval=" + interval + "&limit=" + limit;
                String json = Http.getText(url);
                List<Kline> result = parseKlines(json);
                onSuccess(host, idx);
                return result;
            } catch (Exception e) {
                last = e;
            }
        }
        throw new Exception("Gagal ambil klines: " + (last != null ? last.getMessage() : "semua host gagal"));
    }

    public static Ticker get24h(String symbol) throws Exception {
        Exception last = null;
        for (int idx : hostOrder()) {
            String host = HOSTS[idx];
            try {
                String url = host + "/api/v3/ticker/24hr?symbol=" + symbol;
                String json = Http.getText(url);
                Ticker t = parseTicker(json);
                onSuccess(host, idx);
                return t;
            } catch (Exception e) {
                last = e;
            }
        }
        throw new Exception("Gagal ambil ticker: " + (last != null ? last.getMessage() : "semua host gagal"));
    }

    public static java.util.Map<String, Ticker> getQuoteBatch(List<String> symbols) throws Exception {
        java.util.Map<String, Ticker> out = new java.util.HashMap<String, Ticker>();
        if (symbols == null || symbols.isEmpty()) return out;
        StringBuilder raw = new StringBuilder("[");
        for (int i = 0; i < symbols.size(); i++) {
            if (i > 0) raw.append(",");
            raw.append("\"").append(symbols.get(i)).append("\"");
        }
        raw.append("]");
        String encoded = java.net.URLEncoder.encode(raw.toString(), "UTF-8");
        Exception last = null;
        for (int idx : hostOrder()) {
            String host = HOSTS[idx];
            try {
                String url = host + "/api/v3/ticker/24hr?symbols=" + encoded + "&type=MINI";
                String json = Http.getText(url);
                JSONArray arr = new JSONArray(json);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    String sym = o.getString("symbol");
                    Ticker t = new Ticker();
                    t.lastPrice = o.getDouble("lastPrice");
                    t.highPrice = o.optDouble("highPrice", t.lastPrice);
                    t.lowPrice = o.optDouble("lowPrice", t.lastPrice);
                    t.volume = o.optDouble("volume", 0);
                    t.quoteVolume = o.optDouble("quoteVolume", 0);
                    double open = o.optDouble("openPrice", t.lastPrice);
                    t.priceChange = t.lastPrice - open;
                    t.priceChangePercent = open > 0 ? (t.priceChange / open) * 100.0 : 0;
                    out.put(sym, t);
                }
                onSuccess(host, idx);
                return out;
            } catch (Exception e) {
                last = e;
            }
        }
        throw new Exception("Gagal ambil batch ticker: " + (last != null ? last.getMessage() : "semua host gagal"));
    }

    private static int[] hostOrder() {
        int[] order = new int[HOSTS.length];
        order[0] = lastGoodHostIdx;
        int j = 1;
        for (int i = 0; i < HOSTS.length; i++) {
            if (i != lastGoodHostIdx) {
                order[j++] = i;
            }
        }
        return order;
    }

    private static void onSuccess(String host, int idx) {
        lastGoodHostIdx = idx;
        if (hostCallback != null) {
            hostCallback.onHost(host);
        }
    }

    private static List<Kline> parseKlines(String json) throws Exception {
        JSONArray arr = new JSONArray(json);
        List<Kline> list = new ArrayList<Kline>(arr.length());
        for (int i = 0; i < arr.length(); i++) {
            JSONArray it = arr.getJSONArray(i);
            list.add(new Kline(
                    it.getLong(0),
                    it.getDouble(1),
                    it.getDouble(2),
                    it.getDouble(3),
                    it.getDouble(4),
                    it.getDouble(5)));
        }
        return list;
    }

    private static Ticker parseTicker(String json) throws Exception {
        JSONObject o = new JSONObject(json);
        Ticker t = new Ticker();
        t.lastPrice = o.getDouble("lastPrice");
        t.priceChange = o.getDouble("priceChange");
        t.priceChangePercent = o.getDouble("priceChangePercent");
        t.highPrice = o.getDouble("highPrice");
        t.lowPrice = o.getDouble("lowPrice");
        t.volume = o.getDouble("volume");
        t.quoteVolume = o.getDouble("quoteVolume");
        return t;
    }
}