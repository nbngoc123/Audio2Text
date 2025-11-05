package com.example.audio2text.util;

import android.content.Context;
import android.content.SharedPreferences;

public class ApiKey {

    private static final String PREFS_NAME = "Audio2TextPrefs";
    private static final String API_KEY_FIELD = "user_api_key";

    // API Key mặc định (chỉ dùng khi không có key nào được lưu)
    private static final String DEFAULT_API_KEY = "ec694940212346baa6ea072070884798";

    /**
     * Lưu API Key do người dùng nhập vào bộ nhớ vĩnh viễn của ứng dụng.
     * @param context Context của ứng dụng, cần thiết để truy cập SharedPreferences.
     * @param apiKey  Key do người dùng nhập vào.
     */
    public static void saveApiKey(Context context, String apiKey) {
        if (context == null || apiKey == null) return;
        SharedPreferences.Editor editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
        editor.putString(API_KEY_FIELD, apiKey);
        editor.apply();
    }

    /**
     * Lấy API Key đã được người dùng lưu.
     * Nếu người dùng chưa từng lưu key nào, nó sẽ trả về key mặc định.
     * @param context Context của ứng dụng.
     * @return API Key để sử dụng cho các yêu cầu mạng.
     */
    public static String getApiKey(Context context) {
        if (context == null) return DEFAULT_API_KEY;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        // Lấy key đã lưu, nếu không có thì dùng key mặc định
        return prefs.getString(API_KEY_FIELD, DEFAULT_API_KEY);
    }

    /**
     * Lấy API Key đã được che bớt ký tự để hiển thị an toàn trên giao diện.
     * @param context Context của ứng dụng.
     * @return Chuỗi mô tả API Key hiện tại.
     */
    public static String getMaskedApiKey(Context context) {
        // Lấy key hiện đang được sử dụng (có thể là key người dùng lưu hoặc key mặc định)
        String key = getApiKey(context);

        if (key.isEmpty()) {
            return "Chưa có API Key";
        }

        if (key.length() <= 4) {
            return "API Key: " + key;
        }

        // Che bớt ký tự, chỉ hiển thị 4 ký tự đầu
        String stars = new String(new char[key.length() - 4]).replace("\0", "*");
        return "API Key: " + key.substring(0, 4) + stars;
    }
}