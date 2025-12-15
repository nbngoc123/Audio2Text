package com.example.audio2text.util;

import com.example.audio2text.model.TranscriptItem;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;

import java.util.ArrayList;
import java.util.List;
public class TranslateTranscripts {
    // Interface để gửi thông báo trạng thái về UI
    public interface StatusCallback {
        void onStatusUpdate(String message);
    }

    public static List<TranscriptItem> translateList(
            List<TranscriptItem> originals,
            String targetLangCode,
            String sourceLangStr,
            StatusCallback callback
    ) throws Exception {

        if (targetLangCode == null) return originals;

        if (callback != null) callback.onStatusUpdate("Đang cấu hình gói ngôn ngữ...");

        // 1. Map mã ngôn ngữ từ AssemblyAI/User sang hằng số ML Kit
        String sourceLangML = mapLanguageCode(sourceLangStr);

        // Log để kiểm tra
        // Log.d("TransUtil", "Dịch từ " + sourceLangML + " sang " + targetLangCode);

        TranslatorOptions options = new TranslatorOptions.Builder()
                .setSourceLanguage(sourceLangML)
                .setTargetLanguage(targetLangCode)
                .build();

        Translator translator = Translation.getClient(options);

        // 2. Tải model
        if (callback != null) callback.onStatusUpdate("Đang tải model dịch (" + sourceLangML + " -> " + targetLangCode + ")...");

        DownloadConditions conditions = new DownloadConditions.Builder().requireWifi().build();
        // Dùng Tasks.await để chạy đồng bộ trong Thread nền
        Tasks.await(translator.downloadModelIfNeeded(conditions));

        // 3. Thực hiện dịch
        if (callback != null) callback.onStatusUpdate("Đang dịch nội dung phụ đề...");

        List<TranscriptItem> translatedList = new ArrayList<>();

        for (TranscriptItem item : originals) {
            // Dịch từng câu
            String translatedText = Tasks.await(translator.translate(item.text));

            // Tạo item mới
            translatedList.add(new TranscriptItem(
                    item.label,
                    translatedText,
                    item.startTimeMs,
                    item.endTimeMs,
                    item.speaker
            ));
        }

        translator.close();
        return translatedList;
    }

    // Hàm phụ trợ: Chuyển đổi mã string sang hằng số ML Kit
    private static String mapLanguageCode(String code) {
        if (code == null) return TranslateLanguage.ENGLISH;
        switch (code.toLowerCase()) {
            case "vi": return TranslateLanguage.VIETNAMESE;
            case "en": return TranslateLanguage.ENGLISH;
            case "ja": return TranslateLanguage.JAPANESE;
            case "ko": return TranslateLanguage.KOREAN;
            case "fr": return TranslateLanguage.FRENCH;
            case "de": return TranslateLanguage.GERMAN;
            case "es": return TranslateLanguage.SPANISH;
            case "zh": return TranslateLanguage.CHINESE;
            default: return TranslateLanguage.ENGLISH; // Mặc định nếu không tìm thấy
        }
    }
}
