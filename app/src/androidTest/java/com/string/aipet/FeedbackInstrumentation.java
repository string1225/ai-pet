package com.string.aipet;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.inspector.WindowInspector;
import android.widget.EditText;
import android.widget.TextView;
import com.string.aipet.family.FamilyStore;
import com.string.aipet.family.ParentPin;
import com.string.aipet.model.PetState;
import com.string.aipet.model.PetStore;
import org.json.JSONObject;
import java.lang.reflect.Field;
import java.util.List;

/** Disposable API 29+ emulator. Fixture never dispatches a real Remote Agent task. */
@android.annotation.TargetApi(29)
public final class FeedbackInstrumentation extends Instrumentation {
    private MainActivity activity;
    private int checks;
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            if (!BuildConfig.DEBUG || !BuildConfig.FEEDBACK_TEST_URL.startsWith("http://127.0.0.1:")) throw new AssertionError("Use debug loopback fixture on disposable emulator");
            integration(); result.putString("stream", "PASS: " + checks + " feedback checks\n"); finish(Activity.RESULT_OK, result);
        } catch (Throwable error) { result.putString("stream", "FAIL: " + android.util.Log.getStackTraceString(error)); finish(Activity.RESULT_CANCELED, result); }
    }
    private void check(boolean value, String message) { if (!value) throw new AssertionError(message); checks++; Bundle result = new Bundle(); result.putString("stream", "PASS " + message + "\n"); sendStatus(0, result); }
    private void run(Runnable action) { Throwable[] error = {null}; runOnMainSync(() -> { try { action.run(); } catch (Throwable failure) { error[0] = failure; } }); waitForIdleSync(); if (error[0] != null) throw new AssertionError("View action failed", error[0]); }
    private View find(View view, String label, boolean clickable) {
        boolean matches = label.contentEquals(view.getContentDescription() == null ? "" : view.getContentDescription()) || view instanceof TextView && label.contentEquals(((TextView) view).getText());
        if (matches && (!clickable || view.isClickable())) return view;
        if (view instanceof ViewGroup) { ViewGroup group = (ViewGroup) view; for (int i = 0; i < group.getChildCount(); i++) { View hit = find(group.getChildAt(i), label, clickable); if (hit != null) return hit; } }
        return null;
    }
    private View find(String label, boolean clickable) { List<View> roots = WindowInspector.getGlobalWindowViews(); return roots.isEmpty() ? null : find(roots.get(roots.size() - 1), label, clickable); }
    private void click(String label) { run(() -> { View view = find(label, true); if (view == null) throw new AssertionError("Missing button " + label); view.performClick(); }); }
    private EditText edit(View view, String label) {
        if (view instanceof EditText && label.contentEquals(view.getContentDescription() == null ? "" : view.getContentDescription())) return (EditText) view;
        if (view instanceof ViewGroup) { ViewGroup group = (ViewGroup) view; for (int i = 0; i < group.getChildCount(); i++) { EditText hit = edit(group.getChildAt(i), label); if (hit != null) return hit; } }
        return null;
    }
    private void field(String label, String value) { run(() -> { List<View> roots = WindowInspector.getGlobalWindowViews(); EditText view = roots.isEmpty() ? null : edit(roots.get(roots.size()-1), label); if (view == null) throw new AssertionError("Missing input " + label); view.setText(value); }); }
    private void await(String label) throws Exception { long until = System.currentTimeMillis() + 20000; while (System.currentTimeMillis() < until && find(label, false) == null) Thread.sleep(100); check(find(label, false) != null, "Visible: " + label); }
    private void integration() throws Exception {
        getTargetContext().getSharedPreferences("app-feedback", 0).edit().clear().commit();
        FamilyStore family = FamilyStore.get(getTargetContext()); family.change(state -> ParentPin.set(state, "1357"));
        PetState before = new PetStore(getTargetContext()).load(); int balance = family.snapshot().balance();
        activity = (MainActivity) startActivitySync(new Intent(getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK)); waitForIdleSync();
        click("设置"); click("意见与改进"); await("家长 PIN"); field("家长 PIN", "1357"); click("进入家长空间");
        await("家庭配对码"); field("家庭配对码", "TEST-FAMILY-2026"); click("连接意见服务"); await("提出新建议");
        click("提出新建议"); field("建议标题", "模拟器建议：丰富题目"); field("问题和期望的改进", "希望增加更多数学题，并保留已有学习记录。"); click("提交并整理方案"); await("刷新方案进度");
        FeedbackClient client = new FeedbackClient(getTargetContext()); check(client.paired(), "Pairing persists a device credential");
        String id = client.list().getJSONObject(0).getString("id");
        JSONObject item = waitStatus(client, id, "awaiting_approval"); click("刷新方案进度"); await("确认方案并执行");
        int revision = item.getInt("revision"); click("补充或修改建议"); field("补充说明", "请优先增加 20 以内加减法。"); click("按补充内容重新整理");
        long revisionDeadline = System.currentTimeMillis() + 20000;
        do { item = client.get(id); if (item.getInt("revision") > revision) break; Thread.sleep(100); } while (System.currentTimeMillis() < revisionDeadline);
        item = waitStatus(client, id, "awaiting_approval"); check(item.getInt("revision") > revision, "Supplement creates a new plan revision");
        click("刷新方案进度"); await("确认方案并执行"); click("确认方案并执行");
        Field familyField = MainActivity.class.getDeclaredField("family"); familyField.setAccessible(true); Object controller = familyField.get(activity);
        Field expiry = FamilyController.class.getDeclaredField("parentUntil"); expiry.setAccessible(true); expiry.setLong(controller, 0);
        click("确认执行"); await("家长 PIN");
        check(client.get(id).getString("status").equals("awaiting_approval"), "Expired parent session cannot approve from a stale confirmation dialog");
        field("家长 PIN", "1357"); click("进入家长空间");
        waitStatus(client, id, "executing"); check(!client.get(id).getString("status").equals("resolved"), "Accepted external task is not marked resolved");
        PetState after = new PetStore(getTargetContext()).load(); check(before.name.equals(after.name) && before.stars == after.stars && family.snapshot().balance() == balance, "Feedback leaves pet and family rewards intact");
        click("回到小屋"); click("设置"); click("检查应用更新");
        await("自动检查并下载新版（安装需家长确认）");
        check(!getTargetContext().getSharedPreferences("app-update", 0).getBoolean("automatic", false), "Automatic downloads are off until a parent opts in");
    }
    private JSONObject waitStatus(FeedbackClient client, String id, String wanted) throws Exception {
        long until = System.currentTimeMillis() + 20000; JSONObject item;
        do { item = client.get(id); if (wanted.equals(item.optString("status"))) return item; Thread.sleep(150); } while (System.currentTimeMillis() < until);
        throw new AssertionError("Expected " + wanted + ", got " + item.optString("status"));
    }
}
