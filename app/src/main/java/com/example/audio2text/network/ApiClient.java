package com.example.audio2text.network;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor; // Thêm import này
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {
    private static final String BASE_URL = "https://api.freeconvert.com/v1/";
    private static Retrofit retrofit = null;

    public static Retrofit getClient() {
        if (retrofit == null) {
            // --- THÊM PHẦN CODE NÀY ĐỂ BẬT LOGGING ---
            HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
            loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY); // In ra toàn bộ body

            OkHttpClient client = new OkHttpClient.Builder()
                    .addInterceptor(loggingInterceptor)
                    .build();
            // ------------------------------------------

            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(client) // <-- Sử dụng OkHttpClient đã có logging
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofit;
    }
}