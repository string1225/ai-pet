package com.string.aipet.ui;

import android.graphics.Canvas;
import android.graphics.Path;
import com.string.aipet.model.PetState;

public final class SnakeRenderer implements PetRenderer {
    private final Art art = new Art(null);
    @Override public void draw(Canvas c, PetState state, float x, float y, float time, float reaction) {
        Art a = art.use(c);
        float sway = (float)Math.sin(time * 1.7) * (state.sleeping ? 1 : 4);
        c.save(); c.translate(x, y + sway - reaction * 9);
        a.oval(-106, 56, 205, 31, 0x1C526047);
        Path body = new Path(); body.moveTo(-104, 41);
        body.cubicTo(-42, 92, 66, 78, 39, 40);
        body.cubicTo(21, 12, -100, 46, -55, 3);
        body.cubicTo(-32, -17, 29, 13, 33, -35);
        a.stroke(body, 43, 0xFF608360); a.stroke(body, 35, 0xFF97B883);
        Path shine = new Path(); shine.moveTo(-90, 38); shine.cubicTo(-25, 65, 40, 57, 19, 42);
        a.stroke(shine, 8, 0xFFC5D8A8);
        a.oval(-20, -75, 107, 89, 0xFF618665);
        a.oval(-24, -79, 107, 87, 0xFFA6C68E);
        a.oval(9, -22, 67, 24, 0xFFD7E5B9);
        a.oval(-9, -27, 20, 11, 0xFFE4B7A0); a.oval(65, -28, 18, 10, 0xFFE4B7A0);
        boolean blink = state.sleeping || time % 5 > 4.78;
        if (blink) {
            a.line(5, -43, 19, -43, 4, 0xFF334B35); a.line(51, -43, 65, -43, 4, 0xFF334B35);
        } else {
            a.oval(8, -54, 10, 17, 0xFF334B35); a.oval(52, -54, 10, 17, 0xFF334B35);
            a.circle(11, -50, 2, 0xFFFFFFFF); a.circle(55, -50, 2, 0xFFFFFFFF);
        }
        Path smile = new Path(); smile.moveTo(26, -22); smile.quadTo(34, -14, 43, -23);
        a.stroke(smile, 3, 0xFF4F6C4D);
        c.save(); c.translate(-8, -78); c.rotate(-24);
        a.leaf(0, 0, 13, 27, 0xFF638953); a.line(0, 0, 0, -22, 2, 0xFFADC48B); c.restore();
        if (reaction > 0) { a.heart(-49, -102 - reaction * 18, 12, 0xFFD89887); a.heart(91, -94 - reaction * 24, 8, 0xFFE2B36E); }
        if (state.sleeping) { a.text("z", 85, -77, 24, 0xFF8190A1, true); a.text("Z", 104, -108, 30, 0xFFA6AFBA, true); }
        c.restore();
    }
}
