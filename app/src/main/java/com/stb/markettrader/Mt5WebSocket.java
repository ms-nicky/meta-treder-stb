package com.stb.markettrader;

import android.util.Base64;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.security.SecureRandom;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

public class Mt5WebSocket {

    public interface Listener {
        void onBinary(byte[] data);

        void onClosed(String reason);

        void onError(Exception e);
    }

    private final String host;
    private final int port;
    private final String path;
    private final SSLSocketFactory factory;
    private final Listener listener;
    private final SecureRandom rnd = new SecureRandom();

    private SSLSocket socket;
    private InputStream in;
    private OutputStream out;
    private volatile boolean open;

    public Mt5WebSocket(String host, int port, String path, SSLSocketFactory factory, Listener listener) {
        this.host = host;
        this.port = port;
        this.path = path;
        this.factory = factory;
        this.listener = listener;
    }

    public void connect() throws IOException {
        SSLSocket s = (SSLSocket) factory.createSocket(host, port);
        setServerName(s);
        s.setEnabledProtocols(new String[]{"TLSv1.2"});
        s.startHandshake();
        socket = s;
        in = new BufferedInputStream(s.getInputStream());
        out = new BufferedOutputStream(s.getOutputStream());
        handshake();
        open = true;
        Thread reader = new Thread(new Runnable() {
            @Override
            public void run() {
                readLoop();
            }
        });
        reader.setDaemon(true);
        reader.start();
    }

    private void setServerName(SSLSocket s) {
        try {
            Method m = s.getClass().getMethod("setHostname", String.class);
            m.setAccessible(true);
            m.invoke(s, host);
        } catch (Throwable ignored) {
        }
    }

    private void handshake() throws IOException {
        byte[] keyBytes = new byte[16];
        rnd.nextBytes(keyBytes);
        String key = Base64.encodeToString(keyBytes, Base64.NO_WRAP);
        String req = "GET " + path + " HTTP/1.1\r\n"
                + "Host: " + host + "\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Key: " + key + "\r\n"
                + "Sec-WebSocket-Version: 13\r\n"
                + "Origin: https://" + host + "\r\n"
                + "User-Agent: Mozilla/5.0\r\n"
                + "\r\n";
        out.write(req.getBytes("ISO-8859-1"));
        out.flush();

        String status = readLine(in);
        if (status == null || status.indexOf("101") < 0) {
            throw new IOException("Handshake WS gagal: " + status);
        }
        String line;
        while ((line = readLine(in)) != null && line.length() > 0) {
            // buang header
        }
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        int c;
        boolean any = false;
        while ((c = in.read()) != -1) {
            any = true;
            if (c == '\n') break;
            if (c != '\r') b.write(c);
        }
        if (!any && b.size() == 0) return null;
        return new String(b.toByteArray(), "ISO-8859-1");
    }

    private void readLoop() {
        ByteArrayOutputStream msg = new ByteArrayOutputStream();
        try {
            while (open) {
                int b0 = in.read();
                if (b0 == -1) break;
                int b1 = in.read();
                if (b1 == -1) break;

                boolean fin = (b0 & 0x80) != 0;
                int opcode = b0 & 0x0f;
                boolean masked = (b1 & 0x80) != 0;
                long len = b1 & 0x7f;

                if (len == 126) {
                    len = ((long) readByte() << 8) | readByte();
                } else if (len == 127) {
                    len = 0;
                    for (int i = 0; i < 8; i++) len = (len << 8) | readByte();
                }

                byte[] mask = masked ? readN(4) : null;
                byte[] payload = readN((int) len);
                if (masked) {
                    for (int i = 0; i < payload.length; i++) payload[i] ^= mask[i & 3];
                }

                if (opcode == 0x8) {
                    try {
                        sendFrame(0x8, new byte[0]);
                    } catch (Exception ignored) {
                    }
                    break;
                } else if (opcode == 0x9) {
                    sendFrame(0xA, payload);
                } else if (opcode == 0xA) {
                    // pong, abaikan
                } else if (opcode == 0x1 || opcode == 0x2 || opcode == 0x0) {
                    msg.write(payload, 0, payload.length);
                    if (fin) {
                        byte[] data = msg.toByteArray();
                        msg.reset();
                        listener.onBinary(data);
                    }
                }
            }
        } catch (Exception e) {
            if (open) listener.onError(e);
        } finally {
            open = false;
            try {
                if (socket != null) socket.close();
            } catch (Exception ignored) {
            }
            listener.onClosed("koneksi MT5 ditutup");
        }
    }

    private int readByte() throws IOException {
        int c = in.read();
        if (c == -1) throw new IOException("EOF");
        return c;
    }

    private byte[] readN(int n) throws IOException {
        byte[] b = new byte[n];
        int off = 0;
        while (off < n) {
            int r = in.read(b, off, n - off);
            if (r == -1) throw new IOException("EOF");
            off += r;
        }
        return b;
    }

    private synchronized void sendFrame(int opcode, byte[] payload) throws IOException {
        if (out == null) throw new IOException("belum tersambung");
        int len = payload.length;
        ByteArrayOutputStream h = new ByteArrayOutputStream();
        h.write(0x80 | opcode);
        if (len < 126) {
            h.write(0x80 | len);
        } else if (len < 65536) {
            h.write(0x80 | 126);
            h.write((len >> 8) & 0xff);
            h.write(len & 0xff);
        } else {
            h.write(0x80 | 127);
            for (int i = 7; i >= 0; i--) h.write((int) ((((long) len) >>> (8 * i)) & 0xff));
        }
        byte[] mask = new byte[4];
        rnd.nextBytes(mask);
        h.write(mask, 0, 4);
        out.write(h.toByteArray());
        byte[] masked = new byte[len];
        for (int i = 0; i < len; i++) masked[i] = (byte) (payload[i] ^ mask[i & 3]);
        out.write(masked, 0, len);
        out.flush();
    }

    public void send(byte[] data) throws IOException {
        sendFrame(0x2, data);
    }

    public void close() {
        open = false;
        try {
            sendFrame(0x8, new byte[0]);
        } catch (Exception ignored) {
        }
        try {
            if (socket != null) socket.close();
        } catch (Exception ignored) {
        }
    }
}
