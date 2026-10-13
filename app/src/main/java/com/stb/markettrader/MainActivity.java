package com.stb.markettrader;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private static final String[] CRYPTO_PAIRS = {
            "BTCUSDT", "ETHUSDT", "BNBUSDT", "SOLUSDT", "XRPUSDT", "ADAUSDT",
            "DOGEUSDT", "TRXUSDT", "LTCUSDT", "DOTUSDT", "LINKUSDT", "AVAXUSDT",
            "MATICUSDT", "NEARUSDT", "APEUSDT", "SHIBUSDT",
            "PAXGUSDT", "XAUTUSDT", "FILUSDT", "SUIUSDT", "ARBUSDT", "OPUSDT",
            "UNIUSDT", "AAVEUSDT", "ETCUSDT", "ATOMUSDT", "XLMUSDT"
    };

    private static final String[] FOREX_PAIRS = {
            "EUR/USD", "GBP/USD", "USD/JPY", "USD/CHF", "AUD/USD", "USD/CAD",
            "NZD/USD", "EUR/GBP", "EUR/JPY", "GBP/JPY", "USD/IDR", "USD/SGD",
            "USD/MYR", "USD/CNY", "USD/THB", "USD/PHP"
    };

    private static final String[] METAL_PAIRS = {
            "XAU/USD", "XAG/USD", "XPT/USD", "XPD/USD"
    };

    private static final String[] INTERVALS = {"1m", "5m", "15m", "1h", "4h", "1d"};

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private CandlestickChartView chart;
    private WatchlistView watchlist;
    private TextView symbolText;
    private TextView priceText;
    private TextView changeText;
    private TextView connText;
    private Button updateButton;
    private TextView statHigh;
    private TextView statLow;
    private TextView statVol;
    private TextView statQVol;
    private Button btnInd;
    private Button btnSub;

    private List<String> pairs = new ArrayList<String>();

    private String currentPair = "BTCUSDT";
    private String currentInterval = "1m";
    private String lastRenderedKey = "";

    private Button pairButton;
    private final List<Button> tfButtons = new ArrayList<Button>();

    private final Runnable klinesTask = new Runnable() {
        @Override
        public void run() {
            loadKlines();
            handler.postDelayed(this, 20000);
        }
    };

    private final Runnable tickerTask = new Runnable() {
        @Override
        public void run() {
            loadTicker();
            handler.postDelayed(this, 5000);
        }
    };

    private final Runnable marketTask = new Runnable() {
        @Override
        public void run() {
            loadMarket();
            handler.postDelayed(this, 12000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Tls12SocketFactory.enable(getApplicationContext());
        setContentView(R.layout.activity_main);

        chart = findViewById(R.id.chart);
        watchlist = findViewById(R.id.watchlist);
        symbolText = findViewById(R.id.symbol);
        priceText = findViewById(R.id.price);
        changeText = findViewById(R.id.change);
        connText = findViewById(R.id.conn);
        pairButton = findViewById(R.id.btn_pair);
        updateButton = findViewById(R.id.btn_update);
        btnInd = findViewById(R.id.btn_ind);
        btnSub = findViewById(R.id.btn_sub);
        statHigh = findViewById(R.id.tv_high);
        statLow = findViewById(R.id.tv_low);
        statVol = findViewById(R.id.tv_vol);
        statQVol = findViewById(R.id.tv_qvol);

        pairs.addAll(java.util.Arrays.asList(CRYPTO_PAIRS));
        pairs.addAll(java.util.Arrays.asList(FOREX_PAIRS));
        pairs.addAll(java.util.Arrays.asList(METAL_PAIRS));

        rebuildWatchlist();
        setupIntervalButtons();
        setupPairButton();
        setupUpdateButton();
        setupIndicatorButtons();
        setupWatchlist();

        symbolText.setText(currentPair);
        refreshButtonStates();
        syncIndicatorLabels();

        chart.setFocusable(true);
        chart.requestFocus();

        handler.post(klinesTask);
        handler.post(tickerTask);
        handler.postDelayed(marketTask, 1500);

        loadRemotePairs();
        checkForAppUpdate(false);
    }

    private void rebuildWatchlist() {
        List<WatchlistView.Item> items = new ArrayList<WatchlistView.Item>();
        for (String p : pairs) {
            WatchlistView.Item it = new WatchlistView.Item(p);
            Ticker t = Cache.getTicker(p);
            if (t != null) {
                it.price = t.lastPrice;
                it.changePct = t.priceChangePercent;
                it.hasData = true;
            }
            items.add(it);
        }
        watchlist.setItems(items);
        watchlist.setSelectedPair(currentPair);
    }

    private void refreshWatchlistData() {
        List<WatchlistView.Item> items = new ArrayList<WatchlistView.Item>();
        for (String p : pairs) {
            WatchlistView.Item it = new WatchlistView.Item(p);
            Ticker t = Cache.getTicker(p);
            if (t != null) {
                it.price = t.lastPrice;
                it.changePct = t.priceChangePercent;
                it.hasData = true;
            }
            items.add(it);
        }
        watchlist.setItems(items);
    }

    private void setupWatchlist() {
        watchlist.setOnSelectListener(new WatchlistView.OnSelectListener() {
            @Override
            public void onSelect(String pair) {
                switchPair(pair);
            }
        });
    }

    private void setupIndicatorButtons() {
        btnInd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                chart.cycleOverlay();
                syncIndicatorLabels();
            }
        });
        btnSub.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                chart.cycleSub();
                syncIndicatorLabels();
            }
        });
    }

    private void syncIndicatorLabels() {
        switch (chart.getOverlayType()) {
            case CandlestickChartView.OVERLAY_MA: btnInd.setText("MA"); break;
            case CandlestickChartView.OVERLAY_EMA: btnInd.setText("EMA"); break;
            case CandlestickChartView.OVERLAY_BOLL: btnInd.setText("BOLL"); break;
            default: btnInd.setText("IND OFF"); break;
        }
        switch (chart.getSubType()) {
            case CandlestickChartView.SUB_RSI: btnSub.setText("RSI"); break;
            case CandlestickChartView.SUB_MACD: btnSub.setText("MACD"); break;
            default: btnSub.setText("VOL"); break;
        }
    }

    private void switchPair(String pair) {
        currentPair = pair;
        symbolText.setText(pair);
        watchlist.setSelectedPair(pair);
        loadKlines();
        loadTicker();
    }

    private void loadRemotePairs() {
        UpdateManager.loadRemotePairs(this, new UpdateManager.PairCallback() {
            @Override
            public void onPairs(List<String> remotePairs) {
                if (remotePairs != null && !remotePairs.isEmpty()) {
                    pairs.clear();
                    pairs.addAll(remotePairs);
                    rebuildWatchlist();
                    connText.setText("data: " + remotePairs.size() + " pair");
                }
            }
        });
    }

    private void checkForAppUpdate(final boolean manual) {
        if (manual) {
            connText.setText("memeriksa update...");
        }
        UpdateManager.checkForAppUpdate(this, new UpdateManager.UpdateCallback() {
            @Override
            public void onUpdate(final UpdateManager.UpdateInfo info) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        showUpdateDialog(info);
                    }
                });
            }

            @Override
            public void onResult(final String message) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        updateButton.setEnabled(true);
                        if (manual) {
                            android.widget.Toast.makeText(MainActivity.this, message,
                                    android.widget.Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }
        });
    }

    private void showUpdateDialog(UpdateManager.UpdateInfo info) {
        updateButton.setEnabled(true);
        new AlertDialog.Builder(this)
                .setTitle("Pembaruan Tersedia v" + info.versionName)
                .setMessage(info.notes)
                .setPositiveButton("Unduh & Install", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        android.widget.Toast.makeText(MainActivity.this,
                                "Mengunduh pembaruan...", android.widget.Toast.LENGTH_SHORT).show();
                        UpdateManager.downloadAndInstall(MainActivity.this, info);
                    }
                })
                .setNegativeButton("Nanti", null)
                .show();
    }

    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        if (event.getAction() == android.view.KeyEvent.ACTION_DOWN
                && event.getKeyCode() == android.view.KeyEvent.KEYCODE_BACK
                && chart.isFocused()) {
            pairButton.requestFocus();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private void setupIntervalButtons() {
        int ids[] = {R.id.tf_1m, R.id.tf_5m, R.id.tf_15m, R.id.tf_1h, R.id.tf_4h, R.id.tf_1d};
        for (int i = 0; i < ids.length; i++) {
            final String interval = INTERVALS[i];
            Button b = findViewById(ids[i]);
            tfButtons.add(b);
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    currentInterval = interval;
                    refreshButtonStates();
                    loadKlines();
                }
            });
        }
    }

    private void setupPairButton() {
        pairButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPairDialog();
            }
        });
    }

    private void setupUpdateButton() {
        updateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.setEnabled(false);
                checkForAppUpdate(true);
            }
        });
    }

    private void showPairDialog() {
        String[] items = pairs.toArray(new String[pairs.size()]);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Pilih Pair");
        builder.setItems(items, new android.content.DialogInterface.OnClickListener() {
            @Override
            public void onClick(android.content.DialogInterface dialog, int which) {
                switchPair(pairs.get(which));
            }
        });
        builder.show();
    }

    private void refreshButtonStates() {
        for (Button b : tfButtons) {
            b.setSelected(b.getText().toString().equals(currentInterval));
        }
    }

    private void loadKlines() {
        final String symbol = currentPair;
        final String interval = currentInterval;
        executor.execute(new Runnable() {
            @Override
            public void run() {
                final List<Kline> cached = Cache.getKlines(symbol, interval);
                if (cached != null) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (symbol.equals(currentPair) && interval.equals(currentInterval)) {
                                applyKlines(symbol, interval, cached, "cache");
                            }
                        }
                    });
                }
                try {
                    final List<Kline> klines;
                    final String source;
                    if (YahooApi.isYahoo(symbol)) {
                        klines = YahooApi.getKlines(symbol, interval);
                        source = YahooApi.isMetal(symbol) ? "Yahoo metal" : "Yahoo forex";
                    } else {
                        klines = BinanceApi.getKlines(symbol, interval, 200);
                        source = "Binance";
                    }
                    Cache.putKlines(symbol, interval, klines);
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (symbol.equals(currentPair) && interval.equals(currentInterval)) {
                                applyKlines(symbol, interval, klines,
                                        source + " • " + klines.size() + " candle");
                            }
                        }
                    });
                } catch (final Exception e) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (cached == null) {
                                updateConn("ERR: " + shortErr(e.getMessage()));
                            }
                        }
                    });
                }
            }
        });
    }

    private void applyKlines(String symbol, String interval, List<Kline> klines, String status) {
        String key = symbol + "|" + interval;
        boolean reset = !key.equals(lastRenderedKey);
        lastRenderedKey = key;
        chart.setKlines(klines, reset);
        updateConn(status);
    }

    private void loadTicker() {
        final String symbol = currentPair;
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final Ticker t = YahooApi.isYahoo(symbol)
                            ? YahooApi.getQuote(symbol)
                            : BinanceApi.get24h(symbol);
                    Cache.putTicker(symbol, t);
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (symbol.equals(currentPair)) {
                                renderTicker(t);
                            }
                        }
                    });
                } catch (final Exception e) {
                    final Ticker cached = Cache.getTicker(symbol);
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (!symbol.equals(currentPair)) return;
                            if (cached != null) {
                                renderTicker(cached);
                            } else {
                                Double last = chart.getLastClose();
                                if (last != null) {
                                    renderPrice(last);
                                    changeText.setText("--");
                                }
                                updateConn("ERR: " + shortErr(e.getMessage()));
                            }
                        }
                    });
                }
            }
        });
    }

    private void loadMarket() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    List<String> crypto = new ArrayList<String>();
                    List<String> fx = new ArrayList<String>();
                    for (String p : pairs) {
                        if (YahooApi.isYahoo(p)) fx.add(p);
                        else crypto.add(p);
                    }
                    if (!crypto.isEmpty()) {
                        Map<String, Ticker> m = BinanceApi.getQuoteBatch(crypto);
                        for (Map.Entry<String, Ticker> e : m.entrySet()) {
                            Cache.putTicker(e.getKey(), e.getValue());
                        }
                    }
                    if (!fx.isEmpty()) {
                        Map<String, Ticker> m = YahooApi.getSparkQuotes(fx);
                        for (Map.Entry<String, Ticker> e : m.entrySet()) {
                            Cache.putTicker(e.getKey(), e.getValue());
                        }
                    }
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            refreshWatchlistData();
                        }
                    });
                } catch (Exception ignored) {
                }
            }
        });
    }

    private void renderTicker(Ticker t) {
        renderPrice(t.lastPrice);
        changeText.setText(formatPct(t.priceChangePercent));
        changeText.setTextColor(t.priceChangePercent >= 0 ? 0xFF0ECB81 : 0xFFF6465D);
        boolean yahoo = YahooApi.isYahoo(currentPair);
        String unit = currencyPrefix();
        statHigh.setText(unit + formatPrice(t.highPrice));
        statLow.setText(unit + formatPrice(t.lowPrice));
        statVol.setText(!yahoo && t.volume > 0 ? formatCompact(t.volume) + " " + symbolUnit() : "--");
        statQVol.setText(!yahoo && t.quoteVolume > 0 ? "$" + formatCompact(t.quoteVolume) : "--");
    }

    private void renderPrice(double price) {
        priceText.setText(currencyPrefix() + formatPrice(price));
    }

    private String currencyPrefix() {
        if (YahooApi.isYahoo(currentPair) && !YahooApi.isMetal(currentPair)) return "";
        return "$";
    }

    private String formatPrice(double price) {
        if (price >= 1000) return new DecimalFormat("#,##0.00").format(price);
        if (price >= 1) return new DecimalFormat("#,##0.0000").format(price);
        if (price >= 0.01) return new DecimalFormat("#,##0.000000").format(price);
        return new DecimalFormat("#,##0.00000000").format(price);
    }

    private String formatCompact(double v) {
        if (v >= 1_000_000_000d) return String.format(java.util.Locale.US, "%.2fB", v / 1_000_000_000d);
        if (v >= 1_000_000d) return String.format(java.util.Locale.US, "%.2fM", v / 1_000_000d);
        if (v >= 1_000d) return String.format(java.util.Locale.US, "%.2fK", v / 1_000d);
        return String.format(java.util.Locale.US, "%.2f", v);
    }

    private String symbolUnit() {
        String p = currentPair;
        if (p.endsWith("USDT")) return p.substring(0, p.length() - 4);
        return p;
    }

    private String formatPct(double pct) {
        DecimalFormat df = new DecimalFormat("#,##0.00");
        return (pct >= 0 ? "+" : "") + df.format(pct) + "%";
    }

    private String shortErr(String msg) {
        if (msg == null) return "unknown";
        if (msg.length() > 80) return msg.substring(0, 80);
        return msg;
    }

    private void updateConn(String msg) {
        connText.setText(msg);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(klinesTask);
        handler.removeCallbacks(tickerTask);
        handler.removeCallbacks(marketTask);
        executor.shutdownNow();
    }
}