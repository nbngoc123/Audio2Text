package com.example.audio2text.network;

import android.content.Context;
import com.example.audio2text.util.ApiKey;
import com.example.audio2text.model.TranscriptItem;

import org.json.JSONArray;
import org.json.JSONException;
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
import okhttp3.logging.HttpLoggingInterceptor;

public class TranscriptionService {

    private final OkHttpClient client;
    private final Context context;

    public TranscriptionService(Context context) {
        this.context = context.getApplicationContext(); // Dùng application context để tránh memory leak

        // Bổ sung Interceptor để log lại request/response mạng, rất hữu ích để gỡ lỗi
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);

        this.client = new OkHttpClient.Builder()
                .addInterceptor(logging) // Thêm interceptor
                .connectTimeout(60, TimeUnit.SECONDS) // Tăng thời gian chờ
                .writeTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    public String uploadFile(File file) throws Exception {
        RequestBody body = RequestBody.create(file, MediaType.parse("application/octet-stream"));
        Request req = new Request.Builder()
                .url("https://api.assemblyai.com/v2/upload")
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
        payload.put("speaker_labels", true);
        payload.put("format_text", true); // Bật định dạng văn bản (dấu câu, viết hoa)
        payload.put("language_detection", true);

        RequestBody body = RequestBody.create(payload.toString(), MediaType.parse("application/json"));
        Request req = new Request.Builder()
                .url("https://api.assemblyai.com/v2/transcript")
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

    /**
     * PHƯƠNG THỨC MỚI VÀ ĐÚNG ĐỂ XỬ LÝ KẾT QUẢ TỪ ASSEMBLYAI
     * Phương thức này sẽ duyệt qua mảng "words" (từng từ) mà API trả về,
     * sau đó nhóm chúng lại thành các câu dựa trên khoảng lặng và dấu câu.
     *
     * @param jsonResponse Chuỗi JSON nhận được khi phiên âm hoàn tất.
     * @return Một danh sách các đối tượng TranscriptItem, mỗi đối tượng là một câu hoàn chỉnh.
     * @throws JSONException
     */
    public static List<TranscriptItem> parseTranscriptFromWords(String jsonResponse) throws JSONException {
        List<TranscriptItem> items = new ArrayList<>();
        JSONObject root = new JSONObject(jsonResponse);

        // Kiểm tra xem kết quả có chứa mảng 'words' không
        if (!root.has("words")) {
            return items; // Trả về danh sách rỗng nếu không có
        }

        JSONArray words = root.getJSONArray("words");
        if (words.length() == 0) {
            return items;
        }

        // Ngưỡng thời gian (mili giây) để coi là một khoảng nghỉ giữa các câu.
        final long SENTENCE_PAUSE_THRESHOLD_MS = 500;
        StringBuilder sentenceBuilder = new StringBuilder();

        // Thời gian bắt đầu của câu hiện tại là thời gian bắt đầu của từ đầu tiên.
        long sentenceStartTime = words.getJSONObject(0).getLong("start");
        String currentSpeaker = words.getJSONObject(0).optString("speaker", "A");

        for (int i = 0; i < words.length(); i++) {
            JSONObject currentWord = words.getJSONObject(i);
            String text = currentWord.getString("text");
            long endTime = currentWord.getLong("end");
            String speaker = currentWord.optString("speaker", "A");

            sentenceBuilder.append(text).append(" ");

            boolean isLastWord = (i == words.length() - 1);
            boolean shouldBreakSentence = false;

            if (!isLastWord) {
                JSONObject nextWord = words.getJSONObject(i + 1);
                long nextStartTime = nextWord.getLong("start");
                long pauseDuration = nextStartTime - endTime;
                String nextSpeaker = nextWord.optString("speaker", "A");

                // Điều kiện 1: Khoảng lặng giữa 2 từ đủ lớn
                if (pauseDuration >= SENTENCE_PAUSE_THRESHOLD_MS) {
                    shouldBreakSentence = true;
                }

                // Điều kiện 2: Người nói thay đổi
                if (!speaker.equals(nextSpeaker)) {
                    shouldBreakSentence = true;
                }
            }

            // Nếu là từ cuối cùng HOẶC đủ điều kiện ngắt câu thì tạo thành một TranscriptItem
            if (isLastWord || shouldBreakSentence) {
                // Tạo label thời gian dạng "phút:giây"
                int totalSeconds = (int) (sentenceStartTime / 1000);
                int minutes = totalSeconds / 60;
                int seconds = totalSeconds % 60;
                String label = String.format("%d:%02d", minutes, seconds);

                // Thêm câu hoàn chỉnh vào danh sách kết quả
                items.add(new TranscriptItem(label, sentenceBuilder.toString().trim(), sentenceStartTime, endTime, currentSpeaker));

                // Reset các biến để chuẩn bị cho câu tiếp theo
                sentenceBuilder.setLength(0);
                if (!isLastWord) {
                    sentenceStartTime = words.getJSONObject(i + 1).getLong("start");
                    currentSpeaker = words.getJSONObject(i + 1).optString("speaker", "A");
                }
            }
        }
        return items;
    }
}