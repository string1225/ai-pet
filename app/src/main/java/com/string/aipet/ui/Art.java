package com.string.aipet.ui;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;

/** Resolution-independent 2D shapes, shared by room and pet renderers. */
public final class Art {
    public final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Canvas c;
    private static final Typeface REGULAR = Typeface.create("sans-serif", Typeface.NORMAL);
    private static final Typeface BOLD = Typeface.create("sans-serif", Typeface.BOLD);
    public Art(Canvas canvas) { c = canvas; }
    public Art use(Canvas canvas) { c = canvas; return this; }
    public void fill(int color) { p.setColor(color); p.setStyle(Paint.Style.FILL); p.setShader(null); }
    public void rect(float x, float y, float w, float h, float radius, int color) {
        fill(color); c.drawRoundRect(x, y, x + w, y + h, radius, radius, p);
    }
    public void oval(float x, float y, float w, float h, int color) {
        fill(color); c.drawOval(x, y, x + w, y + h, p);
    }
    public void circle(float x, float y, float r, int color) { fill(color); c.drawCircle(x, y, r, p); }
    public void line(float x, float y, float xx, float yy, float width, int color) {
        fill(color); p.setStrokeWidth(width); p.setStrokeCap(Paint.Cap.ROUND); c.drawLine(x, y, xx, yy, p);
    }
    public void path(Path path, int color) { fill(color); c.drawPath(path, p); }
    public void stroke(Path path, float width, int color) {
        fill(color); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(width); p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND); c.drawPath(path, p); p.setStyle(Paint.Style.FILL);
    }
    public void text(String text, float x, float y, float size, int color, boolean bold) {
        fill(color); p.setTextSize(size); p.setTypeface(bold ? BOLD : REGULAR);
        p.setTextAlign(Paint.Align.CENTER); c.drawText(text, x, y, p);
    }
    public void heart(float x, float y, float size, int color) {
        Path h = new Path(); h.moveTo(x, y + size);
        h.cubicTo(x - size * 1.8f, y, x - size * .5f, y - size, x, y - size * .2f);
        h.cubicTo(x + size * .5f, y - size, x + size * 1.8f, y, x, y + size); path(h, color);
    }
    public void leaf(float x, float y, float w, float h, int color) {
        Path leaf = new Path(); leaf.moveTo(x, y);
        leaf.cubicTo(x - w, y - h * .7f, x - w * .2f, y - h, x, y - h);
        leaf.cubicTo(x + w, y - h * .7f, x + w * .5f, y - h * .2f, x, y); path(leaf, color);
    }
    public void star(float x, float y, float radius, int color) {
        Path star = new Path();
        for (int i = 0; i < 10; i++) {
            double angle = Math.PI * i / 5 - Math.PI / 2;
            float r = i % 2 == 0 ? radius : radius * .48f;
            float xx = x + (float)Math.cos(angle) * r, yy = y + (float)Math.sin(angle) * r;
            if (i == 0) star.moveTo(xx, yy); else star.lineTo(xx, yy);
        }
        star.close(); path(star, color);
    }
}
