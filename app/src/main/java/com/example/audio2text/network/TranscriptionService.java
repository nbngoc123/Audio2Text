package com.example.audio2text.network;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.example.audio2text.util.ApiKey;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class TranscriptionService {

    private static final String BASE_URL = "https://api.assemblyai.com/v2";
    private static final String UPLOAD_URL = BASE_URL + "/upload";
    private static final String TRANSCRIPT_URL = BASE_URL + "/transcript";
    private static final String SENTENCE_URL = BASE_URL + "/transcript/%s/sentences";  // {id}/sentences

    private final Context context;

    public TranscriptionService(Context context) {
        this.context = context;
    }

    public String uploadAndTranscribe(Uri audioUri) {
        try {
            // 1. Upload file → lấy upload_url
            String uploadUrl = uploadFile(audioUri);
            if (uploadUrl == null) return null;

            // 2. Submit transcript với language_detection: true
            String transcriptId = requestTranscription(uploadUrl);
            if (transcriptId == null) return null;

            // 3. Polling đến completed + lấy language_code
            String languageCode = waitForCompletion(transcriptId);
            if (languageCode == null) return null;

            // 4. Lấy sentences với timestamp
            return getSentences(transcriptId, languageCode);

        } catch (Exception e) {
            Log.e("TranscriptionService", "Error: " + e.getMessage());
            return null;
        }
    }

    private String uploadFile(Uri uri) throws Exception {
        InputStream inputStream = context.getContentResolver().openInputStream(uri);
        if (inputStream == null) return null;

        URL url = new URL(UPLOAD_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", ApiKey.apiKey);
        conn.setDoOutput(true);

        DataOutputStream out = new DataOutputStream(conn.getOutputStream());
        byte[] buffer = new byte[4096];
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            out.write(buffer, 0, bytesRead);
        }
        out.flush();
        out.close();
        inputStream.close();

        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            Log.e("Upload", "Error code: " + responseCode);
            return null;
        }

        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        JSONObject response = new JSONObject(br.readLine());
        br.close();
        conn.disconnect();
        return response.getString("upload_url");  // Đúng spec
    }

    private String requestTranscription(String uploadUrl) throws Exception {
        URL url = new URL(TRANSCRIPT_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", ApiKey.apiKey);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);

        JSONObject body = new JSONObject();
        body.put("audio_url", uploadUrl);
        body.put("language_detection", true);  // ✅ Enable auto-detect
        // Thêm nếu cần: body.put("speaker_labels", true); cho multi-speaker

        DataOutputStream out = new DataOutputStream(conn.getOutputStream());
        out.writeBytes(body.toString());
        out.flush();
        out.close();

        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            Log.e("TranscriptRequest", "Error code: " + responseCode);
            return null;
        }

        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        JSONObject response = new JSONObject(br.readLine());
        br.close();
        conn.disconnect();
        return response.getString("id");  // Đúng spec
    }

    private String waitForCompletion(String transcriptId) throws Exception {
        while (true) {
            URL url = new URL(TRANSCRIPT_URL + "/" + transcriptId);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("Authorization", ApiKey.apiKey);

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                Log.e("Polling", "Error code: " + responseCode);
                conn.disconnect();
                return null;
            }

            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            JSONObject response = new JSONObject(br.readLine());
            br.close();
            conn.disconnect();

            String status = response.getString("status");
            if (status.equals("error")) {
                Log.e("Polling", "Transcript error: " + response.optString("error"));
                return null;
            }
            if (status.equals("completed")) {
                // ✅ Lấy language_code (auto-detect)
                String languageCode = response.optString("language_code", "unknown");
                if (languageCode.equals("null") || languageCode.isEmpty()) {
                    // Nếu multi: response.getJSONArray("language_codes")
                    languageCode = "multi";
                }
                return languageCode;  // Trả về để dùng ở sentences
            }
            Thread.sleep(5000);  // Poll every 5s
        }
    }

    private String getSentences(String transcriptId, String languageCode) throws Exception {
        URL url = new URL(String.format(SENTENCE_URL, transcriptId));
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("Authorization", ApiKey.apiKey);

        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            Log.e("Sentences", "Error code: " + responseCode);
            return null;
        }

        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        JSONObject response = new JSONObject(br.readLine());
        br.close();
        conn.disconnect();

        JSONArray sentences = response.getJSONArray("sentences");  // Đúng spec
        StringBuilder sb = new StringBuilder();
        sb.append("Language: ").append(languageCode).append("\n\n");  // Thêm language

        for (int i = 0; i < sentences.length(); i++) {
            JSONObject s = sentences.getJSONObject(i);
            long start = s.getLong("start");
            long end = s.getLong("end");
            String text = s.getString("text");
            String speaker = s.optString("speaker", "");  // Nếu speaker_labels
            String channel = s.optString("channel", "");  // Nếu multichannel

            sb.append(String.format("[%d-%d ms]%s%s %s\n",
                    start, end,
                    speaker.isEmpty() ? "" : " (Speaker " + speaker + ")",
                    channel.isEmpty() ? "" : " (Channel " + channel + ")",
                    text));
        }

        return sb.toString();
    }
}