package com.example.audio2text.util;

import android.content.Context;
import android.util.Log;
import com.example.audio2text.model.TranscriptItem;
import com.example.audio2text.network.AzureTTSManager;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DubbingUtils {

    private static final String TAG = "DubbingDebug";

    public interface ProgressCallback {
        void onUpdate(String message);
    }

    public static List<File> generateVoiceOvers(
            List<TranscriptItem> items,
            AzureTTSManager ttsManager,
            Context context,
            ProgressCallback callback) throws Exception {

        Log.d(TAG, "Bắt đầu tạo Voice-over cho " + items.size() + " câu thoại.");
        List<File> audioFiles = new ArrayList<>();

        for (int i = 0; i < items.size(); i++) {
            TranscriptItem item = items.get(i);
            String debugMsg = "Đang xử lý câu " + (i + 1) + ": " + item.getText();
            Log.d(TAG, debugMsg);
            callback.onUpdate("Đang tạo voice cho câu " + (i + 1) + "/" + items.size());

            String fileName = "dub_" + i + "_" + System.currentTimeMillis() + ".wav";
            try {
                File audio = ttsManager.synthesize(item.getText(), fileName, context);
                if (audio != null && audio.exists()) {
                    Log.d(TAG, "-> Đã lưu file: " + audio.getAbsolutePath() + " (Size: " + audio.length() + " bytes)");
                    audioFiles.add(audio);
                }
            } catch (Exception e) {
                Log.e(TAG, "-> Lỗi tại câu " + i + ": " + e.getMessage());
                throw e;
            }
        }
        return audioFiles;
    }

    public static String buildFFmpegDubbingCommandWithDucking(
            File inputVideo,
            List<File> audioFiles,
            List<TranscriptItem> items,
            File outputFile,
            float bgVolume,
            float voiceVolume
    ) {

        StringBuilder inputs = new StringBuilder();
        StringBuilder filter = new StringBuilder();
        StringBuilder amixLabels = new StringBuilder();

        // Thêm input video gốc
        inputs.append(String.format("-i \"%s\" ", inputVideo.getAbsolutePath()));

        // Thêm các input audio lồng tiếng
        for (File audio : audioFiles) {
            inputs.append(String.format("-i \"%s\" ", audio.getAbsolutePath()));
        }

        filter.append("-filter_complex \"");

        // 1. Áp dụng âm lượng cho background
        filter.append(String.format(Locale.US, "[0:a]volume=%.2f[bg];", bgVolume));

        for (int i = 0; i < items.size(); i++) {
            long startMs = items.get(i).getStart();
            filter.append(String.format(Locale.US, "[%d:a]adelay=%d:all=1,volume=%.2f[a%d];",
                    i + 1, startMs, voiceVolume, i + 1));
            amixLabels.append(String.format(Locale.US, "[a%d]", i + 1));
        }

        // 3. Trộn tất cả lại với nhau
        filter.append("[bg]");
        filter.append(amixLabels.toString());

        // normalize=0: Giữ nguyên mức âm lượng đã thiết lập, không chia đều theo số lượng input
        filter.append(String.format("amix=inputs=%d:duration=first:dropout_transition=0:normalize=0[outa]\"",
                items.size() + 1));

        // Map video gốc và audio đã mix, copy video stream để xử lý cực nhanh (không cần render lại video)
        return String.format("-y %s %s -map 0:v -map \"[outa]\" -c:v copy -c:a aac -b:a 192k -shortest \"%s\"",
                inputs.toString(), filter.toString(), outputFile.getAbsolutePath());
    }
}