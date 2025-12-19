package com.example.audio2text.ui.vietsub;

import java.io.IOException;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.audio2text.data.db.TranscriptionDatabaseHelper;
import com.example.audio2text.network.AzureTTSManager;
import com.example.audio2text.util.DubbingUtils;
import com.example.audio2text.util.TranslateTranscripts;

// --- IMPORT CHUẨN CỦA FFMPEG-KIT ---
import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.FFmpegSession;
import com.arthenica.ffmpegkit.ReturnCode;
import com.arthenica.ffmpegkit.SessionState;

import com.google.mlkit.nl.translate.TranslateLanguage;


import com.example.audio2text.R;
import com.example.audio2text.model.TranscriptItem;
import com.example.audio2text.network.TranscriptionService;
import com.example.audio2text.util.SubtitleUtils;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.List;

public class VietsubFragment extends Fragment {
    private CheckBox cbEnableDubbing;
    private Button btnChooseVideo, btnProcess;
    private TextView txtStatus;
    private ProgressBar progressBar;

    private Uri selectedUri;
    private TranscriptionService svc;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile Thread processThread;
    private Spinner spinnerLanguage, spinnerTargetLanguage;
    private CheckBox cbExportSrt;
    private TranscriptionDatabaseHelper dbHelper;
    private final ActivityResultLauncher<Intent> pickVideoLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    selectedUri = result.getData().getData();
                    txtStatus.setText("Đã chọn video. Nhấn 'Tạo Vietsub' để bắt đầu.");
                    btnProcess.setVisibility(View.VISIBLE);
                }
            }
    );

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_vietsub, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        btnChooseVideo = view.findViewById(R.id.btnSelectVideo);
        btnProcess = view.findViewById(R.id.btnProcess);
        txtStatus = view.findViewById(R.id.txtStatus);
        progressBar = view.findViewById(R.id.progressBar);
        spinnerLanguage = view.findViewById(R.id.spinnerLanguage);
        spinnerTargetLanguage = view.findViewById(R.id.spinnerTargetLanguage);
        cbExportSrt = view.findViewById(R.id.cbExportSrt);
        svc = new TranscriptionService(requireContext());
        cbEnableDubbing = view.findViewById(R.id.cbEnableDubbing);

        btnChooseVideo.setOnClickListener(v -> pickVideo());
        btnProcess.setOnClickListener(v -> startVietsubProcess());
        setupLanguageSpinner();
        dbHelper = new TranscriptionDatabaseHelper(requireContext());
    }

    private void pickVideo() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("video/mp4");
        pickVideoLauncher.launch(Intent.createChooser(intent, "Chọn Video MP4"));
    }

    // Hàm cài đặt Spinner
    private void setupLanguageSpinner() {
        // Setup Spinner Nguồn (Source)
        String[] sourceLangs = {"Tự động (Auto)", "Tiếng Việt", "Tiếng Anh", "Tiếng Nhật"};
        ArrayAdapter<String> adapterSrc = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, sourceLangs);
        adapterSrc.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerLanguage.setAdapter(adapterSrc);

        // Setup Spinner Đích (Target) - Thêm tùy chọn "Giữ nguyên"
        String[] targetLangs = {"Giữ nguyên (Không dịch)", "Tiếng Việt", "Tiếng Anh", "Tiếng Nhật", "Tiếng Hàn"};
        ArrayAdapter<String> adapterTarget = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, targetLangs);
        adapterTarget.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerTargetLanguage.setAdapter(adapterTarget);
    }
    // Hàm phụ trợ để lấy mã code từ lựa chọn
    private String getSelectedLanguageCode() {
        int position = spinnerLanguage.getSelectedItemPosition();
        switch (position) {
            case 0: return "auto";
            case 1: return "vi";
            case 2: return "en";
            case 3: return "ja";
            case 4: return "ko";
            default: return "auto";
        }
    }

    // Helper lấy mã ngôn ngữ đích cho ML Kit
    private String getTargetLangCodeForMLKit() {
        int pos = spinnerTargetLanguage.getSelectedItemPosition();
        switch (pos) {
            case 1: return TranslateLanguage.VIETNAMESE;
            case 2: return TranslateLanguage.ENGLISH;
            case 3: return TranslateLanguage.JAPANESE;
            case 4: return TranslateLanguage.KOREAN;
            default: return null;
        }
    }
    private void startVietsubProcess() {
        if (selectedUri == null) return;
        setupFont();
        setLoading(true);
        processThread = new Thread(() -> {
            try {
                // 1. Copy Video
                updateStatus("Đang chuẩn bị video...");
                File inputVideoFile = copyUriToFile(selectedUri);

                // 2. Tách MP3
                updateStatus("Đang tách âm thanh...");
                File audioFile = extractAudioFromVideo(inputVideoFile);

                // 3. Upload & 4. Tạo Transcript
                updateStatus("Đang tải lên server...");
                String uploadUrl = svc.uploadFile(audioFile);

                String langCode = getSelectedLanguageCode();
                updateStatus("Đang phân tích giọng nói...");
                JSONObject createRes = svc.createTranscript(uploadUrl, langCode, false);
                String transcriptId = createRes.optString("id");

                if (transcriptId.isEmpty()) throw new Exception("Không lấy được ID transcript");

                // 5. Polling kết quả
                updateStatus("Đang chờ kết quả AI...");
                JSONObject result = svc.pollForResult(transcriptId, 120, 2000);

                if (result == null || !"completed".equals(result.optString("status"))) {
                    throw new Exception("Lỗi nhận diện: " + result.optString("status"));
                }

                // 6. Lấy câu thoại
                updateStatus("Đang tải phụ đề...");
                String sentencesJson = getSentencesJson(transcriptId);
                List<TranscriptItem> transcripts = TranscriptionService.parseSentences(result.toString());
                // --- LOGIC DỊCH THUẬT ---
                String targetLang = getTargetLangCodeForMLKit();

                String detectedLang = result.optString("language_code", "en");

                if (targetLang != null) {
                    // Gọi hàm static từ Translation Utils
                    // Truyền vào 'msg -> updateStatus(msg)' để Util có thể cập nhật Text trên màn hình
                    transcripts = TranslateTranscripts.translateList(
                            transcripts,
                            targetLang,
                            detectedLang,
                            msg -> updateStatus(msg) // Callback cập nhật UI
                    );
                }
                // 7. Tạo file SRT
                updateStatus("Đang tạo file phụ đề...");
                File srtFile = SubtitleUtils.createSrtFile(transcripts, requireContext().getCacheDir());
                Log.d("Vietsub", "Số lượng câu thoại: " + transcripts.size());
                if (transcripts.isEmpty()) {
                    mainHandler.post(() -> Toast.makeText(getContext(), "Cảnh báo: AI không tìm thấy giọng nói nào!", Toast.LENGTH_LONG).show());
                }

                // --- LOGIC XUẤT FILE ---
                if (cbExportSrt.isChecked()) {
                    updateStatus("Đang xuất file SRT...");
                    exportSrtToPublic(srtFile);
                }
                File finalVideoToSub = inputVideoFile;
                // 7b. Lồng tiếng (Auto Dubbing)
                if (cbEnableDubbing.isChecked()) {
                    updateStatus("Đang tiến hành lồng tiếng AI...");
                    AzureTTSManager ttsManager = new AzureTTSManager();
                    List<File> voiceFiles = DubbingUtils.generateVoiceOvers(
                            transcripts, ttsManager, requireContext(), msg -> updateStatus(msg));

                    updateStatus("Đang trộn âm thanh...");
                    File dubOutputFile = new File(requireContext().getExternalFilesDir(Environment.DIRECTORY_MOVIES),
                            "Dubbing_" + System.currentTimeMillis() + ".mp4");
                    float backgroundVol = 0.45f;
                    float dubbedVoiceVol = 2.5f;
                    String dubCmd = DubbingUtils.buildFFmpegDubbingCommandWithDucking(
                            inputVideoFile,
                            voiceFiles,
                            transcripts,
                            dubOutputFile,
                            backgroundVol,
                            dubbedVoiceVol
                    );

//                    FFmpegSession dubSession = FFmpegKit.execute(dubCmd);
//                    if (ReturnCode.isSuccess(dubSession.getReturnCode())) {
//                        finalVideoToSub = dubOutputFile;
//                        Log.d("Dubbing", "Trộn âm thanh thành công!");
//                    } else {
//                        Log.e("DubbingError", "Mã lỗi: " + dubSession.getReturnCode());
//                        Log.e("DubbingError", "Nội dung lỗi: " + dubSession.getOutput());
//                        throw new Exception("Lỗi lồng tiếng FFmpeg: " + dubSession.getFailStackTrace());
//                    }

                    FFmpegSession dubSession = FFmpegKit.execute(dubCmd);
                    if (ReturnCode.isSuccess(dubSession.getReturnCode())) {
                        finalVideoToSub = dubOutputFile;
                    } else {
                        String lastLog = dubSession.getOutput();
                        if (lastLog == null) lastLog = "Tiến trình bị hệ thống kill (Out of Memory/Command too long)";

                        Log.e("DubbingDebug", "Lệnh: " + dubCmd);
                        Log.e("DubbingDebug", "Lỗi: " + lastLog);
                        throw new Exception("FFmpeg Error: " + lastLog);
                    }
                }
                // ---------------------------

                // 8. Gắn Sub vào Video
                updateStatus("Đang gắn phụ đề vào video...");
                burnSubtitles(finalVideoToSub, srtFile);

            } catch (Exception e) {
                e.printStackTrace();
                mainHandler.post(() -> {
                    setLoading(false);
                    Toast.makeText(getContext(), "Lỗi: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    txtStatus.setText("Lỗi: " + e.getMessage());
                });
            }
        });
        processThread.start();
    }

    private String getSentencesJson(String transcriptId) throws Exception {
        okhttp3.OkHttpClient client = new okhttp3.OkHttpClient();
        String url = "https://api.assemblyai.com/v2/transcript/" + transcriptId + "/sentences";
        okhttp3.Request req = new okhttp3.Request.Builder()
                .url(url)
                .header("authorization", com.example.audio2text.util.ApiKey.getApiKey(requireContext()))
                .get()
                .build();
        try (okhttp3.Response res = client.newCall(req).execute()) {
            if (!res.isSuccessful()) throw new Exception("Lỗi lấy câu: " + res.code());
            return res.body().string();
        }
    }

    private File extractAudioFromVideo(File videoFile) throws Exception {
        File outputAudio = new File(requireContext().getCacheDir(), "extracted_audio.mp3");
//        File outputAudio = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "extracted_audio_TEST.mp3");

        // Xóa file cũ trước khi tách
        if (outputAudio.exists()) outputAudio.delete();

        // Cú pháp FFmpegKit: -y (ghi đè) -i (input) -vn (bỏ video) -acodec (codec âm thanh)
        String cmd = String.format("-y -i \"%s\" -vn -acodec libmp3lame \"%s\"",
                videoFile.getAbsolutePath(), outputAudio.getAbsolutePath());

        // Gọi lệnh đồng bộ (synchronous) vì đang ở trong Thread phụ
        FFmpegSession session = FFmpegKit.execute(cmd);

        if (ReturnCode.isSuccess(session.getReturnCode())) {
            return outputAudio;
        } else {
            // In log lỗi nếu thất bại
            Log.e("FFmpegError", session.getFailStackTrace());
            throw new Exception("Lỗi tách âm thanh");
        }
    }

    private void burnSubtitles(File videoFile, File srtFile) {
        // 1. Kiểm tra xem file SRT có nội dung không
        if (srtFile.length() < 10) { // File quá nhỏ nghĩa là không có chữ
            mainHandler.post(() -> {
                setLoading(false);
                Toast.makeText(getContext(), "Lỗi: Không có nội dung phụ đề để gắn!", Toast.LENGTH_LONG).show();
            });
            return;
        }

        File appDir = requireContext().getExternalFilesDir(Environment.DIRECTORY_MOVIES);
        String outputName = "Vietsub_" + System.currentTimeMillis() + ".mp4";
        File outputFile = new File(appDir, outputName);

        // 2. Cấu hình Style cho phụ đề (Quan trọng)
        // FontSize=24: Cỡ chữ
        // PrimaryColour=&H00FFFF: Màu Vàng (Cyan/Yellow channel encoding trong ASS hơi ngược, nhưng cứ để mặc định hoặc test màu này)
        // BorderStyle=1, Outline=2: Viền đen đậm để nổi trên mọi nền
        String style = "force_style='FontName=arial,FontSize=24,PrimaryColour=&H00FFFF,BorderStyle=1,Outline=2,Shadow=0,MarginV=20'";

        // 3. Lệnh FFmpeg với Style
        // Lưu ý: Đường dẫn srtFile cần được xử lý dấu ' để tránh lỗi lệnh
        // Cách an toàn nhất là dùng escape path
        String cmd = String.format("-y -i \"%s\" -vf \"subtitles='%s':%s\" \"%s\"",
                videoFile.getAbsolutePath(),
                srtFile.getAbsolutePath().replace("'", "'\\''"), // Xử lý nếu đường dẫn có dấu nháy đơn
                style,
                outputFile.getAbsolutePath());

        Log.d("Vietsub", "Command: " + cmd);

        FFmpegKit.executeAsync(cmd, session -> {
            ReturnCode returnCode = session.getReturnCode();
            mainHandler.post(() -> {
                setLoading(false);
                if (ReturnCode.isSuccess(returnCode)) {
                    txtStatus.setText("Thành công!\nLưu tại: " + outputFile.getAbsolutePath());
                    Toast.makeText(getContext(), "Xong! Video đã có phụ đề.", Toast.LENGTH_LONG).show();
                    saveToGallery(outputFile);
                    // lưu vào db
                    dbHelper.insertTranscript(outputFile.getName(), outputFile.getAbsolutePath(), "Video có phụ đề", 1);
                    mainHandler.post(() -> {
                        Toast.makeText(getContext(), "Đã lưu video vào lịch sử", Toast.LENGTH_SHORT).show();
                    });
                } else {
                    txtStatus.setText("Lỗi gắn sub (Xem Logcat)");
                    Log.e("VietsubError", session.getFailStackTrace());
                    Log.e("VietsubOutput", session.getOutput()); // Xem output để biết tại sao lỗi
                }
            });
        });
    }
    private void saveToGallery(File videoFile) {
        android.content.ContentValues values = new android.content.ContentValues();
        values.put(android.provider.MediaStore.Video.Media.DISPLAY_NAME, "Vietsub_" + System.currentTimeMillis());
        values.put(android.provider.MediaStore.Video.Media.MIME_TYPE, "video/mp4");
        values.put(android.provider.MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES);

        try {
            Uri uri = requireContext().getContentResolver().insert(android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
            if (uri != null) {
                try (java.io.OutputStream out = requireContext().getContentResolver().openOutputStream(uri);
                     java.io.FileInputStream in = new java.io.FileInputStream(videoFile)) {
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
                    Toast.makeText(getContext(), "Đã lưu vào Bộ sưu tập!", Toast.LENGTH_SHORT).show();
                }
            }
        } catch (Exception e) {
            e.printStackTrace(); // Không quan trọng, file gốc vẫn còn trong thư mục app
        }
    }

    private File copyUriToFile(Uri uri) throws Exception {
        File dir = new File(requireContext().getCacheDir(), "video_cache");
        if (!dir.exists()) dir.mkdirs();
        File out = new File(dir, "temp_video.mp4");

        try (InputStream in = requireContext().getContentResolver().openInputStream(uri);
             FileOutputStream fo = new FileOutputStream(out)) {
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) != -1) {
                fo.write(buf, 0, r);
            }
        }
        return out;
    }


    private void updateStatus(String msg) {
        mainHandler.post(() -> txtStatus.setText(msg));
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnChooseVideo.setEnabled(!loading);
        btnProcess.setEnabled(!loading);
    }

    private void setupFont() {
        File fontDir = new File(requireContext().getFilesDir(), "fonts");
        if (!fontDir.exists()) fontDir.mkdirs();

        File fontFile = new File(fontDir, "arial.ttf");
        if (!fontFile.exists()) {
            try (InputStream in = requireContext().getAssets().open("arial.ttf"); // Tên file trong assets
                 FileOutputStream out = new FileOutputStream(fontFile)) {
                byte[] buffer = new byte[1024];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // QUAN TRỌNG: Chỉ định thư mục font cho FFmpegKit
        // Cần import com.arthenica.ffmpegkit.FFmpegKitConfig;
        com.arthenica.ffmpegkit.FFmpegKitConfig.setFontDirectory(requireContext(), fontDir.getAbsolutePath(), null);
    }


    private void exportSrtToPublic(File internalSrtFile) {
        // Dùng MediaStore để lưu file text vào thư mục Documents/Downloads
        android.content.ContentValues values = new android.content.ContentValues();
        values.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "Subtitle_" + System.currentTimeMillis() + ".srt");
        values.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "text/plain"); // Hoặc application/x-subrip
        values.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS);

        try {
            Uri uri = requireContext().getContentResolver().insert(android.provider.MediaStore.Files.getContentUri("external"), values);
            if (uri != null) {
                try (java.io.OutputStream out = requireContext().getContentResolver().openOutputStream(uri);
                     java.io.FileInputStream in = new java.io.FileInputStream(internalSrtFile)) {
                    byte[] buf = new byte[1024];
                    int len;
                    while ((len = in.read(buf)) > 0) out.write(buf, 0, len);

                    mainHandler.post(() -> Toast.makeText(getContext(), "Đã lưu file SRT vào thư mục Documents!", Toast.LENGTH_LONG).show());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            mainHandler.post(() -> Toast.makeText(getContext(), "Lỗi lưu SRT: " + e.getMessage(), Toast.LENGTH_SHORT).show());
        }
    }


}
