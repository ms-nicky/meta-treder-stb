package com.stb.markettrader;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

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
                String json = httpGet(url, true);
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
                String json = httpGet(url, false);
                Ticker t = parseTicker(json);
                onSuccess(host, idx);
                return t;
            } catch (Exception e) {
                last = e;
            }
        }
        throw new Exception("Gagal ambil ticker: " + (last != null ? last.getMessage() : "semua host gagal"));
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

    private static String httpGet(String urlStr, boolean acceptGzip) throws Exception {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 4.4) MarketTraderSTB/1.0");
            conn.setRequestProperty("Accept", "application/json");
            conn.connect();

            int code = conn.getResponseCode();
            if (code >= 200 && code < 300) {
                InputStream in = conn.getInputStream();
                if (acceptGzip) {
                    String enc = conn.getContentEncoding();
                    if (enc != null && enc.toLowerCase().contains("gzip")) {
                        in = new GZIPInputStream(in);
                    }
                }
                return readStream(in);
            } else {
                InputStream es = conn.getErrorStream();
                String body = es != null ? readStream(es) : "";
                throw new Exception("HTTP " + code + " " + body);
            }
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String readStream(InputStream in) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        char[] buf = new char[8192];
        int n;
        while ((n = reader.read(buf)) != -1) {
            sb.append(buf, 0, n);
        }
        reader.close();
        return sb.toString();
    }
}