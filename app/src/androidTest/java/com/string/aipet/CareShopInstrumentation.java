package com.string.aipet;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.inspector.WindowInspector;
import android.widget.TextView;
import com.string.aipet.family.FamilyState;
import com.string.aipet.family.FamilyStore;
import com.string.aipet.learning.LearningSettings;
import com.string.aipet.learning.Question;
import com.string.aipet.learning.Subject;
import com.string.aipet.model.PetState;
import com.string.aipet.model.PetStore;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.util.List;

/** Disposable API 29+ emulator only: resets this app's pet and family test records. */
@android.annotation.TargetApi(29)
public final class CareShopInstrumentation extends Instrumentation {
    private MainActivity activity;
    private FamilyStore family;
    private int checks;
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){Bundle result=new Bundle();try{
        if(!BuildConfig.DEBUG)throw new AssertionError("Use debug on a disposable emulator");
        exercise();result.putString("stream","PASS: "+checks+" care/shop checks\n");finish(Activity.RESULT_OK,result);
    }catch(Throwable e){result.putString("stream","FAIL: "+android.util.Log.getStackTraceString(e));finish(Activity.RESULT_CANCELED,result);}}
    private void check(boolean ok,String why){if(!ok)throw new AssertionError(why);checks++;Bundle b=new Bundle();b.putString("stream","PASS "+why+"\n");sendStatus(0,b);}
    private void run(Runnable r){Throwable[] error={null};runOnMainSync(()->{try{r.run();}catch(Throwable e){error[0]=e;}});waitForIdleSync();if(error[0]!=null)throw new AssertionError(error[0]);}
    private void launch(){activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));waitForIdleSync();}
    private Object field(Object object,String name)throws Exception{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    private PetState pet(){return new PetStore(getTargetContext()).load();}
    private View find(View root,String label){
        if(label.contentEquals(root.getContentDescription()==null?"":root.getContentDescription()))return root;
        if(root instanceof TextView&&label.contentEquals(((TextView)root).getText()))return root;
        if(root instanceof ViewGroup){ViewGroup group=(ViewGroup)root;for(int i=0;i<group.getChildCount();i++){View hit=find(group.getChildAt(i),label);if(hit!=null)return hit;}}
        return null;
    }
    private View find(String label){List<View> roots=WindowInspector.getGlobalWindowViews();return roots.isEmpty()?null:find(roots.get(roots.size()-1),label);}
    private void click(String label){run(()->{View hit=find(label);if(hit==null)throw new AssertionError("Missing control: "+label);if(!hit.isEnabled())throw new AssertionError("Disabled control: "+label);hit.performClick();});}
    @SuppressWarnings("unchecked") private Question question()throws Exception{
        Object lesson=field(activity,"currentLesson");if(lesson==null)throw new AssertionError("No care question");
        List<Question> questions=(List<Question>)field(lesson,"questions");check(questions.size()==1,"Care uses exactly one question");
        check(questions.get(0).subject==Subject.MATH,"Care follows the saved math-only curriculum");return questions.get(0);
    }
    private void answer()throws Exception{Question q=question();click(q.answer);click("完成啦");}
    private void restart(){run(()->activity.finish());launch();}
    private void rotate(int orientation)throws Exception{
        getUiAutomation().setRotation(orientation==ActivityInfo.SCREEN_ORIENTATION_PORTRAIT?1:0);Thread.sleep(800);waitForIdleSync();
        check(activity.getResources().getConfiguration().orientation==(orientation==ActivityInfo.SCREEN_ORIENTATION_PORTRAIT?1:2),"Device actually rotates to the requested orientation");
    }
    private void shot(String name)throws Exception{
        Thread.sleep(2200);waitForIdleSync();
        File target=new File(getTargetContext().getExternalFilesDir(null),name+".png");
        Bitmap bitmap=getUiAutomation().takeScreenshot();try(FileOutputStream out=new FileOutputStream(target)){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();
    }
    private void exercise()throws Exception{
        PetStore store=new PetStore(getTargetContext());PetState initial=new PetState();initial.warmth=100;initial.name="芽芽";
        initial.fullness=40;initial.happiness=40;initial.energy=60;initial.cleanliness=40;initial.snacks=1;initial.stars=40;initial.sound=false;initial.learned.put(Subject.MATH,40);store.saveNow(initial);
        // Simulate an actual v1 JSON save with no new keys, then load it through the production store.
        android.content.SharedPreferences prefs=getTargetContext().getSharedPreferences("pet_home",Context.MODE_PRIVATE);
        JSONObject legacy=new JSONObject(prefs.getString("pet.little_snake","{}"));legacy.put("version",1);legacy.remove("cleanliness");legacy.remove("decorations");legacy.remove("equippedDecorations");
        prefs.edit().putString("pet.little_snake",legacy.toString()).commit();PetState migrated=store.load();
        check(migrated.cleanliness==80&&migrated.decorations.isEmpty()&&migrated.stars==40&&migrated.totalLearned()==40,"Old saves gain safe defaults and retain all knowledge achievements");store.saveNow(initial);
        family=FamilyStore.get(getTargetContext());family.change(s->{s.rules.clear();s.ledger.clear();s.gifts.clear();s.active.clear();s.trackingStarted=false;s.learning=new LearningSettings();s.learning.chinesePercent=0;s.learning.mathPercent=100;s.learning.englishPercent=0;s.ledger.add(new FamilyState.Entry(s.day(System.currentTimeMillis()),"测试奖励","adjust",40,System.currentTimeMillis()));});
        launch();check(find("清洗")!=null&&find("双色星星，打开兑换")!=null,"Home exposes cleaning and both named colored wallets");
        check(find("★ 40 知识星").getWidth()>50&&find("★ 40 成长星").getWidth()>50,"Both home wallet amounts have visible text width");
        click("喂食 · 答题");click("浆果点心");Question q=question();
        check(pet().fullness==40&&pet().snacks==1,"Opening a feeding question does not feed or consume food");
        String wrong="";for(String option:q.options)if(!q.matches(option)){wrong=option;break;}click(wrong);check(pet().stars==40&&pet().fullness==40,"Wrong answer changes neither wallet nor care");
        click("返回");check(pet().fullness==40,"Exiting a care question does not issue care rewards");
        click("喂食 · 答题");click("浆果点心");q=question();View answerButton=find(q.answer);click(q.answer);run(answerButton::performClick);int earned=pet().stars;
        check(earned==41&&pet().fullness==40,"Correct answer grants one gold star and waits for completion");
        rotate(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);check(pet().stars==earned,"Rotation does not repeat the correct-answer star");
        View stale=find("完成啦");click("完成啦");run(stale::performClick);
        check(pet().fullness==62&&pet().snacks==0&&pet().affection==2&&pet().completedLessons==0,"Feeding completion applies care exactly once without a full-lesson bonus");
        rotate(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        click("喂食 · 答题");click("小虫饼干");answer();check(pet().fullness==84&&pet().snacks==0,"Correct care answer prepares food with an empty basket");
        click("喂食 · 答题");click("喝点清凉水");check(pet().energy==60,"Water also waits for a question");answer();check(pet().energy==63,"Completed water question restores energy");
        click("陪玩");check(pet().happiness==48,"Play begins with a question and no premature mood reward");answer();
        check(pet().happiness==66&&pet().energy==57,"Completed play question improves mood and spends six energy");
        click("清洗");check(pet().cleanliness==40,"Cleaning question grants no early hygiene");answer();check(pet().cleanliness==65,"Completed cleaning question restores hygiene");
        shot("care-home");int stars=pet().stars;int growth=family.snapshot().balance();int learned=pet().totalLearned();
        click("装扮与成长");click("星星挂饰 · 6 ★");click("返回礼物");check(pet().stars==stars&&family.snapshot().balance()==growth,"Browsing or canceling a purchase spends no stars");
        click("星星挂饰 · 6 ★");click("用金色知识星星兑换 · 6 ★");
        check(pet().stars==stars-6&&family.snapshot().balance()==growth&&pet().totalLearned()==learned,"Gold purchase spends only knowledge balance and preserves cumulative learning");
        check(pet().equippedDecorations.contains("star_garland")&&find("用金色知识星星兑换 · 6 ★")==null,"Purchased decoration is equipped and cannot be bought twice");
        click("收起装扮");check(!pet().equippedDecorations.contains("star_garland")&&pet().stars==stars-6,"Taking a decoration down is free");click("布置装扮");
        click("返回礼物");click("云朵地毯 · 8 ★");click("用绿色成长星星兑换 · 8 ★");
        check(family.snapshot().balance()==growth-8&&pet().stars==stars-6&&pet().equippedDecorations.contains("cloud_rug"),"Green purchase debits the family ledger and equips the same shop items");
        check(family.snapshot().gifts.size()==1&&family.snapshot().gifts.get(0).delivered,"Growth gift delivery is durably acknowledged");
        click("返回礼物");click("花开盆栽 · 10 ★");click("用金色知识星星兑换 · 10 ★");click("返回礼物");
        click("成长果实 · 10 ★");int level=pet().level();click("用金色知识星星兑换 · 10 ★");check(pet().level()==level+1,"Gold growth fruit increases the pet by exactly one level");
        click("用绿色成长星星兑换 · 10 ★");check(pet().level()==level+2&&family.snapshot().balance()==growth-18,"Child can switch to green stars for the next growth fruit");
        shot("star-choice");click("返回礼物");click("回到小屋");shot("decorated-home");
        restart();check(pet().equippedDecorations.size()==3&&pet().cleanliness==65&&pet().level()==level+2&&family.snapshot().balance()==growth-18,"Restart preserves decorations, cleanliness, both wallets and level without replaying gifts");
        click("装扮与成长");click("成长果实 · 10 ★");int beforeFailure=pet().stars;int affectionBeforeFailure=pet().affection;
        File prefsDirectory=new File(getTargetContext().getApplicationInfo().dataDir,"shared_prefs");
        check(prefsDirectory.setWritable(false,false),"Can simulate unavailable save storage on the disposable emulator");
        try{click("用金色知识星星兑换 · 10 ★");}finally{if(!prefsDirectory.setWritable(true,true))throw new AssertionError("Cannot restore test storage permissions");}
        check(pet().stars==beforeFailure&&pet().affection==affectionBeforeFailure,"Failed disk save restores preference memory and does not spend gold or grant a level");
        check(((PetState)field(activity,"state")).stars==beforeFailure&&((PetState)field(activity,"state")).affection==affectionBeforeFailure,"Failed purchase restores the live home state");
        click("返回礼物");click("回到小屋");restart();check(pet().stars==beforeFailure&&pet().affection==affectionBeforeFailure,"Failed purchase remains rolled back after restart");
        click("休息");int sleepingStars=pet().stars;click("清洗");check(field(activity,"currentLesson")==null&&pet().stars==sleepingStars,"Sleeping pet cannot start a cleaning question");click("休息");
        family.change(s->s.ledger.add(new FamilyState.Entry(s.day(System.currentTimeMillis()),"测试扣星","adjust",-100,System.currentTimeMillis())));
        click("装扮与成长");click("成长果实 · 10 ★");View green=find("用绿色成长星星兑换 · 10 ★");
        check(green!=null&&!green.isEnabled(),"Negative growth balance disables green redemption without preventing gold choice");
        check(find("用金色知识星星兑换 · 10 ★").isEnabled(),"Gold balance remains independently usable");click("返回礼物");click("回到小屋");
        rotate(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);shot("care-portrait");check(find("清洗")!=null&&find("装扮与成长")!=null,"Portrait keeps care and decoration controls available");
        rotate(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
    }
}
