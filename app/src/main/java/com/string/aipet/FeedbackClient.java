package com.string.aipet;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Device-scoped feedback access. The Remote Agent project credential never reaches Android. */
public final class FeedbackClient {
    public static final class Unauthorized extends IOException {
        Unauthorized() { super("连接凭据无效，请让家长重新配对"); }
    }
    public static final String BASE = "https://home.sunny-string.cn/ai-pet-feedback";
    private final SharedPreferences preferences;
    private final String base;
    public FeedbackClient(Context context) {
        preferences = context.getSharedPreferences("app-feedback", Context.MODE_PRIVATE);
        base = BuildConfig.DEBUG && !BuildConfig.FEEDBACK_TEST_URL.isEmpty() ? BuildConfig.FEEDBACK_TEST_URL : BASE;
    }
    public boolean paired() { return !preferences.getString("deviceToken", "").isEmpty(); }
    public String draftTitle() { return preferences.getString("draftTitle", ""); }
    public String draftMessage() { return preferences.getString("draftMessage", ""); }
    public void pair(String code) throws Exception {
        JSONObject result = request("POST", "/pair", new JSONObject().put("code", code.trim()).put("deviceName", "家庭平板"), "");
        String token = result.getString("deviceToken");
        if (token.length() < 16 || !preferences.edit().putString("deviceToken", token).commit()) throw new IOException("无法保存设备连接，请重新配对");
    }
    public JSONArray list() throws Exception { return authenticated("GET", "/feedback", null).getJSONArray("items"); }
    public JSONObject get(String id) throws Exception { return authenticated("GET", path(id), null); }
    public JSONObject submit(String title, String message) throws Exception {
        title = title.trim(); message = message.trim();
        if (title.isEmpty() || title.length() > 80 || message.isEmpty() || message.getBytes(StandardCharsets.UTF_8).length > 12000) throw new IOException("请填写标题（最多 80 字）和建议（最多 4000 个汉字）");
        String requestId = preferences.getString("draftRequestId", "");
        if (requestId.isEmpty() || !title.equals(draftTitle()) || !message.equals(draftMessage())) requestId = UUID.randomUUID().toString();
        if (!preferences.edit().putString("draftRequestId", requestId).putString("draftTitle", title).putString("draftMessage", message).commit()) throw new IOException("无法保存建议草稿，请重试");
        JSONObject result = authenticated("POST", "/feedback", new JSONObject().put("clientRequestId", requestId).put("title", title).put("message", message)
                .put("versionName", BuildConfig.VERSION_NAME).put("versionCode", BuildConfig.VERSION_CODE));
        preferences.edit().remove("draftRequestId").remove("draftTitle").remove("draftMessage").commit();
        return result;
    }
    public JSONObject approve(String id, int revision) throws Exception { return authenticated("POST", path(id) + "/approve", new JSONObject().put("revision", revision)); }
    public JSONObject revise(String id, int revision, String message) throws Exception { return authenticated("POST", path(id) + "/revise", new JSONObject().put("revision", revision).put("message", message)); }
    public JSONObject reopen(String id, String message) throws Exception { return authenticated("POST", path(id) + "/reopen", new JSONObject().put("message", message)); }
    public void installed() throws Exception {
        if (!paired()) return;
        authenticated("POST", "/installed", new JSONObject().put("versionName", BuildConfig.VERSION_NAME).put("versionCode", BuildConfig.VERSION_CODE));
    }
    private static String path(String id) throws IOException {
        if (!id.matches("[A-Za-z0-9_-]{1,128}")) throw new IOException("建议编号无效，请刷新列表");
        return "/feedback/" + id;
    }
    private JSONObject authenticated(String method, String path, JSONObject body) throws Exception {
        if (!paired()) throw new IOException("请先输入家庭配对码，连接意见服务");
        return request(method, path, body, preferences.getString("deviceToken", ""));
    }
    private JSONObject request(String method, String path, JSONObject body, String token) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(base + path).openConnection();
        connection.setRequestMethod(method); connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(10000); connection.setReadTimeout(25000); connection.setUseCaches(false);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "YayaHome/" + BuildConfig.VERSION_NAME);
        if (!token.isEmpty()) connection.setRequestProperty("Authorization", "Bearer " + token);
        try {
            if (body != null) {
                byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
                connection.setDoOutput(true); connection.setRequestProperty("Content-Type", "application/json; charset=utf-8"); connection.setFixedLengthStreamingMode(payload.length);
                try (java.io.OutputStream stream = connection.getOutputStream()) { stream.write(payload); }
            }
            int code = connection.getResponseCode();
            InputStream response = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
            JSONObject result = new JSONObject(read(response));
            if (code < 200 || code >= 300) {
                if (code == 401) throw new Unauthorized();
                if (code == 409) throw new IOException("方案或任务状态已变化，请刷新后再确认");
                throw new IOException("意见服务暂不可用（" + code + "），请稍后刷新；不要重复确认执行");
            }
            return result;
        } finally { connection.disconnect(); }
    }
    private static String read(InputStream stream) throws IOException {
        if (stream == null) return "{}";
        try (InputStream input = stream; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int count;
            while ((count = input.read(buffer)) != -1) { if (out.size() + count > 1024 * 1024) throw new IOException("意见响应过大，请联系家长"); out.write(buffer, 0, count); }
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }
}
