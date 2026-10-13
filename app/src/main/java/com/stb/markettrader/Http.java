package com.stb.markettrader;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.zip.GZIPInputStream;

public class Http {

    private static final String UA =
            "Mozilla/5.0 (Linux; Android 4.4) MarketTraderSTB/2.0";
    private static final int MAX_ATTEMPTS = 3;

    public static class HttpStatusException extends Exception {
        public final int code;

        public HttpStatusException(int code, String body) {
            super("HTTP " + code + " " + body);
            this.code = code;
        }
    }

    public static String getText(String urlStr) throws Exception {
        Exception last = null;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            try {
                return getOnce(urlStr);
            } catch (UnknownHostException e) {
                last = e;
            } catch (IOException e) {
                last = e;
            } catch (HttpStatusException e) {
                if (e.code < 500) throw e;
                last = e;
            }
            sleep(400L * (attempt + 1));
        }
        if (last != null) throw last;
        throw new IOException("Gagal mengambil " + urlStr);
    }

    private static String getOnce(String urlStr) throws Exception {
        HttpURLConnection conn = null;
        InputStream in = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(15000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", UA);
            conn.setRequestProperty("Accept", "*/*");
            conn.setRequestProperty("Accept-Encoding", "gzip");
            conn.setRequestProperty("Connection", "keep-alive");
            conn.connect();

            int code = conn.getResponseCode();
            in = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
            String enc = conn.getContentEncoding();
            if (in != null && enc != null && enc.toLowerCase().contains("gzip")) {
                in = new GZIPInputStream(in);
            }
            String body = in != null ? readStream(in) : "";
            if (code < 200 || code >= 300) {
                throw new HttpStatusException(code, truncate(body));
            }
            return body;
        } finally {
            if (in != null) {
                try { in.close(); } catch (Exception ignored) { }
            }
        }
    }

    public static void download(String urlStr, File out) throws Exception {
        IOException lastIo = null;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            try {
                downloadOnce(urlStr, out);
                return;
            } catch (IOException e) {
                lastIo = e;
            }
            sleep(400L * (attempt + 1));
        }
        if (lastIo != null) throw lastIo;
        throw new IOException("Gagal mengunduh " + urlStr);
    }

    private static void downloadOnce(String urlStr, File out) throws Exception {
        HttpURLConnection conn = null;
        InputStream in = null;
        FileOutputStream fos = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(30000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", UA);
            conn.connect();
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new HttpStatusException(code, "");
            }
            in = conn.getInputStream();
            fos = new FileOutputStream(out);
            byte[] buf = new byte[8192];
            int n;
            long total = 0;
            while ((n = in.read(buf)) != -1) {
                fos.write(buf, 0, n);
                total += n;
            }
            fos.flush();
            if (total <= 0) {
                throw new IOException("File kosong");
            }
        } finally {
            if (fos != null) { try { fos.close(); } catch (Exception ignored) { } }
            if (in != null) { try { in.close(); } catch (Exception ignored) { } }
        }
    }

    private static String readStream(InputStream in) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        char[] buf = new char[8192];
        int n;
        while ((n = reader.read(buf)) != -1) {
            sb.append(buf, 0, n);
        }
        reader.close();
        return sb.toString();
    }

    private static String truncate(String s) {
        if (s == null) return "";
        if (s.length() > 120) return s.substring(0, 120);
        return s;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}