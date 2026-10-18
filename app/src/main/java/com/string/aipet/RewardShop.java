package com.string.aipet;

import android.app.Activity;
import android.app.AlertDialog;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import com.string.aipet.model.PetState;
import com.string.aipet.model.RewardCatalog;
import com.string.aipet.ui.Ui;

/** Children choose one named, colored wallet for each purchase. */
public final class RewardShop {
    public interface Host {
        PetState pet(); int growthStars();
        void buyKnowledge(String id); void buyGrowth(String id); void toggleDecoration(String id);
    }
    private final Activity activity;
    private final Host host;
    private AlertDialog dialog;
    public RewardShop(Activity activity,Host host){this.activity=activity;this.host=host;}
    private LinearLayout column(){LinearLayout p=Ui.column(activity);int pad=Ui.dp(activity,20);p.setPadding(pad,pad,pad,pad);return p;}
    private void note(LinearLayout p,String value,int color){TextView t=Ui.text(activity,value,16,color,false);t.setLineSpacing(Ui.dp(activity,4),1);p.addView(t);Ui.gap(p,12);}
    private Button button(LinearLayout p,String title,int background,Runnable action){Button b=Ui.button(activity,title,background,action);p.addView(b,new LinearLayout.LayoutParams(-1,-2));Ui.gap(p,10);return b;}
    private void balances(LinearLayout p){
        note(p,"★ 金色知识星星  "+host.pet().stars+"  · 答题获得",Ui.KNOWLEDGE_GOLD);
        note(p,"★ 绿色成长星星  "+host.growthStars()+"  · 生活约定获得",Ui.GROWTH_GREEN);
    }
    public void show(){
        LinearLayout p=column();balances(p);
        note(p,"挑一份喜欢的礼物，再选择花哪种星星。",Ui.MUTED);
        for(RewardCatalog.Item item:RewardCatalog.all()){
            boolean owned=item.decoration&&host.pet().decorations.contains(item.id);
            button(p,item.title+(owned?" · 已拥有":" · "+item.cost+" ★"),owned?0xFFE2EAD9:0xFFF0EBDD,()->detail(item.id));
        }
        showDialog("装扮与成长",p,"回到小屋",this::dismiss);
    }
    private void detail(String id){
        RewardCatalog.Item item=RewardCatalog.get(id);LinearLayout p=column();balances(p);note(p,item.description,Ui.INK);
        if(item.decoration&&host.pet().decorations.contains(id)){
            boolean equipped=host.pet().equippedDecorations.contains(id);
            note(p,equipped?"已经布置在小屋里啦，可以免费收起。":"已经拥有啦，随时免费布置。",Ui.MUTED);
            button(p,equipped?"收起装扮":"布置装扮",0xFFE2EAD9,()->{
                try{host.toggleDecoration(id);detail(id);}catch(RuntimeException e){error(e);}
            });
        }else{
            note(p,"任选一种，每次花 "+item.cost+" 颗星星。",Ui.MUTED);
            if(!host.pet().hatched())note(p,"先孵化小伙伴，再一起拆礼物吧。",Ui.MUTED);
            Button gold=button(p,"用金色知识星星兑换 · "+item.cost+" ★",0xFFF3E4BB,()->purchase(id,false));gold.setTextColor(Ui.KNOWLEDGE_GOLD);
            boolean enoughGold=host.pet().stars>=item.cost;gold.setEnabled(host.pet().hatched()&&enoughGold);
            if(!enoughGold)note(p,"金色还差 "+(item.cost-host.pet().stars)+" 颗，答题就能收集。",Ui.KNOWLEDGE_GOLD);
            Button green=button(p,"用绿色成长星星兑换 · "+item.cost+" ★",0xFFDDEBD8,()->purchase(id,true));green.setTextColor(Ui.GROWTH_GREEN);
            boolean enoughGreen=host.growthStars()>=item.cost;green.setEnabled(host.pet().hatched()&&enoughGreen);
            if(!enoughGreen)note(p,"绿色还差 "+(item.cost-host.growthStars())+" 颗，完成生活约定后请家长确认。",Ui.GROWTH_GREEN);
        }
        showDialog(item.title,p,"返回礼物",this::show);
    }
    private void purchase(String id,boolean growth){
        try{
            if(growth)host.buyGrowth(id);else host.buyKnowledge(id);
            Toast.makeText(activity,"兑换成功！"+(id.equals("growth_fruit")?"小伙伴升了 1 级！":"礼物已送到小屋。"),Toast.LENGTH_SHORT).show();detail(id);
        }catch(RuntimeException e){error(e);detail(id);}
    }
    private void error(RuntimeException e){Toast.makeText(activity,e.getMessage()==null?"保存失败，请稍后再试":e.getMessage(),Toast.LENGTH_LONG).show();}
    private void showDialog(String title,LinearLayout p,String back,Runnable action){
        dismiss();ScrollView scroll=new ScrollView(activity);scroll.addView(p);
        dialog=new AlertDialog.Builder(activity).setTitle(title).setView(scroll).setNegativeButton(back,(d,w)->action.run()).create();dialog.show();resize();
    }
    public void resize(){if(dialog!=null&&dialog.isShowing()&&dialog.getWindow()!=null){int width=Math.min(Ui.dp(activity,680),activity.getResources().getDisplayMetrics().widthPixels-Ui.dp(activity,40));dialog.getWindow().setLayout(width,ViewGroup.LayoutParams.WRAP_CONTENT);}}
    public void dismiss(){if(dialog!=null){dialog.dismiss();dialog=null;}}
}
