package com.stb.markettrader;

public class Kline {
    public long openTime;
    public double open;
    public double high;
    public double low;
    public double close;
    public double volume;

    public Kline(long openTime, double open, double high, double low, double close, double volume) {
        this.openTime = openTime;
        this.open = open;
        this.high = high;
        this.low = low;
        this.close = close;
        this.volume = volume;
    }
}