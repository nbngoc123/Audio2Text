package com.example.audio2text.network;

import android.content.Context;
import android.util.Log;

import com.example.audio2text.util.ApiKey;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.example.audio2text.model.TranscriptItem;
import okhttp3.*;

public class TranscriptionService {
    private static final String TAG = "TranscriptionService";
    private static final String UPLOAD_URL = "https://api.assemblyai.com/v2/upload";
    private static final String TRANSCRIPT_URL = "https://api.assemblyai.com/v2/transcript";

    private final OkHttpClient client = new OkHttpClient();
    private final Context context;

    public TranscriptionService(Context context) {
        this.context = context;
    }

    public String uploadFile(File file) throws IOException {
        if (!file.exists() || file.length() == 0) {
            Log.e(TAG, "File không tồn tại hoặc rỗng: " + file.getAbsolutePath());
            return null;
        }
        Log.d(TAG, "Upload file: " + file.getAbsolutePath() + " (" + file.length() + " bytes)");

        // ✅ Fix deprecated: MediaType.get thay parse
        RequestBody body = RequestBody.create(file, MediaType.get("application/octet-stream"));
        Request req = new Request.Builder()
                .url(UPLOAD_URL)
                .header("authorization", ApiKey.apiKey)
                .post(body)
                .build();

        try (Response res = client.newCall(req).execute()) {
            String responseBody = res.body() != null ? res.body().string() : "No body";
            Log.d(TAG, "Upload response code: " + res.code());
            Log.d(TAG, "Upload response body: " + responseBody);

            if (!res.isSuccessful()) {
                Log.e(TAG, "Upload failed: " + res.code() + " - " + responseBody);
                return null;
            }
            JSONObject j = new JSONObject(responseBody);
            return j.optString("upload_url", null);
        } catch (Exception e) {
            Log.e(TAG, "Upload error", e);
            return null;
        }
    }

    public static List<TranscriptItem> parseSentences(String json) {
        List<TranscriptItem> list = new ArrayList<>();
        try {
            JSONObject root = new JSONObject(json);

            // Dùng utterances nếu có speaker_labels
            JSONArray utterances = root.optJSONArray("utterances");
            if (utterances != null) {
                for (int i = 0; i < utterances.length(); i++) {
                    JSONObject utterance = utterances.getJSONObject(i);
                    String speaker = utterance.optString("speaker", "");

                    JSONArray words = utterance.optJSONArray("words");
                    if (words != null && words.length() > 0) {
                        StringBuilder textBuilder = new StringBuilder();
                        long start = words.getJSONObject(0).optLong("start");
                        long end = words.getJSONObject(words.length() - 1).optLong("end");

                        for (int j = 0; j < words.length(); j++) {
                            String wordText = words.getJSONObject(j).optString("text");
                            textBuilder.append(wordText);
                            if (j < words.length() - 1) textBuilder.append(" ");
                        }

                        String text = textBuilder.toString();
                        String label = formatTimestampRange(start, end);

                        list.add(new TranscriptItem(label, text, start, end, speaker));
                    }
                }
            } else {
                // fallback sang sentences nếu utterances không có
                JSONArray sentences = root.optJSONArray("sentences");
                if (sentences != null) {
                    for (int i = 0; i < sentences.length(); i++) {
                        JSONObject s = sentences.getJSONObject(i);
                        long start = s.optLong("start");
                        long end = s.optLong("end");
                        String text = s.optString("text");
                        String speaker = s.optString("speaker", "");
                        String label = formatTimestampRange(start, end);

                        list.add(new TranscriptItem(label, text, start, end, speaker));
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public JSONObject createTranscript(String uploadUrl) throws IOException {
    JSONObject bodyJson = new JSONObject();
    try {
        bodyJson.put("audio_url", uploadUrl);
        bodyJson.put("speaker_labels", true);
        bodyJson.put("format_text", true);
        bodyJson.put("language_detection", true);
    } catch (Exception ignored) {}

    RequestBody body = RequestBody.create(
            bodyJson.toString(),
            MediaType.parse("application/json")
    );

    Request req = new Request.Builder()
            .url(TRANSCRIPT_URL)
            .header("authorization", ApiKey.apiKey)
            .post(body)
            .build();

    try (Response res = client.newCall(req).execute()) {
        String responseBody = res.body().string(); // ĐỌC TRƯỚC KHI ĐÓNG

        if (!res.isSuccessful()) {
            Log.e(TAG, "create transcript failed: " + res.code() + ", body: " + responseBody);
            return null;
        }

        Log.d(TAG, "create transcript success: " + responseBody);
        return new JSONObject(responseBody);
    } catch (Exception e) {
        Log.e(TAG, "create transcript error", e);
        return null;
    }
}
    // Poll until completed or error. Returns transcript JSON (GET /v2/transcript/{id})

    public JSONObject pollForResult(String transcriptId, int maxAttempts, long delayMs)
            throws IOException, InterruptedException {
        String url = TRANSCRIPT_URL + "/" + transcriptId;
        for (int i = 0; i < maxAttempts; i++) {
            Request req = new Request.Builder()
                    .url(url)
                    .header("authorization", ApiKey.apiKey)
                    .get()
                    .build();

            try (Response res = client.newCall(req).execute()) {
                if (!res.isSuccessful()) {
                    Log.e(TAG, "poll failed: " + res.code());
                    return null;
                }
                JSONObject j = new JSONObject(res.body().string());
                String status = j.optString("status", "");
                if ("completed".equals(status) || "error".equals(status)) {
                    return j; //  trả về full JSON luôn
                }
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
            Thread.sleep(delayMs);
        }
        return null;
    }

    private static String formatTimestampRange(long startMs, long endMs) {
        return formatTimestamp(startMs) + " - " + formatTimestamp(endMs);
    }

    private static String formatTimestamp(long ms) {
        long totalSec = ms / 1000;
        long sec = totalSec % 60;
        long min = (totalSec / 60) % 60;
        long hr = totalSec / 3600;

        if (hr > 0)
            return String.format("%d:%02d:%02d", hr, min, sec);
        else
            return String.format("%d:%02d", min, sec);
    }

}
