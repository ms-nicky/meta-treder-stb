package com.stb.markettrader;

import java.util.List;

public class Indicators {

    public static double[] closes(List<Kline> data) {
        double[] c = new double[data.size()];
        for (int i = 0; i < data.size(); i++) c[i] = data.get(i).close;
        return c;
    }

    public static double[] sma(double[] src, int period) {
        double[] out = new double[src.length];
        double sum = 0;
        for (int i = 0; i < src.length; i++) {
            sum += src[i];
            if (i >= period) sum -= src[i - period];
            out[i] = i >= period - 1 ? sum / period : Double.NaN;
        }
        return out;
    }

    public static double[] ema(double[] src, int period) {
        double[] out = new double[src.length];
        double k = 2.0 / (period + 1);
        double prev = Double.NaN;
        double seedSum = 0;
        for (int i = 0; i < src.length; i++) {
            if (i < period - 1) {
                seedSum += src[i];
                out[i] = Double.NaN;
            } else if (i == period - 1) {
                seedSum += src[i];
                prev = seedSum / period;
                out[i] = prev;
            } else {
                prev = src[i] * k + prev * (1 - k);
                out[i] = prev;
            }
        }
        return out;
    }

    public static double[][] bollinger(double[] src, int period, double mult) {
        double[] mid = sma(src, period);
        double[] upper = new double[src.length];
        double[] lower = new double[src.length];
        for (int i = 0; i < src.length; i++) {
            if (i < period - 1) {
                upper[i] = Double.NaN;
                lower[i] = Double.NaN;
                continue;
            }
            double mean = mid[i];
            double sq = 0;
            for (int j = i - period + 1; j <= i; j++) {
                double d = src[j] - mean;
                sq += d * d;
            }
            double sd = Math.sqrt(sq / period);
            upper[i] = mean + mult * sd;
            lower[i] = mean - mult * sd;
        }
        return new double[][]{upper, mid, lower};
    }

    public static double[] rsi(double[] src, int period) {
        double[] out = new double[src.length];
        for (int i = 0; i < src.length; i++) out[i] = Double.NaN;
        if (src.length <= period) return out;
        double gain = 0, loss = 0;
        for (int i = 1; i <= period; i++) {
            double diff = src[i] - src[i - 1];
            if (diff >= 0) gain += diff;
            else loss -= diff;
        }
        double avgGain = gain / period;
        double avgLoss = loss / period;
        out[period] = rsiValue(avgGain, avgLoss);
        for (int i = period + 1; i < src.length; i++) {
            double diff = src[i] - src[i - 1];
            double g = diff > 0 ? diff : 0;
            double l = diff < 0 ? -diff : 0;
            avgGain = (avgGain * (period - 1) + g) / period;
            avgLoss = (avgLoss * (period - 1) + l) / period;
            out[i] = rsiValue(avgGain, avgLoss);
        }
        return out;
    }

    private static double rsiValue(double avgGain, double avgLoss) {
        if (avgLoss == 0) return 100;
        double rs = avgGain / avgLoss;
        return 100 - (100 / (1 + rs));
    }

    public static double[][] macd(double[] src, int fast, int slow, int signal) {
        double[] emaFast = ema(src, fast);
        double[] emaSlow = ema(src, slow);
        double[] macdLine = new double[src.length];
        for (int i = 0; i < src.length; i++) {
            macdLine[i] = (Double.isNaN(emaFast[i]) || Double.isNaN(emaSlow[i]))
                    ? Double.NaN : emaFast[i] - emaSlow[i];
        }
        double[] signalLine = emaRunning(macdLine, signal);
        double[] hist = new double[src.length];
        for (int i = 0; i < src.length; i++) {
            hist[i] = (Double.isNaN(macdLine[i]) || Double.isNaN(signalLine[i]))
                    ? Double.NaN : macdLine[i] - signalLine[i];
        }
        return new double[][]{macdLine, signalLine, hist};
    }

    private static double[] emaRunning(double[] src, int period) {
        double[] out = new double[src.length];
        double k = 2.0 / (period + 1);
        double prev = Double.NaN;
        int count = 0;
        double seedSum = 0;
        for (int i = 0; i < src.length; i++) {
            if (Double.isNaN(src[i])) {
                out[i] = Double.NaN;
                continue;
            }
            if (Double.isNaN(prev)) {
                seedSum += src[i];
                count++;
                if (count == period) {
                    prev = seedSum / period;
                    out[i] = prev;
                } else {
                    out[i] = Double.NaN;
                }
            } else {
                prev = src[i] * k + prev * (1 - k);
                out[i] = prev;
            }
        }
        return out;
    }

    public static double lastValid(double[] arr) {
        for (int i = arr.length - 1; i >= 0; i--) {
            if (!Double.isNaN(arr[i])) return arr[i];
        }
        return Double.NaN;
    }
}