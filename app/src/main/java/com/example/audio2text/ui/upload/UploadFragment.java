package com.example.audio2text.ui.upload;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

import com.example.audio2text.R;
import com.example.audio2text.data.repository.TranscriptionRepository;
import com.example.audio2text.model.TranscriptItem;
import com.example.audio2text.network.TranscriptionService;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import okhttp3.Request;
import okhttp3.Response;
import okhttp3.OkHttpClient;

public class UploadFragment extends Fragment {

    private static final int PICK_AUDIO = 1001;
    private Button btnChoose, btnUpload;
    private TextView txtStatus;
    private ProgressBar progressBar;
    private Uri selectedUri;
    private File tempFile;

    private TranscriptionService svc;
    private TranscriptionRepository repo;
    private Handler main = new Handler(Looper.getMainLooper());

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_upload, container, false);
        btnChoose = root.findViewById(R.id.btnChoose);
        btnUpload = root.findViewById(R.id.btnUpload);
        txtStatus = root.findViewById(R.id.txtStatus);
        progressBar = root.findViewById(R.id.progressBar);

        svc = new TranscriptionService(requireContext());
        repo = new TranscriptionRepository(requireContext());

        btnChoose.setOnClickListener(v -> pickAudio());
        btnUpload.setOnClickListener(v -> {
            if (selectedUri == null) {
                Toast.makeText(getContext(), "Chọn file trước", Toast.LENGTH_SHORT).show();
                return;
            }
            startTranscription();
        });

        return root;
    }

    private void pickAudio() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("audio/*");
        startActivityForResult(Intent.createChooser(intent, "Chọn audio"), PICK_AUDIO);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_AUDIO && resultCode == Activity.RESULT_OK && data != null) {
            selectedUri = data.getData();
            txtStatus.setText("Đã chọn: " + selectedUri.getLastPathSegment());
        }
    }

    private void startTranscription() {
        progressBar.setVisibility(View.VISIBLE);
        txtStatus.setText("Đang xử lý...");

        new Thread(() -> {
            try {
                tempFile = copyUriToFile(selectedUri);
                updateStatus("Đang upload...");
                String uploadUrl = svc.uploadFile(tempFile);
                if (uploadUrl == null) throw new Exception("Upload thất bại!");

                updateStatus("Tạo transcript...");
                JSONObject createRes = svc.createTranscript(uploadUrl);
                String transcriptId = createRes.optString("id");
                if (transcriptId == null) throw new Exception("Không nhận được transcript ID");

                updateStatus("Đang chờ kết quả...");
                JSONObject result = svc.pollForResult(transcriptId, 60, 2000);
                if (result == null || !"completed".equals(result.optString("status")))
                    throw new Exception("Transcript lỗi hoặc quá thời gian chờ");

                updateStatus("Đang tải câu thoại...");
                List<TranscriptItem> list = TranscriptionService.parseSentences(getSentencesJson(transcriptId));

                // Lưu DB
                saveToDatabase(tempFile.getName(), tempFile.getAbsolutePath(), result.optString("text", ""), list);

                main.post(() -> {
                    progressBar.setVisibility(View.GONE);
                    txtStatus.setText("Hoàn tất! " + list.size() + " câu");
                    Toast.makeText(requireContext(), "Lưu thành công!", Toast.LENGTH_LONG).show();
                });

            } catch (Exception e) {
                showError(e.getMessage());
            }
        }).start();
    }
    private void saveToDatabase(String name, String audioUriString, String fullText, List<TranscriptItem> items) {
        long recId = repo.insertTranscript(name, audioUriString, fullText); // Lưu Uri string
        for (TranscriptItem it : items) {
            repo.insertSentence(recId, it.text, it.startTimeMs, it.endTimeMs, it.speaker);
        }
    }

    // 🧠 Lấy JSON sentences/utterances từ API
    private String getSentencesJson(String transcriptId) throws Exception {
        String url = String.format("https://api.assemblyai.com/v2/transcript/%s/sentences", transcriptId);
        OkHttpClient client = new OkHttpClient();
        Request req = new Request.Builder()
                .url(url)
                .header("authorization", com.example.audio2text.util.ApiKey.apiKey)
                .get()
                .build();

        try (Response res = client.newCall(req).execute()) {
            if (!res.isSuccessful()) throw new IOException("Sentences fetch failed: " + res.code());
            return res.body().string();
        }
    }

    private File copyUriToFile(Uri uri) throws Exception {
        // Lấy tên gốc
        String uriName = getFileNameFromUri(uri);
        if (uriName == null) uriName = "upload_" + System.currentTimeMillis() + ".tmp";

        // Sanitize
        String sanitized = sanitizeFileName(uriName);

        // Lưu vào thư mục app/files/audio
        File dir = new File(requireContext().getFilesDir(), "audio");
        if (!dir.exists()) dir.mkdirs();

        File out = new File(dir, sanitized);
        try (InputStream in = requireContext().getContentResolver().openInputStream(uri);
             FileOutputStream fo = new FileOutputStream(out)) {
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) != -1) fo.write(buf, 0, r);
        }
        return out;
    }


    private void updateStatus(String msg) {
        main.post(() -> txtStatus.setText(msg));
    }

    private void showError(String msg) {
        main.post(() -> {
            progressBar.setVisibility(View.GONE);
            txtStatus.setText("Lỗi: " + msg);
            Toast.makeText(getContext(), msg, Toast.LENGTH_LONG).show();
        });
    }



    private String getFileNameFromUri(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (Cursor cursor = requireContext().getContentResolver()
                    .query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex >= 0) result = cursor.getString(nameIndex);
                }
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) result = result.substring(cut + 1);
        }
        return result;
    }
    private String sanitizeFileName(String name) {
        // 1️⃣ Tách phần base và extension
        String base = name.replaceAll("\\.[^.]*$", ""); // bỏ extension
        String ext = "";
        int dotIndex = name.lastIndexOf('.');
        if (dotIndex != -1) ext = name.substring(dotIndex);

        // 2️⃣ Chuyển về chữ không dấu (Unicode)
        String normalized = java.text.Normalizer.normalize(base, java.text.Normalizer.Form.NFD);
        String noAccent = normalized.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");

        // 3️⃣ Giữ lại chỉ chữ, số, dấu gạch dưới hoặc dấu gạch ngang
        String clean = noAccent.replaceAll("[^a-zA-Z0-9_-]", "");

        // 4️⃣ Giới hạn độ dài
        if (clean.length() > 50) clean = clean.substring(0, 50);

        return clean + ext;
    }


}
