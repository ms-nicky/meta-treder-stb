package com.stb.markettrader;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

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

    private List<Kline> data;
    private Paint gridPaint;
    private Paint textPaint;
    private Paint upPaint;
    private Paint downPaint;
    private Paint volPaint;
    private Paint lastPaint;
    private Paint crossPaint;

    private float candleWidth = 10f;
    private float offsetX = 0f;

    private float lastDownX;
    private float lastDownY;
    private ScaleGestureDetector scaleDetector;

    private float crossX = -1f;
    private float crossY = -1f;

    private float topPadding = 12f;
    private float bottomPadding = 40f;
    private float rightPadding = 62f;
    private float volumeRatio = 0.20f;

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
        this.data = klines;
        if (klines != null && !klines.isEmpty() && klines.size() > 5) {
            float w = getWidth() > 0 ? getWidth() : 1000f;
            candleWidth = w / 90f;
        }
        resetView();
        invalidate();
    }

    public void resetView() {
        offsetX = 0f;
        invalidate();
    }

    private float contentWidth() {
        if (data == null || data.isEmpty()) return 1f;
        return data.size() * candleWidth;
    }

    private void clampOffset() {
        float chartW = getWidth() - rightPadding;
        float content = contentWidth();
        float maxOffset = chartW - content;
        float minOffset = 40f;
        if (offsetX < maxOffset) offsetX = maxOffset;
        if (offsetX > minOffset) offsetX = minOffset;
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
        float volH = chartH * volumeRatio;
        float priceTop = topPadding;
        float priceBottom = chartH - volH - bottomPadding;
        float priceH = priceBottom - priceTop;

        if (data == null || data.isEmpty()) {
            textPaint.setColor(COLOR_TEXT);
            canvas.drawText("Memuat data...", chartW / 2f - 80f, chartH / 2f, textPaint);
            return;
        }

        drawGrid(canvas, chartW, priceTop, priceBottom, volH);

        PriceRange range = visibleRange();
        if (range == null) return;
        double span = range.max - range.min;
        if (span <= 0) span = range.max > 0 ? range.max * 0.01 : 1;

        float lastClose = (float) data.get(data.size() - 1).close;
        float lastY = priceTop + (float) ((range.max - lastClose) / span) * priceH;

        float maxVol = 1f;
        for (Kline k : data) {
            if (k.volume > maxVol) maxVol = (float) k.volume;
        }

        for (int i = 0; i < data.size(); i++) {
            float x = i * candleWidth + offsetX;
            if (x < -candleWidth || x > chartW) continue;
            Kline k = data.get(i);
            drawCandle(canvas, k, x, priceTop, priceH, span, range.max, volH, maxVol);
        }

        lastPaint.setColor(COLOR_LAST);
        lastPaint.setStyle(Paint.Style.STROKE);
        if (lastY > priceTop && lastY < priceBottom) {
            canvas.drawLine(0, lastY, chartW, lastY, lastPaint);
            textPaint.setColor(COLOR_LAST);
            canvas.drawText(String.format("%.2f", lastClose), chartW + 6f, lastY - 6f, textPaint);
        }
        lastPaint.setStyle(Paint.Style.FILL_AND_STROKE);
        lastPaint.setColor(COLOR_BG);
        canvas.drawCircle(chartW, lastY, 8f, lastPaint);

        if (crossX >= 0 && crossY >= 0
                && crossX <= chartW && crossY >= priceTop && crossY <= priceBottom) {
            canvas.drawLine(crossX, priceTop, crossX, priceBottom, crossPaint);
            canvas.drawLine(0, crossY, chartW, crossY, crossPaint);
            int idx = (int) ((crossX - offsetX) / candleWidth);
            if (idx >= 0 && idx < data.size()) {
                Kline k = data.get(idx);
                double p = range.max - ((crossY - priceTop) / priceH) * span;
                textPaint.setColor(COLOR_TEXT);
                String pLabel = String.format("%s  p:%.2f", timeLabel(k.openTime), p);
                canvas.drawText(pLabel, chartW - textPaint.measureText(pLabel) - 8f, chartH - 12f, textPaint);
            }
        }
    }

    private void drawGrid(Canvas canvas, float chartW, float priceTop, float priceBottom, float volH) {
        int lines = 5;
        for (int i = 0; i <= lines; i++) {
            float y = priceTop + ((priceBottom - priceTop) * i / lines);
            canvas.drawLine(0, y, chartW, y, gridPaint);
        }
        float volY = priceBottom + 4f;
        canvas.drawLine(0, volY, chartW, volY, gridPaint);
    }

    private void drawCandle(Canvas canvas, Kline k, float x, float priceTop, float priceH,
                            double span, double maxP, float volH, float maxVol) {
        float bodyTop = priceTop + (float) ((maxP - Math.max(k.open, k.close)) / span) * priceH;
        float bodyBottom = priceTop + (float) ((maxP - Math.min(k.open, k.close)) / span) * priceH;
        float half = candleWidth * 0.34f;
        if (half < 0.8f) half = 0.8f;

        float wickTop = priceTop + (float) ((maxP - k.high) / span) * priceH;
        float wickBottom = priceTop + (float) ((maxP - k.low) / span) * priceH;

        Paint p = k.close >= k.open ? upPaint : downPaint;

        float cx = x + candleWidth / 2f;
        canvas.drawLine(cx, wickTop, cx, wickBottom, p);
        float minH = 1.2f;
        if (bodyBottom - bodyTop < minH) {
            bodyBottom = bodyTop + minH;
        }
        RectF body = new RectF(x + candleWidth / 2f - half, bodyTop, x + candleWidth / 2f + half, bodyBottom);
        canvas.drawRect(body, p);

        float volBottom = getHeight() - bottomPadding;
        float volHpx = (float) (k.volume / maxVol) * (volH - 4f);
        float volTop = volBottom - volHpx;
        canvas.drawRect(x + candleWidth / 2f - half, volTop, x + candleWidth / 2f + half, volBottom, volPaint);
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
        if (!any) return null;
        double pad = (max - min) * 0.04;
        return new PriceRange(min - pad, max + pad);
    }

    private String timeLabel(long openTimeMs) {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd MMM HH:mm", java.util.Locale.US);
        return sdf.format(new java.util.Date(openTimeMs));
    }

    private static class PriceRange {
        final double min, max;
        PriceRange(double min, double max) {
            this.min = min;
            this.max = max;
        }
    }
}