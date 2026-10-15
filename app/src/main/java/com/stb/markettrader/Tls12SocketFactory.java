package com.stb.markettrader;

import android.content.Context;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Collection;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

public class Tls12SocketFactory extends SSLSocketFactory {

    private static final String[] TLS_V12_ONLY = {"TLSv1.2"};

    private final SSLSocketFactory delegate;

    public Tls12SocketFactory(SSLSocketFactory base) {
        this.delegate = base;
    }

    @Override
    public String[] getDefaultCipherSuites() {
        return delegate.getDefaultCipherSuites();
    }

    @Override
    public String[] getSupportedCipherSuites() {
        return delegate.getSupportedCipherSuites();
    }

    @Override
    public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
        return patch(delegate.createSocket(s, host, port, autoClose));
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException {
        return patch(delegate.createSocket(host, port));
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
        return patch(delegate.createSocket(host, port, localHost, localPort));
    }

    @Override
    public Socket createSocket(InetAddress host, int port) throws IOException {
        return patch(delegate.createSocket(host, port));
    }

    @Override
    public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
        return patch(delegate.createSocket(address, port, localAddress, localPort));
    }

    private Socket patch(Socket socket) {
        if (socket instanceof SSLSocket) {
            ((SSLSocket) socket).setEnabledProtocols(TLS_V12_ONLY);
        }
        return socket;
    }

    public static SSLSocketFactory socketFactory(Context context) throws Exception {
        X509TrustManager system = systemTrustManager();
        X509TrustManager bundle = bundleTrustManager(context);
        TrustManager[] managers = new TrustManager[]{new CompositeTrustManager(system, bundle)};

        SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
        sslContext.init(null, managers, null);
        return sslContext.getSocketFactory();
    }

    public static void enable(Context context) {
        try {
            SSLSocketFactory factory = socketFactory(context);
            HttpsURLConnection.setDefaultSSLSocketFactory(new Tls12SocketFactory(factory));
        } catch (NoSuchAlgorithmException e) {
            System.out.println("TLS1.2 tidak tersedia: " + e.getMessage());
        } catch (KeyManagementException e) {
            System.out.println("Gagal init SSL: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("Gagal muat CA bundle: " + e.getMessage());
        }
    }

    private static X509TrustManager systemTrustManager() throws Exception {
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init((KeyStore) null);
        for (TrustManager tm : tmf.getTrustManagers()) {
            if (tm instanceof X509TrustManager) {
                return (X509TrustManager) tm;
            }
        }
        throw new CertificateException("System trust manager tidak ditemukan");
    }

    private static X509TrustManager bundleTrustManager(Context context) throws Exception {
        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        ks.load(null, null);
        InputStream in = context.getResources().openRawResource(R.raw.ca_bundle);
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            Collection<? extends Certificate> certs = cf.generateCertificates(in);
            int i = 0;
            for (Certificate cert : certs) {
                ks.setCertificateEntry("ca" + (i++), cert);
            }
        } finally {
            in.close();
        }
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ks);
        for (TrustManager tm : tmf.getTrustManagers()) {
            if (tm instanceof X509TrustManager) {
                return (X509TrustManager) tm;
            }
        }
        throw new CertificateException("Bundle trust manager tidak ditemukan");
    }

    private static class CompositeTrustManager implements X509TrustManager {
        private final X509TrustManager system;
        private final X509TrustManager bundle;

        CompositeTrustManager(X509TrustManager system, X509TrustManager bundle) {
            this.system = system;
            this.bundle = bundle;
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            system.checkClientTrusted(chain, authType);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            try {
                system.checkServerTrusted(chain, authType);
            } catch (CertificateException first) {
                try {
                    bundle.checkServerTrusted(chain, authType);
                } catch (CertificateException ignored) {
                    throw first;
                }
            }
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return system.getAcceptedIssuers();
        }
    }
}