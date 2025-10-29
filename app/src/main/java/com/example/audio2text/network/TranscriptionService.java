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
import java.util.regex.Pattern;

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

    /**
     *  CẢI TIẾN: Parse sentences với logic tách câu thông minh
     * - Ưu tiên utterances (có speaker)
     * - Nếu không có, dùng sentences
     * - THÊM: Tách câu dựa trên từ viết hoa (AI-like)
     */
    public static List<TranscriptItem> parseSentences(String json) {
        List<TranscriptItem> list = new ArrayList<>();
        try {
            JSONObject root = new JSONObject(json);

            // 1️ ƯU TIÊN: Utterances (có speaker_labels)
            JSONArray utterances = root.optJSONArray("utterances");
            if (utterances != null && utterances.length() > 0) {
                Log.d(TAG, "Sử dụng utterances: " + utterances.length() + " items");
                for (int i = 0; i < utterances.length(); i++) {
                    JSONObject utterance = utterances.getJSONObject(i);
                    String speaker = utterance.optString("speaker", "");

                    JSONArray words = utterance.optJSONArray("words");
                    if (words != null && words.length() > 0) {
                        StringBuilder textBuilder = new StringBuilder();
                        long start = words.getJSONObject(0).optLong("start");
                        long end = words.getJSONObject(words.length() - 1).optLong("end");

                        // Xây dựng text từ words
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
            }
            // 2️ FALLBACK: Sentences thông thường
            else {
                JSONArray sentences = root.optJSONArray("sentences");
                if (sentences != null && sentences.length() > 0) {
                    Log.d(TAG, "Sử dụng sentences: " + sentences.length() + " items");
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

            // 3⃣ AI LOGIC: Tách câu thông minh nếu có từ viết hoa
            if (list.size() <= 3) { // Chỉ áp dụng khi ít câu (có thể bị gộp)
                Log.d(TAG, "Áp dụng AI sentence splitting...");
                list = splitSentencesByCapitalization(list);
            }

        } catch (Exception e) {
            Log.e(TAG, "Parse sentences error", e);
        }

        Log.d(TAG, "Tổng số câu sau parse: " + list.size());
        return list;
    }

    /**
     * 🚀 AI LOGIC: Tách câu dựa trên từ viết hoa
     * Ví dụ: "Hello world. This is a test. Bye bye."
     * → Tách thành 3 câu riêng biệt
     */
    private static List<TranscriptItem> splitSentencesByCapitalization(List<TranscriptItem> original) {
        List<TranscriptItem> result = new ArrayList<>();

        for (TranscriptItem item : original) {
            String fullText = item.text;
            long totalDuration = item.endTimeMs - item.startTimeMs;
            int numSentences = 1; // Mặc định

            // 🔍 Tìm vị trí từ viết hoa (bắt đầu câu mới)
            // Pattern: [A-Z][a-z]+ (từ viết hoa + chữ thường)
            Pattern capitalPattern = Pattern.compile("\\b[A-Z][a-z]+");
            java.util.regex.Matcher matcher = capitalPattern.matcher(fullText);

            List<Integer> capitalPositions = new ArrayList<>();
            while (matcher.find()) {
                capitalPositions.add(matcher.start());
            }

            // Nếu có từ viết hoa → ước lượng số câu
            if (capitalPositions.size() > 1) {
                numSentences = capitalPositions.size();
                Log.d(TAG, "Phát hiện " + numSentences + " câu tiềm năng trong: " + fullText.substring(0, Math.min(50, fullText.length())));
            }

            // 📝 Tách text theo dấu câu + từ viết hoa
            String[] sentences = splitTextIntelligent(fullText);

            // ⏱️ Phân bổ thời gian đều cho các câu
            long durationPerSentence = totalDuration / Math.max(1, sentences.length);

            long currentTime = item.startTimeMs;
            for (int i = 0; i < sentences.length; i++) {
                String sentenceText = sentences[i].trim();
                if (!sentenceText.isEmpty()) {
                    long sentenceStart = currentTime;
                    long sentenceEnd = Math.min(sentenceStart + durationPerSentence, item.endTimeMs);

                    String label = formatTimestampRange(sentenceStart, sentenceEnd);
                    result.add(new TranscriptItem(label, sentenceText, sentenceStart, sentenceEnd, item.speaker));

                    currentTime = sentenceEnd;
                }
            }
        }

        return result.isEmpty() ? original : result;
    }

    /**
     *  Tách text thông minh: Dấu câu + từ viết hoa
     */
    private static String[] splitTextIntelligent(String text) {
        // 1️⃣ Tách theo dấu câu trước
        String[] byPunctuation = text.split("[.!?]+");

        // 2️⃣ Tách thêm theo từ viết hoa (nếu câu dài)
        List<String> finalSentences = new ArrayList<>();
        for (String part : byPunctuation) {
            part = part.trim();
            if (part.isEmpty()) continue;

            // Nếu câu dài và có từ viết hoa → tách thêm
            if (part.length() > 20 && hasMultipleCapitals(part)) {
                String[] capitalSplit = splitByCapitalWords(part);
                for (String s : capitalSplit) {
                    if (!s.trim().isEmpty()) {
                        finalSentences.add(s.trim());
                    }
                }
            } else {
                finalSentences.add(part);
            }
        }

        return finalSentences.toArray(new String[0]);
    }

    /**
     * 🔍 Kiểm tra có nhiều từ viết hoa trong câu không
     */
    private static boolean hasMultipleCapitals(String text) {
        Pattern capitalPattern = Pattern.compile("\\b[A-Z][a-z]+");
        java.util.regex.Matcher matcher = capitalPattern.matcher(text);
        int count = 0;
        while (matcher.find()) {
            count++;
            if (count >= 2) return true;
        }
        return false;
    }

    /**
     * 📝 Tách câu theo từ viết hoa
     */
    private static String[] splitByCapitalWords(String text) {
        // Tìm tất cả từ viết hoa
        Pattern capitalPattern = Pattern.compile("\\b[A-Z][a-z]+");
        java.util.regex.Matcher matcher = capitalPattern.matcher(text);

        List<Integer> positions = new ArrayList<>();
        while (matcher.find()) {
            positions.add(matcher.start());
        }

        if (positions.size() < 2) return new String[]{text};

        List<String> sentences = new ArrayList<>();
        int start = 0;

        for (int i = 1; i < positions.size(); i++) {
            int end = positions.get(i);
            String sentence = text.substring(start, end).trim();
            if (!sentence.isEmpty()) {
                sentences.add(sentence + "."); // Thêm dấu chấm
            }
            start = end;
        }

        // Câu cuối
        String lastSentence = text.substring(start).trim();
        if (!lastSentence.isEmpty()) {
            sentences.add(lastSentence + ".");
        }

        return sentences.toArray(new String[0]);
    }

    public JSONObject createTranscript(String uploadUrl) throws IOException {
        JSONObject bodyJson = new JSONObject();
        try {
            bodyJson.put("audio_url", uploadUrl);
            bodyJson.put("speaker_labels", true);
            bodyJson.put("format_text", true);
            bodyJson.put("language_detection", true);
            // Thêm: Cải thiện punctuation để tách câu tốt hơn
            bodyJson.put("punctuate", true);
        } catch (Exception ignored) {}

        RequestBody body = RequestBody.create(
                bodyJson.toString(),
                MediaType.get("application/json") //  Fix deprecated
        );

        Request req = new Request.Builder()
                .url(TRANSCRIPT_URL)
                .header("authorization", ApiKey.apiKey)
                .post(body)
                .build();

        try (Response res = client.newCall(req).execute()) {
            String responseBody = res.body() != null ? res.body().string() : "No body";

            if (!res.isSuccessful()) {
                Log.e(TAG, "create transcript failed: " + res.code() + ", body: " + responseBody);
                return null;
            }

            Log.d(TAG, "create transcript success, ID: " + new JSONObject(responseBody).optString("id"));
            return new JSONObject(responseBody);
        } catch (Exception e) {
            Log.e(TAG, "create transcript error", e);
            return null;
        }
    }

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
                String responseBody = res.body().string();
                JSONObject j = new JSONObject(responseBody);
                String status = j.optString("status", "");

                Log.d(TAG, String.format("Poll %d/%d - Status: %s", i + 1, maxAttempts, status));

                if ("completed".equals(status) || "error".equals(status)) {
                    if ("error".equals(status)) {
                        Log.e(TAG, "Transcript error: " + j.optString("error", "Unknown error"));
                    }
                    return j;
                }
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
            Thread.sleep(delayMs);
        }
        Log.w(TAG, "Poll timeout after " + maxAttempts + " attempts");
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