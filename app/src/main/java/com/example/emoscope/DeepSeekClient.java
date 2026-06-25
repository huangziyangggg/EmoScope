package com.example.emoscope;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * DeepSeek AI 调用客户端 — 从 MainActivity 提取。
 * 封装请求构建、指数退避重试、响应解析的完整管线。
 */
public class DeepSeekClient {

    public interface AiCallback {
        void onAiStarted();
        void onAiResponse(String replyText, String faceProbs, String spokenText,
                          String lightDesc, boolean isPositive);
        void onAiError(String errorMessage);
    }

    private final Context context;
    private final OkHttpClient httpClient;
    private final ExecutorService executor;
    private final EmoDatabaseHelper dbHelper;
    private final AiCallback callback;
    private String apiKey;
    private volatile boolean isInProgress = false;

    public DeepSeekClient(Context context, ExecutorService executor,
                          EmoDatabaseHelper dbHelper, AiCallback callback) {
        this.context = context;
        this.executor = executor;
        this.dbHelper = dbHelper;
        this.callback = callback;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(Constants.CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
                .readTimeout(Constants.READ_TIMEOUT_SEC, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
    }

    public void setApiKey(String key) { this.apiKey = key; }

    public boolean isInProgress() { return isInProgress; }

    /** 发起 AI 请求 */
    public void call(String faceProbs, String spokenText, String speedDesc, String lightDesc) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            callback.onAiError(context.getString(R.string.ai_no_api_key));
            return;
        }
        boolean acknowledged = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(Constants.KEY_AI_DATA_CONSENT, false);
        if (!AiDataConsentPolicy.canSendToExternalAi(apiKey, acknowledged)) {
            callback.onAiError(context.getString(R.string.ai_consent_required));
            return;
        }
        if (isInProgress) {
            callback.onAiError(context.getString(R.string.ai_busy));
            return;
        }
        isInProgress = true;
        callback.onAiStarted();

        String longTermMemory = fetchRecentMemory();

        try {
            JSONObject jsonBody = new JSONObject();
            jsonBody.put("model", Constants.DEEPSEEK_MODEL);
            jsonBody.put("temperature", Constants.AI_TEMPERATURE);
            jsonBody.put("max_tokens", Constants.AI_MAX_TOKENS);
            jsonBody.put("frequency_penalty", Constants.AI_FREQUENCY_PENALTY);

            JSONArray messages = new JSONArray();
            JSONObject systemMsg = new JSONObject();
            systemMsg.put("role", "system");
            systemMsg.put("content",
                    "你是一位极其温柔、富有同理心的情感疗愈师。" +
                    "请结合用户的【面部表情概率】、【环境光照】、【声带语速】和【长程记忆】进行综合分析。" +
                    "要求：① 先简短共情（1句），再给出积极的解读或建议（1-2句）" +
                    "② 如果检测到负面情绪，优先安抚而非分析 " +
                    "③ 总字数严格控制在80字以内 " +
                    "④ 不要使用\"你应该\"等说教句式，用\"或许可以\"\"不妨试试\"等柔软表达。");
            messages.put(systemMsg);

            JSONObject userMsg = new JSONObject();
            userMsg.put("role", "user");
            userMsg.put("content", "面部:" + faceProbs + " | 环境:" + lightDesc +
                    " | 语速:" + speedDesc + " | 话语:" + spokenText +
                    "\n【记忆】\n" + longTermMemory + "\n请安抚我。");
            messages.put(userMsg);
            jsonBody.put("messages", messages);

            RequestBody body = RequestBody.create(jsonBody.toString(),
                    MediaType.get("application/json; charset=utf-8"));
            Request request = new Request.Builder()
                    .url(Constants.DEEPSEEK_API_URL)
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(body).build();

            executeRequest(request, faceProbs, spokenText, speedDesc, lightDesc, 0);
        } catch (Exception e) {
            Log.e(Constants.TAG, "DeepSeek request build error", e);
            isInProgress = false;
            callback.onAiError(context.getString(R.string.ai_request_failed));
        }
    }

    /** 指数退避重试执行器 */
    private void executeRequest(Request request, String faceProbs, String spokenText,
                                String speedDesc, String lightDesc, int attempt) {
        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(Constants.TAG, "DeepSeek attempt " + (attempt + 1) + " failed", e);
                if (attempt < Constants.AI_MAX_RETRIES - 1) {
                    long delay = Constants.AI_RETRY_BASE_DELAY_MS * (1L << attempt);
                    Log.i(Constants.TAG, "Retrying in " + delay + "ms");
                    executor.execute(() -> {
                        try { Thread.sleep(delay); } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            isInProgress = false;
                            return;
                        }
                        executeRequest(request, faceProbs, spokenText, speedDesc, lightDesc, attempt + 1);
                    });
                } else {
                    isInProgress = false;
                    callback.onAiError(networkErrorMessage(e));
                }
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try {
                    okhttp3.ResponseBody body = response.body();
                    String respStr = "";
                    if (body != null) {
                        respStr = body.string();
                    }
                    if (!response.isSuccessful()) {
                        Log.e(Constants.TAG, "DeepSeek HTTP " + response.code() + ": " + respStr);
                        if (attempt < Constants.AI_MAX_RETRIES - 1 && response.code() >= 500) {
                            long delay = Constants.AI_RETRY_BASE_DELAY_MS * (1L << attempt);
                            executor.execute(() -> {
                                try { Thread.sleep(delay); } catch (InterruptedException ie) {
                                    Thread.currentThread().interrupt();
                                    isInProgress = false;
                                    return;
                                }
                                executeRequest(request, faceProbs, spokenText, speedDesc, lightDesc, attempt + 1);
                            });
                            return;
                        }
                        isInProgress = false;
                        callback.onAiError(httpErrorMessage(response.code()));
                        return;
                    }
                    JSONObject respJson = new JSONObject(respStr);
                    String aiReply = respJson.getJSONArray("choices").getJSONObject(0)
                            .getJSONObject("message").getString("content");
                    boolean isPos = !(aiReply.contains("压力") || aiReply.contains("焦虑")
                            || aiReply.contains("抑郁") || aiReply.contains("悲伤"));

                    isInProgress = false;
                    callback.onAiResponse(aiReply, faceProbs, spokenText, lightDesc, isPos);
                } catch (Exception e) {
                    Log.e(Constants.TAG, "DeepSeek response parse error", e);
                    isInProgress = false;
                    callback.onAiError(context.getString(R.string.ai_request_failed));
                } finally {
                    response.close();
                }
            }
        });
    }

    private String networkErrorMessage(IOException e) {
        if (e instanceof SocketTimeoutException) {
            return "AI 连接超时，请稍后重试";
        }
        return "AI 连接失败，请检查网络后重试";
    }

    private String httpErrorMessage(int code) {
        if (code == 401 || code == 403) {
            return "AI API Key 无效或权限不足，请在“我的”页重新配置";
        }
        if (code == 429) {
            return "AI 请求过于频繁或额度不足，请稍后再试";
        }
        if (code >= 500) {
            return "AI 服务暂时繁忙，请稍后重试";
        }
        return "AI 请求失败，请检查网络或 API Key";
    }

    /** 查询最近 3 条数据库记忆。不关闭连接（由 SQLiteOpenHelper 管理生命周期）。 */
    private String fetchRecentMemory() {
        StringBuilder memory = new StringBuilder();
        android.database.sqlite.SQLiteDatabase db = null;
        android.database.Cursor cursor = null;
        try {
            db = dbHelper.getReadableDatabase();
            cursor = db.rawQuery(
                    "SELECT " + Constants.COL_TIME + ", " + Constants.COL_DETAIL +
                    " FROM " + Constants.TABLE_RECORDS +
                    " ORDER BY " + Constants.COL_ID + " DESC LIMIT 3", null);
            if (cursor.getCount() == 0) return context.getString(R.string.history_no_memory);
            while (cursor.moveToNext())
                memory.append("- [").append(cursor.getString(0)).append("]: ")
                      .append(cursor.getString(1).replace("\n", " ")).append("\n");
            return memory.toString();
        } finally {
            if (cursor != null) cursor.close();
            // 不关闭 db — SQLiteOpenHelper 管理单例连接生命周期
        }
    }
}
