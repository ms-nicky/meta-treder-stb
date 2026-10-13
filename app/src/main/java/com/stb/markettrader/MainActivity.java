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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private static final String[] PAIRS = {
            "BTCUSDT", "ETHUSDT", "BNBUSDT", "SOLUSDT", "XRPUSDT", "ADAUSDT",
            "DOGEUSDT", "TRXUSDT", "LTCUSDT", "DOTUSDT", "LINKUSDT", "AVAXUSDT",
            "MATICUSDT", "NEARUSDT", "APEUSDT", "SHIBUSDT",
            "PAXGUSDT", "FILUSDT", "SUIUSDT", "ARBUSDT", "OPUSDT", "UNIUSDT",
            "AAVEUSDT", "ETCUSDT", "ATOMUSDT", "XLMUSDT"
    };

    private static final String[] INTERVALS = {"1m", "5m", "15m", "1h", "4h", "1d"};

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private CandlestickChartView chart;
    private TextView symbolText;
    private TextView priceText;
    private TextView changeText;
    private TextView connText;
    private Button updateButton;

    private List<String> pairs = new ArrayList<String>();

    private String currentPair = "BTCUSDT";
    private String currentInterval = "1m";

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Tls12SocketFactory.enable(getApplicationContext());
        setContentView(R.layout.activity_main);

        chart = findViewById(R.id.chart);
        symbolText = findViewById(R.id.symbol);
        priceText = findViewById(R.id.price);
        changeText = findViewById(R.id.change);
        connText = findViewById(R.id.conn);
        pairButton = findViewById(R.id.btn_pair);
        updateButton = findViewById(R.id.btn_update);

        pairs.addAll(java.util.Arrays.asList(PAIRS));

        setupIntervalButtons();
        setupPairButton();
        setupUpdateButton();

        symbolText.setText(currentPair);
        refreshButtonStates();

        chart.setFocusable(true);
        chart.requestFocus();

        handler.post(klinesTask);
        handler.post(tickerTask);

        loadRemotePairs();
        checkForAppUpdate(false);
    }

    private void loadRemotePairs() {
        UpdateManager.loadRemotePairs(this, new UpdateManager.PairCallback() {
            @Override
            public void onPairs(List<String> remotePairs) {
                if (remotePairs != null && !remotePairs.isEmpty()) {
                    pairs.clear();
                    pairs.addAll(remotePairs);
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
                currentPair = pairs.get(which);
                symbolText.setText(currentPair);
                loadKlines();
                loadTicker();
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
                try {
                    final List<Kline> klines = BinanceApi.getKlines(symbol, interval, 200);
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (symbol.equals(currentPair) && interval.equals(currentInterval)) {
                                chart.setKlines(klines);
                                updateConn("OK api.binance.com");
                            }
                        }
                    });
                } catch (final Exception e) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            updateConn("ERR: " + shortErr(e.getMessage()));
                        }
                    });
                }
            }
        });
    }

    private void loadTicker() {
        final String symbol = currentPair;
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final Ticker t = BinanceApi.get24h(symbol);
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (symbol.equals(currentPair)) {
                                renderTicker(t);
                            }
                        }
                    });
                } catch (final Exception e) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            updateConn("ERR: " + shortErr(e.getMessage()));
                        }
                    });
                }
            }
        });
    }

    private void renderTicker(Ticker t) {
        DecimalFormat df = new DecimalFormat("#,##0.00");
        DecimalFormat raw = new DecimalFormat("0.00000000");
        if (t.lastPrice >= 1) {
            priceText.setText("$" + df.format(t.lastPrice));
        } else {
            priceText.setText("$" + raw.format(t.lastPrice));
        }
        changeText.setText(formatPct(t.priceChangePercent));
        changeText.setTextColor(t.priceChangePercent >= 0 ? 0xFF0ECB81 : 0xFFF6465D);
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
        executor.shutdownNow();
    }
}