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
        payload.put("format_text", true);
        payload.put("language_detection", true);

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
        JSONArray sentences = root.optJSONArray("sentences");
        JSONArray utterances = root.optJSONArray("utterances");

        if (utterances != null) {
            for (int i = 0; i < utterances.length(); i++) {
                JSONObject u = utterances.getJSONObject(i);
                String speaker = u.optString("speaker", "A");
                JSONArray words = u.optJSONArray("words");
                if (words != null && words.length() > 0) {
                    long start = words.getJSONObject(0).optLong("start");
                    long end = words.getJSONObject(words.length() - 1).optLong("end");
                    StringBuilder sb = new StringBuilder();
                    for (int j = 0; j < words.length(); j++) {
                        sb.append(words.getJSONObject(j).optString("text"));
                        if (j < words.length() - 1) sb.append(" ");
                    }
                    items.add(new TranscriptItem(formatTimestampRange(start, end), sb.toString(), start, end, speaker));
                }
            }
        } else if (sentences != null) {
            for (int i = 0; i < sentences.length(); i++) {
                JSONObject s = sentences.getJSONObject(i);
                String text = s.optString("text", "");
                long start = s.optLong("start");
                long end = s.optLong("end");
                String speaker = s.optString("speaker", "A");
                items.add(new TranscriptItem(formatTimestampRange(start, end), text, start, end, speaker));
            }
        }

        // Gọi hàm tách câu theo chữ hoa
        items = splitSentencesByCapital(items);

        return items;
    }
    private static List<TranscriptItem> splitSentencesByCapital(List<TranscriptItem> original) {
        List<TranscriptItem> result = new ArrayList<>();
        for (TranscriptItem item : original) {
            String text = item.text;
            long start = item.startTimeMs;
            long end = item.endTimeMs;
            String speaker = item.speaker;

            if (text.length() > 50) { // chỉ tách nếu câu dài
                List<String> sentences = new ArrayList<>();
                int last = 0;
                for (int i = 1; i < text.length(); i++) {
                    char c = text.charAt(i);
                    if (Character.isUpperCase(c) && text.charAt(i - 1) == ' ') {
                        sentences.add(text.substring(last, i).trim());
                        last = i;
                    }
                }
                sentences.add(text.substring(last).trim());

                long durationPerSentence = (end - start) / sentences.size();
                long current = start;
                for (String s : sentences) {
                    if (!s.isEmpty()) {
                        long sentenceEnd = current + durationPerSentence;
                        result.add(new TranscriptItem(formatTimestampRange(current, sentenceEnd),
                                s, current, sentenceEnd, speaker));
                        current = sentenceEnd;
                    }
                }
            } else {
                result.add(item);
            }
        }
        return result.isEmpty() ? original : result;
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