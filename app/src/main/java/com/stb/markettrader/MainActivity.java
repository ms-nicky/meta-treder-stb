package com.stb.markettrader;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private static final String[] TRADE_PAIRS = {
            "XAU/USD"
    };

    private static final String[] INTERVALS = {"1m", "5m", "15m", "1h", "4h", "1d"};

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ExecutorService mt5Executor = Executors.newSingleThreadExecutor();
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

    private TextView lblStat1;
    private TextView lblStat2;
    private TextView lblStat3;
    private TextView lblStat4;

    private Button tabTrade;
    private Button tabPos;
    private Button tabHist;
    private Button tabDom;
    private ScrollView panelScroll;
    private LinearLayout tradePanel;
    private int activeTab = 0;

    private static final String PREF_MT5 = "mt5";
    private static final String DEFAULT_HOST = "demo6.justmarketsidn.pro";
    private static final String DEFAULT_LOGIN = "1300048546";
    private static final String DEFAULT_PASS = "@NiCKY070709";
    private Button brokerButton;
    private Mt5Client mt5Client;
    private boolean mt5Connected = false;
    private long lastLiveRender = 0;
    private double lastBrokerMid = 0;
    private double lastBid = 0;
    private double lastAsk = 0;

    private Mt5Client.Account account;
    private List<Mt5Client.TradeLine> positions = new ArrayList<Mt5Client.TradeLine>();
    private List<Mt5Client.Deal> deals = new ArrayList<Mt5Client.Deal>();
    private List<Mt5Client.BookLevel> bookBids = new ArrayList<Mt5Client.BookLevel>();
    private List<Mt5Client.BookLevel> bookAsks = new ArrayList<Mt5Client.BookLevel>();
    private TextView domBid;
    private TextView domAsk;
    private TextView domSpread;

    private EditText lotInput;
    private EditText slInput;
    private EditText tpInput;
    private Button tradeSellBtn;
    private Button tradeBuyBtn;

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
        lblStat1 = findViewById(R.id.lbl_stat1);
        lblStat2 = findViewById(R.id.lbl_stat2);
        lblStat3 = findViewById(R.id.lbl_stat3);
        lblStat4 = findViewById(R.id.lbl_stat4);
        tabTrade = findViewById(R.id.tab_trade);
        tabPos = findViewById(R.id.tab_pos);
        tabHist = findViewById(R.id.tab_hist);
        tabDom = findViewById(R.id.tab_dom);
        panelScroll = findViewById(R.id.panel_scroll);
        tradePanel = findViewById(R.id.trade_panel);

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
        setupTabs();

        symbolText.setText(currentPair);
        refreshButtonStates();
        syncIndicatorLabels();
        switchTab(0);

        chart.setFocusable(true);
        chart.requestFocus();

        handler.post(klinesTask);
        handler.post(tickerTask);
        handler.post(clockTask);
        handler.postDelayed(marketTask, 1500);

        checkForAppUpdate(false);
        startBrokerClient();
    }

    // ---------------- tabs / panel ----------------

    private void setupTabs() {
        tabTrade.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchTab(0);
            }
        });
        tabPos.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchTab(1);
            }
        });
        tabHist.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchTab(2);
            }
        });
        tabDom.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchTab(3);
            }
        });
    }

    private void switchTab(int tab) {
        activeTab = tab;
        tabTrade.setTextColor(tab == 0 ? 0xFFF0B90B : 0xFFCFD8E3);
        tabPos.setTextColor(tab == 1 ? 0xFFF0B90B : 0xFFCFD8E3);
        tabHist.setTextColor(tab == 2 ? 0xFFF0B90B : 0xFFCFD8E3);
        tabDom.setTextColor(tab == 3 ? 0xFFF0B90B : 0xFFCFD8E3);
        rebuildPanel();
    }

    private void rebuildPanel() {
        tradePanel.removeAllViews();
        lotInput = null;
        slInput = null;
        tpInput = null;
        tradeSellBtn = null;
        tradeBuyBtn = null;
        domBid = null;
        domAsk = null;
        domSpread = null;
        switch (activeTab) {
            case 0: buildTradePanel(); break;
            case 1: buildPositionsPanel(); break;
            case 2: buildHistoryPanel(); break;
            default: buildDomPanel(); break;
        }
    }

    private TextView panelTitle(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(0xFF8A94A6);
        t.setTextSize(15);
        t.setPadding(0, 0, 0, 10);
        return t;
    }

    private void buildTradePanel() {
        if (!mt5Connected) {
            tradePanel.addView(panelTitle("Hubungkan MT5 (tombol MT5 di atas) untuk trading."));
            return;
        }
        tradePanel.addView(panelTitle("VOLUME (lot)"));

        lotInput = new EditText(this);
        lotInput.setText("0.01");
        lotInput.setSingleLine(true);
        lotInput.setTextColor(0xFFF2F5FA);
        lotInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        tradePanel.addView(lotInput, rowParams());

        LinearLayout sltp = new LinearLayout(this);
        sltp.setOrientation(LinearLayout.HORIZONTAL);
        slInput = new EditText(this);
        slInput.setHint("SL");
        slInput.setSingleLine(true);
        slInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        tpInput = new EditText(this);
        tpInput.setHint("TP");
        tpInput.setSingleLine(true);
        tpInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        sltp.addView(slInput, weightParams());
        sltp.addView(tpInput, weightParams());
        tradePanel.addView(sltp);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Button sell = new Button(this);
        sell.setText("SELL " + fmt(lastBid));
        sell.setTextColor(0xFFFFFFFF);
        sell.setBackgroundColor(0xFFF6465D);
        sell.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmTrade(false);
            }
        });
        Button buy = new Button(this);
        buy.setText("BUY " + fmt(lastAsk));
        buy.setTextColor(0xFFFFFFFF);
        buy.setBackgroundColor(0xFF0ECB81);
        buy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmTrade(true);
            }
        });
        tradeSellBtn = sell;
        tradeBuyBtn = buy;
        row.addView(sell, weightParams());
        row.addView(buy, weightParams());
        tradePanel.addView(row);

        tradePanel.addView(panelTitle("SL/TP opsional (harga). Kosongkan bila tidak."));
    }

    private void buildPositionsPanel() {
        if (!mt5Connected) {
            tradePanel.addView(panelTitle("Hubungkan MT5 untuk melihat posisi."));
            return;
        }
        if (positions.isEmpty()) {
            tradePanel.addView(panelTitle("Tidak ada posisi aktif."));
            return;
        }
        for (int i = 0; i < positions.size(); i++) {
            final Mt5Client.TradeLine p = positions.get(i);
            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setPadding(0, 8, 0, 8);

            TextView head = new TextView(this);
            head.setText((p.pending ? "PENDING " : "") + (p.buy ? "BUY" : "SELL")
                    + "  " + trim(p.volume) + " lot");
            head.setTextColor(p.buy ? 0xFF0ECB81 : 0xFFF6465D);
            head.setTextSize(18);
            head.setTypeface(Typeface.DEFAULT_BOLD);
            box.addView(head);

            TextView sub = new TextView(this);
            sub.setText("Entry " + fmt(p.entry)
                    + (p.sl > 0 ? "   SL " + fmt(p.sl) : "")
                    + (p.tp > 0 ? "   TP " + fmt(p.tp) : ""));
            sub.setTextColor(0xFFB6C0CF);
            sub.setTextSize(15);
            box.addView(sub);

            if (!p.pending) {
                TextView pl = new TextView(this);
                pl.setText("P/L " + (p.profit >= 0 ? "+" : "") + fmt(p.profit));
                pl.setTextColor(p.profit >= 0 ? 0xFF0ECB81 : 0xFFF6465D);
                pl.setTextSize(17);
                pl.setTypeface(Typeface.DEFAULT_BOLD);
                box.addView(pl);

                Button close = new Button(this);
                close.setText("TUTUP");
                close.setTextSize(16);
                close.setTextColor(0xFFFFFFFF);
                close.setBackgroundColor(0xFF3A4351);
                close.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        confirmClose(p);
                    }
                });
                box.addView(close, rowParams());
            }

            tradePanel.addView(box);
            View sep = new View(this);
            sep.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
            sep.setBackgroundColor(0xFF232C3B);
            tradePanel.addView(sep);
        }
    }

    private void buildHistoryPanel() {
        if (!mt5Connected) {
            tradePanel.addView(panelTitle("Hubungkan MT5 untuk melihat riwayat."));
            return;
        }
        if (deals.isEmpty()) {
            tradePanel.addView(panelTitle("Belum ada transaksi XAUUSD."));
            return;
        }
        for (int i = deals.size() - 1; i >= 0 && i >= deals.size() - 30; i--) {
            Mt5Client.Deal d = deals.get(i);
            TextView t = new TextView(this);
            String dir = d.action == 0 ? "BUY" : "SELL";
            t.setText(wibTime(d.time) + "  " + dir + " " + trim(d.volume)
                    + "\n" + fmt(d.open) + " -> " + fmt(d.close)
                    + "   P/L " + (d.profit >= 0 ? "+" : "") + fmt(d.profit));
            t.setTextColor(d.profit >= 0 ? 0xFFB6E3C8 : 0xFFF0B6BC);
            t.setTextSize(15);
            t.setPadding(0, 6, 0, 6);
            tradePanel.addView(t);
        }
    }

    private void buildDomPanel() {
        if (!mt5Connected) {
            tradePanel.addView(panelTitle("Hubungkan MT5 untuk Depth of Market."));
            return;
        }
        if (bookBids.isEmpty() && bookAsks.isEmpty()) {
            domBid = null;
            domAsk = null;
            domSpread = null;
            tradePanel.addView(panelTitle("Order book tidak tersedia dari broker.\nHarga terkini:"));
            domBid = domLine("BID", lastBid, 0xFF0ECB81);
            domAsk = domLine("ASK", lastAsk, 0xFFF6465D);
            domSpread = domLine("SPREAD", spread(), 0xFFF0B90B);
            return;
        }
        tradePanel.addView(panelTitle("ASK (jual)"));
        int n = Math.min(bookAsks.size(), 6);
        for (int i = n - 1; i >= 0; i--) {
            Mt5Client.BookLevel l = bookAsks.get(i);
            tradePanel.addView(domRow(fmt(l.price), trim(l.volume), 0xFFF6465D));
        }
        tradePanel.addView(panelTitle("BID (beli)"));
        int m = Math.min(bookBids.size(), 6);
        for (int i = 0; i < m; i++) {
            Mt5Client.BookLevel l = bookBids.get(i);
            tradePanel.addView(domRow(fmt(l.price), trim(l.volume), 0xFF0ECB81));
        }
    }

    private TextView domLine(String name, double value, int color) {
        TextView t = new TextView(this);
        t.setText(name + "   " + (value > 0 ? fmt(value) : "--"));
        t.setTextColor(color);
        t.setTextSize(20);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, 6, 0, 6);
        return t;
    }

    private double spread() {
        if (lastBid > 0 && lastAsk > 0) return lastAsk - lastBid;
        return 0;
    }

    private void refreshDomFallback() {
        if (activeTab != 3 || domBid == null) return;
        domBid.setText("BID   " + (lastBid > 0 ? fmt(lastBid) : "--"));
        domAsk.setText("ASK   " + (lastAsk > 0 ? fmt(lastAsk) : "--"));
        domSpread.setText("SPREAD   " + (spread() > 0 ? fmt(spread()) : "--"));
    }

    private LinearLayout domRow(String price, String vol, int color) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        TextView p = new TextView(this);
        p.setText(price);
        p.setTextColor(color);
        p.setTextSize(17);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setLayoutParams(weightParams());
        TextView v = new TextView(this);
        v.setText(vol);
        v.setTextColor(0xFFB6C0CF);
        v.setTextSize(17);
        v.setGravity(Gravity.RIGHT);
        v.setLayoutParams(weightParams());
        r.addView(p);
        r.addView(v);
        return r;
    }

    private LinearLayout.LayoutParams rowParams() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams weightParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(2, 2, 2, 2);
        return lp;
    }

    // ---------------- trade actions ----------------

    private void confirmTrade(final boolean buy) {
        if (!mt5Connected) return;
        double lots = parseDouble(lotInput, 0.01);
        double sl = parseDouble(slInput, 0);
        double tp = parseDouble(tpInput, 0);
        if (lots <= 0) {
            toast("Volume tidak valid");
            return;
        }
        final double flots = lots;
        final double fsl = sl;
        final double ftp = tp;
        new AlertDialog.Builder(this)
                .setTitle(buy ? "Konfirmasi BUY" : "Konfirmasi SELL")
                .setMessage((buy ? "BUY " : "SELL ") + flots + " lot XAU/USD @ "
                        + (buy ? "market (ask)" : "market (bid)")
                        + (fsl > 0 ? "\nSL " + fsl : "") + (ftp > 0 ? "\nTP " + ftp : ""))
                .setPositiveButton("Kirim", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        doTrade(buy, flots, fsl, ftp);
                    }
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    private void doTrade(final boolean buy, final double lots, final double sl, final double tp) {
        final Mt5Client client = mt5Client;
        if (client == null) return;
        toast("Mengirim order...");
        mt5Executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final Mt5Client.TradeResult r = client.placeMarket(buy, lots, sl, tp);
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            onTradeDone(r);
                        }
                    });
                } catch (final Exception e) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            toast("Gagal: " + e.getMessage());
                        }
                    });
                }
            }
        });
    }

    private void confirmClose(final Mt5Client.TradeLine p) {
        new AlertDialog.Builder(this)
                .setTitle("Tutup posisi?")
                .setMessage((p.buy ? "BUY " : "SELL ") + trim(p.volume) + " lot @ " + fmt(p.entry)
                        + "\nP/L " + (p.profit >= 0 ? "+" : "") + fmt(p.profit))
                .setPositiveButton("Tutup", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        doClose(p);
                    }
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    private void doClose(final Mt5Client.TradeLine p) {
        final Mt5Client client = mt5Client;
        if (client == null) return;
        toast("Menutup posisi...");
        mt5Executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final Mt5Client.TradeResult r = client.closePosition(p.id, p.buy, p.volume);
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            onTradeDone(r);
                        }
                    });
                } catch (final Exception e) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            toast("Gagal: " + e.getMessage());
                        }
                    });
                }
            }
        });
    }

    private void onTradeDone(Mt5Client.TradeResult r) {
        if (r == null) {
            toast("Tidak ada balasan server");
        } else if (r.retcode == 0) {
            if (r.price > 0) {
                toast("Order terkirim @ " + fmt(r.price));
            } else {
                toast("Order terkirim (menunggu konfirmasi server)");
            }
        } else if (r.ok()) {
            toast("Order berhasil @ " + fmt(r.price)
                    + (r.comment != null && r.comment.length() > 0 ? " (" + r.comment + ")" : ""));
        } else {
            toast("Ditolak (kode " + r.retcode + ")" + (r.comment.length() > 0 ? " " + r.comment : ""));
        }
        if (mt5Client != null) mt5Client.refreshPositions();
        refreshAccount();
        loadHistory();
        switchTab(1);
    }

    private void refreshAccount() {
        final Mt5Client client = mt5Client;
        if (client == null) return;
        mt5Executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final Mt5Client.Account a = client.fetchAccount();
                    if (a != null) {
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                account = a;
                                renderAccount();
                            }
                        });
                    }
                } catch (Exception ignored) {
                }
            }
        });
    }

    private void loadHistory() {
        final Mt5Client client = mt5Client;
        if (client == null) return;
        mt5Executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    long now = System.currentTimeMillis() / 1000L;
                    final List<Mt5Client.Deal> d = client.fetchHistory(now - 86400L * 30, now);
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            deals = d;
                            if (activeTab == 2) rebuildPanel();
                        }
                    });
                } catch (Exception ignored) {
                }
            }
        });
    }

    // ---------------- account rendering ----------------

    private void renderAccount() {
        if (account == null) return;
        setAccountLabels();
        statHigh.setText("$" + fmt(account.balance));
        statLow.setText("$" + fmt(account.equity));
        statVol.setText((account.profit >= 0 ? "+" : "") + "$" + fmt(account.profit));
        statVol.setTextColor(account.profit >= 0 ? 0xFF0ECB81 : 0xFFF6465D);
        statQVol.setText("1:" + account.leverage);
        statQVol.setTextColor(0xFFF2F5FA);
    }

    private void setAccountLabels() {
        lblStat1.setText("Balance: ");
        lblStat2.setText("Equity: ");
        lblStat3.setText("Float: ");
        lblStat4.setText("Leverage: ");
    }

    private void setStatsLabels() {
        lblStat1.setText("24h H: ");
        lblStat2.setText("24h L: ");
        lblStat3.setText("Vol 24h: ");
        lblStat4.setText("USDT: ");
    }

    private void updateClock() {
        TimeZone wib = TimeZone.getTimeZone("Asia/Jakarta");
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
        passEt.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_PASSWORD);

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
                            if (!connected) {
                                chart.setTradeLines(null);
                                positions = new ArrayList<Mt5Client.TradeLine>();
                                setStatsLabels();
                                if (activeTab != 0) switchTab(0);
                            } else {
                                loadHistory();
                            }
                            if (status != null) updateConn(status);
                        }

                        @Override
                        public void onTick(double bid, double ask, long timeSec) {
                            onLiveTick(bid, ask);
                        }

                        @Override
                        public void onPositions(java.util.List<Mt5Client.TradeLine> lines) {
                            updateTradeLines(lines);
                        }

                        @Override
                        public void onAccount(Mt5Client.Account a) {
                            account = a;
                            renderAccount();
                        }

                        @Override
                        public void onHistory(java.util.List<Mt5Client.Deal> d) {
                            deals = d;
                            if (activeTab == 2) rebuildPanel();
                        }

                        @Override
                        public void onTradeResult(Mt5Client.TradeResult r) {
                            onTradeDone(r);
                        }

                        @Override
                        public void onBook(java.util.List<Mt5Client.BookLevel> bids,
                                            java.util.List<Mt5Client.BookLevel> asks) {
                            bookBids = bids;
                            bookAsks = asks;
                            if (activeTab == 3) rebuildPanel();
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
        setStatsLabels();
    }

    private void onLiveTick(double bid, double ask) {
        if (bid > 0) lastBid = bid;
        if (ask > 0) lastAsk = ask;
        if (!"XAU/USD".equals(currentPair)) return;
        double mid = (bid > 0 && ask > 0) ? (bid + ask) / 2 : Math.max(bid, ask);
        if (mid <= 0) return;
        lastBrokerMid = mid;
        long now = System.currentTimeMillis();
        if (now - lastLiveRender < 200) return;
        lastLiveRender = now;
        renderPrice(mid);
        refreshWatchlistData();
        if (activeTab == 0) updateTradePrices();
        if (activeTab == 3) refreshDomFallback();
    }

    private void updateTradePrices() {
        if (activeTab != 0) return;
        if (tradeSellBtn != null && lastBid > 0) {
            tradeSellBtn.setText("SELL " + fmt(lastBid));
        }
        if (tradeBuyBtn != null && lastAsk > 0) {
            tradeBuyBtn.setText("BUY " + fmt(lastAsk));
        }
    }

    private void updateTradeLines(java.util.List<Mt5Client.TradeLine> lines) {
        positions = lines != null ? lines : new ArrayList<Mt5Client.TradeLine>();
        java.util.List<CandlestickChartView.TradeLine> out =
                new java.util.ArrayList<CandlestickChartView.TradeLine>();
        for (Mt5Client.TradeLine t : positions) {
            out.add(new CandlestickChartView.TradeLine(t.buy, t.pending, t.entry, t.sl, t.tp));
        }
        chart.setTradeLines(out);
        if (activeTab == 1) rebuildPanel();
        if (!positions.isEmpty() && !positions.get(0).pending) {
            Mt5Client.TradeLine t = positions.get(0);
            updateConn("Posisi: " + (t.buy ? "BUY" : "SELL") + " " + t.entry
                    + "  P/L " + (t.profit >= 0 ? "+" : "") + trim(t.profit));
        }
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
            boolean typing = (searchText != null && searchText.hasFocus())
                    || (lotInput != null && lotInput.hasFocus())
                    || (slInput != null && slInput.hasFocus())
                    || (tpInput != null && tpInput.hasFocus());

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
        if (mt5Connected && mt5Client != null && symbol.startsWith("XAU/")) {
            loadBrokerKlines(symbol, interval);
            return;
        }
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

    private void loadBrokerKlines(final String symbol, final String interval) {
        final Mt5Client client = mt5Client;
        if (client == null) return;
        mt5Executor.execute(new Runnable() {
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
                    int minutes = intervalMinutes(interval);
                    long now = System.currentTimeMillis() / 1000L;
                    long from = now - minutes * 60L * 260L;
                    final List<Kline> k = client.fetchRates(client.goldSymbol(), minutes, from, now);
                    if (k != null && k.size() > 1) {
                        Cache.putKlines(symbol, interval, k);
                    }
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (symbol.equals(currentPair) && interval.equals(currentInterval)
                                    && k != null && !k.isEmpty()) {
                                applyKlines(symbol, interval, k,
                                        "MT5 broker • " + k.size() + " candle");
                            }
                        }
                    });
                } catch (final Exception e) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (cached == null) updateConn("ERR broker: " + shortErr(e.getMessage()));
                        }
                    });
                }
            }
        });
    }

    private static int intervalMinutes(String interval) {
        if ("1m".equals(interval)) return 1;
        if ("5m".equals(interval)) return 5;
        if ("15m".equals(interval)) return 15;
        if ("1h".equals(interval)) return 60;
        if ("4h".equals(interval)) return 240;
        if ("1d".equals(interval)) return 1440;
        return 1;
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
                            if (symbol.equals(currentPair) && !mt5Connected) {
                                renderTicker(t);
                            }
                        }
                    });
                } catch (final Exception e) {
                    final Ticker cached = Cache.getTicker(symbol);
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (!symbol.equals(currentPair) || mt5Connected) return;
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

    private String fmt(double v) {
        return new DecimalFormat("#,##0.00").format(v);
    }

    private String trim(double v) {
        return new DecimalFormat("0.##").format(v);
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

    private static String wibTime(long unixSec) {
        java.text.SimpleDateFormat df =
                new java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.US);
        df.setTimeZone(TimeZone.getTimeZone("Asia/Jakarta"));
        return df.format(new java.util.Date(unixSec * 1000L));
    }

    private double parseDouble(EditText e, double def) {
        if (e == null) return def;
        try {
            String s = e.getText().toString().trim().replace(",", ".");
            if (s.isEmpty()) return def;
            return Double.parseDouble(s);
        } catch (Exception ex) {
            return def;
        }
    }

    private void toast(String s) {
        android.widget.Toast.makeText(this, s, android.widget.Toast.LENGTH_SHORT).show();
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
        mt5Executor.shutdownNow();
    }
}
