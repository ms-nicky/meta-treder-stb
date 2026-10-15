package com.stb.markettrader;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.Random;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

final class Mt5Protocol {

    static final int CMD_BOOTSTRAP = 0;
    static final int CMD_LOGOUT = 2;
    static final int CMD_GET_ACCOUNT = 3;
    static final int CMD_GET_SYMBOLS = 6;
    static final int CMD_SUBSCRIBE_TICKS = 7;
    static final int CMD_TICK_PUSH = 8;
    static final int CMD_GET_RATES = 11;
    static final int CMD_LOGIN = 28;
    static final int CMD_INIT = 29;
    static final int CMD_GET_SYMBOLS_GZIP = 34;
    static final int CMD_PING = 51;

    static final int SYMBOL_RECORD_SIZE = 526;
    static final int TICK_RECORD_SIZE = 50;

    private static final byte[] IV = new byte[16];
    private static final byte[] INITIAL_KEY =
            hex("02de02a1a65cc794684fcbea1ecb0fd74ae657e43662c11eee885d2fd64f4964");

    private Mt5Protocol() {
    }

    static byte[] initialKey() {
        return INITIAL_KEY;
    }

    static boolean isPush(int cmd) {
        switch (cmd) {
            case CMD_TICK_PUSH:
            case 10:
            case 13:
            case 14:
            case 15:
            case 17:
            case 19:
            case 23:
                return true;
            default:
                return false;
        }
    }

    static byte[] encrypt(byte[] key, byte[] data) throws Exception {
        Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
        c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(IV));
        return c.doFinal(data);
    }

    static byte[] decrypt(byte[] key, byte[] data) throws Exception {
        Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
        c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(IV));
        return c.doFinal(data);
    }

    static byte[] packOuter(byte[] body) {
        byte[] out = new byte[8 + body.length];
        writeU32(out, 0, body.length);
        writeU32(out, 4, 1);
        System.arraycopy(body, 0, out, 8, body.length);
        return out;
    }

    static byte[] unpackOuter(byte[] frame) {
        byte[] body = new byte[frame.length - 8];
        System.arraycopy(frame, 8, body, 0, body.length);
        return body;
    }

    static byte[] buildCommand(int cmd, byte[] payload) {
        byte[] p = payload == null ? new byte[0] : payload;
        byte[] out = new byte[4 + p.length];
        Random r = new Random();
        out[0] = (byte) r.nextInt(256);
        out[1] = (byte) r.nextInt(256);
        out[2] = (byte) (cmd & 0xff);
        out[3] = (byte) ((cmd >> 8) & 0xff);
        System.arraycopy(p, 0, out, 4, p.length);
        return out;
    }

    static int respCommand(byte[] inner) {
        return (inner[2] & 0xff) | ((inner[3] & 0xff) << 8);
    }

    static int respCode(byte[] inner) {
        return inner[4] & 0xff;
    }

    static byte[] respBody(byte[] inner) {
        byte[] b = new byte[inner.length - 5];
        System.arraycopy(inner, 5, b, 0, b.length);
        return b;
    }

    static byte[] buildInitPayload(String password, byte[] cid) {
        Buf b = new Buf();
        b.u32(0);
        b.utf16(prefix(password), 64);
        b.utf16("", 128);
        b.raw(pad(cid, 16));
        b.utf16("", 64);
        b.utf16("", 64);
        b.u64(0);
        b.utf16("", 128);
        b.u32(0);
        b.utf16("", 256);
        b.u64(0);
        return b.done();
    }

    static byte[] buildLoginPayload(String password, long login, byte[] cid) {
        Buf b = new Buf();
        b.u32(0);
        b.utf16(prefix(password), 64);
        b.utf16("", 128);
        b.raw(pad(cid, 16));
        b.utf16("", 64);
        b.utf16("", 64);
        b.u64(0);
        b.utf16("", 128);
        b.u32(0);
        b.utf16("", 256);
        b.u64(login);
        b.raw(new byte[160]);
        b.u64(0);
        return b.done();
    }

    static byte[] buildSubscribePayload(int symbolId) {
        byte[] b = new byte[8];
        writeU32(b, 0, 1);
        writeU32(b, 4, symbolId);
        return b;
    }

    static byte[] buildClientId() {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            String src = "linux;1;en-US;0x0;" + new Random().nextInt(0x1000000);
            byte[] d = md.digest(src.getBytes("UTF-8"));
            byte[] out = new byte[16];
            System.arraycopy(d, 0, out, 0, 16);
            return out;
        } catch (Exception e) {
            return new byte[16];
        }
    }

    private static String prefix(String s) {
        if (s == null) return "";
        return s.length() > 32 ? s.substring(0, 32) : s;
    }

    private static byte[] pad(byte[] v, int len) {
        byte[] out = new byte[len];
        if (v != null) System.arraycopy(v, 0, out, 0, Math.min(v.length, len));
        return out;
    }

    static void writeU32(byte[] b, int off, long v) {
        b[off] = (byte) (v & 0xff);
        b[off + 1] = (byte) ((v >> 8) & 0xff);
        b[off + 2] = (byte) ((v >> 16) & 0xff);
        b[off + 3] = (byte) ((v >> 24) & 0xff);
    }

    static long readU32(byte[] b, int off) {
        return (b[off] & 0xffL) | ((b[off + 1] & 0xffL) << 8)
                | ((b[off + 2] & 0xffL) << 16) | ((b[off + 3] & 0xffL) << 24);
    }

    static int readI32(byte[] b, int off) {
        return (int) readU32(b, off);
    }

    static int readU16(byte[] b, int off) {
        return (b[off] & 0xff) | ((b[off + 1] & 0xff) << 8);
    }

    static long readI64(byte[] b, int off) {
        long r = 0;
        for (int i = 7; i >= 0; i--) r = (r << 8) | (b[off + i] & 0xffL);
        return r;
    }

    static double readF64(byte[] b, int off) {
        return Double.longBitsToDouble(readI64(b, off));
    }

    static String readUtf16(byte[] b, int off, int len) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i + 1 < len; i += 2) {
            int code = (b[off + i] & 0xff) | ((b[off + i + 1] & 0xff) << 8);
            if (code == 0) break;
            sb.append((char) code);
        }
        return sb.toString();
    }

    static byte[] hex(String s) {
        int n = s.length() / 2;
        byte[] out = new byte[n];
        for (int i = 0; i < n; i++) {
            out[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    private static final class Buf {
        private final ByteArrayOutputStream o = new ByteArrayOutputStream();

        Buf u32(long v) {
            byte[] b = new byte[4];
            writeU32(b, 0, v);
            o.write(b, 0, 4);
            return this;
        }

        Buf u64(long v) {
            for (int i = 0; i < 8; i++) o.write((int) ((v >>> (8 * i)) & 0xff));
            return this;
        }

        Buf utf16(String s, int len) {
            byte[] out = new byte[len];
            if (s != null) {
                int n = Math.min(s.length(), len / 2);
                for (int i = 0; i < n; i++) {
                    char ch = s.charAt(i);
                    out[i * 2] = (byte) (ch & 0xff);
                    out[i * 2 + 1] = (byte) ((ch >> 8) & 0xff);
                }
            }
            o.write(out, 0, out.length);
            return this;
        }

        Buf raw(byte[] b) {
            if (b != null) o.write(b, 0, b.length);
            return this;
        }

        byte[] done() {
            return o.toByteArray();
        }
    }
}
