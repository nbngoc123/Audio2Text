package com.example.audio2text.util;

import android.content.Context;
import android.util.Log;

import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.FFmpegSession;
import com.arthenica.ffmpegkit.ReturnCode;
import com.example.audio2text.model.TranscriptItem;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class SubtitleUtils {

    public static File createSrtFile(List<TranscriptItem> transcripts, File outputDir) {
        File srtFile = new File(outputDir, "subtitles.srt");
        try (FileWriter writer = new FileWriter(srtFile)) {
            for (int i = 0; i < transcripts.size(); i++) {
                TranscriptItem item = transcripts.get(i);

                // 1. Số thứ tự (SRT bắt đầu từ 1)
                writer.write((i + 1) + "\n");

                // 2. Thời gian: 00:00:01,000 --> 00:00:04,000
                String timeLine = String.format("%s --> %s\n",
                        formatTime(item.startTimeMs), formatTime(item.endTimeMs));
                writer.write(timeLine);

                // 3. Nội dung text
                writer.write(item.text + "\n\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        return srtFile;
    }

    private static String formatTime(long millis) {
        return String.format(Locale.getDefault(), "%02d:%02d:%02d,%03d",
                TimeUnit.MILLISECONDS.toHours(millis),
                TimeUnit.MILLISECONDS.toMinutes(millis) - TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(millis)),
                TimeUnit.MILLISECONDS.toSeconds(millis) - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(millis)),
                millis % 1000);
    }

    public static File extractAudioFromVideo(Context context, File videoFile) throws Exception {
        // Sử dụng context.getCacheDir() thay vì requireContext()
        File outputAudio = new File(context.getCacheDir(), "extracted_audio_" + System.currentTimeMillis() + ".mp3");

        if (outputAudio.exists()) outputAudio.delete();

        // Lệnh FFmpeg: -y (ghi đè), -i (input), -vn (loại bỏ video), -acodec libmp3lame (nén mp3)
        String cmd = String.format("-y -i \"%s\" -vn -acodec libmp3lame \"%s\"",
                videoFile.getAbsolutePath(), outputAudio.getAbsolutePath());

        Log.d("FFmpeg", "Đang thực thi: " + cmd);
        FFmpegSession session = FFmpegKit.execute(cmd);

        if (ReturnCode.isSuccess(session.getReturnCode())) {
            return outputAudio;
        } else {
            Log.e("FFmpegError", "Lỗi: " + session.getFailStackTrace());
            throw new Exception("Không thể tách âm thanh từ video. Kiểm tra định dạng file!");
        }
    }
}