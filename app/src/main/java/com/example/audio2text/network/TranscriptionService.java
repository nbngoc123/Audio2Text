package com.example.audio2text.network;

import android.content.Context;
import android.util.Log;

import com.example.audio2text.util.ApiKey;
import com.example.audio2text.model.TranscriptItem;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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

    /** Upload file audio */
    public String uploadFile(File file) throws IOException {
        if (!file.exists() || file.length() == 0) return null;

        RequestBody body = RequestBody.create(file, MediaType.get("application/octet-stream"));
        Request req = new Request.Builder()
                .url(UPLOAD_URL)
                .header("authorization", ApiKey.apiKey)
                .post(body)
                .build();

        try (Response res = client.newCall(req).execute()) {
            String responseBody = res.body() != null ? res.body().string() : "";
            if (!res.isSuccessful()) {
                Log.e(TAG, "Upload failed: " + res.code() + " " + responseBody);
                return null;
            }
            JSONObject j = new JSONObject(responseBody);
            return j.optString("upload_url", null);
        } catch (Exception e) {
            Log.e(TAG, "Upload error", e);
            return null;
        }
    }

    /** Tạo transcript từ upload URL */
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
            String responseBody = res.body() != null ? res.body().string() : "";
            if (!res.isSuccessful()) {
                Log.e(TAG, "create transcript failed: " + res.code() + " " + responseBody);
                return null;
            }
            return new JSONObject(responseBody);
        } catch (Exception e) {
            Log.e(TAG, "create transcript error", e);
            return null;
        }
    }

    /** Poll until completed, trả về JSON transcript */
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
                if (!res.isSuccessful()) return null;
                JSONObject j = new JSONObject(res.body().string());
                String status = j.optString("status", "");
                if ("completed".equals(status) || "error".equals(status)) return j;
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
            Thread.sleep(delayMs);
        }
        return null;
    }

    /** Parse sentences (utterances hoặc sentences) + split chữ hoa nếu cần */
    public static List<TranscriptItem> parseSentences(String json) {
        List<TranscriptItem> list = new ArrayList<>();
        try {
            JSONObject root = new JSONObject(json);

            JSONArray utterances = root.optJSONArray("utterances");
            if (utterances != null) {
                for (int i = 0; i < utterances.length(); i++) {
                    JSONObject u = utterances.getJSONObject(i);
                    String speaker = u.optString("speaker", "");
                    JSONArray words = u.optJSONArray("words");
                    if (words != null && words.length() > 0) {
                        long start = words.getJSONObject(0).optLong("start");
                        long end = words.getJSONObject(words.length()-1).optLong("end");
                        StringBuilder sb = new StringBuilder();
                        for (int j=0;j<words.length();j++) {
                            sb.append(words.getJSONObject(j).optString("text"));
                            if (j<words.length()-1) sb.append(" ");
                        }
                        list.add(new TranscriptItem(formatTimestampRange(start,end),
                                sb.toString(), start, end, speaker));
                    }
                }
            } else {
                JSONArray sentences = root.optJSONArray("sentences");
                if (sentences != null) {
                    for (int i=0;i<sentences.length();i++) {
                        JSONObject s = sentences.getJSONObject(i);
                        long start = s.optLong("start");
                        long end = s.optLong("end");
                        String text = s.optString("text");
                        String speaker = s.optString("speaker","");
                        list.add(new TranscriptItem(formatTimestampRange(start,end),
                                text, start, end, speaker));
                    }
                }
            }

            // Split text dài theo chữ hoa
            list = splitSentencesByCapital(list);

        } catch (Exception e) {
            Log.e(TAG, "parseSentences error", e);
        }
        return list;
    }

    /** Split text dài theo chữ hoa (mỗi chữ hoa sau khoảng trắng là câu mới) */
    private static List<TranscriptItem> splitSentencesByCapital(List<TranscriptItem> original) {
        List<TranscriptItem> result = new ArrayList<>();
        for (TranscriptItem item : original) {
            String text = item.text;
            long start = item.startTimeMs;
            long end = item.endTimeMs;
            String speaker = item.speaker;

            if (text.length() > 50) { // chỉ tách text dài
                List<String> sentences = new ArrayList<>();
                int last = 0;
                for (int i=1;i<text.length();i++) {
                    char c = text.charAt(i);
                    if (Character.isUpperCase(c) && text.charAt(i-1)==' ') {
                        sentences.add(text.substring(last,i).trim());
                        last = i;
                    }
                }
                sentences.add(text.substring(last).trim());

                long durationPerSentence = (end-start)/sentences.size();
                long current = start;
                for (String s: sentences) {
                    if (!s.isEmpty()) {
                        long sentenceEnd = current+durationPerSentence;
                        result.add(new TranscriptItem(formatTimestampRange(current,sentenceEnd),
                                s, current, sentenceEnd, speaker));
                        current = sentenceEnd;
                    }
                }
            } else {
                result.add(item); // giữ nguyên
            }
        }
        return result.isEmpty()?original:result;
    }

    private static String formatTimestampRange(long startMs, long endMs) {
        return formatTimestamp(startMs) + " - " + formatTimestamp(endMs);
    }

    private static String formatTimestamp(long ms) {
        long sec = (ms/1000)%60;
        long min = (ms/60000)%60;
        long hr = ms/3600000;
        if (hr>0) return String.format("%d:%02d:%02d",hr,min,sec);
        return String.format("%d:%02d", min, sec);
    }
}
