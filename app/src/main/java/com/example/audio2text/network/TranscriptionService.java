package com.example.audio2text.network;

import android.content.Context;
import android.util.Log;

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

    public JSONObject createTranscript(String audioUrl, String languageCode, Boolean speakerLabels) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("audio_url", audioUrl);
        payload.put("format_text", true);
        payload.put("punctuate", true);
        //  tra ngôn ngữ đầu vào
        if (languageCode != null && !languageCode.isEmpty() && !languageCode.equals("auto")) {
            payload.put("language_code", languageCode);
            payload.put("language_detection", false);
        } else {
            payload.put("language_detection", true);
        }
        //  tra speaker đầu vào
        if (speakerLabels != null) {
            payload.put("speaker_labels", speakerLabels);
        }


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
        final String TAG = "ParseDebug";
        Log.d(TAG, "--- BẮT ĐẦU PHÂN TÍCH JSON ---");

        List<TranscriptItem> items = new ArrayList<>();
        JSONObject root = new JSONObject(jsonResponse);

        JSONArray words = root.optJSONArray("words");

        if (words == null || words.length() == 0) {
            Log.e(TAG, "Lỗi: Mảng 'words' bị null hoặc rỗng!");
            return items;
        }

        Log.d(TAG, "Tìm thấy tổng cộng: " + words.length() + " từ.");

        StringBuilder currentSentenceText = new StringBuilder();
        long currentSentenceStart = -1;
        long currentSentenceEnd = -1;

        for (int i = 0; i < words.length(); i++) {
            JSONObject w = words.getJSONObject(i);
            String wordText = w.optString("text");
            long start = w.optLong("start");
            long end = w.optLong("end");

            // Log từng từ (Nếu nhiều quá có thể comment lại)
            // Log.v(TAG, String.format("Word[%d]: %s (%d - %d)", i, wordText, start, end));

            // Logic kiểm tra viết hoa
            boolean isCapital = !wordText.isEmpty() && Character.isUpperCase(wordText.charAt(0));
            boolean hasContent = currentSentenceText.length() > 0;

            // 1. NGẮT CÂU DO VIẾT HOA
            if (hasContent && isCapital) {
                Log.d(TAG, ">>> NGẮT CÂU (Lý do: Chữ hoa): Gặp từ '" + wordText + "'");

                String sentence = currentSentenceText.toString().trim();
                Log.d(TAG, "   + Lưu câu: [" + sentence + "]");
                Log.d(TAG, "   + Thời gian: " + currentSentenceStart + " -> " + currentSentenceEnd);

                items.add(new TranscriptItem(
                        formatTimestampRange(currentSentenceStart, currentSentenceEnd),
                        sentence,
                        currentSentenceStart,
                        currentSentenceEnd,
                        "A"
                ));

                // Reset
                currentSentenceText.setLength(0);
                currentSentenceStart = -1;
            }

            // 2. GHÉP TỪ
            if (currentSentenceText.length() > 0) {
                currentSentenceText.append(" ");
            }
            currentSentenceText.append(wordText);

            // Cập nhật thời gian
            if (currentSentenceStart == -1) {
                currentSentenceStart = start; // Set thời gian bắt đầu câu
            }
            currentSentenceEnd = end; // Luôn cập nhật thời gian kết thúc mới nhất

            // 3. NGẮT CÂU DO DẤU CÂU (., ?, !)
            if (wordText.endsWith(".") || wordText.endsWith("?") || wordText.endsWith("!")) {
                Log.d(TAG, ">>> NGẮT CÂU (Lý do: Dấu câu): Tại từ '" + wordText + "'");

                String sentence = currentSentenceText.toString().trim();
                Log.d(TAG, "   + Lưu câu: [" + sentence + "]");
                Log.d(TAG, "   + Thời gian: " + currentSentenceStart + " -> " + currentSentenceEnd);

                items.add(new TranscriptItem(
                        formatTimestampRange(currentSentenceStart, currentSentenceEnd),
                        sentence,
                        currentSentenceStart,
                        currentSentenceEnd,
                        "A"
                ));

                // Reset
                currentSentenceText.setLength(0);
                currentSentenceStart = -1;
            }
        }

        // 4. XỬ LÝ PHẦN CÒN DƯ (Sentence cuối cùng chưa kịp ngắt)
        if (currentSentenceText.length() > 0) {
            Log.d(TAG, ">>> LƯU CÂU CUỐI CÙNG (Kết thúc mảng words)");
            String sentence = currentSentenceText.toString().trim();
            Log.d(TAG, "   + Lưu câu: [" + sentence + "]");

            items.add(new TranscriptItem(
                    formatTimestampRange(currentSentenceStart, currentSentenceEnd),
                    sentence,
                    currentSentenceStart,
                    currentSentenceEnd,
                    "A"
            ));
        }

        Log.d(TAG, "--- KẾT THÚC: Tạo được " + items.size() + " câu thoại ---");
        return items;
    }

    private static String formatTimestampRange(long startMs, long endMs) {
        return formatTimestamp(startMs) + " - " + formatTimestamp(endMs);
    }

    private static String formatTimestamp(long ms) {
        long sec = (ms / 1000) % 60;
        long min = (ms / 60000) % 60;
        long hr = ms / 3600000;
        if (hr > 0) return String.format("%d:%02d:%02d", hr, min, sec);
        return String.format("%d:%02d", min, sec);
    }


}