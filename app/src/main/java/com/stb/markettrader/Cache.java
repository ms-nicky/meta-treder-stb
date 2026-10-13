package com.stb.markettrader;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Cache {

    private static final int MAX_KLINES = 80;
    private static final int MAX_TICKERS = 120;

    private static final Map<String, List<Kline>> klineCache =
            new LinkedHashMap<String, List<Kline>>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, List<Kline>> e) {
                    return size() > MAX_KLINES;
                }
            };

    private static final Map<String, Ticker> tickerCache =
            new LinkedHashMap<String, Ticker>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Ticker> e) {
                    return size() > MAX_TICKERS;
                }
            };

    private static String key(String pair, String interval) {
        return pair + "|" + interval;
    }

    public static synchronized List<Kline> getKlines(String pair, String interval) {
        List<Kline> v = klineCache.get(key(pair, interval));
        return v == null ? null : new ArrayList<Kline>(v);
    }

    public static synchronized void putKlines(String pair, String interval, List<Kline> klines) {
        if (klines == null || klines.isEmpty()) return;
        klineCache.put(key(pair, interval), new ArrayList<Kline>(klines));
    }

    public static synchronized Ticker getTicker(String pair) {
        Ticker t = tickerCache.get(pair);
        if (t == null) return null;
        return copy(t);
    }

    public static synchronized void putTicker(String pair, Ticker t) {
        if (t == null) return;
        tickerCache.put(pair, copy(t));
    }

    private static Ticker copy(Ticker t) {
        Ticker c = new Ticker();
        c.lastPrice = t.lastPrice;
        c.priceChange = t.priceChange;
        c.priceChangePercent = t.priceChangePercent;
        c.highPrice = t.highPrice;
        c.lowPrice = t.lowPrice;
        c.volume = t.volume;
        c.quoteVolume = t.quoteVolume;
        return c;
    }
}