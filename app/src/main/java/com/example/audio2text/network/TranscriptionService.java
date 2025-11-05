package com.example.audio2text.network;

import android.content.Context;
import com.example.audio2text.util.ApiKey; // Import lớp ApiKey
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import com.example.audio2text.model.TranscriptItem;


public class TranscriptionService {

    private final OkHttpClient client;
    // 1. Thêm biến Context
    private final Context context;

    // 2. Thêm Constructor để nhận Context
    public TranscriptionService(Context context) {
        this.context = context.getApplicationContext(); // Dùng application context để tránh memory leak
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    public String uploadFile(File file) throws Exception {
        RequestBody body = RequestBody.create(file, MediaType.parse("application/octet-stream"));
        Request req = new Request.Builder()
                .url("https://api.assemblyai.com/v2/upload")
                // 3. Lấy API Key theo cách mới
                .header("authorization", ApiKey.getApiKey(context))
                .post(body)
                .build();

        try (Response res = client.newCall(req).execute()) {
            if (!res.isSuccessful()) throw new IOException("Upload failed: " + res.code());
            JSONObject json = new JSONObject(res.body().string());
            return json.getString("upload_url");
        }
    }

    public JSONObject createTranscript(String audioUrl) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("audio_url", audioUrl);
        payload.put("speaker_labels", true); // Bật speaker labels nếu API hỗ trợ

        RequestBody body = RequestBody.create(payload.toString(), MediaType.parse("application/json"));
        Request req = new Request.Builder()
                .url("https://api.assemblyai.com/v2/transcript")
                // 3. Lấy API Key theo cách mới
                .header("authorization", ApiKey.getApiKey(context))
                .post(body)
                .build();

        try (Response res = client.newCall(req).execute()) {
            if (!res.isSuccessful()) throw new IOException("Transcript creation failed: " + res.code());
            return new JSONObject(res.body().string());
        }
    }

    public JSONObject pollForResult(String transcriptId, int maxWaitSecs, int pollIntervalMs) throws Exception {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < maxWaitSecs * 1000) {
            Request req = new Request.Builder()
                    .url("https://api.assemblyai.com/v2/transcript/" + transcriptId)
                    // 3. Lấy API Key theo cách mới
                    .header("authorization", ApiKey.getApiKey(context))
                    .get()
                    .build();

            try (Response res = client.newCall(req).execute()) {
                if (!res.isSuccessful()) throw new IOException("Polling failed: " + res.code());
                JSONObject json = new JSONObject(res.body().string());
                String status = json.getString("status");
                if ("completed".equals(status) || "error".equals(status)) {
                    return json;
                }
            }
            Thread.sleep(pollIntervalMs);
        }
        return null;
    }

    public static List<TranscriptItem> parseSentences(String jsonResponse) throws Exception {
        List<TranscriptItem> items = new ArrayList<>();
        JSONObject root = new JSONObject(jsonResponse);
        JSONArray sentences = root.getJSONArray("sentences");

        for (int i = 0; i < sentences.length(); i++) {
            JSONObject s = sentences.getJSONObject(i);
            String text = s.getString("text");
            long start = s.getLong("start");
            long end = s.getLong("end");
            String speaker = s.optString("speaker", "A"); // Lấy speaker, mặc định là "A"

            // Tạo label thời gian
            int s_val = (int) (start / 1000);
            int m = s_val / 60;
            s_val %= 60;
            String label = String.format("%d:%02d", m, s_val);

            items.add(new TranscriptItem(label, text, start, end, speaker));
        }
        return items;
    }
}