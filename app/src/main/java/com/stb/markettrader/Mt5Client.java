package com.stb.markettrader;

import android.os.Handler;
import android.os.Looper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.zip.InflaterInputStream;

import javax.net.ssl.SSLSocketFactory;

public class Mt5Client {

    public static class TradeLine {
        public final String symbol;
        public final boolean buy;
        public final boolean pending;
        public final double entry;
        public final double sl;
        public final double tp;
        public final long id;
        public final double volume;
        public final double profit;
        public final int digits;

        public TradeLine(String symbol, boolean buy, boolean pending,
                         double entry, double sl, double tp,
                         long id, double volume, double profit, int digits) {
            this.symbol = symbol;
            this.buy = buy;
            this.pending = pending;
            this.entry = entry;
            this.sl = sl;
            this.tp = tp;
            this.id = id;
            this.volume = volume;
            this.profit = profit;
            this.digits = digits;
        }
    }

    public static class Account {
        public double balance;
        public double credit;
        public double profit;
        public double equity;
        public double margin;
        public double freeMargin;
        public String currency = "";
        public String name = "";
        public String server = "";
        public int leverage;
        public int digits = 2;
    }

    public static class Deal {
        public long time;
        public String symbol = "";
        public int action;
        public int entry;
        public double open;
        public double close;
        public double volume;
        public double profit;
        public long positionId;
    }

    public static class TradeResult {
        public int retcode;
        public long order;
        public double volume;
        public double price;
        public double bid;
        public double ask;
        public String comment = "";

        public boolean ok() {
            return retcode == 0 || retcode == 10008 || retcode == 10009 || retcode == 10010;
        }
    }

    public static class BookLevel {
        public double price;
        public double volume;

        public BookLevel(double price, double volume) {
            this.price = price;
            this.volume = volume;
        }
    }

    public interface Listener {
        void onStatus(String status, boolean connected);

        void onTick(double bid, double ask, long timeSec);

        void onPositions(List<TradeLine> lines);

        void onAccount(Account account);

        void onHistory(List<Deal> deals);

        void onTradeResult(TradeResult result);

        void onBook(List<BookLevel> bids, List<BookLevel> asks);
    }

    private static final long REQ_TIMEOUT_MS = 15000;
    private static final long PING_INTERVAL_MS = 20000;
    public static final double VOLUME_SCALE = 1e8;

    private final String host;
    private final int port;
    private final String path;
    private final long login;
    private final String password;
    private final SSLSocketFactory factory;
    private final Listener listener;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final LinkedBlockingQueue<Object[]> responses = new LinkedBlockingQueue<Object[]>();
    private final Object reqLock = new Object();
    private final ExecutorService refreshExec = Executors.newSingleThreadExecutor();

    private Mt5WebSocket ws;
    private Thread worker;
    private volatile boolean running;
    private volatile boolean wsAlive;
    private volatile boolean online;

    private byte[] currentKey;
    private byte[] clientId;
    private int goldId = -1;
    private String goldName = null;
    private int goldDigits = 2;
    private double lastBid;
    private double lastAsk;

    public Mt5Client(String host, int port, String path, long login, String password,
                     SSLSocketFactory factory, Listener listener) {
        this.host = host;
        this.port = port;
        this.path = path;
        this.login = login;
        this.password = password;
        this.factory = factory;
        this.listener = listener;
    }

    public void start() {
        if (running) return;
        running = true;
        worker = new Thread(new Runnable() {
            @Override
            public void run() {
                runClient();
            }
        });
        worker.setDaemon(true);
        worker.start();
    }

    public void stop() {
        running = false;
        wsAlive = false;
        if (ws != null) ws.close();
        if (worker != null) worker.interrupt();
        worker = null;
        setOnline(false);
    }

    public boolean isConnected() {
        return online;
    }

    public String goldSymbol() {
        return goldName;
    }

    public int goldDigits() {
        return goldDigits;
    }

    private void runClient() {
        while (running) {
            try {
                connectAndRun();
            } catch (Exception e) {
                postStatus("MT5: " + shortMsg(e), false);
            }
            wsAlive = false;
            setOnline(false);
            if (running) {
                postStatus("MT5: menyambung ulang...", false);
                sleep(5000);
            }
        }
    }

    private void connectAndRun() throws Exception {
        postStatus("MT5: menghubungkan " + host + "...", false);
        responses.clear();
        currentKey = Mt5Protocol.initialKey();
        if (clientId == null) clientId = Mt5Protocol.buildClientId();

        ws = new Mt5WebSocket(host, port, path, factory, new Mt5WebSocket.Listener() {
            @Override
            public void onBinary(byte[] data) {
                onFrame(data);
            }

            @Override
            public void onClosed(String reason) {
                wsAlive = false;
                setOnline(false);
            }

            @Override
            public void onError(Exception e) {
                wsAlive = false;
                setOnline(false);
            }
        });
        ws.connect();
        wsAlive = true;

        byte[] boot = request(Mt5Protocol.CMD_BOOTSTRAP, new byte[64]);
        if (boot == null || boot.length < 66 + 16) throw new Exception("bootstrap gagal");
        currentKey = Arrays.copyOfRange(boot, 66, boot.length);

        request(Mt5Protocol.CMD_INIT, Mt5Protocol.buildInitPayload(password, clientId));
        byte[] loginResp = request(Mt5Protocol.CMD_LOGIN,
                Mt5Protocol.buildLoginPayload(password, login, clientId));
        if (loginResp == null) throw new Exception("login gagal");

        postStatus("MT5: login ok, ambil simbol...", false);
        byte[] symBody = request(Mt5Protocol.CMD_GET_SYMBOLS_GZIP, null);
        if (symBody == null) throw new Exception("gagal ambil simbol");
        findGold(symBody);
        if (goldId < 0) throw new Exception("XAUUSD tidak ditemukan");

        request(Mt5Protocol.CMD_SUBSCRIBE_TICKS, Mt5Protocol.buildSubscribePayload(goldId));
        setOnline(true);
        postStatus("MT5 live: " + goldName, true);

        try {
            sendCommand(Mt5Protocol.CMD_GET_POSITIONS_ORDERS, null);
        } catch (Exception ignored) {
        }
        refreshAccount();
        subscribeBook();

        long lastPing = System.currentTimeMillis();
        while (running && wsAlive) {
            sleep(500);
            if (System.currentTimeMillis() - lastPing >= PING_INTERVAL_MS) {
                lastPing = System.currentTimeMillis();
                try {
                    request(Mt5Protocol.CMD_PING, new byte[0]);
                } catch (Exception ignored) {
                }
            }
        }
        throw new Exception("koneksi terputus");
    }

    private void findGold(byte[] symBody) {
        goldId = -1;
        goldName = null;
        try {
            InflaterInputStream iis = new InflaterInputStream(
                    new ByteArrayInputStream(symBody, 4, symBody.length - 4));
            ByteArrayOutputStream raw = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = iis.read(buf)) != -1) raw.write(buf, 0, n);
            byte[] d = raw.toByteArray();
            if (d.length < 4) return;
            long count = Mt5Protocol.readU32(d, 0);
            int off = 4;
            int size = Mt5Protocol.SYMBOL_RECORD_SIZE;
            for (long i = 0; i < count && off + size <= d.length; i++, off += size) {
                String name = Mt5Protocol.readUtf16(d, off, 64);
                if (name.length() >= 6 && name.toUpperCase().startsWith("XAUUSD")) {
                    int digits = (int) Mt5Protocol.readU32(d, off + 192);
                    int id = (int) Mt5Protocol.readU32(d, off + 196);
                    boolean prefer = goldName == null || name.length() < goldName.length();
                    if (prefer) {
                        goldName = name;
                        goldId = id;
                        goldDigits = digits > 0 ? digits : 2;
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void onFrame(byte[] frame) {
        try {
            byte[] body = Mt5Protocol.unpackOuter(frame);
            byte[] inner = Mt5Protocol.decrypt(currentKey, body);
            int cmd = Mt5Protocol.respCommand(inner);
            int code = Mt5Protocol.respCode(inner);
            byte[] rb = Mt5Protocol.respBody(inner);
            if (cmd == Mt5Protocol.CMD_TICK_PUSH) {
                handleTicks(rb);
                return;
            }
            if (cmd == Mt5Protocol.CMD_GET_POSITIONS_ORDERS) {
                handlePositions(rb);
                return;
            }
            if (cmd == Mt5Protocol.CMD_ACCOUNT_UPDATE_PUSH) {
                handleAccountPush(rb);
                return;
            }
            if (cmd == Mt5Protocol.CMD_BOOK_PUSH) {
                handleBook(rb);
                return;
            }
            if (cmd == Mt5Protocol.CMD_TRADE_UPDATE_PUSH) {
                refreshExec.execute(new Runnable() {
                    @Override
                    public void run() {
                        refreshPositions();
                        refreshAccount();
                    }
                });
                return;
            }
            if (cmd == 19) {
                handleTradeResult(rb);
                return;
            }
            responses.offer(new Object[]{cmd, code, rb});
        } catch (Exception ignored) {
        }
    }

    private void handlePositions(byte[] body) {
        final List<TradeLine> lines = new ArrayList<TradeLine>();
        try {
            if (body == null || body.length < 4) return;
            int size = Mt5Protocol.POSITION_RECORD_SIZE;
            long posCount = Mt5Protocol.readU32(body, 0);
            int off = 4;
            for (long i = 0; i < posCount && off + size <= body.length; i++, off += size) {
                String sym = Mt5Protocol.readUtf16(body, off + Mt5Protocol.POS_SYMBOL_OFF, 64);
                if (!isGold(sym)) continue;
                long id = Mt5Protocol.readI64(body, off + Mt5Protocol.POS_ID_OFF);
                int action = (int) Mt5Protocol.readU32(body, off + Mt5Protocol.POS_ACTION_OFF);
                double open = Mt5Protocol.readF64(body, off + Mt5Protocol.POS_PRICE_OPEN_OFF);
                double sl = Mt5Protocol.readF64(body, off + Mt5Protocol.POS_SL_OFF);
                double tp = Mt5Protocol.readF64(body, off + Mt5Protocol.POS_TP_OFF);
                double vol = Mt5Protocol.readI64(body, off + Mt5Protocol.POS_VOLUME_OFF) / VOLUME_SCALE;
                double profit = Mt5Protocol.readF64(body, off + Mt5Protocol.POS_PROFIT_OFF);
                int digits = (int) Mt5Protocol.readU32(body, off + Mt5Protocol.POS_DIGITS_OFF);
                lines.add(new TradeLine(sym, action == 0, false, open, sl, tp,
                        id, vol, profit, digits > 0 ? digits : goldDigits));
            }

            int orderOff = 4 + (int) posCount * size;
            if (orderOff + 4 <= body.length) {
                int osize = Mt5Protocol.ORDER_RECORD_SIZE;
                long ordCount = Mt5Protocol.readU32(body, orderOff);
                int o = orderOff + 4;
                for (long i = 0; i < ordCount && o + osize <= body.length; i++, o += osize) {
                    String sym = Mt5Protocol.readUtf16(body, o + Mt5Protocol.ORD_SYMBOL_OFF, 64);
                    if (!isGold(sym)) continue;
                    long id = Mt5Protocol.readI64(body, o + Mt5Protocol.ORD_ID_OFF);
                    int type = (int) Mt5Protocol.readU32(body, o + Mt5Protocol.ORD_TYPE_OFF);
                    double price = Mt5Protocol.readF64(body, o + Mt5Protocol.ORD_PRICE_OFF);
                    double sl = Mt5Protocol.readF64(body, o + Mt5Protocol.ORD_SL_OFF);
                    double tp = Mt5Protocol.readF64(body, o + Mt5Protocol.ORD_TP_OFF);
                    double vol = Mt5Protocol.readI64(body, o + Mt5Protocol.ORD_VOLUME_CURRENT_OFF)
                            / VOLUME_SCALE;
                    boolean buy = (type == 0 || type == 2 || type == 4 || type == 6);
                    lines.add(new TradeLine(sym, buy, true, price, sl, tp,
                            id, vol, 0, goldDigits));
                }
            }
        } catch (Exception ignored) {
        }
        main.post(new Runnable() {
            @Override
            public void run() {
                listener.onPositions(lines);
            }
        });
    }

    private void handleAccountPush(byte[] body) {
        final Account a = parseAccount(body);
        if (a == null) return;
        main.post(new Runnable() {
            @Override
            public void run() {
                listener.onAccount(a);
            }
        });
    }

    private void handleTradeResult(byte[] body) {
        final TradeResult r = parsePush(body);
        if (r == null) {
            refreshExec.execute(new Runnable() {
                @Override
                public void run() {
                    refreshPositions();
                    refreshAccount();
                }
            });
            return;
        }
        main.post(new Runnable() {
            @Override
            public void run() {
                listener.onTradeResult(r);
            }
        });
        refreshExec.execute(new Runnable() {
            @Override
            public void run() {
                refreshPositions();
                refreshAccount();
            }
        });
    }

    private TradeResult parsePush(byte[] body) {
        if (body == null || body.length < Mt5Protocol.TRADE_PUSH_SIZE) return null;
        TradeResult r = new TradeResult();
        r.retcode = 0;
        r.volume = Mt5Protocol.readI64(body, Mt5Protocol.TPUSH_VOLUME_OFF) / VOLUME_SCALE;
        r.order = Mt5Protocol.readI64(body, Mt5Protocol.TPUSH_ORDER_OFF);
        r.price = Mt5Protocol.readF64(body, Mt5Protocol.TPUSH_PRICE_OFF);
        r.comment = Mt5Protocol.readUtf16(body, Mt5Protocol.TPUSH_COMMENT_OFF, 64);
        return r;
    }

    private void handleBook(byte[] body) {
        final List<BookLevel> bids = new ArrayList<BookLevel>();
        final List<BookLevel> asks = new ArrayList<BookLevel>();
        try {
            if (body == null || body.length < 4) return;
            long count = Mt5Protocol.readU32(body, 0);
            int off = 4;
            int hs = Mt5Protocol.BOOK_HEADER_SIZE;
            int ls = Mt5Protocol.BOOK_LEVEL_SIZE;
            for (long i = 0; i < count && off + hs <= body.length; i++) {
                int symId = (int) Mt5Protocol.readU32(body, off);
                long bidCount = Mt5Protocol.readU32(body, off + Mt5Protocol.BOOK_BID_COUNT_OFF);
                long askCount = Mt5Protocol.readU32(body, off + Mt5Protocol.BOOK_ASK_COUNT_OFF);
                off += hs;
                for (long b = 0; b < bidCount && off + ls <= body.length; b++, off += ls) {
                    if (symId == goldId) {
                        bids.add(new BookLevel(Mt5Protocol.readF64(body, off),
                                Mt5Protocol.readI64(body, off + 8) / VOLUME_SCALE));
                    }
                }
                for (long a = 0; a < askCount && off + ls <= body.length; a++, off += ls) {
                    if (symId == goldId) {
                        asks.add(new BookLevel(Mt5Protocol.readF64(body, off),
                                Mt5Protocol.readI64(body, off + 8) / VOLUME_SCALE));
                    }
                }
            }
        } catch (Exception ignored) {
        }
        if (bids.isEmpty() && asks.isEmpty()) return;
        main.post(new Runnable() {
            @Override
            public void run() {
                listener.onBook(bids, asks);
            }
        });
    }

    private static boolean isGold(String sym) {
        return sym != null && sym.toUpperCase().startsWith("XAUUSD");
    }

    private void handleTicks(byte[] body) {
        if (body == null || body.length < Mt5Protocol.TICK_RECORD_SIZE) return;
        int size = Mt5Protocol.TICK_RECORD_SIZE;
        boolean fired = false;
        for (int off = 0; off + size <= body.length; off += size) {
            int symbolId = (int) Mt5Protocol.readU32(body, off);
            if (symbolId != goldId) continue;
            long time = Mt5Protocol.readI32(body, off + 4);
            double bid = Mt5Protocol.readF64(body, off + 12);
            double ask = Mt5Protocol.readF64(body, off + 20);
            if (bid != 0) lastBid = bid;
            if (ask != 0) lastAsk = ask;
            fired = true;
            final double fb = lastBid;
            final double fa = lastAsk;
            final long ft = time;
            if (fb > 0 || fa > 0) {
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        listener.onTick(fb, fa, ft);
                    }
                });
            }
        }
        if (fired) setOnline(true);
    }

    // ---------------- request/response ----------------

    private byte[] request(int cmd, byte[] payload) throws Exception {
        synchronized (reqLock) {
            if (ws == null || !wsAlive) throw new Exception("tidak terhubung");
            byte[] inner = Mt5Protocol.buildCommand(cmd, payload);
            byte[] enc = Mt5Protocol.encrypt(currentKey, inner);
            ws.send(Mt5Protocol.packOuter(enc));
            long deadline = System.currentTimeMillis() + REQ_TIMEOUT_MS;
            while (running && wsAlive) {
                long remain = deadline - System.currentTimeMillis();
                if (remain <= 0) return null;
                Object[] r = responses.poll(remain, TimeUnit.MILLISECONDS);
                if (r == null) return null;
                if (((Integer) r[0]) == cmd) return (byte[]) r[2];
            }
        }
        return null;
    }

    private void sendCommand(int cmd, byte[] payload) throws Exception {
        if (ws == null || !wsAlive) throw new Exception("tidak terhubung");
        byte[] inner = Mt5Protocol.buildCommand(cmd, payload);
        byte[] enc = Mt5Protocol.encrypt(currentKey, inner);
        ws.send(Mt5Protocol.packOuter(enc));
    }

    // ---------------- blocking API (call from background thread) ----------------

    public List<Kline> fetchRates(String symbol, int periodMinutes, long fromTs, long toTs)
            throws Exception {
        byte[] body = request(Mt5Protocol.CMD_GET_RATES,
                Mt5Protocol.buildRatesPayload(symbol, periodMinutes, (int) fromTs, (int) toTs));
        return parseRates(body);
    }

    private List<Kline> parseRates(byte[] body) {
        List<Kline> out = new ArrayList<Kline>();
        if (body == null || body.length < Mt5Protocol.RATE_BAR_SIZE) return out;
        int size = Mt5Protocol.RATE_BAR_SIZE;
        if (body.length % Mt5Protocol.RATE_BAR_EXT_SIZE == 0
                && body.length % Mt5Protocol.RATE_BAR_SIZE != 0) {
            size = Mt5Protocol.RATE_BAR_EXT_SIZE;
        }
        for (int off = 0; off + size <= body.length; off += size) {
            long time = Mt5Protocol.readI32(body, off + Mt5Protocol.RATE_TIME_OFF) * 1000L;
            double open = Mt5Protocol.readF64(body, off + Mt5Protocol.RATE_OPEN_OFF);
            double high = Mt5Protocol.readF64(body, off + Mt5Protocol.RATE_HIGH_OFF);
            double low = Mt5Protocol.readF64(body, off + Mt5Protocol.RATE_LOW_OFF);
            double close = Mt5Protocol.readF64(body, off + Mt5Protocol.RATE_CLOSE_OFF);
            double vol = Mt5Protocol.readI64(body, off + Mt5Protocol.RATE_TICKVOL_OFF);
            out.add(new Kline(time, open, high, low, close, vol));
        }
        return out;
    }

    public Account fetchAccount() throws Exception {
        byte[] body = request(Mt5Protocol.CMD_GET_ACCOUNT, null);
        return parseAccount(body);
    }

    private Account parseAccount(byte[] body) {
        if (body == null || body.length < Mt5Protocol.ACC_PROFIT_OFF + 8) return null;
        Account a = new Account();
        a.balance = Mt5Protocol.readF64(body, Mt5Protocol.ACC_BALANCE_OFF);
        a.credit = Mt5Protocol.readF64(body, Mt5Protocol.ACC_CREDIT_OFF);
        a.profit = Mt5Protocol.readF64(body, Mt5Protocol.ACC_PROFIT_OFF);
        a.equity = a.balance + a.credit + a.profit;
        a.currency = Mt5Protocol.readUtf16(body, Mt5Protocol.ACC_CURRENCY_OFF, 64);
        a.leverage = (int) Mt5Protocol.readU32(body, Mt5Protocol.ACC_LEVERAGE_OFF);
        a.digits = (int) Mt5Protocol.readU32(body, Mt5Protocol.ACC_CURRENCY_DIGITS_OFF);
        a.name = Mt5Protocol.readUtf16(body, Mt5Protocol.ACC_NAME_OFF, 256);
        a.server = Mt5Protocol.readUtf16(body, Mt5Protocol.ACC_SERVER_OFF, 128);
        return a;
    }

    public List<Deal> fetchHistory(long fromTs, long toTs) throws Exception {
        byte[] body = request(Mt5Protocol.CMD_GET_TRADE_HISTORY,
                Mt5Protocol.buildHistoryPayload(fromTs, toTs));
        return parseHistory(body);
    }

    private List<Deal> parseHistory(byte[] body) {
        List<Deal> out = new ArrayList<Deal>();
        if (body == null || body.length < 4) return out;
        int dsize = Mt5Protocol.DEAL_RECORD_SIZE;
        long dealCount = Mt5Protocol.readU32(body, 0);
        int off = 4;
        for (long i = 0; i < dealCount && off + dsize <= body.length; i++, off += dsize) {
            String sym = Mt5Protocol.readUtf16(body, off + Mt5Protocol.DEAL_SYMBOL_OFF, 64);
            if (!isGold(sym)) continue;
            Deal d = new Deal();
            d.time = Mt5Protocol.readU32(body, off + Mt5Protocol.DEAL_TIME_OFF);
            d.symbol = sym;
            d.action = (int) Mt5Protocol.readU32(body, off + Mt5Protocol.DEAL_ACTION_OFF);
            d.entry = (int) Mt5Protocol.readU32(body, off + Mt5Protocol.DEAL_ENTRY_OFF);
            d.open = Mt5Protocol.readF64(body, off + Mt5Protocol.DEAL_PRICE_OPEN_OFF);
            d.close = Mt5Protocol.readF64(body, off + Mt5Protocol.DEAL_PRICE_CLOSE_OFF);
            d.volume = Mt5Protocol.readI64(body, off + Mt5Protocol.DEAL_VOLUME_OFF) / VOLUME_SCALE;
            d.profit = Mt5Protocol.readF64(body, off + Mt5Protocol.DEAL_PROFIT_OFF);
            d.positionId = Mt5Protocol.readI64(body, off + Mt5Protocol.DEAL_POSITION_ID_OFF);
            out.add(d);
        }
        return out;
    }

    public TradeResult placeMarket(boolean buy, double lots, double sl, double tp)
            throws Exception {
        long volume = Math.round(lots * VOLUME_SCALE);
        int type = buy ? Mt5Protocol.ORDER_TYPE_BUY : Mt5Protocol.ORDER_TYPE_SELL;
        byte[] payload = Mt5Protocol.buildTradeRequest(Mt5Protocol.TRADE_ACTION_DEAL,
                goldName, volume, goldDigits, type, 0, sl, tp, 0, "");
        byte[] body = request(Mt5Protocol.CMD_TRADE_REQUEST, payload);
        return parseTradeResult(body);
    }

    public TradeResult closePosition(long positionId, boolean wasBuy, double lots)
            throws Exception {
        long volume = Math.round(lots * VOLUME_SCALE);
        int type = wasBuy ? Mt5Protocol.ORDER_TYPE_SELL : Mt5Protocol.ORDER_TYPE_BUY;
        byte[] payload = Mt5Protocol.buildTradeRequest(Mt5Protocol.TRADE_ACTION_DEAL,
                goldName, volume, goldDigits, type, 0, 0, 0, positionId, "");
        byte[] body = request(Mt5Protocol.CMD_TRADE_REQUEST, payload);
        return parseTradeResult(body);
    }

    public TradeResult modifySltp(long positionId, double sl, double tp) throws Exception {
        byte[] payload = Mt5Protocol.buildTradeRequest(Mt5Protocol.TRADE_ACTION_SLTP,
                goldName, 0, goldDigits, 0, 0, sl, tp, positionId, "");
        byte[] body = request(Mt5Protocol.CMD_TRADE_REQUEST, payload);
        return parseTradeResult(body);
    }

    private TradeResult parseTradeResult(byte[] body) {
        TradeResult r = new TradeResult();
        if (body == null || body.length < 4) {
            r.retcode = -1;
            r.comment = "tidak ada balasan server";
            return r;
        }
        r.retcode = (int) Mt5Protocol.readU32(body, 0);
        if (body.length >= Mt5Protocol.TRADE_PUSH_SIZE) {
            r.order = Mt5Protocol.readI64(body, Mt5Protocol.TPUSH_ORDER_OFF);
            r.volume = Mt5Protocol.readI64(body, Mt5Protocol.TPUSH_VOLUME_OFF) / VOLUME_SCALE;
            r.price = Mt5Protocol.readF64(body, Mt5Protocol.TPUSH_PRICE_OFF);
            r.comment = Mt5Protocol.readUtf16(body, Mt5Protocol.TPUSH_COMMENT_OFF, 64);
        } else if (body.length >= Mt5Protocol.TRADE_RESULT_SIZE) {
            r.order = Mt5Protocol.readI64(body, Mt5Protocol.TRES_ORDER_OFF);
            r.volume = Mt5Protocol.readI64(body, Mt5Protocol.TRES_VOLUME_OFF) / VOLUME_SCALE;
            r.price = Mt5Protocol.readF64(body, Mt5Protocol.TRES_PRICE_OFF);
            r.bid = Mt5Protocol.readF64(body, Mt5Protocol.TRES_BID_OFF);
            r.ask = Mt5Protocol.readF64(body, Mt5Protocol.TRES_ASK_OFF);
            r.comment = Mt5Protocol.readUtf16(body, Mt5Protocol.TRES_COMMENT_OFF, 64);
        }
        return r;
    }

    public void subscribeBook() {
        if (goldId < 0) return;
        try {
            sendCommand(Mt5Protocol.CMD_SUBSCRIBE_BOOK,
                    Mt5Protocol.buildSubscribePayload(goldId));
        } catch (Exception ignored) {
        }
    }

    public void refreshPositions() {
        try {
            sendCommand(Mt5Protocol.CMD_GET_POSITIONS_ORDERS, null);
        } catch (Exception ignored) {
        }
    }

    public void refreshAccount() {
        refreshExec.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final Account a = fetchAccount();
                    if (a != null) {
                        main.post(new Runnable() {
                            @Override
                            public void run() {
                                listener.onAccount(a);
                            }
                        });
                    }
                } catch (Exception ignored) {
                }
            }
        });
    }

    // ---------------- status ----------------

    private void setOnline(boolean v) {
        if (online == v) return;
        online = v;
        postStatus(v ? "MT5 live" : "MT5 offline", v);
    }

    private void postStatus(final String s, final boolean connected) {
        main.post(new Runnable() {
            @Override
            public void run() {
                listener.onStatus(s, connected);
            }
        });
    }

    private static String shortMsg(Exception e) {
        String m = e.getMessage();
        return m == null ? e.getClass().getSimpleName() : m;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }
}
