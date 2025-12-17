package com.example.audio2text.network;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class GeminiHelper {

    // ===============================================================
    // DÁN KEY MỚI (TẠO TỪ DỰ ÁN MỚI) VÀO ĐÂY
    // ===============================================================
    private static final String API_KEY = "AIzaSyBI8N4Dc1LaJeOnmzFJdwvoqDCE6gEekAk";

    // URL chuẩn theo tài liệu REST API
    private static final String API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + API_KEY;    private final OkHttpClient client;
    private final Handler mainHandler;

    public GeminiHelper() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public interface AiResponseCallback {
        void onSuccess(String result);
        void onError(Throwable t);
    }

    public void summarizeText(String transcriptText, AiResponseCallback callback) {
        String prompt = "Tóm tắt 3 ý chính của nội dung sau bằng tiếng Việt:\n\n" + transcriptText;
        callGeminiApi(prompt, callback);
    }

    public void chatWithContent(String transcriptText, String userQuestion, AiResponseCallback callback) {
        String prompt = "Nội dung:\n" + transcriptText + "\n\nCâu hỏi: " + userQuestion;
        callGeminiApi(prompt, callback);
    }

    private void callGeminiApi(String prompt, AiResponseCallback callback) {
        JSONObject jsonBody = new JSONObject();
        try {
            // Cấu trúc JSON chuẩn: { "contents": [{ "parts": [{ "text": "..." }] }] }
            JSONArray partsArray = new JSONArray();
            JSONObject part = new JSONObject();
            part.put("text", prompt);
            partsArray.put(part);

            JSONObject content = new JSONObject();
            content.put("parts", partsArray);

            JSONArray contentsArray = new JSONArray();
            contentsArray.put(content);

            jsonBody.put("contents", contentsArray);
        } catch (Exception e) {
            callback.onError(e);
            return;
        }

        RequestBody body = RequestBody.create(jsonBody.toString(), MediaType.get("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url(API_URL)
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                mainHandler.post(() -> callback.onError(e));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    // Đọc nội dung lỗi từ Google trả về
                    String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                    Log.e("GeminiAPI", "Lỗi: " + errorBody); // Xem lỗi này trong Logcat
                    mainHandler.post(() -> callback.onError(new Exception("Lỗi API " + response.code() + ": " + errorBody)));
                    return;
                }

                try {
                    String responseData = response.body().string();
                    JSONObject jsonResponse = new JSONObject(responseData);

                    JSONArray candidates = jsonResponse.getJSONArray("candidates");
                    String resultText = candidates.getJSONObject(0)
                            .getJSONObject("content")
                            .getJSONArray("parts")
                            .getJSONObject(0)
                            .getString("text");

                    mainHandler.post(() -> callback.onSuccess(resultText));

                } catch (Exception e) {
                    mainHandler.post(() -> callback.onError(new Exception("Lỗi phân tích JSON: " + e.getMessage())));
                }
            }
        });
    }
}