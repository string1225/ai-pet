package com.string.aipet;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;
import com.string.aipet.family.*;
import com.string.aipet.learning.LearningSettings;
import com.string.aipet.model.PetState;
import com.string.aipet.ui.Ui;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** The home notebook joins real-world habits, parent agreements and learning preferences. */
public final class FamilyController {
    public interface Host { PetState pet(); void deliverGifts(); void changed(); }
    private final Activity activity;
    private final Host host;
    private final FamilyStore store;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private AlertDialog dialog;
    private long parentUntil, blockedUntil;
    private int failedPins;
    private boolean resumed,destroyed,syncing,protectedScreen;
    private String pendingExport;
    private static final int EXPORT_REQUEST=2031;
    public FamilyController(Activity activity,Host host){this.activity=activity;this.host=host;store=FamilyStore.get(activity);}
    public FamilyState snapshot(){return store.snapshot();}
    public LearningSettings learning(){return snapshot().learning;}
    private boolean parent(){return resumed&&SystemClock.elapsedRealtime()<parentUntil;}
    private int dp(float n){return Ui.dp(activity,n);}
    private TextView text(String s,int size){return Ui.text(activity,s,size,Ui.INK,false);}
    private LinearLayout form(){LinearLayout p=Ui.column(activity);p.setPadding(dp(24),dp(10),dp(24),dp(18));return p;}
    private void note(LinearLayout p,String value){TextView t=text(value,14);t.setLineSpacing(dp(4),1);p.addView(t);Ui.gap(p,14);}
    private void button(LinearLayout p,String label,Runnable action){Button b=Ui.button(activity,label,Ui.GREEN,action);b.setMinHeight(dp(52));p.addView(b,new LinearLayout.LayoutParams(-1,-2));Ui.gap(p,10);}
    private EditText input(LinearLayout p,String label,String initial,boolean number){
        p.addView(text(label,14));EditText e=new EditText(activity);e.setSingleLine(true);e.setText(initial);e.setContentDescription(label);e.setInputType(number?InputType.TYPE_CLASS_NUMBER:InputType.TYPE_CLASS_TEXT);p.addView(e,new LinearLayout.LayoutParams(-1,dp(56)));Ui.gap(p,10);return e;
    }
    private Spinner select(LinearLayout p,String label,String[] options,int index){p.addView(text(label,14));Spinner s=new Spinner(activity);s.setContentDescription(label);s.setAdapter(new ArrayAdapter<>(activity,android.R.layout.simple_spinner_dropdown_item,options));s.setSelection(index);p.addView(s,new LinearLayout.LayoutParams(-1,dp(52)));Ui.gap(p,8);return s;}
    private void show(String title,LinearLayout content,boolean protectedPage){
        dismiss();protectedScreen=protectedPage;ScrollView scroll=new ScrollView(activity);scroll.addView(content);
        AlertDialog shown=new AlertDialog.Builder(activity).setTitle(title).setView(scroll).setNegativeButton("回到小屋",(d,w)->{if(protectedPage)parentUntil=0;}).create();dialog=shown;
        shown.setOnCancelListener(d->{if(protectedPage)parentUntil=0;});
        shown.setOnDismissListener(d->{if(dialog==shown){dialog=null;protectedScreen=false;}});shown.show();resize();
    }
    public void resize(){if(dialog!=null&&dialog.getWindow()!=null){int w=Math.min(dp(720),activity.getResources().getDisplayMetrics().widthPixels-dp(40));dialog.getWindow().setLayout(w,ViewGroup.LayoutParams.WRAP_CONTENT);}}
    public void dismiss(){if(dialog!=null)dialog.dismiss();}
    private void toast(String s){Toast.makeText(activity,s,Toast.LENGTH_LONG).show();}
    private void mutate(java.util.function.Consumer<FamilyState> change){try{store.change(change);host.changed();}catch(RuntimeException e){toast(e.getMessage()==null?"本机保存失败，请重试":e.getMessage());throw e;}}
    private int number(EditText input,int min,int max){try{int n=Integer.parseInt(input.getText().toString().trim());if(n>=min&&n<=max)return n;}catch(NumberFormatException ignored){}input.setError("请填写 "+min+"–"+max+" 的整数");return Integer.MIN_VALUE;}
    private boolean requireParent(Runnable retry){if(parent())return true;gate(retry);return false;}
    public void gate(Runnable action){
        gate("",action);
    }
    public void gate(String reason,Runnable action){
        if(destroyed||activity.isFinishing())return;
        if(parent()){action.run();return;}
        FamilyState state=snapshot();boolean first=state.pinHash.isEmpty();LinearLayout p=form();
        note(p,(reason.isEmpty()?"":reason+"\n")+(first?"请家长设置 4–8 位数字 PIN。用于确认任务、调整约定和学习方案；请记住这组数字。":"请输入家长 PIN。离开应用或 5 分钟后会锁定。"));
        EditText pin=input(p,"家长 PIN","",true);pin.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        EditText repeat=first?input(p,"再次输入 PIN","",true):null;if(repeat!=null)repeat.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        button(p,first?"保存 PIN":"进入家长空间",()->{
            if(SystemClock.elapsedRealtime()<blockedUntil){pin.setError("请稍等 30 秒再试");return;}
            String value=pin.getText().toString();if(!value.matches("\\d{4,8}")||(first&&!value.equals(repeat.getText().toString()))){pin.setError(first?"需 4–8 位数字，且两次输入一致":"请输入 4–8 位数字");return;}
            if(first){try{mutate(s->ParentPin.set(s,value));}catch(RuntimeException e){return;}}
            else if(!ParentPin.verify(snapshot(),value)){failedPins++;if(failedPins>=5){blockedUntil=SystemClock.elapsedRealtime()+30000;failedPins=0;}pin.setError("PIN 不正确，请让家长输入");return;}
            failedPins=0;parentUntil=SystemClock.elapsedRealtime()+5*60_000;dismiss();action.run();
        });show(first?"设置家长 PIN":"请家长帮忙",p,false);
    }
    public void showTasks(){
        FamilyState state=snapshot();String today=state.day(System.currentTimeMillis());LinearLayout p=form();
        note(p,state.childName+"的生活小约定，也能让小伙伴的家更开心。\n成长星星 "+state.balance()+" ★ · 完成后请家长确认，每项每天一次。");
        for(FamilyState.Task task:state.tasks)if(task.enabled){
            note(p,task.emoji+" "+task.title+" · +"+task.stars+" 成长星星");FamilyState.Completion c=state.completion(task.id,today);
            if(c==null)button(p,"我完成啦 · "+task.title,()->{try{mutate(s->{for(FamilyState.Task t:s.tasks)if(t.id.equals(task.id))s.submit(t,System.currentTimeMillis());});showTasks();}catch(RuntimeException ignored){}});
            else note(p,c.approved?"✓ 家长已确认，真棒！":"已提交，等待家长确认");
        }
        button(p,"家长确认任务",this::showParent);button(p,"心愿礼物",this::showGifts);button(p,"成长星星账本",this::showLedger);button(p,"娱乐 App 约定",()->showUsage(false));
        show("今日约定",p,false);
    }
    public void showGifts(){
        LinearLayout p=form();note(p,"成长星星 "+snapshot().balance()+" ★\n用生活中的小努力，为伙伴准备一份礼物。基础喂食、休息和学习照常进行。");
        button(p,"野餐点心礼包 · 3 ★ · 点心 +2",()->redeem("snacks"));
        button(p,"伙伴庆祝派对 · 5 ★ · 亲密度 +12",()->redeem("party"));
        button(p,"去完成今日约定",this::showTasks);show("心愿礼物",p,false);
    }
    private void redeem(String kind){
        PetState pet=host.pet();if(!pet.hatched()){toast("先孵化小伙伴，再一起拆礼物吧");return;}
        try{mutate(s->s.redeem(pet.speciesId,kind,System.currentTimeMillis()));host.deliverGifts();toast("礼物已送达，小伙伴谢谢你！");showGifts();}
        catch(RuntimeException e){toast(e.getMessage()==null?"礼物暂时没有送达，重新打开小屋会继续送达":e.getMessage());}
    }
    public void showLedger(){showLedgerDate(LocalDate.parse(snapshot().day(System.currentTimeMillis())));}
    private void showLedgerDate(LocalDate day){
        FamilyState state=snapshot();LinearLayout p=form();note(p,"成长星星余额 "+state.balance()+" ★\n知识星星记学习；成长星星记生活约定和心愿礼物。用量结算可暂时欠星。");
        button(p,"前一天",()->showLedgerDate(day.minusDays(1)));if(day.isBefore(LocalDate.parse(state.day(System.currentTimeMillis()))))button(p,"后一天",()->showLedgerDate(day.plusDays(1)));
        String date=day.toString();boolean any=false;
        for(FamilyState.Entry e:state.ledger)if(e.day.equals(date)){note(p,e.title+"   "+(e.delta>0?"+":"")+e.delta+" ★");any=true;}
        for(FamilyState.Completion c:state.completions)if(c.day.equals(date))note(p,c.title+" · "+(c.approved?"家长已确认":"等待确认"));
        for(FamilyState.Rule r:state.rules){long used=state.usageToday(r,date);if(used>0)note(p,r.label+" · 前台使用 "+used/60000+" 分 "+used/1000%60+" 秒 · "+(r.end==0?"当前约定":"历史约定"));}
        if(!any)note(p,"这一天还没有星星收支。");show("成长账本 · "+date,p,false);
    }
    public void showParent(){gate(this::parentPage);}
    private void parentPage(){
        if(!requireParent(this::parentPage))return;FamilyState state=snapshot();LinearLayout p=form();
        note(p,"奖励先确认，约定一起定。成长星星余额 "+state.balance()+" ★\n离开应用或 5 分钟后锁定。知识星星和宠物记录各自保留。");
        button(p,"语数英学习方案",this::showLearningSettings);button(p,"娱乐 App 约定",()->showUsage(true));button(p,"补奖或扣星",this::adjustment);
        note(p,"等待家长确认");boolean pending=false;
        for(FamilyState.Completion c:state.completions)if(!c.approved){pending=true;note(p,c.day+" · "+c.title+" · +"+c.stars+" ★");
            button(p,"确认完成 · "+c.title,()->completionAction(c,true));button(p,"退回任务 · "+c.title,()->completionAction(c,false));}
        if(!pending)note(p,"暂时没有待确认的任务。");
        note(p,"每日任务与奖励");button(p,"添加每日任务",()->taskEditor(null));
        for(FamilyState.Task t:state.tasks){note(p,t.title+" · "+t.stars+" ★ · "+(t.enabled?"启用":"停用"));button(p,"修改任务 · "+t.title,()->taskEditor(t));button(p,(t.enabled?"停用":"启用")+" · "+t.title,()->{if(!requireParent(this::parentPage))return;try{mutate(s->{for(FamilyState.Task live:s.tasks)if(live.id.equals(t.id))live.enabled=!live.enabled;});parentPage();}catch(RuntimeException ignored){}});}
        note(p,"已确认记录（可撤销）");for(FamilyState.Completion c:state.completions)if(c.approved)button(p,"撤销记录 · "+c.day+" · "+c.title,()->{
            if(!requireParent(this::parentPage))return;
            new AlertDialog.Builder(activity).setTitle("撤销这条记录？").setMessage("已发放的成长星星将退回账本，保留撤销收支。").setNegativeButton("保留记录",null).setPositiveButton("确认撤销",(d,w)->completionAction(c,false)).show();
        });
        button(p,"修改小朋友昵称",this::childName);button(p,"导出成长账本",this::export);button(p,"修改家长 PIN",this::changePin);button(p,"退出家长模式",()->{parentUntil=0;dismiss();});show("家长空间",p,true);
    }
    private void childName(){
        if(!requireParent(this::childName))return;LinearLayout p=form();EditText name=input(p,"小朋友昵称",snapshot().childName,false);
        button(p,"保存昵称",()->{if(!requireParent(this::childName))return;String value=name.getText().toString().trim();if(value.isEmpty()||value.length()>20){name.setError("请填写 1–20 个字符");return;}try{mutate(s->s.childName=value);parentPage();}catch(RuntimeException ignored){}});show("修改小朋友昵称",p,true);
    }
    private void export(){
        if(!requireParent(this::export))return;pendingExport=store.export();
        try{activity.startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json").putExtra(Intent.EXTRA_TITLE,"yaya-family-"+snapshot().day(System.currentTimeMillis())+".json"),EXPORT_REQUEST);}
        catch(RuntimeException e){pendingExport=null;toast("这台设备暂时不能选择导出位置");}
    }
    public void activityResult(int request,int result,Intent data){
        if(request!=EXPORT_REQUEST)return;String value=pendingExport;pendingExport=null;
        if(result!=Activity.RESULT_OK||data==null||data.getData()==null||value==null)return;Uri uri=data.getData();
        worker.execute(()->{String error=null;try(java.io.OutputStream out=activity.getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new java.io.IOException("无法打开导出文件");out.write(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));}catch(Exception e){error=e.getMessage();}
            String message=error==null?"成长账本已导出，不包含 PIN 凭据":"导出失败："+error;main.post(()->{if(!destroyed&&resumed)toast(message);});
        });
    }
    private void completionAction(FamilyState.Completion selected,boolean approve){
        if(!requireParent(this::parentPage))return;
        try{mutate(s->{FamilyState.Completion live=s.completion(selected.taskId,selected.day);if(live!=null){if(approve)s.approve(live,System.currentTimeMillis());else s.undo(live,System.currentTimeMillis());}});parentPage();}catch(RuntimeException ignored){}
    }
    private void taskEditor(FamilyState.Task task){
        if(!requireParent(()->taskEditor(task)))return;LinearLayout p=form();
        EditText title=input(p,"任务名称",task==null?"":task.title,false), reward=input(p,"奖励成长星星（1–100）",task==null?"3":""+task.stars,true);
        note(p,"每天只能提交一次。修改奖励只影响之后的提交，已提交任务按当时的奖励确认。");
        button(p,"保存任务",()->{if(!requireParent(()->taskEditor(task)))return;String name=title.getText().toString().trim();int stars=number(reward,1,100);if(name.isEmpty()||name.length()>40){title.setError("请填写 1–40 个字符");return;}if(stars==Integer.MIN_VALUE)return;
            try{mutate(s->{if(task==null)s.tasks.add(new FamilyState.Task(name,"🌱",stars));else for(FamilyState.Task t:s.tasks)if(t.id.equals(task.id)){t.title=name;t.stars=stars;}});parentPage();}catch(RuntimeException ignored){}
        });show(task==null?"添加每日任务":"修改每日任务",p,true);
    }
    private void changePin(){
        if(!requireParent(this::changePin))return;LinearLayout p=form();EditText a=input(p,"新 PIN","",true),b=input(p,"再次输入新 PIN","",true);
        a.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD);b.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        button(p,"保存新 PIN",()->{if(!requireParent(this::changePin))return;String value=a.getText().toString();if(!value.matches("\\d{4,8}")||!value.equals(b.getText().toString())){a.setError("需 4–8 位数字，且两次一致");return;}
            try{mutate(s->ParentPin.set(s,value));parentUntil=0;dismiss();toast("PIN 已修改，家长模式已锁定");}catch(RuntimeException ignored){}
        });show("修改家长 PIN",p,true);
    }
    private void adjustment(){
        if(!requireParent(this::adjustment))return;LinearLayout p=form();EditText amount=input(p,"成长星星数量（正数补奖、负数扣星）","",true);amount.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_SIGNED);EditText reason=input(p,"补记原因","",false);
        button(p,"确认补记",()->{if(!requireParent(this::adjustment))return;int n=number(amount,-1000,1000);String r=reason.getText().toString().trim();if(n==0||n==Integer.MIN_VALUE){amount.setError("请填写非零整数");return;}if(r.isEmpty()||r.length()>60){reason.setError("请填写 1–60 个字符的原因");return;}
            try{mutate(s->{long now=System.currentTimeMillis();s.ledger.add(new FamilyState.Entry(s.day(now),r,"manual",n,now));});parentPage();}catch(RuntimeException ignored){}
        });show("补奖或扣星",p,true);
    }
    public void showLearningSettings(){gate(this::learningPage);}
    private void learningPage(){
        if(!requireParent(this::learningPage))return;LearningSettings l=learning();LinearLayout p=form();
        note(p,"难度影响综合、单科、孵化、赚点心和陪玩后的题目。语数英比例合计 100%；0% 不出该科题。");
        Spinner chinese=select(p,"语文难度",new String[]{"启蒙 · 单韵母 / 常用字","基础 · 拼音拼读 / 辨字形","挑战 · 四声辨读 / 词语补字"},l.chineseDifficulty);
        Spinner math=select(p,"数学难度",new String[]{"启蒙 · 5 以内加减","基础 · 10 以内加减","挑战 · 20 以内加减"},l.mathDifficulty);
        Spinner english=select(p,"英语难度",new String[]{"启蒙 · 常见词","基础 · 动物 / 颜色 / 日常词","挑战 · 看英文选中文"},l.englishDifficulty);
        EditText cn=input(p,"语文比例（%）",""+l.chinesePercent,true), ma=input(p,"数学比例（%）",""+l.mathPercent,true), en=input(p,"英语比例（%）",""+l.englishPercent,true);
        button(p,"均衡方案 · 语40 / 数40 / 英20",()->preset(cn,ma,en,40,40,20));button(p,"语文重点 · 60 / 30 / 10",()->preset(cn,ma,en,60,30,10));
        button(p,"数学重点 · 30 / 60 / 10",()->preset(cn,ma,en,30,60,10));button(p,"英语重点 · 30 / 30 / 40",()->preset(cn,ma,en,30,30,40));
        EditText pin=input(p,"语文中拼音比例（其余为识字，%）",""+l.pinyinPercent,true);
        Spinner length=select(p,"每轮综合练习题数",new String[]{"5 题","10 题","20 题"},l.sessionLength==5?0:l.sessionLength==10?1:2);
        TextView feedback=text("新方案在下一轮练习生效。",14);p.addView(feedback);Ui.gap(p,12);
        button(p,"保存学习方案",()->{if(!requireParent(this::learningPage))return;LearningSettings n=new LearningSettings();
            n.chineseDifficulty=chinese.getSelectedItemPosition();n.mathDifficulty=math.getSelectedItemPosition();n.englishDifficulty=english.getSelectedItemPosition();
            n.chinesePercent=number(cn,0,100);n.mathPercent=number(ma,0,100);n.englishPercent=number(en,0,100);n.pinyinPercent=number(pin,0,100);n.sessionLength=new int[]{5,10,20}[length.getSelectedItemPosition()];
            try{n.validate();mutate(s->s.learning=n);toast("学习方案已保存");parentPage();}catch(IllegalArgumentException e){feedback.setText(e.getMessage());}catch(RuntimeException ignored){}
        });show("语数英学习方案",p,true);
    }
    private void preset(EditText a,EditText b,EditText c,int x,int y,int z){a.setText(""+x);b.setText(""+y);c.setText(""+z);}
    public void showUsage(boolean editing){if(editing){gate(()->usagePage(true));}else usagePage(false);}
    private void usagePage(boolean editing){
        if(editing&&!requireParent(()->usagePage(true)))return;FamilyState state=snapshot();LinearLayout p=form();
        note(p,"成长星星 "+state.balance()+" ★\n"+state.syncNote+"\n只统计家长选定 App 的前台时长；每天按完整计费单位结算，免费额度每天重新计算。");
        if(!UsageTracker.permitted(activity))button(p,"家长授权用量统计",()->gate(()->{
            dismiss();try{activity.startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS,Uri.parse("package:"+activity.getPackageName())));}catch(RuntimeException e){activity.startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));}
        }));else button(p,syncing?"正在刷新…":"刷新用量",()->syncUsage(()->usagePage(editing&&parent())));
        if(editing)button(p,"添加娱乐 App",this::chooseApp);
        String today=state.day(System.currentTimeMillis());int active=0;
        for(FamilyState.Rule r:state.rules)if(r.end==0){active++;long used=0;int charged=0;for(FamilyState.Rule v:state.rules)if(v.packageName.equals(r.packageName)){used+=state.usageToday(v,today);charged+=state.billed.getOrDefault(v.id+"|"+today,0);}
            note(p,r.label+" · 今日 "+used/60000+" 分 "+used/1000%60+" 秒 · 已扣 "+charged+" ★\n每天免费 "+r.freeMinutes+" 分钟，之后每 "+r.unitMinutes+" 分钟扣 "+r.stars+" ★");
            if(editing){button(p,"修改约定 · "+r.label,()->ruleEditor(r.packageName,r.label,r));button(p,"停止统计 · "+r.label,()->{
                if(!requireParent(()->usagePage(true)))return;
                new AlertDialog.Builder(activity).setTitle("停止统计？").setMessage("先结算已用时长，保留历史约定和扣星记录。").setNegativeButton("继续统计",null).setPositiveButton("停止统计",(d,w)->{if(requireParent(()->usagePage(true)))queueRuleChange(()->store.change(s->{for(FamilyState.Rule v:s.rules)if(v.id.equals(r.id))v.end=System.currentTimeMillis();}));}).show();
            });}
        }
        if(active==0)note(p,"还没有娱乐 App 约定，请家长按家庭需要添加。");
        note(p,"后台约每 15 分钟尝试结算，系统省电可能延后；打开小屋时会补算。只补算最近 48 小时可用事件。成长星星可以暂时欠一点，基础养宠物和学习继续进行。");
        show("娱乐 App 约定",p,editing);
    }
    private void chooseApp(){
        if(!requireParent(this::chooseApp))return;List<ResolveInfo> apps=activity.getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0);
        apps.sort(Comparator.comparing(a->a.loadLabel(activity.getPackageManager()).toString()));List<String> packages=new ArrayList<>(),labels=new ArrayList<>();
        for(ResolveInfo app:apps){String pkg=app.activityInfo.packageName;if(!pkg.equals(activity.getPackageName())&&!packages.contains(pkg)){packages.add(pkg);labels.add(app.loadLabel(activity.getPackageManager()).toString());}}
        if(packages.isEmpty()){toast("这台平板没有可选择的 App");return;}
        dismiss();AlertDialog picker=new AlertDialog.Builder(activity).setTitle("选择娱乐 App").setItems(labels.toArray(new String[0]),(d,i)->{
            if(!requireParent(this::chooseApp))return;FamilyState.Rule old=null;for(FamilyState.Rule r:snapshot().rules)if(r.end==0&&r.packageName.equals(packages.get(i)))old=r;ruleEditor(packages.get(i),labels.get(i),old);
        }).setNegativeButton("取消",null).create();dialog=picker;protectedScreen=true;picker.setOnDismissListener(d->{if(dialog==picker){dialog=null;protectedScreen=false;}});picker.show();resize();
    }
    private void ruleEditor(String pkg,String label,FamilyState.Rule old){
        if(!requireParent(()->ruleEditor(pkg,label,old)))return;LinearLayout p=form();note(p,label+"\n新约定从保存时开始，不追扣之前的用量；修改前先按旧约定结算，之后的免费额度重新计算。");
        EditText free=input(p,"每日免费分钟（0–1440）",old==null?"0":""+old.freeMinutes,true),unit=input(p,"每次计费分钟（1–1440）",old==null?"10":""+old.unitMinutes,true),stars=input(p,"每次扣成长星星（1–100）",old==null?"1":""+old.stars,true);
        button(p,"保存 App 约定",()->{if(!requireParent(()->ruleEditor(pkg,label,old)))return;int f=number(free,0,1440),u=number(unit,1,1440),n=number(stars,1,100);if(f<0||u<1||n<1)return;
            queueRuleChange(()->store.change(s->{long now=System.currentTimeMillis();for(FamilyState.Rule r:s.rules)if(r.packageName.equals(pkg)&&r.end==0)r.end=now;s.rules.add(new FamilyState.Rule(pkg,label,f,u,n,now));}));
        });show("设置 App 约定",p,true);
    }
    private void queueRuleChange(Runnable change){
        if(syncing){toast("正在结算，请稍候");return;}syncing=true;
        worker.execute(()->{String error=null;try{synchronized(UsageTracker.class){UsageTracker.sync(activity);change.run();UsageTracker.sync(activity);}UsageJobService.schedule(activity);}catch(RuntimeException e){error=e.getMessage();}
            String result=error;main.post(()->{if(destroyed)return;syncing=false;host.changed();if(resumed){if(result!=null)toast(result);else usagePage(parent());}});
        });
    }
    private void syncUsage(Runnable after){
        if(syncing||destroyed)return;syncing=true;
        worker.execute(()->{String error=null;try{UsageTracker.sync(activity);}catch(RuntimeException e){error=e.getMessage();}
            String result=error;main.post(()->{if(destroyed)return;syncing=false;host.changed();if(resumed){if(result!=null)toast(result);if(after!=null)after.run();}});
        });
    }
    public void onResume(){resumed=true;if(UsageTracker.permitted(activity))UsageJobService.schedule(activity);syncUsage(null);checkLock();}
    public void onTick(){checkLock();syncUsage(null);}
    private void checkLock(){if(protectedScreen&&!parent()){dismiss();toast("家长模式已锁定，请重新进入");}}
    public void onPause(){resumed=false;parentUntil=0;dismiss();}
    public void destroy(){destroyed=true;worker.shutdownNow();main.removeCallbacksAndMessages(null);dismiss();}
}
