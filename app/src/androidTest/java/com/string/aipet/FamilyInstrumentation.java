package com.string.aipet;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.view.inspector.WindowInspector;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.ListView;
import com.string.aipet.family.*;
import com.string.aipet.learning.*;
import com.string.aipet.model.*;
import java.lang.reflect.Field;
import java.util.List;
import java.util.function.BooleanSupplier;

/** Disposable API 29+ emulator only. Exercises actual Android views, persistence and usage events. */
@android.annotation.TargetApi(29)
public final class FamilyInstrumentation extends Instrumentation {
    private MainActivity activity;
    private FamilyStore store;
    private int checks;
    private boolean usageSuite;
    @Override public void onCreate(Bundle args){super.onCreate(args);usageSuite=args!=null&&"usage".equals(args.getString("suite"));start();}
    @Override public void onStart(){Bundle result=new Bundle();try{
        if(!BuildConfig.DEBUG)throw new AssertionError("Use the debug test build on a disposable emulator");
        store=FamilyStore.get(getTargetContext());
        if(usageSuite)usage();else integration();
        result.putString("stream","PASS: "+checks+" "+(usageSuite?"usage":"family/learning")+" checks\n");finish(Activity.RESULT_OK,result);
    }catch(Throwable e){result.putString("stream","FAIL: "+android.util.Log.getStackTraceString(e));finish(Activity.RESULT_CANCELED,result);}}
    private void check(boolean ok,String why){if(!ok)throw new AssertionError(why);checks++;Bundle b=new Bundle();b.putString("stream","PASS "+why+"\n");sendStatus(0,b);}
    private void run(Runnable r){Throwable[] failed={null};runOnMainSync(()->{try{r.run();}catch(Throwable e){failed[0]=e;}});waitForIdleSync();if(failed[0]!=null)throw new AssertionError("Android view action failed",failed[0]);}
    private void launch(){activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));waitForIdleSync();}
    private List<View> roots(){return WindowInspector.getGlobalWindowViews();}
    private View find(View v,String label){if(label.contentEquals(v.getContentDescription()==null?"":v.getContentDescription()))return v;
        if(v instanceof TextView&&label.contentEquals(((TextView)v).getText()))return v;
        if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){View hit=find(g.getChildAt(i),label);if(hit!=null)return hit;}}return null;}
    private View find(String label){List<View> all=roots();return all.isEmpty()?null:find(all.get(all.size()-1),label);}
    private void click(String label){run(()->{View v=find(label);if(v==null)throw new AssertionError("Missing control: "+label);if(!v.performClick())throw new AssertionError("Control not clickable: "+label);});}
    private View typed(View v,String label,Class<?> kind){if(kind.isInstance(v)&&label.contentEquals(v.getContentDescription()==null?"":v.getContentDescription()))return v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){View hit=typed(g.getChildAt(i),label,kind);if(hit!=null)return hit;}}return null;}
    private View typed(String label,Class<?> kind){List<View> all=roots();return all.isEmpty()?null:typed(all.get(all.size()-1),label,kind);}
    private void field(String label,String value){run(()->{View v=typed(label,EditText.class);if(v==null)throw new AssertionError("Missing field: "+label);((EditText)v).setText(value);});}
    private void select(String label,int value){run(()->{View v=typed(label,Spinner.class);if(v==null)throw new AssertionError("Missing selector: "+label);((Spinner)v).setSelection(value);});}
    private void await(BooleanSupplier until,String description)throws Exception{long end=System.currentTimeMillis()+15000;while(System.currentTimeMillis()<end&&!until.getAsBoolean())Thread.sleep(100);check(until.getAsBoolean(),description);waitForIdleSync();}
    private void enterParent(){if(find("家长 PIN")!=null){field("家长 PIN","1357");if(find("再次输入 PIN")!=null){field("再次输入 PIN","1357");click("保存 PIN");}else click("进入家长空间");}}
    private void parent(){click("设置");click("家长空间");enterParent();}
    private PetState pet(){return new PetStore(getTargetContext()).load();}
    @SuppressWarnings("unchecked") private List<Question> lesson()throws Exception{Field f=MainActivity.class.getDeclaredField("currentLesson");f.setAccessible(true);Object value=f.get(activity);if(value==null)throw new AssertionError("No active lesson");Field q=value.getClass().getDeclaredField("questions");q.setAccessible(true);return (List<Question>)q.get(value);}
    private void integration()throws Exception{
        // Reset family-only test records, leaving the existing pet untouched.
        store.change(s->{s.tasks.clear();s.seedTasks();s.completions.clear();s.rules.clear();s.ledger.clear();s.gifts.clear();s.usage.clear();s.billed.clear();s.active.clear();s.pinHash="";s.pinSalt="";s.learning=new LearningSettings();s.trackingStarted=false;});
        launch();PetState before=pet();check(before.hatched(),"Existing hatched pet remains after the 0.2.0 upgrade");
        check(find("今日约定")!=null,"Pet home exposes daily real-world habits");
        click("今日约定");click("我完成啦 · 自己收拾玩具");click("我完成啦 · 认真阅读 20 分钟");
        check(store.snapshot().completions.size()==2&&store.snapshot().balance()==0,"Child submissions remain pending without wallet rewards");
        check(find("我完成啦 · 自己收拾玩具")==null,"Daily completed submission cannot be submitted twice in the child UI");
        click("家长确认任务");field("家长 PIN","1357");field("再次输入 PIN","2468");click("保存 PIN");
        check(store.snapshot().pinHash.isEmpty(),"Different PIN confirmation cannot initialize parent credentials");
        field("再次输入 PIN","1357");click("保存 PIN");check(ParentPin.verify(store.snapshot(),"1357"),"Parent PIN is salted and persisted");
        click("修改任务 · 认真阅读 20 分钟");field("任务名称","每天阅读 10 分钟");field("奖励成长星星（1–100）","9");click("保存任务");
        click("确认完成 · 认真阅读 20 分钟");click("确认完成 · 自己收拾玩具");
        check(store.snapshot().balance()==5,"Pending task keeps its submitted reward after parent edits");
        check(pet().stars==before.stars,"Real-world rewards leave the learned knowledge stars intact");
        String today=store.snapshot().day(System.currentTimeMillis());click("撤销记录 · "+today+" · 自己收拾玩具");click("保留记录");check(store.snapshot().balance()==5,"Canceling record reversal retains growth stars");
        click("撤销记录 · "+today+" · 自己收拾玩具");click("确认撤销");check(store.snapshot().balance()==3,"Confirmed reversal leaves a compensating ledger entry");
        click("回到小屋");click("今日约定");click("心愿礼物");int snacks=pet().snacks;
        click("野餐点心礼包 · 3 ★ · 点心 +2");check(store.snapshot().balance()==0&&pet().snacks==snacks+2,"Approved life task rewards buy a real pet snack gift");
        check(store.snapshot().gifts.size()==1&&store.snapshot().gifts.get(0).delivered,"Gift delivery is durably acknowledged");
        click("野餐点心礼包 · 3 ★ · 点心 +2");check(pet().snacks==snacks+2&&store.snapshot().gifts.size()==1,"Insufficient growth stars cannot issue another gift");
        click("回到小屋");run(()->activity.finish());launch();check(pet().snacks==snacks+2,"Restart does not replay a delivered gift");
        click("设置");click("家长空间");field("家长 PIN","2468");click("进入家长空间");check(find("家长 PIN")!=null,"Parent session is locked after leaving the previous Activity");enterParent();
        click("语数英学习方案");field("语文比例（%）","60");field("数学比例（%）","30");field("英语比例（%）","20");click("保存学习方案");
        check(store.snapshot().learning.chinesePercent==40,"Invalid percentage sum cannot replace the saved plan");
        field("英语比例（%）","10");field("语文中拼音比例（其余为识字，%）","75");select("语文难度",2);select("数学难度",2);select("英语难度",2);select("每轮综合练习题数",2);click("保存学习方案");
        LearningSettings saved=store.snapshot().learning;check(saved.chinesePercent==60&&saved.mathPercent==30&&saved.englishPercent==10&&saved.chineseDifficulty==2&&saved.mathDifficulty==2&&saved.englishDifficulty==2&&saved.sessionLength==20,"Difficulty and exact subject percentages are persisted");
        click("回到小屋");click("学习");click("今日综合练习");List<Question> mixed=lesson();
        check(mixed.size()==20&&mixed.stream().filter(q->q.subject==Subject.MATH).count()==6&&mixed.stream().filter(q->q.subject==Subject.ENGLISH).count()==2,"The actual 20-question child lesson follows 60/30/10 quotas");
        check(mixed.stream().filter(q->q.subject==Subject.PINYIN).count()==9&&mixed.stream().filter(q->q.subject==Subject.LITERACY).count()==3,"Chinese lessons honor the pinyin/character sub-ratio");
        check(mixed.stream().filter(q->q.subject==Subject.PINYIN).allMatch(q->q.prompt.contains("声调"))&&mixed.stream().filter(q->q.subject==Subject.LITERACY).allMatch(q->q.prompt.contains("（　）")),"Challenge selections change the presented Chinese question format");click("返回");
        parent();click("语数英学习方案");field("语文比例（%）","0");field("数学比例（%）","0");field("英语比例（%）","100");select("每轮综合练习题数",0);click("保存学习方案");click("回到小屋");click("学习");
        check(find("小小数学家")==null&&find("拼音小火车")==null&&find("汉字花园")==null,"Zero-percent topics are hidden from child single-subject choices");
        click("今日综合练习");List<Question> english=lesson();check(english.size()==5&&english.stream().allMatch(q->q.subject==Subject.ENGLISH&&q.prompt.matches("[a-z]+")),"English-only challenge plan reaches actual child questions");
        Question first=english.get(0);int stars=pet().stars;click(first.answer);click(first.answer);check(pet().stars==stars+1&&store.snapshot().balance()==0,"Answer reward cannot be duplicated or converted into growth wallet credits");click("返回");
        parent();click("语数英学习方案");click("均衡方案 · 语40 / 数40 / 英20");select("语文难度",1);select("数学难度",1);select("英语难度",1);field("语文中拼音比例（其余为识字，%）","50");select("每轮综合练习题数",1);click("保存学习方案");
        check(store.export().contains("learning")&&!store.export().contains("pinHash")&&!store.export().contains("pinSalt"),"Export includes family and curriculum records without PIN credentials");
        click("退出家长模式");click("设置");click("家长空间");check(find("家长 PIN")!=null,"Explicit parent exit requires PIN on next entry");enterParent();click("语数英学习方案");
        field("语文比例（%）","55");field("数学比例（%）","35");field("英语比例（%）","10");
        View stale=find("保存学习方案");Field family=MainActivity.class.getDeclaredField("family");family.setAccessible(true);FamilyController controller=(FamilyController)family.get(activity);Field expiry=FamilyController.class.getDeclaredField("parentUntil");expiry.setAccessible(true);expiry.setLong(controller,0);
        run(controller::onTick);run(stale::performClick);check(find("家长 PIN")!=null&&store.snapshot().learning.chinesePercent==40,"Expired parent callbacks cannot write curriculum preferences");
        click("回到小屋");check(!pet().appliedGifts.isEmpty()&&ParentPin.verify(store.snapshot(),"1357"),"Pet delivery IDs and parent credentials survive the integration workflow");
    }
    private void chooseSystemApp(){ListView[] chosen={null};int[] position={-1};run(()->{List<View> all=roots();View root=all.get(all.size()-1);ListView list=firstList(root);if(list==null)throw new AssertionError("App selector list missing");for(int i=0;i<list.getAdapter().getCount();i++)if(list.getAdapter().getItem(i).toString().equals("Settings"))position[0]=i;if(position[0]<0)throw new AssertionError("Settings app is not selectable");chosen[0]=list;list.setSelection(position[0]);});run(()->{ListView list=chosen[0];list.performItemClick(list.getChildAt(position[0]-list.getFirstVisiblePosition()),position[0],list.getAdapter().getItemId(position[0]));});}
    private ListView firstList(View view){if(view instanceof ListView)return (ListView)view;if(view instanceof ViewGroup){ViewGroup g=(ViewGroup)view;for(int i=0;i<g.getChildCount();i++){ListView hit=firstList(g.getChildAt(i));if(hit!=null)return hit;}}return null;}
    private void usage()throws Exception{
        launch();check(ParentPin.verify(store.snapshot(),"1357"),"Uses previously created family PIN");parent();click("娱乐 App 约定");
        if(find("家长授权用量统计")!=null){click("家长授权用量统计");await(()->{android.view.accessibility.AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();return root!=null&&root.getPackageName()!=null&&root.getPackageName().toString().contains("settings");},"Parent authorization opens Android usage-access settings");
            try(android.os.ParcelFileDescriptor shell=getUiAutomation().executeShellCommand("appops set com.string.aipet GET_USAGE_STATS allow")){Thread.sleep(500);}
            launch();parent();click("娱乐 App 约定");
        }
        check(UsageTracker.permitted(getTargetContext()),"Usage-access permission granted for the disposable emulator");
        click("添加娱乐 App");chooseSystemApp();field("每日免费分钟（0–1440）","0");field("每次计费分钟（1–1440）","1");field("每次扣成长星星（1–100）","2");click("保存 App 约定");
        await(()->store.snapshot().rules.stream().anyMatch(r->r.end==0&&r.packageName.equals("com.android.settings")),"Parent selected installed App and saved a billing agreement");
        int balance=store.snapshot().balance();PetState previous=pet();UsageTracker.sync(getTargetContext());
        getTargetContext().startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        for(int i=0;i<32;i++)Thread.sleep(2000);
        getTargetContext().startActivity(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));Thread.sleep(1200);UsageTracker.sync(getTargetContext());
        FamilyState state=store.snapshot();FamilyState.Rule rule=state.rules.stream().filter(r->r.end==0&&r.packageName.equals("com.android.settings")).findFirst().get();
        long used=state.usageToday(rule,state.day(System.currentTimeMillis()));check(used>=60000&&used<90000,"Actual Android Settings foreground usage is counted for one minute");
        check(state.balance()==balance-2,"Actual App use deducts growth stars by the agreed full billing unit");UsageTracker.sync(getTargetContext());check(store.snapshot().balance()==balance-2,"Repeated real-event sync cannot charge twice");
        check(pet().stars==previous.stars&&pet().snacks==previous.snacks,"App-use deductions preserve knowledge achievements and food supplies");
        // Leaving the app locks the parent session even when it was previously active.
        run(()->activity.startActivity(new Intent(activity,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)));waitForIdleSync();click("设置");click("家长空间");check(find("家长 PIN")!=null,"Returning from entertainment requires a fresh parent PIN");enterParent();click("娱乐 App 约定");click("停止统计 · Settings");click("停止统计");
        await(()->store.snapshot().rules.stream().noneMatch(r->r.end==0&&r.packageName.equals("com.android.settings")),"Stopping an App agreement keeps its history and stops new billing");
        check(store.snapshot().rules.size()>=1&&!store.snapshot().usage.isEmpty(),"Retired App usage remains in the family record");
    }
}
