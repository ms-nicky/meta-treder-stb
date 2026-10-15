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
    static final int CMD_GET_POSITIONS_ORDERS = 4;
    static final int CMD_GET_TRADE_HISTORY = 5;
    static final int CMD_GET_SYMBOLS = 6;
    static final int CMD_SUBSCRIBE_TICKS = 7;
    static final int CMD_TICK_PUSH = 8;
    static final int CMD_GET_RATES = 11;
    static final int CMD_TRADE_REQUEST = 12;
    static final int CMD_TRADE_UPDATE_PUSH = 10;
    static final int CMD_ACCOUNT_UPDATE_PUSH = 14;
    static final int CMD_SUBSCRIBE_BOOK = 22;
    static final int CMD_BOOK_PUSH = 23;
    static final int CMD_LOGIN = 28;
    static final int CMD_INIT = 29;
    static final int CMD_GET_SYMBOLS_GZIP = 34;
    static final int CMD_PING = 51;

    static final int SYMBOL_RECORD_SIZE = 526;
    static final int TICK_RECORD_SIZE = 50;
    static final int POSITION_RECORD_SIZE = 344;
    static final int ORDER_RECORD_SIZE = 356;
    static final int DEAL_RECORD_SIZE = 356;
    static final int RATE_BAR_SIZE = 48;
    static final int RATE_BAR_EXT_SIZE = 56;
    static final int ACCOUNT_WEB_MAIN_SIZE = 816;
    static final int TRADE_REQUEST_SIZE = 236;
    static final int TRADE_RESULT_SIZE = 120;
    static final int TRADE_RESULT_MIN_SIZE = 108;
    static final int TRADE_UPDATE_BALANCE_SIZE = 48;
    static final int BOOK_HEADER_SIZE = 22;
    static final int BOOK_LEVEL_SIZE = 16;

    static final int TRADE_ACTION_DEAL = 1;
    static final int TRADE_ACTION_PENDING = 5;
    static final int TRADE_ACTION_SLTP = 6;
    static final int ORDER_TYPE_BUY = 0;
    static final int ORDER_TYPE_SELL = 1;

    static final int POS_SYMBOL_OFF = 24;
    static final int POS_ACTION_OFF = 88;
    static final int POS_PRICE_OPEN_OFF = 92;
    static final int POS_SL_OFF = 108;
    static final int POS_TP_OFF = 116;
    static final int POS_VOLUME_OFF = 124;
    static final int POS_PROFIT_OFF = 132;
    static final int POS_ID_OFF = 0;
    static final int POS_CONTRACT_SIZE_OFF = 252;
    static final int POS_DIGITS_OFF = 260;

    static final int ORD_SYMBOL_OFF = 72;
    static final int ORD_TYPE_OFF = 148;
    static final int ORD_PRICE_OFF = 164;
    static final int ORD_SL_OFF = 188;
    static final int ORD_TP_OFF = 196;
    static final int ORD_ID_OFF = 0;
    static final int ORD_VOLUME_CURRENT_OFF = 212;

    static final int DEAL_TIME_OFF = 80;
    static final int DEAL_SYMBOL_OFF = 88;
    static final int DEAL_ACTION_OFF = 152;
    static final int DEAL_ENTRY_OFF = 156;
    static final int DEAL_PRICE_OPEN_OFF = 160;
    static final int DEAL_PRICE_CLOSE_OFF = 168;
    static final int DEAL_VOLUME_OFF = 192;
    static final int DEAL_PROFIT_OFF = 200;
    static final int DEAL_POSITION_ID_OFF = 248;

    static final int RATE_TIME_OFF = 0;
    static final int RATE_OPEN_OFF = 4;
    static final int RATE_HIGH_OFF = 12;
    static final int RATE_LOW_OFF = 20;
    static final int RATE_CLOSE_OFF = 28;
    static final int RATE_TICKVOL_OFF = 36;

    static final int ACC_TYPE_OFF = 0;
    static final int ACC_BALANCE_OFF = 9;
    static final int ACC_CREDIT_OFF = 17;
    static final int ACC_CURRENCY_OFF = 25;
    static final int ACC_CURRENCY_DIGITS_OFF = 89;
    static final int ACC_LEVERAGE_OFF = 93;
    static final int ACC_NAME_OFF = 97;
    static final int ACC_SERVER_OFF = 355;
    static final int ACC_PROFIT_OFF = 784;

    static final int TR_TRADE_ACTION_OFF = 4;
    static final int TR_SYMBOL_OFF = 8;
    static final int TR_VOLUME_OFF = 72;
    static final int TR_DIGITS_OFF = 80;
    static final int TR_ORDER_OFF = 84;
    static final int TR_TRADE_TYPE_OFF = 92;
    static final int TR_TYPE_FILLING_OFF = 96;
    static final int TR_TYPE_TIME_OFF = 100;
    static final int TR_PRICE_ORDER_OFF = 112;
    static final int TR_PRICE_SL_OFF = 128;
    static final int TR_PRICE_TP_OFF = 136;
    static final int TR_DEVIATION_OFF = 144;
    static final int TR_COMMENT_OFF = 152;
    static final int TR_POSITION_ID_OFF = 216;

    static final int TRES_RETCODE_OFF = 0;
    static final int TRES_DEAL_OFF = 4;
    static final int TRES_ORDER_OFF = 12;
    static final int TRES_VOLUME_OFF = 20;
    static final int TRES_PRICE_OFF = 28;
    static final int TRES_BID_OFF = 36;
    static final int TRES_ASK_OFF = 44;
    static final int TRES_COMMENT_OFF = 52;

    static final int TRADE_PUSH_SIZE = 244;
    static final int TPUSH_CODE_OFF = 0;
    static final int TPUSH_SYMBOL_OFF = 12;
    static final int TPUSH_VOLUME_OFF = 76;
    static final int TPUSH_ORDER_OFF = 88;
    static final int TPUSH_PRICE_OFF = 116;
    static final int TPUSH_COMMENT_OFF = 172;

    static final int TUB_BALANCE_OFF = 0;
    static final int TUB_CREDIT_OFF = 8;
    static final int TUB_ACC_PROFIT_OFF = 16;
    static final int TUB_EQUITY_OFF = 24;
    static final int TUB_MARGIN_OFF = 32;
    static final int TUB_MARGIN_FREE_OFF = 40;

    static final int BOOK_BID_COUNT_OFF = 12;
    static final int BOOK_ASK_COUNT_OFF = 16;

    private static final int[] PERIOD_FROM = {1, 5, 15, 30, 60, 240, 1440, 10080, 43200};
    private static final int[] PERIOD_TO = {1, 5, 15, 30, 16385, 16388, 16408, 32769, 49153};

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

    static int mapPeriod(int minutes) {
        for (int i = 0; i < PERIOD_FROM.length; i++) {
            if (PERIOD_FROM[i] == minutes) return PERIOD_TO[i];
        }
        return minutes;
    }

    static byte[] buildRatesPayload(String symbol, int periodMinutes, int fromTs, int toTs) {
        int period = mapPeriod(periodMinutes);
        Buf b = new Buf();
        b.utf16(prefix(symbol), 64);
        byte[] p = new byte[2];
        p[0] = (byte) (period & 0xff);
        p[1] = (byte) ((period >> 8) & 0xff);
        b.raw(p);
        b.i32(fromTs);
        b.i32(toTs);
        return b.done();
    }

    static byte[] buildHistoryPayload(long fromTs, long toTs) {
        byte[] b = new byte[8];
        writeU32(b, 0, fromTs);
        writeU32(b, 4, toTs);
        return b;
    }

    static byte[] buildTradeRequest(int tradeAction, String symbol, long volume, int digits,
                                    int tradeType, double priceOrder, double priceSl,
                                    double priceTp, long positionId, String comment) {
        byte[] out = new byte[TRADE_REQUEST_SIZE];
        writeU32(out, TR_TRADE_ACTION_OFF, tradeAction);
        putUtf16(out, TR_SYMBOL_OFF, prefix(symbol), 64);
        writeU32(out, TR_VOLUME_OFF, volume);
        writeU32(out, TR_DIGITS_OFF, digits);
        writeU32(out, TR_TRADE_TYPE_OFF, tradeType);
        putF64(out, TR_PRICE_ORDER_OFF, priceOrder);
        putF64(out, TR_PRICE_SL_OFF, priceSl);
        putF64(out, TR_PRICE_TP_OFF, priceTp);
        writeU32(out, TR_DEVIATION_OFF, 30);
        putUtf16(out, TR_COMMENT_OFF, prefix(comment), 64);
        writeU32(out, TR_POSITION_ID_OFF, positionId);
        return out;
    }

    static void putUtf16(byte[] b, int off, String s, int len) {
        if (s == null) return;
        int n = Math.min(s.length(), len / 2);
        for (int i = 0; i < n; i++) {
            char ch = s.charAt(i);
            b[off + i * 2] = (byte) (ch & 0xff);
            b[off + i * 2 + 1] = (byte) ((ch >> 8) & 0xff);
        }
    }

    static void putF64(byte[] b, int off, double v) {
        writeI64(b, off, Double.doubleToLongBits(v));
    }

    static void writeI64(byte[] b, int off, long v) {
        for (int i = 0; i < 8; i++) b[off + i] = (byte) ((v >>> (8 * i)) & 0xff);
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

        Buf i32(int v) {
            byte[] b = new byte[4];
            writeU32(b, 0, v & 0xffffffffL);
            o.write(b, 0, 4);
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
