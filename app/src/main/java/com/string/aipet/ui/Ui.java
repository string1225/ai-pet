package com.string.aipet.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class Ui {
    public static final int INK=0xFF3D4F40, MUTED=0xFF85897C, GREEN=0xFF6F8A65, BG=0xFFF8F6EF;
    public static final int KNOWLEDGE_GOLD=0xFF926819, GROWTH_GREEN=0xFF3E754B;
    private Ui() {}
    public static int dp(Context context, float value) { return Math.round(value*context.getResources().getDisplayMetrics().density); }
    public static GradientDrawable shape(int color, float radius, Context c) {
        GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(c,radius)); return d;
    }
    public static TextView text(Context c,String value,float size,int color,boolean bold) {
        TextView t=new TextView(c); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        t.setFontFeatureSettings("kern"); t.setIncludeFontPadding(false);
        if(bold)t.setTypeface(android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL));
        return t;
    }
    public static LinearLayout column(Context c) { LinearLayout l=new LinearLayout(c); l.setOrientation(LinearLayout.VERTICAL); return l; }
    public static LinearLayout row(Context c) { LinearLayout l=new LinearLayout(c); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l; }
    public static Button button(Context c,String title,int color,Runnable click) {
        Button b=new Button(c); b.setText(title); b.setTextSize(17); b.setAllCaps(false); b.setTextColor(color==GREEN?0xFFFFFFFF:INK);
        b.setMinHeight(dp(c,48)); b.setMinimumHeight(dp(c,48)); b.setStateListAnimator(null);
        b.setPadding(dp(c,18),dp(c,9),dp(c,18),dp(c,9));
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x226F8A65),shape(color,16,c),null));
        b.setOnClickListener(v->click.run()); return b;
    }
    public static void gap(LinearLayout parent,int height) { View v=new View(parent.getContext()); parent.addView(v,new LinearLayout.LayoutParams(1,dp(parent.getContext(),height))); }
    public static class Icon extends View {
        private final String kind; private final int color;
        public Icon(Context c,String kind,int color){super(c);this.kind=kind;this.color=color;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onDraw(Canvas c){
            float s=Math.min(getWidth(),getHeight())/48f; c.save();c.scale(s,s);Art a=new Art(c);
            a.circle(24,24,23,color);
            int ink=0xFF687B5C;
            switch(kind){
                case "food": a.rect(12,23,24,12,6,ink);a.oval(12,19,24,10,0xFFF5F1DC);a.circle(18,22,3,0xFFD7A46A);a.circle(26,21,3,0xFF93A878);a.circle(30,23,3,0xFFD2AF64); break;
                case "play":a.circle(24,24,12,0xFFC78872);a.line(13,23,35,23,3,0xFFF8E2BF);a.line(23,13,23,35,3,0xFFF8E2BF);break;
                case "clean":a.rect(11,25,27,12,7,0xFF86B7BA);a.circle(19,22,6,0xFFEAF9F6);a.circle(29,19,8,0xFFF5FFFD);a.circle(17,12,3,0xFFF5FFFD);break;
                case "book":a.rect(12,12,25,27,4,ink);a.rect(15,12,3,27,1,0xFFD5DBC2);a.line(22,20,31,20,2,0xFFF6F3DC);a.line(22,25,29,25,2,0xFFF6F3DC);break;
                case "sleep":a.circle(24,23,13,ink);a.circle(30,17,12,color);a.star(34,32,4,0xFFD4AE6E);break;
                case "egg":a.oval(14,9,22,31,0xFFF7EDD1);a.oval(18,19,5,7,0xFF9CAF7D);a.oval(26,28,5,7,0xFFBBC89F);break;
                case "star":a.star(24,24,12,0xFFBC9554);break;
                default:a.leaf(24,33,12,27,ink);a.line(24,34,24,14,2,0xFFD9E3CA);
            }
            c.restore();
        }
    }
}
