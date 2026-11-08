package com.string.aipet;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import com.string.aipet.ui.Ui;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Parent-only idea notebook. Network work never opens a closed or background screen. */
public final class FeedbackController {
    private final Activity activity;
    private final Consumer<Runnable> parentGate;
    private final Runnable showUpdates;
    private final FeedbackClient client;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private AlertDialog dialog;
    private boolean busy, resumed, destroyed;
    private int generation;
    public FeedbackController(Activity activity, Consumer<Runnable> parentGate, Runnable showUpdates) {
        this.activity = activity; this.parentGate = parentGate; this.showUpdates = showUpdates; client = new FeedbackClient(activity);
    }
    public void show() { if (ready()) parentGate.accept(() -> { if (client.paired()) list(); else pairing(); }); }
    private boolean ready() { return !destroyed && resumed && !activity.isFinishing(); }
    private int dp(int value) { return Ui.dp(activity, value); }
    private LinearLayout form() { LinearLayout view = Ui.column(activity); view.setPadding(dp(24), dp(16), dp(24), dp(16)); return view; }
    private void note(LinearLayout view, String value) { TextView text = Ui.text(activity, value, 15, Ui.INK, false); text.setLineSpacing(dp(5), 1); view.addView(text); Ui.gap(view, 12); }
    private void button(LinearLayout view, String label, Runnable action) {
        android.widget.Button button = Ui.button(activity, label, Ui.GREEN, () -> { if (ready() && !busy) parentGate.accept(action); });
        button.setMinHeight(dp(50)); view.addView(button); Ui.gap(view, 8);
    }
    private EditText input(LinearLayout view, String label, String value, int maximum, boolean multiline) {
        note(view, label); EditText edit = new EditText(activity); edit.setText(value); edit.setContentDescription(label); edit.setTextSize(18);
        edit.setSingleLine(!multiline); edit.setInputType(InputType.TYPE_CLASS_TEXT | (multiline ? InputType.TYPE_TEXT_FLAG_MULTI_LINE : 0));
        edit.setFilters(new InputFilter[]{new InputFilter.LengthFilter(maximum)});
        if (multiline) { edit.setMinLines(4); edit.setGravity(android.view.Gravity.TOP); }
        view.addView(edit, new LinearLayout.LayoutParams(-1, multiline ? dp(150) : dp(56))); Ui.gap(view, 12); return edit;
    }
    private void display(String title, LinearLayout view) {
        dismiss(); if (!ready()) return;
        ScrollView scroll = new ScrollView(activity); scroll.addView(view);
        dialog = new AlertDialog.Builder(activity).setTitle(title).setView(scroll).setNegativeButton("回到小屋", null).create();
        AlertDialog shown = dialog; shown.setOnDismissListener(d -> { if (dialog == shown) { dialog = null; generation++; } });
        shown.show(); shown.getWindow().setLayout(Math.min(dp(760), activity.getResources().getDisplayMetrics().widthPixels - dp(32)), -2);
    }
    private void pairing() {
        LinearLayout view = form(); note(view, "输入家庭配对码，连接意见与改进服务。只上传你填写的建议和应用版本，不上传宠物存档或学习记录。");
        EditText code = input(view, "家庭配对码", "", 64, false);
        button(view, "连接意见服务", () -> perform(() -> { client.pair(code.getText().toString()); return null; }, value -> list()));
        display("连接意见服务", view);
    }
    private void list() {
        if (!ready()) return;
        LinearLayout loading = form(); note(loading, "正在读取意见和方案…"); button(loading, "重新读取意见", this::list); display("意见与改进", loading);
        perform(() -> { client.installed(); return client.list(); }, items -> {
            LinearLayout view = form(); note(view, "提交想法 → 查看方案 → 家长确认执行 → 查看发布和更新进度。");
            button(view, "提出新建议", this::compose); button(view, "刷新意见列表", this::list);
            if (items.length() == 0) note(view, "还没有建议。你希望芽芽小屋增加什么，或哪里需要改进？");
            for (int i = 0; i < items.length(); i++) { JSONObject item = items.optJSONObject(i); if (item == null) continue;
                String id = item.optString("id"); button(view, item.optString("title") + " · " + status(item.optString("status")), () -> detail(id));
            }
            display("意见与改进", view);
        });
    }
    private void compose() {
        LinearLayout view = form(); EditText title = input(view, "建议标题", client.draftTitle(), 80, false);
        EditText message = input(view, "问题和期望的改进", client.draftMessage(), 4000, true);
        note(view, "例如：希望每天的学习题更丰富，完成一轮后能看见答题记录。请不要填写姓名、电话等个人信息。");
        button(view, "提交并整理方案", () -> perform(() -> client.submit(title.getText().toString(), message.getText().toString()), item -> renderDetail(item)));
        button(view, "返回意见列表", this::list); display("提出新建议", view);
    }
    private void detail(String id) { perform(() -> client.get(id), this::renderDetail); }
    private void renderDetail(JSONObject item) {
        LinearLayout view = form(); String id = item.optString("id"), state = item.optString("status"); int revision = item.optInt("revision");
        note(view, "状态：" + status(state) + "\n" + item.optString("statusMessage")); note(view, item.optString("message"));
        JSONObject plan = item.optJSONObject("plan");
        if (plan != null) { note(view, "方案第 " + revision + " 版\n" + plan.optString("summary")); lines(view, "计划修改", plan.optJSONArray("changes")); lines(view, "验收标准", plan.optJSONArray("acceptance")); lines(view, "需要留意", plan.optJSONArray("risks")); }
        if (state.equals("awaiting_approval")) {
            button(view, "确认方案并执行", () -> confirm(item));
        }
        if (state.equals("awaiting_approval") || state.equals("planning_failed")) {
            button(view, "补充或修改建议", () -> supplement(id, revision, false));
        }
        if (state.equals("failed")) button(view, "补充说明并重新规划", () -> supplement(id, revision, true));
        if (state.equals("resolved")) button(view, "仍有问题，重新打开", () -> supplement(id, revision, true));
        if (!item.optString("releaseVersion").isEmpty()) note(view, "实现版本：" + item.optString("releaseVersion"));
        if (state.equals("published")) button(view, "检查并安装新版", () -> { dismiss(); showUpdates.run(); });
        if (!item.optString("sessionUrl").isEmpty()) note(view, "任务已交给家庭电脑，可在 Remote Agent 对应项目中查看详细执行记录。");
        button(view, "刷新方案进度", () -> detail(id)); button(view, "返回意见列表", this::list);
        display(item.optString("title", "建议详情"), view);
    }
    private void lines(LinearLayout view, String title, JSONArray lines) {
        if (lines == null || lines.length() == 0) return;
        StringBuilder text = new StringBuilder(title); for (int i = 0; i < lines.length(); i++) text.append("\n• ").append(lines.optString(i)); note(view, text.toString());
    }
    private void confirm(JSONObject item) {
        if (!ready()) return;
        String id = item.optString("id"); int revision = item.optInt("revision");
        new AlertDialog.Builder(activity).setTitle("确认执行第 " + revision + " 版方案？")
                .setMessage("家庭电脑将按当前方案修改应用、测试并发布新版。安装仍需家长确认。确认后可在这里刷新进度。")
                .setNegativeButton("再看看", null).setPositiveButton("确认执行", (dialog, which) -> {
                    if (ready() && !busy) parentGate.accept(() -> perform(() -> client.approve(id, revision), this::renderDetail));
                }).show();
    }
    private void supplement(String id, int revision, boolean reopen) {
        LinearLayout view = form(); EditText extra = input(view, "补充说明", "", 4000, true);
        button(view, reopen ? "重新打开并整理方案" : "按补充内容重新整理", () -> {
            String message = extra.getText().toString().trim(); if (message.isEmpty()) { extra.setError("请写下需要改进的内容"); return; }
            perform(() -> reopen ? client.reopen(id, message) : client.revise(id, revision, message), this::renderDetail);
        }); button(view, "返回方案", () -> detail(id)); display("补充建议", view);
    }
    private interface Work<T> { T run() throws Exception; }
    private <T> void perform(Work<T> work, Consumer<T> success) {
        if (!ready() || busy) return; busy = true; int current = generation;
        worker.execute(() -> {
            try { T value = work.run(); main.post(() -> { busy = false; if (ready() && current == generation && dialog != null) success.accept(value); }); }
            catch (Exception failure) { main.post(() -> { busy = false; if (ready() && current == generation) {
                if (failure instanceof FeedbackClient.Unauthorized) { parentGate.accept(this::pairing); return; }
                String message = failure instanceof java.net.SocketTimeoutException ? "网络响应超时，请先刷新进度确认结果，避免重复执行。" : failure.getMessage();
                Toast.makeText(activity, message == null ? "连接失败，请稍后重试" : message, Toast.LENGTH_LONG).show();
            } }); }
        });
    }
    static String status(String value) {
        switch (value) {
            case "planning": return "正在整理方案"; case "awaiting_approval": return "等待家长确认"; case "dispatching": return "正在提交任务";
            case "executing": return "正在修改应用"; case "testing": return "正在测试与打包"; case "published": return "已发布，等待更新";
            case "resolved": return "已解决"; case "planning_failed": return "方案整理失败，可补充重试"; case "failed": return "执行失败，请查看说明";
            case "dispatch_uncertain": return "接收状态待核对，请在工作台查证"; default: return "处理中";
        }
    }
    public void onResume() {
        resumed = true;
        if (client.paired() && !destroyed) worker.execute(() -> { try { client.installed(); } catch (Exception ignored) { /* Try on the next foreground entry. */ } });
    }
    public void onPause() { resumed = false; dismiss(); }
    private void dismiss() { generation++; if (dialog != null) { dialog.dismiss(); dialog = null; } }
    public void destroy() { destroyed = true; dismiss(); worker.shutdownNow(); main.removeCallbacksAndMessages(null); }
}
