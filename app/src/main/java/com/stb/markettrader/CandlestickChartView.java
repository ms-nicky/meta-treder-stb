package com.stb.markettrader;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class CandlestickChartView extends View {

    private static final int COLOR_BG = Color.rgb(16, 18, 22);
    private static final int COLOR_GRID = Color.rgb(38, 42, 48);
    private static final int COLOR_TEXT = Color.rgb(170, 178, 190);
    private static final int COLOR_UP = Color.rgb(14, 203, 129);
    private static final int COLOR_DOWN = Color.rgb(246, 70, 93);
    private static final int COLOR_VOL = Color.argb(90, 128, 138, 155);
    private static final int COLOR_LAST = Color.rgb(240, 185, 11);
    private static final int COLOR_CROSS = Color.argb(160, 200, 200, 210);

    public static final int OVERLAY_NONE = 0;
    public static final int OVERLAY_MA = 1;
    public static final int OVERLAY_EMA = 2;
    public static final int OVERLAY_BOLL = 3;

    public static final int SUB_VOL = 0;
    public static final int SUB_RSI = 1;
    public static final int SUB_MACD = 2;

    private static final int COLOR_MA7 = 0xFFF0B90B;
    private static final int COLOR_MA25 = 0xFFE056FD;
    private static final int COLOR_MA99 = 0xFF3D7EFF;
    private static final int COLOR_EMA9 = 0xFF00E5FF;
    private static final int COLOR_EMA21 = 0xFFFF9800;
    private static final int COLOR_BOLL = 0xFF8A93A6;

    private List<Kline> data;
    private Paint gridPaint;
    private Paint textPaint;
    private Paint upPaint;
    private Paint downPaint;
    private Paint volPaint;
    private Paint lastPaint;
    private Paint crossPaint;
    private Paint linePaint;
    private Paint tradePaint;
    private Paint arrowPaint;

    public static class TradeLine {
        public final boolean buy;
        public final boolean pending;
        public final double entry;
        public final double sl;
        public final double tp;

        public TradeLine(boolean buy, boolean pending, double entry, double sl, double tp) {
            this.buy = buy;
            this.pending = pending;
            this.entry = entry;
            this.sl = sl;
            this.tp = tp;
        }
    }

    private List<TradeLine> tradeLines = new ArrayList<TradeLine>();

    private float candleWidth = 10f;
    private float offsetX = 0f;
    private float defaultCandleWidth = 0f;
    private boolean followLatest = true;

    private float lastDownX;
    private float lastDownY;
    private ScaleGestureDetector scaleDetector;

    private float crossX = -1f;
    private float crossY = -1f;

    private float topPadding = 12f;
    private float bottomPadding = 40f;
    private float rightPadding = 68f;
    private float subRatio = 0.22f;

    private int overlayType = OVERLAY_MA;
    private int subType = SUB_VOL;

    private double[] ma7, ma25, ma99, ema9, ema21, rsi;
    private double[][] boll;
    private double[][] macd;

    public CandlestickChartView(Context context) {
        super(context);
        init();
    }

    public CandlestickChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CandlestickChartView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        setBackgroundColor(Color.TRANSPARENT);
        gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridPaint.setColor(COLOR_GRID);
        gridPaint.setStrokeWidth(1f);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(COLOR_TEXT);
        textPaint.setTextSize(24f);

        upPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        downPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        for (Paint p : new Paint[]{upPaint, downPaint}) {
            p.setStrokeWidth(1.8f);
            p.setStyle(Paint.Style.FILL);
        }
        upPaint.setColor(COLOR_UP);
        downPaint.setColor(COLOR_DOWN);

        volPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        volPaint.setColor(COLOR_VOL);

        lastPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        lastPaint.setColor(COLOR_LAST);
        lastPaint.setStrokeWidth(1.5f);
        lastPaint.setStyle(Paint.Style.STROKE);

        crossPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        crossPaint.setColor(COLOR_CROSS);
        crossPaint.setStrokeWidth(1.2f);

        linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(2f);

        tradePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        tradePaint.setStrokeWidth(1.6f);
        tradePaint.setStyle(Paint.Style.STROKE);

        arrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        arrowPaint.setStyle(Paint.Style.FILL);

        scaleDetector = new ScaleGestureDetector(getContext(),
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScale(ScaleGestureDetector detector) {
                        float factor = detector.getScaleFactor();
                        float newW = clamp(candleWidth * factor, 4f, 70f);
                        factor = newW / candleWidth;
                        offsetX = detector.getFocusX() - (detector.getFocusX() - offsetX) * factor;
                        candleWidth = newW;
                        clampOffset();
                        invalidate();
                        return true;
                    }
                });
    }

    private float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    public void setKlines(List<Kline> klines) {
        setKlines(klines, true);
    }

    public void setKlines(List<Kline> klines, boolean resetView) {
        this.data = klines;
        computeIndicators();
        if (resetView && klines != null && !klines.isEmpty() && klines.size() > 5) {
            float w = getWidth() > 0 ? getWidth() : 1000f;
            defaultCandleWidth = w / 90f;
            candleWidth = defaultCandleWidth;
        }
        if (resetView) {
            resetView();
        } else {
            if (followLatest) offsetX = minOffset();
            clampOffset();
            invalidate();
        }
    }

    private void computeIndicators() {
        if (data == null || data.isEmpty()) {
            ma7 = ma25 = ma99 = ema9 = ema21 = rsi = null;
            boll = null;
            macd = null;
            return;
        }
        double[] c = Indicators.closes(data);
        ma7 = Indicators.sma(c, 7);
        ma25 = Indicators.sma(c, 25);
        ma99 = Indicators.sma(c, 99);
        ema9 = Indicators.ema(c, 9);
        ema21 = Indicators.ema(c, 21);
        boll = Indicators.bollinger(c, 20, 2.0);
        rsi = Indicators.rsi(c, 14);
        macd = Indicators.macd(c, 12, 26, 9);
    }

    public int getOverlayType() {
        return overlayType;
    }

    public int getSubType() {
        return subType;
    }

    public int cycleOverlay() {
        overlayType = overlayType == OVERLAY_BOLL ? OVERLAY_NONE : overlayType + 1;
        invalidate();
        return overlayType;
    }

    public int cycleSub() {
        subType = subType == SUB_MACD ? SUB_VOL : subType + 1;
        invalidate();
        return subType;
    }

    public void resetView() {
        if (defaultCandleWidth > 0) candleWidth = defaultCandleWidth;
        followLatest = true;
        offsetX = minOffset();
        invalidate();
    }

    public void setTradeLines(List<TradeLine> lines) {
        tradeLines = lines != null ? lines : new ArrayList<TradeLine>();
        invalidate();
    }

    public Double getLastClose() {
        if (data != null && !data.isEmpty()) {
            return data.get(data.size() - 1).close;
        }
        return null;
    }

    public void updateLastPrice(double price) {
        if (data == null || data.isEmpty() || price <= 0) return;
        Kline k = data.get(data.size() - 1);
        k.close = price;
        if (price > k.high) k.high = price;
        if (price < k.low || k.low <= 0) k.low = price;
        computeIndicators();
        invalidate();
    }

    private float latestOffset() {
        return minOffset();
    }

    private float contentWidth() {
        if (data == null || data.isEmpty()) return 1f;
        return data.size() * candleWidth;
    }

    private float minOffset() {
        float chartW = getWidth() - rightPadding;
        if (chartW <= 0f) return 0f;
        float content = contentWidth();
        if (content <= chartW) return (chartW - content) / 2f;
        float shift = chartW * 0.06f;
        return chartW - shift - content;
    }

    private float maxOffset() {
        float lo = minOffset();
        return lo > 0f ? lo : 0f;
    }

    private void clampOffset() {
        float lo = minOffset();
        float hi = maxOffset();
        if (offsetX < lo) offsetX = lo;
        if (offsetX > hi) offsetX = hi;
        followLatest = offsetX <= lo + candleWidth * 0.5f;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (data != null && !data.isEmpty()) {
            if (followLatest) offsetX = minOffset();
            clampOffset();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (data == null || data.isEmpty()) {
            return super.onKeyDown(keyCode, event);
        }
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                offsetX -= candleWidth * 3f;
                clampOffset();
                invalidate();
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                offsetX += candleWidth * 3f;
                clampOffset();
                invalidate();
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                zoom(1.25f);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                zoom(0.8f);
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                resetView();
                return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void zoom(float factor) {
        float newW = clamp(candleWidth * factor, 3f, 80f);
        candleWidth = newW;
        clampOffset();
        invalidate();
    }

    public void zoomIn() {
        if (data == null || data.isEmpty()) return;
        zoom(1.25f);
    }

    public void zoomOut() {
        if (data == null || data.isEmpty()) return;
        zoom(0.8f);
    }

    public void panLeft() {
        if (data == null || data.isEmpty()) return;
        offsetX -= candleWidth * 3f;
        clampOffset();
        invalidate();
    }

    public void panRight() {
        if (data == null || data.isEmpty()) return;
        offsetX += candleWidth * 3f;
        clampOffset();
        invalidate();
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if (data == null || data.isEmpty()) return super.onGenericMotionEvent(event);
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_SCROLL) {
            float v = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
            if (v > 0) zoomOut();
            else if (v < 0) zoomIn();
            return true;
        }
        if (action == MotionEvent.ACTION_HOVER_MOVE) {
            crossX = event.getX();
            crossY = event.getY();
            invalidate();
            return true;
        }
        if (action == MotionEvent.ACTION_HOVER_EXIT) {
            crossX = -1f;
            crossY = -1f;
            invalidate();
            return true;
        }
        return super.onGenericMotionEvent(event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        return true;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastDownX = event.getX();
                lastDownY = event.getY();
                crossX = event.getX();
                crossY = event.getY();
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!scaleDetector.isInProgress()) {
                    float dx = event.getX() - lastDownX;
                    offsetX += dx;
                    lastDownX = event.getX();
                    clampOffset();
                } else {
                    crossX = event.getX();
                    crossY = event.getY();
                }
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                lastDownX = 0;
                lastDownY = 0;
                return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float chartH = getHeight();
        float chartW = getWidth() - rightPadding;
        float subH = chartH * subRatio;
        float priceTop = topPadding;
        float priceBottom = chartH - subH - bottomPadding;
        float priceH = priceBottom - priceTop;
        float subTop = priceBottom + 4f;
        float subBottom = chartH - bottomPadding;

        if (data == null || data.isEmpty()) {
            textPaint.setColor(COLOR_TEXT);
            canvas.drawText("Memuat data...", chartW / 2f - 80f, chartH / 2f, textPaint);
            return;
        }

        drawGrid(canvas, chartW, priceTop, priceBottom, subTop, subBottom);

        clampOffset();
        PriceRange range = visibleRange();
        if (range == null) return;
        double span = range.max - range.min;
        if (span <= 0) span = range.max > 0 ? range.max * 0.01 : 1;

        float lastClose = (float) data.get(data.size() - 1).close;
        float lastY = mapPrice(lastClose, priceTop, priceH, range.max, span);

        float maxVol = 1f;
        for (Kline k : data) {
            if (k.volume > maxVol) maxVol = (float) k.volume;
        }

        for (int i = 0; i < data.size(); i++) {
            float x = i * candleWidth + offsetX;
            if (x < -candleWidth || x > chartW) continue;
            Kline k = data.get(i);
            drawCandle(canvas, k, x, priceTop, priceH, span, range.max);
        }

        drawSignals(canvas, chartW, priceTop, priceH, range.max, span);

        drawOverlays(canvas, chartW, priceTop, priceH, range.max, span);

        drawTradeLines(canvas, chartW, priceTop, priceBottom, priceH, range.max, span);

        lastPaint.setColor(COLOR_LAST);
        lastPaint.setStyle(Paint.Style.STROKE);
        if (lastY > priceTop && lastY < priceBottom) {
            canvas.drawLine(0, lastY, chartW, lastY, lastPaint);
            textPaint.setColor(COLOR_LAST);
            canvas.drawText(formatNum(lastClose), chartW + 6f, lastY - 6f, textPaint);
        }
        lastPaint.setStyle(Paint.Style.FILL_AND_STROKE);
        lastPaint.setColor(COLOR_BG);
        canvas.drawCircle(chartW, lastY, 8f, lastPaint);

        drawSubPanel(canvas, chartW, subTop, subBottom, maxVol);
        drawLegend(canvas, chartW, priceTop);

        if (crossX >= 0 && crossY >= 0
                && crossX <= chartW && crossY >= priceTop && crossY <= subBottom) {
            canvas.drawLine(crossX, priceTop, crossX, subBottom, crossPaint);
            canvas.drawLine(0, crossY, chartW, crossY, crossPaint);
            int idx = (int) ((crossX - offsetX) / candleWidth);
            if (idx >= 0 && idx < data.size()) {
                Kline k = data.get(idx);
                textPaint.setColor(COLOR_TEXT);
                String pLabel;
                if (crossY <= priceBottom) {
                    double p = range.max - ((crossY - priceTop) / priceH) * span;
                    pLabel = String.format("%s  p:%s", timeLabel(k.openTime), formatNum(p));
                } else {
                    pLabel = String.format("%s  c:%s", timeLabel(k.openTime), formatNum(k.close));
                }
                canvas.drawText(pLabel, chartW - textPaint.measureText(pLabel) - 8f, chartH - 12f, textPaint);
            }
        }
    }

    private float mapPrice(double v, float top, float h, double max, double span) {
        return top + (float) ((max - v) / span) * h;
    }

    private void drawOverlays(Canvas canvas, float chartW, float priceTop, float priceH,
                              double max, double span) {
        if (overlayType == OVERLAY_MA) {
            drawSeries(canvas, ma7, COLOR_MA7, chartW, priceTop, priceH, max, span);
            drawSeries(canvas, ma25, COLOR_MA25, chartW, priceTop, priceH, max, span);
            drawSeries(canvas, ma99, COLOR_MA99, chartW, priceTop, priceH, max, span);
        } else if (overlayType == OVERLAY_EMA) {
            drawSeries(canvas, ema9, COLOR_EMA9, chartW, priceTop, priceH, max, span);
            drawSeries(canvas, ema21, COLOR_EMA21, chartW, priceTop, priceH, max, span);
        } else if (overlayType == OVERLAY_BOLL && boll != null) {
            drawSeries(canvas, boll[0], COLOR_BOLL, chartW, priceTop, priceH, max, span);
            drawSeries(canvas, boll[1], COLOR_BOLL, chartW, priceTop, priceH, max, span);
            drawSeries(canvas, boll[2], COLOR_BOLL, chartW, priceTop, priceH, max, span);
        }
    }

    private void drawSeries(Canvas canvas, double[] arr, int color, float chartW,
                            float top, float h, double max, double span) {
        if (arr == null || arr.length == 0) return;
        linePaint.setColor(color);
        Path path = new Path();
        boolean started = false;
        for (int i = 0; i < arr.length; i++) {
            float x = i * candleWidth + offsetX + candleWidth / 2f;
            if (x < 0) continue;
            if (x > chartW) break;
            double v = arr[i];
            if (Double.isNaN(v)) {
                started = false;
                continue;
            }
            float y = mapPrice(v, top, h, max, span);
            if (!started) {
                path.moveTo(x, y);
                started = true;
            } else {
                path.lineTo(x, y);
            }
        }
        canvas.save();
        canvas.clipRect(0f, top, chartW, top + h);
        canvas.drawPath(path, linePaint);
        canvas.restore();
    }

    private void drawSubPanel(Canvas canvas, float chartW, float subTop, float subBottom, float maxVol) {
        float subH = subBottom - subTop;
        if (subType == SUB_VOL) {
            for (int i = 0; i < data.size(); i++) {
                float x = i * candleWidth + offsetX;
                if (x < -candleWidth || x > chartW) continue;
                Kline k = data.get(i);
                float half = candleWidth * 0.34f;
                if (half < 0.8f) half = 0.8f;
                float hpx = (float) (k.volume / maxVol) * (subH - 2f);
                float top = subBottom - hpx;
                canvas.drawRect(x + candleWidth / 2f - half, top,
                        x + candleWidth / 2f + half, subBottom, volPaint);
            }
            return;
        }
        if (subType == SUB_RSI) {
            drawRsi(canvas, chartW, subTop, subBottom);
        } else {
            drawMacd(canvas, chartW, subTop, subBottom);
        }
    }

    private void drawRsi(Canvas canvas, float chartW, float subTop, float subBottom) {
        if (rsi == null) return;
        float h = subBottom - subTop;
        double max = 100, span = 100;
        int c70 = Color.rgb(60, 66, 78);
        gridPaint.setColor(c70);
        float y70 = mapPrice(70, subTop, h, max, span);
        float y30 = mapPrice(30, subTop, h, max, span);
        canvas.drawLine(0, y70, chartW, y70, gridPaint);
        canvas.drawLine(0, y30, chartW, y30, gridPaint);
        gridPaint.setColor(COLOR_GRID);
        drawSeries(canvas, rsi, COLOR_MA25, chartW, subTop, h, max, span);
        textPaint.setColor(COLOR_TEXT);
        double last = Indicators.lastValid(rsi);
        canvas.drawText("RSI(14) " + (Double.isNaN(last) ? "--" : String.format("%.1f", last)),
                6f, subTop + 20f, textPaint);
    }

    private void drawMacd(Canvas canvas, float chartW, float subTop, float subBottom) {
        if (macd == null) return;
        float h = subBottom - subTop;
        double[] line = macd[0], sig = macd[1], hist = macd[2];
        double amax = 0;
        for (int i = 0; i < line.length; i++) {
            if (!Double.isNaN(line[i])) amax = Math.max(amax, Math.abs(line[i]));
            if (!Double.isNaN(sig[i])) amax = Math.max(amax, Math.abs(sig[i]));
            if (!Double.isNaN(hist[i])) amax = Math.max(amax, Math.abs(hist[i]));
        }
        if (amax <= 0) amax = 1;
        double span = amax * 2.2;
        double max = amax * 1.1;
        float zeroY = mapPrice(0, subTop, h, max, span);

        for (int i = 0; i < hist.length; i++) {
            float x = i * candleWidth + offsetX;
            if (x < -candleWidth || x > chartW) continue;
            double v = hist[i];
            if (Double.isNaN(v)) continue;
            float y = mapPrice(v, subTop, h, max, span);
            float half = candleWidth * 0.3f;
            if (half < 0.6f) half = 0.6f;
            Paint p = v >= 0 ? upPaint : downPaint;
            p.setAlpha(150);
            canvas.drawRect(x + candleWidth / 2f - half, Math.min(zeroY, y),
                    x + candleWidth / 2f + half, Math.max(zeroY, y), p);
            p.setAlpha(255);
        }
        drawSeries(canvas, line, COLOR_EMA9, chartW, subTop, h, max, span);
        drawSeries(canvas, sig, COLOR_EMA21, chartW, subTop, h, max, span);
        textPaint.setColor(COLOR_TEXT);
        double m = Indicators.lastValid(line);
        double s = Indicators.lastValid(sig);
        String txt = "MACD(12,26,9) "
                + (Double.isNaN(m) ? "--" : String.format("%.2f", m))
                + "  " + (Double.isNaN(s) ? "--" : String.format("%.2f", s));
        canvas.drawText(txt, 6f, subTop + 20f, textPaint);
    }

    private void drawLegend(Canvas canvas, float chartW, float priceTop) {
        float y = priceTop + 20f;
        float x = 6f;
        if (overlayType == OVERLAY_MA) {
            x = legendItem(canvas, x, y, "MA7", COLOR_MA7, ma7);
            x = legendItem(canvas, x, y, "MA25", COLOR_MA25, ma25);
            legendItem(canvas, x, y, "MA99", COLOR_MA99, ma99);
        } else if (overlayType == OVERLAY_EMA) {
            x = legendItem(canvas, x, y, "EMA9", COLOR_EMA9, ema9);
            legendItem(canvas, x, y, "EMA21", COLOR_EMA21, ema21);
        } else if (overlayType == OVERLAY_BOLL && boll != null) {
            double up = Indicators.lastValid(boll[0]);
            String s = "BOLL(20,2) upper " + (Double.isNaN(up) ? "--" : formatNum(up));
            textPaint.setColor(COLOR_BOLL);
            canvas.drawText(s, x, y, textPaint);
        }
    }

    private float legendItem(Canvas canvas, float x, float y, String name, int color, double[] arr) {
        double v = arr == null ? Double.NaN : Indicators.lastValid(arr);
        String s = name + " " + (Double.isNaN(v) ? "--" : formatNum(v)) + "   ";
        textPaint.setColor(color);
        canvas.drawText(s, x, y, textPaint);
        return x + textPaint.measureText(s);
    }

    private void drawSignals(Canvas canvas, float chartW, float priceTop, float priceH,
                             double max, double span) {
        if (macd == null || subType != SUB_MACD) return;
        double[] line = macd[0];
        double[] sig = macd[1];
        int n = Math.min(line.length, sig.length);
        for (int i = 1; i < n; i++) {
            if (Double.isNaN(line[i]) || Double.isNaN(sig[i])
                    || Double.isNaN(line[i - 1]) || Double.isNaN(sig[i - 1])) continue;
            boolean buy = line[i - 1] <= sig[i - 1] && line[i] > sig[i];
            boolean sell = line[i - 1] >= sig[i - 1] && line[i] < sig[i];
            if (!buy && !sell) continue;
            float x = i * candleWidth + offsetX + candleWidth / 2f;
            if (x < -candleWidth || x > chartW) continue;
            Kline k = data.get(i);
            if (buy) {
                float y = mapPrice(k.low, priceTop, priceH, max, span) + 22f;
                drawArrow(canvas, x, y, true, COLOR_UP);
            } else {
                float y = mapPrice(k.high, priceTop, priceH, max, span) - 22f;
                drawArrow(canvas, x, y, false, COLOR_DOWN);
            }
        }
    }

    private void drawArrow(Canvas canvas, float x, float y, boolean up, int color) {
        float s = Math.max(7f, Math.min(13f, candleWidth * 0.55f));
        Path p = new Path();
        if (up) {
            p.moveTo(x, y - s);
            p.lineTo(x - s * 0.75f, y);
            p.lineTo(x + s * 0.75f, y);
        } else {
            p.moveTo(x, y + s);
            p.lineTo(x - s * 0.75f, y);
            p.lineTo(x + s * 0.75f, y);
        }
        p.close();
        arrowPaint.setColor(color);
        canvas.drawPath(p, arrowPaint);
    }

    private void drawTradeLines(Canvas canvas, float chartW, float priceTop, float priceBottom,
                                float priceH, double max, double span) {
        if (tradeLines.isEmpty()) return;
        for (TradeLine t : tradeLines) {
            int entryColor = t.buy ? COLOR_UP : COLOR_DOWN;
            String tag = (t.pending ? "ORD " : "") + (t.buy ? "BUY" : "SELL");
            drawTradeLine(canvas, t.entry, entryColor, tag, false, chartW,
                    priceTop, priceBottom, priceH, max, span);
            if (t.sl > 0) {
                drawTradeLine(canvas, t.sl, COLOR_DOWN, "SL", true, chartW,
                        priceTop, priceBottom, priceH, max, span);
            }
            if (t.tp > 0) {
                drawTradeLine(canvas, t.tp, COLOR_UP, "TP", true, chartW,
                        priceTop, priceBottom, priceH, max, span);
            }
        }
    }

    private void drawTradeLine(Canvas canvas, double value, int color, String label,
                               boolean dashed, float chartW, float priceTop, float priceBottom,
                               float priceH, double max, double span) {
        float y = mapPrice(value, priceTop, priceH, max, span);
        if (y < priceTop || y > priceBottom) return;
        tradePaint.setColor(color);
        if (dashed) {
            tradePaint.setPathEffect(new DashPathEffect(new float[]{10f, 8f}, 0f));
        } else {
            tradePaint.setPathEffect(null);
        }
        canvas.drawLine(0, y, chartW, y, tradePaint);

        String text = label + " " + formatNum(value);
        float tw = textPaint.measureText(text) + 10f;
        float th = 30f;
        float left = chartW - tw - 2f;
        tradePaint.setPathEffect(null);
        tradePaint.setStyle(Paint.Style.FILL);
        tradePaint.setColor(color);
        canvas.drawRect(left, y - th / 2f, left + tw, y + th / 2f, tradePaint);
        tradePaint.setStyle(Paint.Style.STROKE);
        int oldColor = textPaint.getColor();
        textPaint.setColor(Color.rgb(16, 18, 22));
        canvas.drawText(text, left + 5f, y + 8f, textPaint);
        textPaint.setColor(oldColor);
    }

    private void drawGrid(Canvas canvas, float chartW, float priceTop, float priceBottom,
                          float subTop, float subBottom) {
        int lines = 5;
        for (int i = 0; i <= lines; i++) {
            float y = priceTop + ((priceBottom - priceTop) * i / lines);
            canvas.drawLine(0, y, chartW, y, gridPaint);
        }
        canvas.drawLine(0, subTop, chartW, subTop, gridPaint);
        canvas.drawLine(0, subBottom, chartW, subBottom, gridPaint);
    }

    private void drawCandle(Canvas canvas, Kline k, float x, float priceTop, float priceH,
                            double span, double maxP) {
        float bodyTop = mapPrice(Math.max(k.open, k.close), priceTop, priceH, maxP, span);
        float bodyBottom = mapPrice(Math.min(k.open, k.close), priceTop, priceH, maxP, span);
        float half = candleWidth * 0.34f;
        if (half < 0.8f) half = 0.8f;

        float wickTop = mapPrice(k.high, priceTop, priceH, maxP, span);
        float wickBottom = mapPrice(k.low, priceTop, priceH, maxP, span);

        Paint p = k.close >= k.open ? upPaint : downPaint;

        float cx = x + candleWidth / 2f;
        canvas.drawLine(cx, wickTop, cx, wickBottom, p);
        float minH = 1.2f;
        if (bodyBottom - bodyTop < minH) {
            bodyBottom = bodyTop + minH;
        }
        RectF body = new RectF(x + candleWidth / 2f - half, bodyTop,
                x + candleWidth / 2f + half, bodyBottom);
        canvas.drawRect(body, p);
    }

    private PriceRange visibleRange() {
        float chartW = getWidth() - rightPadding;
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        boolean any = false;
        for (int i = 0; i < data.size(); i++) {
            float x = i * candleWidth + offsetX;
            if (x < -candleWidth || x > chartW + candleWidth) continue;
            Kline k = data.get(i);
            any = true;
            if (k.low < min) min = k.low;
            if (k.high > max) max = k.high;
        }
        if (overlayType == OVERLAY_BOLL && boll != null) {
            for (int i = 0; i < data.size(); i++) {
                float x = i * candleWidth + offsetX;
                if (x < -candleWidth || x > chartW + candleWidth) continue;
                if (!Double.isNaN(boll[0][i])) max = Math.max(max, boll[0][i]);
                if (!Double.isNaN(boll[2][i])) min = Math.min(min, boll[2][i]);
            }
        }
        if (!any) {
            for (Kline k : data) {
                if (k.low < min) min = k.low;
                if (k.high > max) max = k.high;
            }
            if (min > max) return null;
        }
        double pad = (max - min) * 0.04;
        return new PriceRange(min - pad, max + pad);
    }

    private String timeLabel(long openTimeMs) {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd MMM HH:mm", java.util.Locale.US);
        sdf.setTimeZone(java.util.TimeZone.getTimeZone("Asia/Jakarta"));
        return sdf.format(new java.util.Date(openTimeMs));
    }

    private String formatNum(double v) {
        if (v >= 1000) return String.format(java.util.Locale.US, "%,.2f", v);
        if (v >= 1) return String.format(java.util.Locale.US, "%.4f", v);
        if (v >= 0.01) return String.format(java.util.Locale.US, "%.6f", v);
        return String.format(java.util.Locale.US, "%.8f", v);
    }

    private static class PriceRange {
        final double min, max;
        PriceRange(double min, double max) {
            this.min = min;
            this.max = max;
        }
    }
}