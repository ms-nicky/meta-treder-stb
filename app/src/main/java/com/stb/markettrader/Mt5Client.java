package com.stb.markettrader;

import android.os.Handler;
import android.os.Looper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.zip.InflaterInputStream;

import javax.net.ssl.SSLSocketFactory;

public class Mt5Client {

    public interface Listener {
        void onStatus(String status, boolean connected);

        void onTick(double bid, double ask, long timeSec);
    }

    private static final long REQ_TIMEOUT_MS = 15000;
    private static final long PING_INTERVAL_MS = 20000;

    private final String host;
    private final int port;
    private final String path;
    private final long login;
    private final String password;
    private final SSLSocketFactory factory;
    private final Listener listener;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final LinkedBlockingQueue<Object[]> responses = new LinkedBlockingQueue<Object[]>();

    private Mt5WebSocket ws;
    private Thread worker;
    private volatile boolean running;
    private volatile boolean wsAlive;
    private volatile boolean online;

    private byte[] currentKey;
    private byte[] clientId;
    private int goldId = -1;
    private String goldName = null;
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
            responses.offer(new Object[]{cmd, code, rb});
        } catch (Exception ignored) {
        }
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

    private byte[] request(int cmd, byte[] payload) throws Exception {
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
        return null;
    }

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
