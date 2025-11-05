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
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.example.audio2text.R;
import com.example.audio2text.data.repository.TranscriptionRepository;
import com.example.audio2text.model.TranscriptItem;
import com.example.audio2text.network.TranscriptionService;
import com.example.audio2text.util.ApiKey;
import com.example.audio2text.util.NetworkUtils;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class UploadFragment extends Fragment {

    private static final int PICK_AUDIO = 1001;

    private Button btnChoose, btnUpload, btnCancel;
    private TextView txtStatus, txtProgressStatus;
    private Uri selectedUri;
    private File tempFile;
    private LinearLayout layoutButtons, layoutProgress;

    private TranscriptionService svc;
    private TranscriptionRepository repo;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile Thread transcriptionThread;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_upload, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initViews(view);

        svc = new TranscriptionService(requireContext());
        repo = new TranscriptionRepository(requireContext());

        btnChoose.setOnClickListener(v -> pickAudio());
        btnUpload.setOnClickListener(v -> startTranscription());
        btnCancel.setOnClickListener(v -> cancelTranscription());

        setIdleState();
    }

    private void initViews(View view) {
        btnChoose = view.findViewById(R.id.btnChoose);
        btnUpload = view.findViewById(R.id.btnUpload);
        btnCancel = view.findViewById(R.id.btnCancel);
        txtStatus = view.findViewById(R.id.txtStatus);
        txtProgressStatus = view.findViewById(R.id.txtProgressStatus);
        layoutButtons = view.findViewById(R.id.layout_buttons);
        layoutProgress = view.findViewById(R.id.layout_progress);
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
            String fileName = getFileNameFromUri(selectedUri);
            txtStatus.setText("Đã chọn: " + (fileName != null ? fileName : ""));
            btnUpload.setEnabled(true);
        }
    }

    private void startTranscription() {
        if (selectedUri == null) {
            Toast.makeText(getContext(), "Vui lòng chọn một file âm thanh trước", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!NetworkUtils.isNetworkAvailable(requireContext())) {
            Toast.makeText(getContext(), "Không có kết nối Internet. Vui lòng thử lại.", Toast.LENGTH_LONG).show();
            return;
        }

        setLoadingState();

        transcriptionThread = new Thread(() -> {
            try {
                updateProgressStatus("Chuẩn bị file...");
                tempFile = copyUriToFile(selectedUri);

                if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                updateProgressStatus("Đang upload...");
                String uploadUrl = svc.uploadFile(tempFile);
                if (uploadUrl == null) throw new Exception("Upload thất bại!");

                if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                updateProgressStatus("Tạo transcript...");
                JSONObject createRes = svc.createTranscript(uploadUrl);
                String transcriptId = createRes.optString("id");
                if (transcriptId.isEmpty()) {
                    String error = createRes.optString("error");
                    if (!error.isEmpty()) throw new Exception("Lỗi API: " + error);
                    throw new Exception("Không nhận được transcript ID");
                }

                if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                updateProgressStatus("Đang chờ kết quả...");
                JSONObject result = svc.pollForResult(transcriptId, 120, 2000);
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException();

                if (result == null) {
                    throw new Exception("Quá thời gian chờ kết quả");
                }

                String status = result.optString("status");
                if ("error".equals(status)) {
                    throw new Exception("Lỗi xử lý file từ API: " + result.optString("error"));
                }
                if (!"completed".equals(status)) {
                    throw new Exception("Transcript không hoàn tất, trạng thái: " + status);
                }

                updateProgressStatus("Đang tải câu thoại...");
                List<TranscriptItem> list = TranscriptionService.parseSentences(getSentencesJson(transcriptId));

                saveToDatabase(tempFile.getName(), tempFile.getAbsolutePath(), result.optString("text", ""), list);

                mainHandler.post(() -> {
                    Toast.makeText(requireContext(), "Hoàn tất và đã lưu thành công!", Toast.LENGTH_LONG).show();
                    setIdleState();
                });

            } catch (InterruptedException e) {
                handleCancellation();
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                if (Thread.currentThread().isInterrupted()) {
                    handleCancellation();
                } else {
                    mainHandler.post(() -> showError(e.getMessage()));
                }
            } finally {
                transcriptionThread = null;
            }
        });
        transcriptionThread.start();
    }

    private void cancelTranscription() {
        if (transcriptionThread != null) {
            transcriptionThread.interrupt();
        }
    }

    private void setLoadingState() {
        layoutButtons.setVisibility(View.GONE);
        txtStatus.setVisibility(View.INVISIBLE);
        layoutProgress.setVisibility(View.VISIBLE);
    }

    private void setIdleState() {
        selectedUri = null;
        layoutButtons.setVisibility(View.VISIBLE);
        btnUpload.setEnabled(false);
        txtStatus.setText("Chưa chọn file nào");
        txtStatus.setVisibility(View.VISIBLE);
        layoutProgress.setVisibility(View.GONE);
    }

    private void handleCancellation() {
        mainHandler.post(() -> {
            Toast.makeText(getContext(), "Tác vụ đã được hủy", Toast.LENGTH_SHORT).show();
            setIdleState();
        });
    }

    private void updateProgressStatus(String msg) {
        mainHandler.post(() -> txtProgressStatus.setText(msg));
    }

    private void showError(String msg) {
        Toast.makeText(getContext(), "Lỗi: " + msg, Toast.LENGTH_LONG).show();
        setIdleState();
    }

    private void saveToDatabase(String name, String audioUriString, String fullText, List<TranscriptItem> items) {
        long recId = repo.insertTranscript(name, audioUriString, fullText);
        for (TranscriptItem it : items) {
            repo.insertSentence(recId, it.text, it.startTimeMs, it.endTimeMs, it.speaker);
        }
    }

    private String getSentencesJson(String transcriptId) throws Exception {
        String url = String.format("https://api.assemblyai.com/v2/transcript/%s/sentences", transcriptId);
        OkHttpClient client = new OkHttpClient();
        String apiKey = ApiKey.getApiKey(requireContext());
        Request req = new Request.Builder()
                .url(url)
                .header("authorization", apiKey)
                .get()
                .build();
        try (Response res = client.newCall(req).execute()) {
            if (!res.isSuccessful()) throw new IOException("Sentences fetch failed: " + res.code());
            return res.body().string();
        }
    }

    private File copyUriToFile(Uri uri) throws Exception {
        String uriName = getFileNameFromUri(uri);
        if (uriName == null) uriName = "upload_" + System.currentTimeMillis() + ".tmp";
        String sanitized = sanitizeFileName(uriName);
        File dir = new File(requireContext().getFilesDir(), "audio");
        if (!dir.exists()) dir.mkdirs();
        File out = new File(dir, sanitized);
        try (InputStream in = requireContext().getContentResolver().openInputStream(uri);
             FileOutputStream fo = new FileOutputStream(out)) {
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) != -1) {
                if (Thread.currentThread().isInterrupted()) {
                    fo.close();
                    out.delete();
                    throw new InterruptedException();
                }
                fo.write(buf, 0, r);
            }
        }
        return out;
    }

    private String getFileNameFromUri(Uri uri) {
        String result = null;
        if ("content".equals(uri.getScheme())) {
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
        String base = name.replaceAll("\\.[^.]*$", "");
        String ext = "";
        int dotIndex = name.lastIndexOf('.');
        if (dotIndex != -1) ext = name.substring(dotIndex);
        String normalized = java.text.Normalizer.normalize(base, java.text.Normalizer.Form.NFD);
        String noAccent = normalized.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        String clean = noAccent.replaceAll("[^a-zA-Z0-9_-]", "");
        if (clean.length() > 50) clean = clean.substring(0, 50);
        return clean + ext;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        cancelTranscription();
    }
}