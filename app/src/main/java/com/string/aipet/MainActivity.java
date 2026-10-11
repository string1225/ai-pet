package com.string.aipet;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Build;
import android.speech.tts.TextToSpeech;
import android.text.InputFilter;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import com.string.aipet.learning.Question;
import com.string.aipet.learning.QuestionBank;
import com.string.aipet.learning.Subject;
import com.string.aipet.model.PetDefinition;
import com.string.aipet.model.PetEngine;
import com.string.aipet.model.PetRegistry;
import com.string.aipet.model.PetState;
import com.string.aipet.model.PetStore;
import com.string.aipet.ui.Art;
import com.string.aipet.ui.RoomView;
import com.string.aipet.ui.SnakeRenderer;
import com.string.aipet.ui.RendererRegistry;
import com.string.aipet.ui.Ui;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity {
    private PetStore store;
    private PetState state;
    private PetEngine engine;
    private final QuestionBank bank=new QuestionBank();
    private RoomView room;
    private TextView petName, level, starCount, roomTitle, hint, tipTitle;
    private final TextView[] statLabels=new TextView[3];
    private final ProgressBar[] statBars=new ProgressBar[3];
    private TextToSpeech voice;
    private boolean voiceReady;
    private Dialog activeDialog;
    private Lesson currentLesson;
    private Button careButton;
    private View petAvatar;
    private boolean wideHome;
    private android.window.OnBackInvokedCallback modernBack;
    private long lastPetTap;
    private final android.os.Handler clock=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable updateClock=new Runnable(){public void run(){engine.tick(System.currentTimeMillis());save();clock.postDelayed(this,60000);}};

    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        store=new PetStore(this); state=store.load(); engine=new PetEngine(state); engine.tick(System.currentTimeMillis());
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        buildHome();
        voice=new TextToSpeech(this,status->{voiceReady=status==TextToSpeech.SUCCESS;if(voiceReady)voice.setLanguage(Locale.SIMPLIFIED_CHINESE);});
    }
    private int dp(float value){return Ui.dp(this,value);}
    private LinearLayout.LayoutParams lp(int width,int height){return new LinearLayout.LayoutParams(width<0?width:dp(width),height<0?height:dp(height));}
    private TextView text(String value,float size,int color,boolean bold){return Ui.text(this,value,size,color,bold);}
    private String petSpecies(){return PetRegistry.get(state.speciesId).species;}
    private void buildHome(){
        boolean wide=getResources().getConfiguration().screenWidthDp>=840;wideHome=wide;
        LinearLayout root=Ui.column(this);root.setBackgroundColor(Ui.BG);root.setPadding(dp(wide?28:16),dp(12),dp(wide?28:16),dp(8));
        root.setOnApplyWindowInsetsListener((v,insets)->{
            if(Build.VERSION.SDK_INT>=30){
                android.graphics.Insets safe=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
                v.setPadding(dp(wide?28:16)+safe.left,dp(12)+safe.top,dp(wide?28:16)+safe.right,dp(8)+safe.bottom);
            }
            return insets;
        });
        LinearLayout header=Ui.row(this);root.addView(header,lp(-1,72));
        Ui.Icon brand=new Ui.Icon(this,"leaf",0xFFE5EBD9);header.addView(brand,lp(48,48));
        LinearLayout heading=Ui.column(this);LinearLayout.LayoutParams headingLp=new LinearLayout.LayoutParams(0,-2,1);headingLp.setMargins(dp(14),0,0,0);header.addView(heading,headingLp);
        heading.addView(text("芽芽小屋",27,Ui.INK,true));Ui.gap(heading,5);heading.addView(text("把每一天，养成小小的快乐。",13,Ui.MUTED,false));
        starCount=text("",16,0xFF9C7B42,true);starCount.setPadding(dp(17),dp(12),dp(17),dp(12));starCount.setBackground(Ui.shape(0xFFF1E8D3,20,this));header.addView(starCount,lp(-2,-2));
        Button journal=Ui.button(this,"成长记录",0xFFEEEEE3,this::showJournal);LinearLayout.LayoutParams jp=lp(-2,48);jp.setMargins(dp(12),0,dp(8),0);header.addView(journal,jp);
        header.addView(Ui.button(this,"设置",0xFFEEEEE3,this::showSettings),lp(-2,48));
        LinearLayout body=wide?Ui.row(this):Ui.column(this);LinearLayout.LayoutParams bodyLp=new LinearLayout.LayoutParams(-1,0,1);bodyLp.setMargins(0,dp(12),0,dp(12));root.addView(body,bodyLp);
        LinearLayout sidebar=Ui.column(this);sidebar.setPadding(dp(20),dp(20),dp(20),dp(20));sidebar.setBackground(Ui.shape(0xFFEFF0E5,24,this));
        ScrollView sideScroll=new ScrollView(this);sideScroll.setFillViewport(true);sideScroll.setVerticalScrollBarEnabled(false);sideScroll.addView(sidebar);
        if(wide)body.addView(sideScroll,lp(210,-1));else body.addView(sideScroll,lp(-1,120));
        sidebar.addView(text("我的小伙伴",13,Ui.MUTED,true));Ui.gap(sidebar,10);
        View avatar=new View(this){@Override protected void onDraw(Canvas c){
            float s=getHeight()/118f;c.save();c.translate(getWidth()/2f,getHeight()/2f);c.scale(s,s);Art a=new Art(c);a.circle(0,0,54,0xFFE0E6D2);
            if(state.hatched()){c.scale(.49f,.49f);RendererRegistry.get(state.speciesId).draw(c,state,-4,-4,0,0);}else{a.oval(-25,-36,50,69,0xFFF7EFD3);a.oval(-13,-10,10,14,0xFFB6C899);a.oval(6,10,9,12,0xFFB6C899);}c.restore();}};
        petAvatar=avatar;sidebar.addView(avatar,lp(-1,108));
        petName=text("",23,Ui.INK,true);petName.setGravity(Gravity.CENTER);sidebar.addView(petName,lp(-1,-2));Ui.gap(sidebar,7);
        level=text("",12,0xFF829074,false);level.setGravity(Gravity.CENTER);sidebar.addView(level,lp(-1,-2));Ui.gap(sidebar,22);
        String[] labels={"饱腹","心情","精力"};int[] colors={0xFFD5B176,0xFFB0BE91,0xFFA8BAD0};
        for(int i=0;i<3;i++){
            statLabels[i]=text(labels[i],13,Ui.MUTED,false);sidebar.addView(statLabels[i],lp(-1,-2));Ui.gap(sidebar,7);
            ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(100);
            bar.setProgressTintList(android.content.res.ColorStateList.valueOf(colors[i]));bar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFE0E3D6));
            statBars[i]=bar;sidebar.addView(bar,lp(-1,8));Ui.gap(sidebar,16);
        }
        Ui.gap(sidebar,3);tipTitle=text("",14,Ui.INK,true);sidebar.addView(tipTitle);Ui.gap(sidebar,8);
        hint=text("",12,Ui.MUTED,false);hint.setLineSpacing(dp(4),1);sidebar.addView(hint);
        if(!wide){
            // A compact portrait profile keeps the whole pet and all three care values visible.
            body.removeView(sideScroll);LinearLayout compact=Ui.row(this);compact.setPadding(dp(14),dp(10),dp(14),dp(10));
            compact.setBackground(Ui.shape(0xFFEFF0E5,24,this));body.addView(compact,0,lp(-1,112));
            sidebar.removeView(avatar);compact.addView(avatar,lp(82,82));
            LinearLayout identity=Ui.column(this);LinearLayout.LayoutParams identityLp=lp(190,-2);identityLp.setMargins(dp(12),0,dp(14),0);compact.addView(identity,identityLp);
            sidebar.removeView(petName);sidebar.removeView(level);petName.setTextSize(21);petName.setGravity(Gravity.START);level.setTextSize(11);level.setGravity(Gravity.START);
            identity.addView(petName);Ui.gap(identity,8);identity.addView(level);
            for(int i=0;i<3;i++){
                LinearLayout care=Ui.column(this);care.setGravity(Gravity.CENTER);LinearLayout.LayoutParams careLp=new LinearLayout.LayoutParams(0,-2,1);careLp.setMargins(dp(7),0,dp(7),0);compact.addView(care,careLp);
                sidebar.removeView(statLabels[i]);sidebar.removeView(statBars[i]);statLabels[i].setGravity(Gravity.CENTER);statLabels[i].setLineSpacing(dp(5),1);
                care.addView(statLabels[i],lp(-1,-2));Ui.gap(care,9);care.addView(statBars[i],lp(-1,8));
            }
        }
        LinearLayout house=Ui.column(this);LinearLayout.LayoutParams houseLp=wide?new LinearLayout.LayoutParams(0,-1,1):new LinearLayout.LayoutParams(-1,0,1);
        houseLp.setMargins(dp(wide?24:0),dp(wide?0:10),0,0);body.addView(house,houseLp);
        LinearLayout houseHeading=Ui.row(this);house.addView(houseHeading,lp(-1,52));
        LinearLayout houseWords=Ui.column(this);houseHeading.addView(houseWords,new LinearLayout.LayoutParams(0,-2,1));roomTitle=text("小蛇的家",21,Ui.INK,true);houseWords.addView(roomTitle);Ui.gap(houseWords,4);
        houseWords.addView(text("点一点小物件，发现新的快乐",12,Ui.MUTED,false));
        houseHeading.addView(Ui.button(this,"宠物图鉴",0xFFEEEFE4,this::showPets),lp(-2,46));
        room=new RoomView(this,state,this::interact);room.setRunning(true);house.addView(room,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout dock=Ui.row(this);root.addView(dock,lp(-1,88));
        careButton=Ui.button(this,"",Ui.GREEN,()->{if(!state.hatched())startIncubation();else showFood();});
        LinearLayout.LayoutParams first=new LinearLayout.LayoutParams(0,dp(72),1.2f);first.setMargins(0,0,dp(12),0);dock.addView(careButton,first);
        actionTile(dock,"play","陪玩","追追小球",0xFFF0E2D9,()->guard(this::startPlay));
        actionTile(dock,"book","学习","每天一点点",0xFFE6ECD9,()->guard(this::showLearning));
        actionTile(dock,"sleep","休息","做个甜甜的梦",0xFFE6E9EE,()->guard(this::toggleSleep));
        TextView footer=text("小小陪伴，大大成长",11,0xFFA1A391,false);footer.setGravity(Gravity.CENTER);root.addView(footer,lp(-1,21));
        setContentView(root);refresh();
    }
    private void actionTile(LinearLayout dock,String icon,String title,String subtitle,int color,Runnable action){
        boolean small=getResources().getConfiguration().screenWidthDp<700;
        LinearLayout tile=Ui.row(this);tile.setPadding(dp(small?10:16),dp(8),dp(10),dp(8));tile.setBackground(Ui.shape(0xFFF0EFE7,20,this));tile.setClickable(true);tile.setFocusable(true);tile.setContentDescription(title);tile.setOnClickListener(v->action.run());
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(72),1);p.setMargins(0,0,dp(12),0);dock.addView(tile,p);
        tile.addView(new Ui.Icon(this,icon,color),lp(small?34:44,small?34:44));LinearLayout words=Ui.column(this);LinearLayout.LayoutParams wp=new LinearLayout.LayoutParams(0,-2,1);wp.setMargins(dp(small?8:12),0,0,0);tile.addView(words,wp);
        words.addView(text(title,18,Ui.INK,true));if(!small){Ui.gap(words,5);words.addView(text(subtitle,11,Ui.MUTED,false));}
    }
    private void refresh(){
        if(room==null)return;boolean born=state.hatched();if(petAvatar!=null)petAvatar.invalidate();
        petName.setText(born?state.name:"等待小生命");level.setText(born?petSpecies()+"  ·  Lv."+state.level()+"  ·  相伴第 "+state.days(System.currentTimeMillis())+" 天":petSpecies()+"蛋  ·  温暖 "+state.warmth+"%");
        starCount.setText("★  "+state.stars+"  星星");careButton.setText(born?"喂食  ·  点心 "+state.snacks:"一起孵化  ·  "+state.warmth+"%");
        roomTitle.setText(born?state.name+"的家":"小生命的新家");int[] values={state.fullness,state.happiness,state.energy};String[] labels={"饱腹","心情","精力"};
        for(int i=0;i<3;i++){statLabels[i].setText(labels[i]+(wideHome?"                         ":"\n")+(born?values[i]:"—"));statBars[i].setProgress(born?values[i]:0);}
        tipTitle.setText(born?(state.sleeping?"嘘，"+state.name+"睡着啦":"今天的小发现"):"第一份陪伴");
        hint.setText(born?(state.sleeping?"点小床叫醒它。休息时，精力会慢慢恢复。":"已经收集 "+state.totalLearned()+" 个知识星星。\n完成一轮学习，还能获得 2 份点心。"):
                "用 4 个小知识给蛋加温，认识你的第一位小伙伴。");room.refresh();
    }
    private void save(){store.save(state);refresh();}
    private void guard(Runnable action){if(!state.hatched()){room.message("先给小蛋一点温暖吧");startIncubation();}else action.run();}
    private boolean awake(){if(state.sleeping){room.message(state.name+"正在做梦，先点小床叫醒它吧");return false;}return true;}
    private void interact(String action){
        switch(action){
            case "pet":if(!state.hatched()){startIncubation();return;}if(state.sleeping){room.message("嘘，等它睡醒再摸摸吧");return;}
                long now=android.os.SystemClock.uptimeMillis();if(now-lastPetTap>800){engine.pet();save();lastPetTap=now;}room.react(state.name+"喜欢你的轻轻抚摸");break;
            case "feed":guard(this::showFood);break;
            case "learn":guard(this::showLearning);break;
            case "sleep":guard(this::toggleSleep);break;
            case "ball":guard(this::startPlay);break;
            case "play_complete":startLesson(bank.session(Subject.PINYIN,1),"小球追到啦，拼一个小音节",()->{engine.play();save();room.react("玩得好开心！心情 +18");});break;
            case "plant":room.react("给绿植浇一点水，小屋也开心了");break;
        }
    }
    private LinearLayout panel(){LinearLayout p=Ui.column(this);p.setPadding(dp(28),dp(24),dp(28),dp(22));return p;}
    private Dialog sheet(String title,String subtitle,LinearLayout content){
        dismissDialog();currentLesson=null;Dialog dialog=new Dialog(this);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout outer=panel();outer.setBackground(Ui.shape(Ui.BG,28,this));
        LinearLayout heading=Ui.row(this);outer.addView(heading);heading.addView(text(title,25,Ui.INK,true),new LinearLayout.LayoutParams(0,-2,1));
        Button close=Ui.button(this,"返回",0xFFEBEEE2,dialog::dismiss);heading.addView(close,lp(-2,48));
        if(!subtitle.isEmpty()){Ui.gap(outer,10);TextView sub=text(subtitle,14,Ui.MUTED,false);sub.setLineSpacing(dp(3),1);outer.addView(sub);}
        Ui.gap(outer,18);outer.addView(content,new LinearLayout.LayoutParams(-1,-2));
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(false);scroll.addView(outer);scroll.setBackground(Ui.shape(Ui.BG,28,this));dialog.setContentView(scroll);
        Window window=dialog.getWindow();if(window!=null){window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));window.setDimAmount(.26f);window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);}
        dialog.setOnDismissListener(d->{if(activeDialog==dialog){activeDialog=null;currentLesson=null;}});dialog.show();
        if(window!=null){int w=Math.min(getResources().getDisplayMetrics().widthPixels-dp(56),dp(720));int h=getResources().getDisplayMetrics().heightPixels-dp(70);window.setLayout(w,ViewGroup.LayoutParams.WRAP_CONTENT);
            scroll.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.AT_MOST));if(scroll.getMeasuredHeight()>=h)window.setLayout(w,h);}
        activeDialog=dialog;return dialog;
    }
    private void dismissDialog(){if(activeDialog!=null){Dialog previous=activeDialog;activeDialog=null;previous.dismiss();}}
    private void startIncubation(){if(state.hatched())return;startLesson(bank.incubation(state.warmth),"一起孵化 · 用知识为小蛋加温",()->{save();room.react("欢迎你，"+state.name+"！");showHatched();});}
    private void showHatched(){
        LinearLayout p=Ui.column(this);TextView welcome=text("你好呀，我是你的"+petSpecies()+"！",26,Ui.INK,true);welcome.setGravity(Gravity.CENTER);p.addView(welcome);Ui.gap(p,14);
        TextView sub=text("每一次照顾和学习，都会让我们更亲近。",16,Ui.MUTED,false);sub.setGravity(Gravity.CENTER);p.addView(sub);Ui.gap(p,20);
        Button go=Ui.button(this,"带我回家",Ui.GREEN,()->{dismissDialog();room.react("点点我，或试试家里的小家具吧");});p.addView(go,lp(-1,54));sheet(petSpecies()+"出生啦！","你已经点亮了 4 个知识星星。",p);
    }
    private void showFood(){
        if(!awake())return;room.cancelPlay();LinearLayout p=Ui.column(this);
        addChoice(p,"浆果点心","消耗 1 份点心 · 饱腹 +22",0xFFF2E3D8,()->feed("浆果点心"));
        addChoice(p,"小虫饼干","消耗 1 份点心 · 饱腹 +22",0xFFEBECD8,()->feed("小虫饼干"));
        addChoice(p,"喝点清凉水","不用点心 · 精力 +3",0xFFE2EDEF,()->{engine.water();save();dismissDialog();room.react("咕嘟咕嘟，好清凉！");});
        addChoice(p,"做算术赚点心","3 道小题 · 完成后获得 2 份点心",0xFFF2EBD9,()->startLesson(bank.session(Subject.MATH,3),"小小厨房 · 数一数点心",null));
        sheet("今天吃点什么？","点心篮里还有 "+state.snacks+" 份。小伙伴喜欢童话里的点心。",p);
    }
    private void feed(String food){
        if(state.fullness>=100){dismissDialog();room.message("已经吃饱啦，陪我玩一会儿吧");return;}
        if(state.snacks<1){dismissDialog();startLesson(bank.session(Subject.MATH,3),"点心用完啦，一起赚两份",null);return;}
        if(engine.feed()){save();dismissDialog();room.react("啊呜，"+food+"真好吃！");}
    }
    private void startPlay(){
        if(!awake())return;if(state.energy<10){room.message("有点累啦，先去小床休息一下吧");return;}
        dismissDialog();room.startPlay();room.message("点点闪闪的小球，追到 3 次吧！");
    }
    private void toggleSleep(){
        room.cancelPlay();if(state.sleeping){engine.wake(System.currentTimeMillis());save();room.react("睡醒啦，新的一天也要开心！");}
        else{engine.sleep();save();room.message("晚安，休息时精力会慢慢恢复");}
    }
    private void showLearning(){
        if(!awake())return;room.cancelPlay();LinearLayout p=Ui.column(this);
        for(Subject s:Subject.values())addChoice(p,s.title,s.description+" · 3 题小练习",blend(s.color),()->startLesson(bank.session(s,3),s.title,null));
        sheet("一起发现新知识","不用着急，答错可以再试。每题 1 颗星星，每轮 2 份点心。",p);
    }
    private int blend(int color){return Color.rgb((Color.red(color)+255*3)/4,(Color.green(color)+255*3)/4,(Color.blue(color)+255*3)/4);}
    private void addChoice(LinearLayout p,String title,String subtitle,int color,Runnable click){
        LinearLayout choice=Ui.column(this);choice.setPadding(dp(20),dp(15),dp(20),dp(15));choice.setBackground(Ui.shape(color,17,this));
        choice.setClickable(true);choice.setFocusable(true);choice.setContentDescription(title);choice.setOnClickListener(v->click.run());
        choice.addView(text(title,19,Ui.INK,true));Ui.gap(choice,5);choice.addView(text(subtitle,13,Ui.MUTED,false));p.addView(choice,lp(-1,-2));Ui.gap(p,10);
    }
    private final class Lesson {
        final List<Question> questions;final String title;final Runnable after;int index;boolean answered;int earned;
        Lesson(List<Question> q,String title,Runnable after){questions=new ArrayList<>(q);this.title=title;this.after=after;}
    }
    private void startLesson(List<Question> questions,String title,Runnable after){
        if(questions.isEmpty())return;room.cancelPlay();currentLesson=new Lesson(questions,title,after);showQuestion(currentLesson);
    }
    private void showQuestion(Lesson lesson){
        Question q=lesson.questions.get(lesson.index);LinearLayout p=Ui.column(this);
        LinearLayout info=Ui.row(this);TextView tag=text(q.subject.shortName+"  ·  "+(lesson.index+1)+" / "+lesson.questions.size(),14,Ui.GREEN,true);info.addView(tag,new LinearLayout.LayoutParams(0,-2,1));
        info.addView(Ui.button(this,"听一听",0xFFE7ECDF,()->speak(q,true)),lp(-2,48));p.addView(info);Ui.gap(p,14);
        TextView prompt=text(q.prompt,q.subject==Subject.LITERACY?46:38,Ui.INK,true);prompt.setGravity(Gravity.CENTER);prompt.setPadding(0,dp(12),0,dp(10));p.addView(prompt,lp(-1,-2));
        TextView cue=text(q.cue,17,Ui.MUTED,false);cue.setGravity(Gravity.CENTER);p.addView(cue,lp(-1,-2));Ui.gap(p,20);
        List<Button> answers=new ArrayList<>();TextView feedback=text("点一点你的答案",16,Ui.MUTED,false);feedback.setGravity(Gravity.CENTER);feedback.setMinHeight(dp(42));
        Button next=Ui.button(this,lesson.index==lesson.questions.size()-1?"完成啦":"下一题",Ui.GREEN,()->{
            if(!lesson.answered)return;
            if(++lesson.index<lesson.questions.size()){lesson.answered=false;showQuestion(lesson);}
            else{engine.finishLesson();save();dismissDialog();currentLesson=null;if(lesson.after!=null)lesson.after.run();else showLessonResult(lesson);}
        });next.setVisibility(View.INVISIBLE);
        for(int row=0;row<2;row++){
            LinearLayout options=Ui.row(this);p.addView(options,lp(-1,68));
            for(int col=0;col<2;col++){
                String option=q.options.get(row*2+col);Button answer=Ui.button(this,option,0xFFEEEEE3,()->{});answer.setTextSize(q.subject==Subject.LITERACY?29:24);
                answer.setOnClickListener(v->{
                    if(lesson.answered)return;
                    if(q.matches(option)){
                        lesson.answered=true;lesson.earned++;engine.correct(q.subject,System.currentTimeMillis());save();
                        answer.setBackground(Ui.shape(0xFFDCE9D0,16,this));feedback.setText("答对啦！你收到了 1 颗星星 ★");feedback.setTextColor(Ui.GREEN);
                        for(Button other:answers)other.setEnabled(false);next.setVisibility(View.VISIBLE);speak(q,false);
                    }else{answer.setBackground(Ui.shape(0xFFF2E3D4,16,this));feedback.setText("再试一次。"+q.explanation);feedback.setTextColor(0xFF997A55);}
                });answers.add(answer);LinearLayout.LayoutParams op=new LinearLayout.LayoutParams(0,dp(58),1);op.setMargins(col==0?0:dp(10),0,0,0);options.addView(answer,op);
            }
        }
        Ui.gap(p,8);p.addView(feedback,lp(-1,-2));Ui.gap(p,12);p.addView(next,lp(-1,52));
        if(lesson.answered){
            for(Button answer:answers){answer.setEnabled(false);if(q.matches(answer.getText().toString()))answer.setBackground(Ui.shape(0xFFDCE9D0,16,this));}
            next.setVisibility(View.VISIBLE);feedback.setText("答对啦！你收到了 1 颗星星 ★");feedback.setTextColor(Ui.GREEN);
        }
        sheet(lesson.title,"轻轻想一想，"+state.name+"会陪着你。",p);currentLesson=lesson;
    }
    private void showLessonResult(Lesson lesson){
        LinearLayout p=Ui.column(this);TextView reward=text("★ +"+lesson.earned+"    点心 +2",28,0xFF9F7D46,true);reward.setGravity(Gravity.CENTER);p.addView(reward);Ui.gap(p,18);
        TextView copy=text("每一点进步都值得开心，去和"+state.name+"分享吧。",17,Ui.MUTED,false);copy.setGravity(Gravity.CENTER);p.addView(copy);Ui.gap(p,22);
        p.addView(Ui.button(this,"回到小屋",Ui.GREEN,()->{dismissDialog();room.react("我们又学会了新知识！");}),lp(-1,54));sheet("做得真棒！","这一轮学习完成啦。",p);
    }
    private void speak(Question q,boolean requested){
        if(!state.sound){if(requested)android.widget.Toast.makeText(this,"朗读已关闭，可以在设置里打开",android.widget.Toast.LENGTH_SHORT).show();return;}
        if(!voiceReady){if(requested)android.widget.Toast.makeText(this,"这台设备暂时没有朗读声音，看看文字也可以练习",android.widget.Toast.LENGTH_SHORT).show();return;}
        int supported=voice.setLanguage(q.subject==Subject.ENGLISH?Locale.ENGLISH:Locale.SIMPLIFIED_CHINESE);
        if(supported<0){if(requested)android.widget.Toast.makeText(this,"这台设备还没有对应的朗读声音",android.widget.Toast.LENGTH_SHORT).show();return;}
        voice.setSpeechRate(.72f);voice.speak(q.speech,TextToSpeech.QUEUE_FLUSH,null,"lesson");
    }
    private void showJournal(){
        LinearLayout p=Ui.column(this);TextView overview=text("相伴 "+state.days(System.currentTimeMillis())+" 天   ·   Lv."+state.level()+"   ·   完成 "+state.completedLessons+" 轮学习",17,Ui.INK,true);p.addView(overview);Ui.gap(p,20);
        for(Subject s:Subject.values()){TextView line=text(s.title+"                         "+state.learnedCount(s)+" 颗星星",18,Ui.INK,false);line.setPadding(dp(16),dp(16),dp(16),dp(16));line.setBackground(Ui.shape(blend(s.color),16,this));p.addView(line,lp(-1,-2));Ui.gap(p,10);}
        sheet("我们的成长记录","星星记录每一次答对，亲密度来自照顾与陪伴。",p);
    }
    private void showPets(){
        LinearLayout p=Ui.column(this);for(PetDefinition pet:PetRegistry.all())addChoice(p,pet.species+" · "+pet.defaultName,pet.introduction,0xFFE8EDD9,()->{
            dismissDialog();if(state.speciesId.equals(pet.id)){room.message("这就是我们的第一位小伙伴");return;}
            store.save(state);room.setRunning(false);state=store.activate(pet.id);engine=new PetEngine(state);engine.tick(System.currentTimeMillis());buildHome();save();});
        sheet("宠物图鉴","每一位小伙伴都有自己的家与成长故事。",p);
    }
    private void showSettings(){
        LinearLayout p=Ui.column(this);
        addChoice(p,"给"+petSpecies()+"取名字","现在叫「"+state.name+"」",0xFFE8EDD9,this::rename);
        addChoice(p,state.sound?"关闭朗读":"打开朗读","中文和英文朗读使用设备已有的声音",0xFFE6EAF1,()->{state.sound=!state.sound;save();showSettings();});
        addChoice(p,"家长：重新领养","清空成长记录，回到宠物蛋",0xFFF1E2D9,this::parentGate);
        TextView privacy=text("芽芽小屋 0.1.0\n成长记录保存在本机。没有广告、内购或账号。",13,Ui.MUTED,false);privacy.setLineSpacing(dp(5),1);p.addView(privacy);sheet("小屋设置","让陪伴更适合你。",p);
    }
    private void rename(){
        LinearLayout p=Ui.column(this);EditText input=new EditText(this);input.setSingleLine(true);input.setText(state.name);input.setTextSize(23);input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(8)});input.setHint("最多 8 个字");p.addView(input,lp(-1,64));Ui.gap(p,20);
        p.addView(Ui.button(this,"保存名字",Ui.GREEN,()->{String name=input.getText().toString().trim();if(name.isEmpty()){input.setError("给小伙伴一个名字吧");return;}state.name=name;save();dismissDialog();room.react("以后我就叫"+name+"啦！");}),lp(-1,54));sheet("给"+petSpecies()+"取名字","取一个你喜欢的名字吧。",p);
    }
    private void parentGate(){
        LinearLayout p=Ui.column(this);EditText answer=new EditText(this);answer.setSingleLine(true);answer.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);answer.setHint("18 + 7 = ?");p.addView(answer,lp(-1,64));Ui.gap(p,16);
        p.addView(Ui.button(this,"继续",Ui.GREEN,()->{if(!answer.getText().toString().trim().equals("25")){answer.setError("请让家长完成这一步");return;}
            new AlertDialog.Builder(this).setTitle("重新领养？").setMessage("这会清空这位伙伴的名字、星星和成长记录，重新获得一枚"+petSpecies()+"蛋。").setNegativeButton("保留小伙伴",null).setPositiveButton("重新领养",(d,w)->{
                dismissDialog();String speciesId=state.speciesId;state=new PetState();state.speciesId=speciesId;state.name=PetRegistry.get(speciesId).defaultName;engine=new PetEngine(state);store.save(state);buildHome();}).show();}),lp(-1,54));sheet("请家长帮忙","重新领养会清空这位小伙伴的成长记录。",p);
    }
    @Override protected void onResume(){super.onResume();if(engine!=null){engine.tick(System.currentTimeMillis());save();}if(room!=null)room.setRunning(true);clock.removeCallbacks(updateClock);clock.postDelayed(updateClock,60000);
        if(Build.VERSION.SDK_INT>=33){if(modernBack==null)modernBack=()->handleBack();getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,modernBack);}}
    @Override protected void onPause(){super.onPause();clock.removeCallbacks(updateClock);if(room!=null)room.setRunning(false);if(store!=null)store.save(state);if(voice!=null)voice.stop();
        if(Build.VERSION.SDK_INT>=33)getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(modernBack);}
    @Override protected void onDestroy(){clock.removeCallbacks(updateClock);dismissDialog();if(voice!=null)voice.shutdown();super.onDestroy();}
    @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(room!=null)room.setRunning(focused);}
    @Override public void onConfigurationChanged(Configuration config){super.onConfigurationChanged(config);Lesson pending=currentLesson;if(room!=null)room.setRunning(false);dismissDialog();buildHome();if(pending!=null && pending.index<pending.questions.size())showQuestion(pending);}
    private void handleBack(){if(room.isPlaying()){room.cancelPlay();room.message("小球先休息一会儿");}else finish();}
    @Override public boolean onKeyUp(int key,android.view.KeyEvent event){if(Build.VERSION.SDK_INT<33 && key==android.view.KeyEvent.KEYCODE_BACK){handleBack();return true;}return super.onKeyUp(key,event);}
}
