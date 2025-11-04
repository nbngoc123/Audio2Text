package com.example.audio2text.network;

import com.example.audio2text.model.JobRequest;
import com.google.gson.JsonObject;
import okhttp3.RequestBody;
import okhttp3.ResponseBody; // Thêm import này
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET; // Thêm import này
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.Path; // Thêm import này
import retrofit2.http.Streaming; // Thêm import này
import retrofit2.http.Url;

public interface FreeConvertApiService {

    @POST("process/jobs")
    Call<JsonObject> createConversionJob(
            @Header("Authorization") String authToken,
            @Body JobRequest jobRequest
    );

    @POST
    Call<JsonObject> uploadFileAsBody(
            @Url String uploadUrl,
            @Body RequestBody body
    );

    // ================== THÊM CÁC PHƯƠNG THỨC MỚI ==================

    // 1. Dùng để lấy thông tin của một Job bằng ID
    @GET("process/jobs/{id}")
    Call<JsonObject> getJobStatus(
            @Header("Authorization") String authToken,
            @Path("id") String jobId
    );

    // 2. Dùng để tải file từ một URL động
    @GET
    @Streaming // Quan trọng: Báo cho Retrofit stream file, không load hết vào RAM
    Call<ResponseBody> downloadFile(
            @Url String fileUrl
    );
    // ===============================================================
}