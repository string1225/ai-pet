package com.string.aipet.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Bitmap;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;
import com.string.aipet.model.PetState;
import java.util.ArrayList;
import java.util.List;

/** The pet's illustrated home. Furniture is interactive, including accessible virtual buttons. */
public final class RoomView extends View {
    public interface Listener { void onInteraction(String action); }
    private final PetState state;
    private final Listener listener;
    private final List<Target> targets = new ArrayList<>();
    private final Art art = new Art(null);
    private final Path roomClip = new Path();
    private final Bitmap scenery;
    private String furnishingStyle="";
    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private float scale = 1, offsetX, offsetY;
    private boolean running, playing;
    private int ballRound;
    private long reactionUntil, messageUntil;
    private String message = "";
    private static final float[][] BALLS = {{655, 477}, {385, 495}, {643, 405}};
    private static final class Target {
        final String action, label; final RectF bounds;
        Target(String action, String label, float x, float y, float w, float h) {
            this.action = action; this.label = label; bounds = new RectF(x, y, x + w, y + h);
        }
    }
    public RoomView(Context context, PetState state, Listener listener) {
        super(context); this.state = state; this.listener = listener;
        roomClip.addRoundRect(new RectF(0, 0, 1000, 600), 28, 28, Path.Direction.CW);
        scenery = Bitmap.createBitmap(1000, 600, Bitmap.Config.ARGB_8888);
        updateScenery();
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setFocusable(true); setContentDescription("宠物的家，有书架、小床、点心碗、小球和清洗盆");
        updateTargets();
    }
    public void setRunning(boolean value) { running = value; if (value) invalidate(); }
    public void refresh() { updateScenery(); updateTargets(); invalidate(); sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED); }
    private void updateScenery(){
        String style=state.equippedDecorations.contains("cloud_rug")+":"+state.equippedDecorations.contains("star_garland")+":"+state.equippedDecorations.contains("flower_planter");
        if(!style.equals(furnishingStyle)){furnishingStyle=style;drawFurnishings(new Canvas(scenery));}
    }
    public void react(String text) {
        reactionUntil = SystemClock.uptimeMillis() + 1800; message(text);
    }
    public void message(String text) {
        message = text; messageUntil = SystemClock.uptimeMillis() + 4300; invalidate();
        announceForAccessibility(text);
    }
    public void startPlay() { playing = true; ballRound = 0; updateTargets(); invalidate(); }
    public boolean isPlaying() { return playing; }
    public void cancelPlay() { playing = false; ballRound = 0; updateTargets(); invalidate(); }
    private void updateTargets() {
        targets.clear();
        targets.add(new Target("pet", state.hatched() ? "摸摸" + state.name : "神秘的蛋，一起孵化", 385, 320, 220, 205));
        targets.add(new Target("feed", "点心碗，喂食", 701, 467, 135, 93));
        targets.add(new Target("learn", "绘本书架，学习", 56, 170, 200, 213));
        targets.add(new Target("sleep", "软软小床，休息", 42, 383, 256, 139));
        float[] ball = BALLS[playing ? Math.min(ballRound, 2) : 0];
        targets.add(new Target("ball", playing ? "闪闪的小球，第" + (ballRound + 1) + "回合" : "玩具小球，陪玩", ball[0] - 37, ball[1] - 37, 74, 74));
        targets.add(new Target("plant", "窗边绿植，浇水", 842, 258, 99, 220));
        targets.add(new Target("clean", "泡泡清洗盆，清洗", 862, 481, 115, 70));
    }
    private void activate(Target target) {
        if (playing && target.action.equals("ball")) {
            ballRound++; react("追到小球啦！");
            if (ballRound == 3) { playing = false; listener.onInteraction("play_complete"); }
            updateTargets(); invalidate(); return;
        }
        listener.onInteraction(target.action);
    }
    @Override public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_UP) {
            float x = (e.getX() - offsetX) / scale, y = (e.getY() - offsetY) / scale;
            // Ball takes precedence when it passes in front of the pet.
            for (int i = targets.size() - 1; i >= 0; i--) if (targets.get(i).bounds.contains(x, y)) { activate(targets.get(i)); performClick(); return true; }
        }
        return true;
    }
    @Override public boolean performClick() { super.performClick(); return true; }
    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        scale = Math.min(getWidth() / 1000f, getHeight() / 600f);
        offsetX = (getWidth() - 1000 * scale) / 2; offsetY = (getHeight() - 600 * scale) / 2;
        c.save(); c.translate(offsetX, offsetY); c.scale(scale, scale);
        c.clipPath(roomClip);
        c.drawBitmap(scenery, 0, 0, backgroundPaint);
        Art a = art.use(c);
        long now = SystemClock.uptimeMillis(); float time = now / 1000f;
        if (state.hatched()) {
            c.save();
            if (state.sleeping) { c.translate(163, 453); c.scale(.68f, .68f); }
            else c.translate(488, 446);
            float reaction = Math.max(0, (reactionUntil - now) / 1800f);
            RendererRegistry.get(state.speciesId).draw(c, state, 0, 0, time, reaction); c.restore();
        } else egg(c, a, time);
        float[] ball = BALLS[playing ? Math.min(ballRound, 2) : 0];
        ball(a, ball[0], ball[1], time);
        if(reactionUntil>now&&message.startsWith("泡泡")){
            for(int i=0;i<7;i++){float rise=(time*35+i*23)%120;a.circle(410+i*28,440-rise,8+i%3*3,0xA6E6F8F2);a.circle(407+i*28,437-rise,2,0xDDFFFFFF);}
        }
        if (playing) {
            a.rect(293, 22, 419, 50, 25, 0xFFFDFBF3);
            a.text("找到小球 " + Math.min(ballRound + 1, 3) + " / 3 · 点点闪闪的小球", 502, 54, 19, 0xFF607256, true);
        } else if (messageUntil > now) {
            float width = Math.min(640, Math.max(250, message.length() * 19f + 40));
            a.rect(500 - width / 2, 21, width, 51, 25, 0xFFFDFBF3);
            a.text(message, 500, 54, 19, 0xFF607256, true);
        }
        if (state.sleeping) a.rect(0, 0, 1000, 600, 28, 0x20364B63);
        a.rect(32, 551, 145, 29, 14, 0x80FFFCF4); a.circle(49, 566, 5, 0xFF8DA476);
        a.text("温暖小屋 · 晴", 109, 571, 14, 0xFF7A7D65, false);
        c.restore();
        if (running && isShown()) postInvalidateDelayed(33);
    }
    /** Furniture is drawn once, so animation only redraws the pet and interactive effects. */
    private void drawFurnishings(Canvas c) {
        Art a = new Art(c);
        a.rect(0, 0, 1000, 600, 28, 0xFFF0E9D9);
        a.rect(0, 371, 1000, 229, 0, 0xFFE5D4B9);
        a.rect(0, 363, 1000, 14, 0, 0xFFCEBBA0);
        a.rect(0, 374, 1000, 8, 0, 0xFFF8ECDA);
        for (int y = 423; y < 600; y += 59) {
            a.line(0, y, 1000, y, 2, 0xFFD6C2A4);
            for (int x = y % 2 == 0 ? 70 : 195; x < 1000; x += 260) a.line(x, y - 56, x, y, 1.2f, 0xFFD6C2A4);
        }
        // Subtle wallpaper dots and a warm stripe along the ceiling.
        for (int x = 22; x < 1000; x += 42) for (int y = 30; y < 353; y += 42) a.circle(x, y, 1.5f, 0xFFE0D9C8);
        a.rect(0, 0, 1000, 13, 0, 0xFFD9D4BF);
        window(c, a); bookshelf(a); picture(a); plant(c, a);
        // Reading lamp and small round side table.
        a.oval(694, 433, 103, 20, 0x206D604B);
        a.line(718, 396, 711, 436, 9, 0xFFAF8B61); a.line(770, 396, 777, 436, 9, 0xFFAF8B61);
        a.oval(692, 374, 103, 33, 0xFFA98157); a.oval(692, 368, 103, 30, 0xFFD3B488);
        a.line(738, 359, 738, 269, 7, 0xFF9F8462); a.oval(716, 352, 45, 12, 0xFFB39B77);
        Path shade = new Path(); shade.moveTo(708, 265); shade.lineTo(768, 265); shade.lineTo(782, 304); shade.lineTo(694, 304); shade.close();
        a.path(shade, 0xFFECC77D); a.oval(694, 299, 88, 14, 0xFFF8DFA9);
        // Rug with stitched border.
        boolean cloud=state.equippedDecorations.contains("cloud_rug");
        a.oval(305, 412, 381, 152, cloud?0xFF8EBBC9:0xFFBAC3A0); a.oval(319, 422, 353, 130, cloud?0xFFD4EAF0:0xFFE5E7CC);
        for (int i = 0; i < 40; i++) {
            double angle = Math.PI * i / 20;
            float x = 496 + (float)Math.cos(angle) * 173, y = 487 + (float)Math.sin(angle) * 61;
            a.circle(x, y, 1.9f, cloud?0xFF8DB4C4:0xFFB6BF9C);
        }
        if(cloud)for(int i=0;i<3;i++){float x=373+i*105;a.oval(x,477+i%2*23,52,16,0xFFFCFEFF);a.circle(x+18,475+i%2*23,12,0xFFFCFEFF);a.circle(x+34,478+i%2*23,9,0xFFFCFEFF);}
        bed(a); bowl(a);washBasin(a);
        if(state.equippedDecorations.contains("star_garland")){
            Path cord=new Path();cord.moveTo(278,43);cord.quadTo(483,105,688,43);a.stroke(cord,2,0xFFB79A5A);
            for(int i=0;i<7;i++){float x=294+i*63;float y=52+(float)Math.sin(i*Math.PI/6)*28;a.line(x,y-3,x,y+10,2,0xFFB79A5A);a.star(x,y+19,10,0xFFE4BC5E);}
        }
        if(state.equippedDecorations.contains("flower_planter")){
            for(int i=0;i<4;i++){float x=865+i*19,y=320+i%2*38;for(int petal=0;petal<5;petal++){double angle=petal*Math.PI*2/5;a.circle(x+(float)Math.cos(angle)*9,y+(float)Math.sin(angle)*9,7,0xFFE4A6B1);}a.circle(x,y,5,0xFFF8D888);}
        }
    }
    private void washBasin(Art a){
        a.oval(864,535,112,14,0x1F6C5844);a.rect(870,505,99,34,16,0xFF91B8BE);a.oval(868,496,103,25,0xFFCADFE0);a.oval(877,501,84,13,0xFF9BCDD3);
        a.circle(894,493,9,0xFFF1FAF5);a.circle(909,487,12,0xFFF5FDFA);a.circle(927,492,10,0xFFF1FAF5);a.circle(941,498,7,0xFFF5FDFA);a.circle(939,477,4,0xFFEAF9F6);
    }
    private void window(Canvas c, Art a) {
        a.rect(346, 73, 281, 260, 120, 0xFFD3BE98); a.rect(355, 78, 263, 244, 116, 0xFFF8F2DF);
        Path shape = new Path(); shape.addRoundRect(new RectF(364, 86, 609, 309), new float[]{110,110,110,110,7,7,7,7}, Path.Direction.CW);
        c.save(); c.clipPath(shape); a.rect(364, 86, 245, 223, 0, 0xFFC9DEE0);
        a.circle(556, 137, 26, 0xFFFBE8A6);
        a.oval(369, 139, 77, 19, 0xFFEFF5EC); a.oval(406, 125, 47, 34, 0xFFEFF5EC);
        a.oval(484, 181, 87, 20, 0xFFEAF2E9);
        Path hill = new Path(); hill.moveTo(355, 284); hill.quadTo(411, 210, 500, 286); hill.quadTo(560, 232, 632, 274); hill.lineTo(632, 321); hill.lineTo(355, 321); hill.close();
        a.path(hill, 0xFFAFC6A8);
        hill.reset(); hill.moveTo(355, 313); hill.quadTo(518, 236, 636, 311); hill.lineTo(636, 326); hill.lineTo(355, 326); hill.close(); a.path(hill, 0xFF98B69B);
        a.line(486, 86, 486, 310, 9, 0xFFF7F1DE); a.line(361, 233, 615, 233, 8, 0xFFF7F1DE); c.restore();
        a.rect(334, 315, 305, 18, 7, 0xFFC7AC82); a.rect(334, 315, 305, 7, 4, 0xFFE3CCA5);
        // Curtains.
        a.rect(316, 108, 30, 184, 10, 0xFFD7DDC0); a.rect(623, 108, 30, 184, 10, 0xFFD7DDC0);
        a.line(331, 118, 331, 280, 3, 0xFFBEC9AA); a.line(638, 118, 638, 280, 3, 0xFFBEC9AA);
    }
    private void bookshelf(Art a) {
        a.oval(62, 365, 199, 20, 0x156C5E43);
        a.rect(63, 178, 191, 192, 12, 0xFFB79B75); a.rect(73, 187, 172, 169, 6, 0xFFE1C9A5);
        int[] colors = {0xFF99B59B,0xFFE0A088,0xFF9BAECC,0xFFE0C180,0xFFADAF87};
        for (int i = 0; i < 5; i++) {
            float h = 59 + (i % 2) * 13;
            a.rect(85 + i * 27, 258 - h, 21, h, 3, colors[i]); a.line(90 + i * 27, 248 - h, 100 + i * 27, 248 - h, 2, 0x80FFFFFF);
        }
        a.rect(71, 257, 174, 10, 3, 0xFFB39975);
        a.rect(87, 290, 62, 13, 3, 0xFFE0A088); a.rect(84, 305, 66, 13, 3, 0xFFADC3AA);
        a.rect(87, 320, 62, 13, 3, 0xFFDBB877);
        a.rect(179, 291, 48, 46, 8, 0xFFF2E9D4); a.star(202, 312, 12, 0xFFE2BC70);
        a.line(75, 370, 73, 385, 7, 0xFF9F835E); a.line(239, 370, 241, 385, 7, 0xFF9F835E);
        a.rect(109, 128, 49, 48, 5, 0xFFD3AE88); a.rect(116, 135, 35, 32, 2, 0xFFFAF2DB); a.heart(134, 152, 9, 0xFFC79A87);
        a.oval(192, 163, 38, 12, 0xFFB29D73); a.rect(196, 140, 30, 28, 8, 0xFFB9C6A3); a.leaf(211, 141, 11, 28, 0xFF859D78);
    }
    private void bed(Art a) {
        a.oval(38, 483, 259, 24, 0x206D604B);
        a.rect(52, 440, 233, 54, 17, 0xFFB19B81); a.rect(50, 422, 237, 60, 18, 0xFFE5DBBF);
        a.rect(57, 416, 223, 53, 21, 0xFFF5EEDB); a.rect(65, 392, 80, 49, 17, 0xFFB8BFD3);
        a.rect(150, 424, 115, 44, 12, 0xFFC7CCB0);
        for(int x = 166; x < 260; x += 23) a.line(x, 429, x, 462, 2, 0xFFB2BA98);
        a.line(66, 478, 66, 504, 8, 0xFFAB8E69); a.line(270, 478, 270, 504, 8, 0xFFAB8E69);
    }
    private void bowl(Art a) {
        a.oval(696, 529, 135, 15, 0x1F6C5844);
        a.rect(699, 493, 76, 36, 14, 0xFFC38F74); a.oval(699, 484, 76, 24, 0xFFE9B799); a.oval(707, 488, 59, 14, 0xFFAE7C5B);
        a.circle(722, 492, 6, 0xFFD7B36A); a.circle(735, 491, 6, 0xFFB79752); a.circle(748, 493, 6, 0xFFD8C084);
        a.rect(786, 506, 59, 26, 11, 0xFFA1B7BA); a.oval(786, 499, 59, 18, 0xFFD1E2DF); a.oval(793, 502, 44, 9, 0xFF8EBABC);
        a.heart(737, 516, 6, 0xFFF1D3BB);
    }
    private void picture(Art a) {
        a.line(739, 130, 773, 103, 2, 0xFFBEAC8D); a.line(809, 130, 773, 103, 2, 0xFFBEAC8D);
        a.rect(728, 126, 94, 102, 5, 0xFFC4A67D); a.rect(736, 134, 78, 86, 2, 0xFFFAF3E2);
        a.circle(776, 166, 16, 0xFFE6C176); a.leaf(769, 206, 15, 30, 0xFF9DAF85); a.leaf(785, 207, 13, 24, 0xFFBEC9A4);
        a.line(770, 209, 776, 179, 2, 0xFF8FA079);
    }
    private void plant(Canvas c, Art a) {
        a.oval(840, 446, 108, 20, 0x2363563F);
        a.rect(856, 391, 74, 67, 19, 0xFFC59E7D); a.rect(849, 384, 88, 21, 7, 0xFFD8B594);
        a.line(891, 383, 891, 289, 5, 0xFF75916C);
        for (int i = 0; i < 5; i++) {
            c.save(); c.translate(891, 380 - i * 18); c.rotate(i % 2 == 0 ? -50 : 52);
            a.leaf(0, 0, 21, 54, i % 2 == 0 ? 0xFF91AC84 : 0xFFADC49B); c.restore();
        }
        a.leaf(892, 302, 17, 48, 0xFF94B48B);
        a.rect(897, 9, 38, 41, 8, 0xFFD1B99A); a.line(914, 0, 914, 20, 3, 0xFFB8A17D);
        for(int i=0;i<7;i++) { a.line(908, 47 + i*17, 914, 67 + i*17, 3, 0xFF9BAC7C); a.oval(895 + i%2*11, 48+i*17, 19, 12, 0xFFB3C39A); }
    }
    private void egg(Canvas c, Art a, float time) {
        float sway = (float)Math.sin(time * 2.3) * (2 + state.warmth / 20f);
        c.save(); c.translate(495, 426); c.rotate(sway);
        a.oval(-65, 57, 134, 20, 0x1A576047);
        a.oval(-61, -90, 122, 161, 0xFFBEAF86); a.oval(-58, -94, 116, 159, 0xFFF9F1D3);
        a.oval(-43, -67, 22, 50, 0xFFFFFAE6);
        a.oval(-37, -22, 21, 31, 0xFFBFCBA5); a.oval(20, 6, 19, 27, 0xFFBFCBA5); a.oval(3, -61, 16, 21, 0xFFD8C99D);
        if (state.warmth > 0) {
            Path crack = new Path(); crack.moveTo(-35, -36); crack.lineTo(-7, -20); crack.lineTo(-22, -1); crack.lineTo(7, 13);
            if(state.warmth>=50){crack.lineTo(-1,35);crack.lineTo(20,54);} a.stroke(crack, 3, 0xFFAD9E78);
        }
        a.heart(61, -85, 10, 0xFFD7A391); c.restore();
        a.rect(390, 516, 208, 34, 17, 0xFFF9F5E9); a.text("神秘的蛋 · 温暖 " + state.warmth + "%", 494, 539, 17, 0xFF7B8762, true);
    }
    private void ball(Art a, float x, float y, float time) {
        a.oval(x - 24, y + 22, 49, 10, 0x236D604B);
        if (playing) { a.circle(x, y, 38 + (float)Math.sin(time * 5) * 3, 0x45FFF9CB); a.star(x + 31, y - 29, 9, 0xFFE4B65F); }
        a.circle(x, y, 26, 0xFFC78A75); a.circle(x - 2, y - 3, 25, 0xFFDB9D86);
        a.line(x - 22, y - 5, x + 21, y - 5, 6, 0xFFF1D6AB); a.line(x - 1, y - 26, x - 1, y + 24, 6, 0xFFEBCA9F);
        a.oval(x - 13, y - 16, 10, 7, 0x77FFFFFF);
    }
    @Override public AccessibilityNodeProvider getAccessibilityNodeProvider() {
        return new AccessibilityNodeProvider() {
            @Override public AccessibilityNodeInfo createAccessibilityNodeInfo(int id) {
                if (id == View.NO_ID) {
                    AccessibilityNodeInfo info = AccessibilityNodeInfo.obtain(RoomView.this);
                    onInitializeAccessibilityNodeInfo(info);
                    for(int i=0;i<targets.size();i++) info.addChild(RoomView.this, i + 1);
                    return info;
                }
                if(id<1 || id>targets.size()) return null;
                Target target = targets.get(id - 1); AccessibilityNodeInfo info = AccessibilityNodeInfo.obtain();
                info.setSource(RoomView.this, id); info.setParent(RoomView.this);
                info.setPackageName(getContext().getPackageName()); info.setClassName("android.widget.Button");
                info.setContentDescription(target.label); info.setText(target.label);
                info.setEnabled(true); info.setClickable(true); info.setFocusable(true); info.setVisibleToUser(isShown());
                Rect bounds = mappedBounds(target.bounds); info.setBoundsInParent(bounds);
                int[] loc = new int[2]; getLocationOnScreen(loc); bounds.offset(loc[0],loc[1]); info.setBoundsInScreen(bounds);
                info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK);
                info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_ACCESSIBILITY_FOCUS);
                return info;
            }
            @Override public boolean performAction(int id, int action, Bundle arguments) {
                if(id<1 || id>targets.size()) return false;
                if(action==AccessibilityNodeInfo.ACTION_CLICK) { activate(targets.get(id-1)); return true; }
                if(action==AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS) {
                    AccessibilityEvent event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED);
                    event.setSource(RoomView.this,id); event.setPackageName(getContext().getPackageName());
                    event.setClassName("android.widget.Button"); event.getText().add(targets.get(id-1).label);
                    getParent().requestSendAccessibilityEvent(RoomView.this,event); return true;
                }
                return false;
            }
        };
    }
    private Rect mappedBounds(RectF r) {
        return new Rect((int)(offsetX+r.left*scale),(int)(offsetY+r.top*scale),
                (int)(offsetX+r.right*scale),(int)(offsetY+r.bottom*scale));
    }
}
