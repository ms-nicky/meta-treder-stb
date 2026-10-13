package com.stb.markettrader;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class WatchlistView extends View {

    private static final int COLOR_BG = Color.rgb(20, 23, 28);
    private static final int COLOR_TITLE = Color.rgb(150, 160, 175);
    private static final int COLOR_TEXT = Color.rgb(220, 226, 235);
    private static final int COLOR_DIM = Color.rgb(120, 130, 145);
    private static final int COLOR_UP = Color.rgb(14, 203, 129);
    private static final int COLOR_DOWN = Color.rgb(246, 70, 93);
    private static final int COLOR_SEL = Color.rgb(35, 47, 66);
    private static final int COLOR_SEL_FOCUS = Color.rgb(28, 90, 130);
    private static final int COLOR_HEADER = Color.rgb(90, 100, 115);

    public static class Item {
        public final String pair;
        public double price;
        public double changePct;
        public boolean hasData;

        public Item(String pair) {
            this.pair = pair;
        }
    }

    public interface OnSelectListener {
        void onSelect(String pair);
    }

    private List<Item> items = new ArrayList<Item>();
    private int selected = 0;
    private int firstVisible = 0;
    private OnSelectListener listener;

    private Paint bgPaint;
    private Paint titlePaint;
    private Paint textPaint;
    private Paint dimPaint;
    private Paint upPaint;
    private Paint downPaint;
    private Paint selPaint;
    private Paint selFocusPaint;
    private Paint headerPaint;
    private Paint divPaint;

    private float density;

    public WatchlistView(Context context) {
        super(context);
        init();
    }

    public WatchlistView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public WatchlistView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        density = getResources().getDisplayMetrics().density;
        setBackgroundColor(Color.TRANSPARENT);

        bgPaint = new Paint();
        bgPaint.setColor(COLOR_BG);

        titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        titlePaint.setColor(COLOR_TITLE);
        titlePaint.setTextSize(17 * density);
        titlePaint.setFakeBoldText(true);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(COLOR_TEXT);
        textPaint.setTextSize(18 * density);

        dimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dimPaint.setColor(COLOR_DIM);
        dimPaint.setTextSize(18 * density);

        upPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        upPaint.setColor(COLOR_UP);
        upPaint.setTextSize(18 * density);
        upPaint.setTextAlign(Paint.Align.RIGHT);

        downPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        downPaint.setColor(COLOR_DOWN);
        downPaint.setTextSize(18 * density);
        downPaint.setTextAlign(Paint.Align.RIGHT);

        selPaint = new Paint();
        selPaint.setColor(COLOR_SEL);

        selFocusPaint = new Paint();
        selFocusPaint.setColor(COLOR_SEL_FOCUS);

        headerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        headerPaint.setColor(COLOR_HEADER);
        headerPaint.setTextSize(14 * density);
        headerPaint.setFakeBoldText(true);

        divPaint = new Paint();
        divPaint.setColor(Color.rgb(40, 45, 55));
    }

    public void setOnSelectListener(OnSelectListener l) {
        this.listener = l;
    }

    public void setItems(List<Item> newItems) {
        this.items = newItems == null ? new ArrayList<Item>() : newItems;
        if (selected >= items.size()) selected = Math.max(0, items.size() - 1);
        invalidate();
    }

    public void setSelectedPair(String pair) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).pair.equals(pair)) {
                selected = i;
                invalidate();
                return;
            }
        }
    }

    public String getSelectedPair() {
        if (items.isEmpty()) return null;
        return items.get(selected).pair;
    }

    private float headerH() {
        return 30 * density;
    }

    private float rowH() {
        return 40 * density;
    }

    private int visibleRows() {
        int n = (int) ((getHeight() - headerH()) / rowH());
        return Math.max(1, n);
    }

    private void ensureVisible() {
        int vis = visibleRows();
        if (selected < firstVisible) firstVisible = selected;
        if (selected >= firstVisible + vis) firstVisible = selected - vis + 1;
        int maxFirst = Math.max(0, items.size() - vis);
        if (firstVisible > maxFirst) firstVisible = maxFirst;
        if (firstVisible < 0) firstVisible = 0;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (items.isEmpty()) return super.onKeyDown(keyCode, event);
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
                if (selected > 0) selected--;
                ensureVisible();
                invalidate();
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (selected < items.size() - 1) selected++;
                ensureVisible();
                invalidate();
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                if (listener != null) listener.onSelect(items.get(selected).pair);
                return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            float y = event.getY() - headerH();
            if (y >= 0) {
                int idx = firstVisible + (int) (y / rowH());
                if (idx >= 0 && idx < items.size()) {
                    selected = idx;
                    invalidate();
                    if (listener != null) listener.onSelect(items.get(idx).pair);
                }
            }
            return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_SCROLL && !items.isEmpty()) {
            float v = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
            if (v > 0 && selected > 0) selected--;
            else if (v < 0 && selected < items.size() - 1) selected++;
            ensureVisible();
            invalidate();
            return true;
        }
        return super.onGenericMotionEvent(event);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        float hh = headerH();
        float rh = rowH();

        canvas.drawRect(0, 0, w, h, bgPaint);

        canvas.drawText("MARKET", 10 * density, hh - 9 * density, titlePaint);
        canvas.drawText("CHG%", w - 10 * density, hh - 9 * density, headerPaint);
        canvas.drawLine(0, hh, w, hh, divPaint);

        ensureVisible();
        int vis = visibleRows();
        int end = Math.min(items.size(), firstVisible + vis);
        for (int i = firstVisible; i < end; i++) {
            float top = hh + (i - firstVisible) * rh;
            boolean sel = i == selected;
            if (sel) {
                canvas.drawRect(0, top, w, top + rh, isFocused() ? selFocusPaint : selPaint);
            }
            Item it = items.get(i);
            float baseline = top + rh * 0.65f;

            String name = it.pair;
            textPaint.setColor(sel ? Color.WHITE : COLOR_TEXT);
            canvas.drawText(name, 10 * density, baseline, textPaint);

            String chg = it.hasData ? String.format(java.util.Locale.US, "%+.2f%%", it.changePct) : "--";
            Paint cp = it.changePct >= 0 ? upPaint : downPaint;
            cp.setColor(it.hasData ? (it.changePct >= 0 ? COLOR_UP : COLOR_DOWN) : COLOR_DIM);
            canvas.drawText(chg, w - 10 * density, baseline, cp);

            String price = it.hasData ? formatPrice(it.price) : "...";
            dimPaint.setColor(COLOR_DIM);
            canvas.drawText(price, 10 * density, baseline + 15 * density, dimPaint);

            canvas.drawLine(10 * density, top + rh, w, top + rh, divPaint);
        }

        if (items.size() > vis) {
            float barH = Math.max(20 * density, vis * rh * vis / (float) items.size());
            float barY = hh + ((h - hh - barH) * firstVisible / (float) Math.max(1, items.size() - vis));
            Paint sb = new Paint();
            sb.setColor(Color.rgb(70, 80, 95));
            canvas.drawRect(w - 4 * density, barY, w - 2 * density, barY + barH, sb);
        }
    }

    private String formatPrice(double v) {
        if (v >= 1000) return String.format(java.util.Locale.US, "%,.2f", v);
        if (v >= 1) return String.format(java.util.Locale.US, "%.4f", v);
        if (v >= 0.01) return String.format(java.util.Locale.US, "%.6f", v);
        return String.format(java.util.Locale.US, "%.8f", v);
    }
}