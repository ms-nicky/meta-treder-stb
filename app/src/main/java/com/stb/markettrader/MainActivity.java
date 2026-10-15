package com.stb.markettrader;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private static final String[] TRADE_PAIRS = {
            "XAU/USD"
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
    private TextView clockText;
    private Button updateButton;
    private TextView statHigh;
    private TextView statLow;
    private TextView statVol;
    private TextView statQVol;
    private Button btnInd;
    private Button btnSub;
    private EditText searchText;

    private static final String PREF_MT5 = "mt5";
    private static final String DEFAULT_HOST = "demo6.justmarketsidn.pro";
    private static final String DEFAULT_LOGIN = "1300048546";
    private static final String DEFAULT_PASS = "@NiCKY070709";
    private Button brokerButton;
    private Mt5Client mt5Client;
    private boolean mt5Connected = false;
    private long lastLiveRender = 0;
    private double lastBrokerMid = 0;

    private List<String> pairs = new ArrayList<String>();
    private final List<String> viewPairs = new ArrayList<String>();

    private String currentPair = "XAU/USD";
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

    private final Runnable clockTask = new Runnable() {
        @Override
        public void run() {
            updateClock();
            handler.postDelayed(this, 1000);
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
        clockText = findViewById(R.id.tv_clock);
        searchText = findViewById(R.id.search);
        brokerButton = findViewById(R.id.btn_broker);
        pairButton = findViewById(R.id.btn_pair);
        searchText.setVisibility(View.GONE);
        pairButton.setVisibility(View.GONE);
        updateButton = findViewById(R.id.btn_update);
        btnInd = findViewById(R.id.btn_ind);
        btnSub = findViewById(R.id.btn_sub);
        statHigh = findViewById(R.id.tv_high);
        statLow = findViewById(R.id.tv_low);
        statVol = findViewById(R.id.tv_vol);
        statQVol = findViewById(R.id.tv_qvol);

        pairs.addAll(java.util.Arrays.asList(TRADE_PAIRS));
        viewPairs.addAll(pairs);

        rebuildWatchlist();
        setupIntervalButtons();
        setupPairButton();
        setupUpdateButton();
        setupIndicatorButtons();
        setupWatchlist();
        setupSearch();
        setupBrokerButton();

        symbolText.setText(currentPair);
        refreshButtonStates();
        syncIndicatorLabels();

        chart.setFocusable(true);
        chart.requestFocus();

        handler.post(klinesTask);
        handler.post(tickerTask);
        handler.post(clockTask);
        handler.postDelayed(marketTask, 1500);

        checkForAppUpdate(false);
        startBrokerClient();
    }

    private void updateClock() {
        java.util.TimeZone wib = java.util.TimeZone.getTimeZone("Asia/Jakarta");
        java.util.Date now = new java.util.Date();
        java.text.SimpleDateFormat tf = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US);
        tf.setTimeZone(wib);
        java.text.SimpleDateFormat df = new java.text.SimpleDateFormat("EEE dd MMM", java.util.Locale.US);
        df.setTimeZone(wib);
        clockText.setText(df.format(now) + "  " + tf.format(now) + " WIB");
    }

    private void rebuildWatchlist() {
        List<WatchlistView.Item> items = new ArrayList<WatchlistView.Item>();
        for (String p : viewPairs) {
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
        for (String p : viewPairs) {
            WatchlistView.Item it = new WatchlistView.Item(p);
            Ticker t = Cache.getTicker(p);
            if (t != null) {
                it.price = t.lastPrice;
                it.changePct = t.priceChangePercent;
                it.hasData = true;
            }
            if (mt5Connected && lastBrokerMid > 0 && p.startsWith("XAU/")) {
                it.price = lastBrokerMid;
                it.hasData = true;
            }
            items.add(it);
        }
        watchlist.setItems(items);
    }

    private void setupSearch() {
        searchText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                applyFilter(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        searchText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, android.view.KeyEvent event) {
                boolean enter = event != null
                        && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER
                        && event.getAction() == android.view.KeyEvent.ACTION_DOWN;
                if (enter || actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
                        || actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                    String sel = watchlist.getSelectedPair();
                    if (sel == null && !viewPairs.isEmpty()) sel = viewPairs.get(0);
                    if (sel != null) {
                        switchPair(sel);
                        chart.requestFocus();
                    }
                    return true;
                }
                return false;
            }
        });
    }

    private void applyFilter(String q) {
        String s = q == null ? "" : q.trim().toUpperCase();
        viewPairs.clear();
        for (String p : pairs) {
            if (s.isEmpty() || p.toUpperCase().contains(s)) {
                viewPairs.add(p);
            }
        }
        rebuildWatchlist();
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

    private void setupBrokerButton() {
        brokerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showBrokerDialog();
            }
        });
        refreshBrokerButton();
    }

    private SharedPreferences brokerPrefs() {
        return getSharedPreferences(PREF_MT5, MODE_PRIVATE);
    }

    private void refreshBrokerButton() {
        if (mt5Connected) {
            brokerButton.setText(" MT5 LIVE ");
            brokerButton.setTextColor(0xFF0ECB81);
        } else if (brokerPrefs().getBoolean("enabled", true)) {
            brokerButton.setText(" MT5 ... ");
            brokerButton.setTextColor(0xFFF0B90B);
        } else {
            brokerButton.setText(" MT5 ");
            brokerButton.setTextColor(getResources().getColor(R.color.btn_pair_text));
        }
    }

    private void showBrokerDialog() {
        SharedPreferences p = brokerPrefs();
        final EditText hostEt = dialogField("Host", p.getString("host", DEFAULT_HOST));
        final EditText loginEt = dialogField("Login", p.getString("login", DEFAULT_LOGIN));
        final EditText passEt = dialogField("Password", p.getString("password", DEFAULT_PASS));
        passEt.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (getResources().getDisplayMetrics().density * 16);
        box.setPadding(pad, pad, pad, pad);
        box.addView(dialogLabel("Server MT5 JustMarkets (host sama seperti web terminal)"));
        box.addView(hostEt);
        box.addView(loginEt);
        box.addView(passEt);

        new AlertDialog.Builder(this)
                .setTitle("Feed Broker (MT5)")
                .setView(box)
                .setPositiveButton("Simpan & Hubungkan", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        SharedPreferences.Editor e = brokerPrefs().edit();
                        e.putBoolean("enabled", true);
                        e.putString("host", hostEt.getText().toString().trim());
                        e.putString("login", loginEt.getText().toString().trim());
                        e.putString("password", passEt.getText().toString());
                        e.apply();
                        startBrokerClient();
                    }
                })
                .setNeutralButton("Putuskan", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        SharedPreferences.Editor e = brokerPrefs().edit();
                        e.putBoolean("enabled", false);
                        e.apply();
                        stopBrokerClient();
                        refreshBrokerButton();
                        updateConn("MT5 dimatikan");
                    }
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    private TextView dialogLabel(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(15);
        t.setPadding(0, 0, 0, 8);
        return t;
    }

    private EditText dialogField(String hint, String value) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(value);
        e.setSingleLine(true);
        return e;
    }

    private void startBrokerClient() {
        stopBrokerClient();
        SharedPreferences p = brokerPrefs();
        if (!p.getBoolean("enabled", true)) {
            refreshBrokerButton();
            return;
        }
        String host = p.getString("host", DEFAULT_HOST).trim();
        String loginS = p.getString("login", DEFAULT_LOGIN).trim();
        final String pass = p.getString("password", DEFAULT_PASS);
        if (host.isEmpty() || loginS.isEmpty()) {
            updateConn("MT5: host/login belum diisi");
            return;
        }
        long login;
        try {
            login = Long.parseLong(loginS);
        } catch (Exception e) {
            updateConn("MT5: login harus angka");
            return;
        }
        try {
            javax.net.ssl.SSLSocketFactory factory =
                    Tls12SocketFactory.socketFactory(getApplicationContext());
            mt5Client = new Mt5Client(host, 443, "/terminal", login, pass, factory,
                    new Mt5Client.Listener() {
                        @Override
                        public void onStatus(final String status, final boolean connected) {
                            mt5Connected = connected;
                            if (!connected) lastBrokerMid = 0;
                            refreshBrokerButton();
                            if (status != null) updateConn(status);
                        }

                        @Override
                        public void onTick(double bid, double ask, long timeSec) {
                            onLiveTick(bid, ask);
                        }
                    });
            mt5Client.start();
        } catch (Exception e) {
            updateConn("MT5: " + e.getMessage());
        }
        refreshBrokerButton();
    }

    private void stopBrokerClient() {
        if (mt5Client != null) {
            mt5Client.stop();
            mt5Client = null;
        }
        mt5Connected = false;
    }

    private void onLiveTick(double bid, double ask) {
        if (!"XAU/USD".equals(currentPair)) return;
        double mid = (bid > 0 && ask > 0) ? (bid + ask) / 2 : Math.max(bid, ask);
        if (mid <= 0) return;
        lastBrokerMid = mid;
        long now = System.currentTimeMillis();
        if (now - lastLiveRender < 200) return;
        lastLiveRender = now;
        renderPrice(mid);
        refreshWatchlistData();
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
                    applyFilter(searchText.getText().toString());
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
        if (event.getAction() == android.view.KeyEvent.ACTION_DOWN) {
            int kc = event.getKeyCode();
            boolean typing = searchText != null && searchText.hasFocus();

            if (kc == android.view.KeyEvent.KEYCODE_BACK && chart.isFocused()) {
                pairButton.requestFocus();
                return true;
            }
            if (kc == android.view.KeyEvent.KEYCODE_ESCAPE) {
                onBackPressed();
                return true;
            }
            if (!typing) {
                switch (kc) {
                    case android.view.KeyEvent.KEYCODE_PLUS:
                    case android.view.KeyEvent.KEYCODE_EQUALS:
                    case android.view.KeyEvent.KEYCODE_NUMPAD_ADD:
                        chart.zoomIn();
                        return true;
                    case android.view.KeyEvent.KEYCODE_MINUS:
                    case android.view.KeyEvent.KEYCODE_NUMPAD_SUBTRACT:
                        chart.zoomOut();
                        return true;
                    case android.view.KeyEvent.KEYCODE_M:
                        chart.cycleOverlay();
                        syncIndicatorLabels();
                        return true;
                    case android.view.KeyEvent.KEYCODE_V:
                        chart.cycleSub();
                        syncIndicatorLabels();
                        return true;
                    case android.view.KeyEvent.KEYCODE_P:
                        showPairDialog();
                        return true;
                    case android.view.KeyEvent.KEYCODE_U:
                        updateButton.performClick();
                        return true;
                    case android.view.KeyEvent.KEYCODE_S:
                    case android.view.KeyEvent.KEYCODE_SLASH:
                        searchText.requestFocus();
                        return true;
                    case android.view.KeyEvent.KEYCODE_1:
                    case android.view.KeyEvent.KEYCODE_2:
                    case android.view.KeyEvent.KEYCODE_3:
                    case android.view.KeyEvent.KEYCODE_4:
                    case android.view.KeyEvent.KEYCODE_5:
                    case android.view.KeyEvent.KEYCODE_6:
                        setIntervalIndex(kc - android.view.KeyEvent.KEYCODE_1);
                        return true;
                }
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private void setIntervalIndex(int idx) {
        if (idx < 0 || idx >= INTERVALS.length) return;
        currentInterval = INTERVALS[idx];
        refreshButtonStates();
        loadKlines();
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
                        klines = fetchYahooKlines(symbol, interval);
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
                    final Ticker t = fetchTicker(symbol);
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
                List<String> crypto = new ArrayList<String>();
                List<String> fx = new ArrayList<String>();
                for (String p : pairs) {
                    if (YahooApi.isYahoo(p)) fx.add(p);
                    else crypto.add(p);
                }
                if (!crypto.isEmpty()) {
                    try {
                        Map<String, Ticker> m = BinanceApi.getQuoteBatch(crypto);
                        for (Map.Entry<String, Ticker> e : m.entrySet()) {
                            Cache.putTicker(e.getKey(), e.getValue());
                        }
                    } catch (Exception ignored) {
                    }
                }
                if (!fx.isEmpty()) {
                    try {
                        Map<String, Ticker> m = YahooApi.getSparkQuotes(fx);
                        for (Map.Entry<String, Ticker> e : m.entrySet()) {
                            Cache.putTicker(e.getKey(), e.getValue());
                        }
                    } catch (Exception ignored) {
                    }
                }
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        refreshWatchlistData();
                    }
                });
            }
        });
    }

    private static final String GOLD_FALLBACK = "XAUTUSDT";

    private List<Kline> fetchYahooKlines(String symbol, String interval) throws Exception {
        try {
            return YahooApi.getKlines(symbol, interval);
        } catch (Exception e) {
            if (symbol.startsWith("XAU/")) {
                return BinanceApi.getKlines(GOLD_FALLBACK, interval, 200);
            }
            throw e;
        }
    }

    private Ticker fetchTicker(String symbol) throws Exception {
        if (!YahooApi.isYahoo(symbol)) {
            return BinanceApi.get24h(symbol);
        }
        try {
            return YahooApi.getQuote(symbol);
        } catch (Exception e) {
            if (symbol.startsWith("XAU/")) {
                return BinanceApi.get24h(GOLD_FALLBACK);
            }
            throw e;
        }
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
        chart.updateLastPrice(price);
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
        String m = msg;
        if (m.contains("Unable to resolve host") || m.contains("UnknownHost")
                || m.contains("No address associated")) {
            return "DNS/jaringan bermasalah — mencoba lagi otomatis";
        }
        if (m.toLowerCase().contains("timed out") || m.toLowerCase().contains("timeout")) {
            return "koneksi lambat (timeout) — mencoba lagi";
        }
        if (m.length() > 80) m = m.substring(0, 80);
        return m;
    }

    private void updateConn(String msg) {
        connText.setText(msg);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopBrokerClient();
        handler.removeCallbacks(klinesTask);
        handler.removeCallbacks(tickerTask);
        handler.removeCallbacks(marketTask);
        handler.removeCallbacks(clockTask);
        executor.shutdownNow();
    }
}