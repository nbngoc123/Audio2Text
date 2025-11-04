package com.example.audio2text.data.repository;

import android.app.DownloadManager;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.lifecycle.MutableLiveData;
import com.example.audio2text.model.JobRequest;
import com.example.audio2text.network.ApiClient;
import com.example.audio2text.network.FreeConvertApiService;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okio.Okio;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ConversionRepository {
    private FreeConvertApiService apiService;
    private static final String YOUR_ACCESS_TOKEN = "Bearer api_production_74a3dded5770636084f9b66e24167979238446c0bd5d4c15f4e16356f21131f8.69096e775d83271de3af4aee.69096ecb142a194b363cedd4";
    private Handler pollingHandler;
    private Runnable pollingRunnable;

    public ConversionRepository() {
        apiService = ApiClient.getClient().create(FreeConvertApiService.class);
        pollingHandler = new Handler(Looper.getMainLooper());
    }

    public void startMp4ToMp3Conversion(Context context, Uri fileUri, MutableLiveData<String> resultLiveData) {
        // ... (phần tạo tasks và jobRequest giữ nguyên)
        Map<String, Object> tasks = new HashMap<>();
        // ...
        Map<String, String> importTaskMap = new HashMap<>();
        importTaskMap.put("operation", "import/upload");
        tasks.put("import-1", importTaskMap);
        Map<String, String> convertTaskMap = new HashMap<>();
        convertTaskMap.put("operation", "convert");
        convertTaskMap.put("input", "import-1");
        convertTaskMap.put("output_format", "mp3");
        tasks.put("convert-1", convertTaskMap);
        Map<String, Object> exportTaskMap = new HashMap<>();
        exportTaskMap.put("operation", "export/url");
        exportTaskMap.put("input", new String[]{"convert-1"});
        tasks.put("export-1", exportTaskMap);
        JobRequest jobRequest = new JobRequest(tasks);

        resultLiveData.postValue("Đang tạo job chuyển đổi...");
        apiService.createConversionJob(YOUR_ACCESS_TOKEN, jobRequest).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        JsonObject body = response.body();
                        // Lấy Job ID để bắt đầu polling
                        String jobId = body.get("id").getAsString();

                        JsonArray tasksArray = body.getAsJsonArray("tasks");
                        JsonObject importTask = findTaskByName(tasksArray, "import-1");

                        if (importTask == null) {
                            resultLiveData.postValue("Tạo job thất bại: Không tìm thấy task import.");
                            return;
                        }

                        JsonObject importTaskResult = importTask.getAsJsonObject("result");
                        if (importTaskResult == null) {
                            resultLiveData.postValue("Tạo job thất bại: Không tìm thấy kết quả upload.");
                            return;
                        }

                        JsonObject formParameters = importTaskResult.getAsJsonObject("form");
                        if (formParameters == null) {
                            resultLiveData.postValue("Tạo job thất bại: Không tìm thấy form parameters.");
                            return;
                        }

                        String uploadUrl = formParameters.get("url").getAsString();
                        // Truyền Job ID vào hàm uploadFile
                        uploadFile(context, fileUri, uploadUrl, formParameters, jobId, resultLiveData);

                    } catch (Exception e) {
                        resultLiveData.postValue("Tạo job thất bại: Lỗi phân tích JSON - " + e.getMessage());
                    }
                } else {
                    handleApiError(response, "Tạo job thất bại", resultLiveData);
                }
            }
            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                resultLiveData.postValue("Lỗi mạng khi tạo job: " + t.getMessage());
            }
        });
    }

    private void uploadFile(Context context, Uri fileUri, String uploadUrl, JsonObject formParameters, String jobId, MutableLiveData<String> resultLiveData) {
        try {
            resultLiveData.postValue("Đang tải file lên...");
            InputStream inputStream = context.getContentResolver().openInputStream(fileUri);
            byte[] fileBytes = Okio.buffer(Okio.source(inputStream)).readByteArray();
            inputStream.close();
            RequestBody fileRequestBody = RequestBody.create(MediaType.parse(context.getContentResolver().getType(fileUri)), fileBytes);

            JsonObject uploadParams = formParameters.getAsJsonObject("parameters");
            MultipartBody.Builder multipartBodyBuilder = new MultipartBody.Builder().setType(MultipartBody.FORM);
            for (Map.Entry<String, JsonElement> entry : uploadParams.entrySet()) {
                if (entry.getValue() != null && !entry.getValue().isJsonNull()) {
                    multipartBodyBuilder.addFormDataPart(entry.getKey(), entry.getValue().getAsString());
                }
            }
            multipartBodyBuilder.addFormDataPart("file", "uploaded_video.mp4", fileRequestBody);
            RequestBody finalRequestBody = multipartBodyBuilder.build();

            apiService.uploadFileAsBody(uploadUrl, finalRequestBody).enqueue(new Callback<JsonObject>() {
                @Override
                public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                    if (response.isSuccessful()) {
                        resultLiveData.postValue("Upload thành công! Đang chờ xử lý...");
                        // BẮT ĐẦU POLLING SAU KHI UPLOAD THÀNH CÔNG
                        startPolling(jobId, context, resultLiveData);
                    } else {
                        handleApiError(response, "Upload thất bại", resultLiveData);
                    }
                }
                @Override
                public void onFailure(Call<JsonObject> call, Throwable t) {
                    resultLiveData.postValue("Lỗi mạng khi upload: " + t.getMessage());
                }
            });
        } catch (Exception e) {
            resultLiveData.postValue("Lỗi khi chuẩn bị upload: " + e.getMessage());
        }
    }

    // ================== LOGIC MỚI CHO POLLING ==================
    private void startPolling(String jobId, Context context, MutableLiveData<String> resultLiveData) {
        stopPolling(); // Dừng polling cũ nếu có
        pollingRunnable = () -> {
            // Chỉ cập nhật trạng thái "Đang kiểm tra" khi thực sự gọi API
            resultLiveData.postValue("Đang kiểm tra trạng thái...");
            apiService.getJobStatus(YOUR_ACCESS_TOKEN, jobId).enqueue(new Callback<JsonObject>() {
                @Override
                public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        JsonObject body = response.body();
                        String jobStatus = body.get("status").getAsString();
                        Log.d("Polling", "Job status: " + jobStatus);

                        if ("completed".equals(jobStatus)) {
                            stopPolling();
                            JsonArray tasksArray = body.getAsJsonArray("tasks");
                            JsonObject exportTask = findTaskByName(tasksArray, "export-1");

                            if (exportTask != null && exportTask.has("result")) {
                                JsonObject result = exportTask.getAsJsonObject("result");
                                if (result.has("url") && !result.get("url").getAsString().isEmpty()) {
                                    String downloadUrl = result.get("url").getAsString();
                                    String filename = "converted_file.mp3";
                                    if(result.has("filename") && !result.get("filename").isJsonNull()){
                                        filename = result.get("filename").getAsString();
                                    }
                                    resultLiveData.postValue("Hoàn thành! Chuẩn bị tải về...");
                                    downloadFileWithManager(context, downloadUrl, filename);

                                    return;
                                }
                            }
                            resultLiveData.postValue("Hoàn thành nhưng không tìm thấy file!");

                        } else if ("failed".equals(jobStatus)) {
                            resultLiveData.postValue("Xử lý thất bại trên server.");
                            stopPolling();
                        } else {
                            // Job chưa xong, tiếp tục polling sau 5 giây
                            resultLiveData.postValue("Upload thành công! Đang chờ xử lý..."); // Cập nhật lại trạng thái chờ
                            pollingHandler.postDelayed(pollingRunnable, 5000);
                        }
                    } else {
                        resultLiveData.postValue("Kiểm tra thất bại. Mã lỗi: " + response.code());
                        stopPolling();
                    }
                }

                @Override
                public void onFailure(Call<JsonObject> call, Throwable t) {
                    resultLiveData.postValue("Lỗi mạng khi kiểm tra trạng thái: " + t.getMessage());
                    stopPolling();
                }
            });
        };
        // ================== SỬA LỖI Ở ĐÂY ==================
        // Di chuyển lệnh này ra ngoài lambda để nó được gọi ngay lập tức lần đầu tiên
        pollingHandler.post(pollingRunnable);
        // ================================================
    }

    public void stopPolling() {
        if (pollingHandler != null && pollingRunnable != null) {
            pollingHandler.removeCallbacks(pollingRunnable);
        }
    }

    // ================== LOGIC MỚI ĐỂ TẢI FILE ==================
    private void downloadFileWithManager(Context context, String url, String filename) {
        DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        Uri uri = Uri.parse(url);
        DownloadManager.Request request = new DownloadManager.Request(uri);
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename);
        downloadManager.enqueue(request);
        Log.d("Download", "Bắt đầu tải file: " + filename);
    }


    // ================== HÀM TIỆN ÍCH ==================
    private JsonObject findTaskByName(JsonArray tasksArray, String name) {
        for (JsonElement taskElement : tasksArray) {
            JsonObject taskObject = taskElement.getAsJsonObject();
            if (name.equals(taskObject.get("name").getAsString())) {
                return taskObject;
            }
        }
        return null;
    }

    private void handleApiError(Response<?> response, String prefix, MutableLiveData<String> resultLiveData) {
        String errorBody = "Không thể đọc nội dung lỗi.";
        try {
            if (response.errorBody() != null) { errorBody = response.errorBody().string(); }
        } catch (IOException e) { /* Bỏ qua */ }
        resultLiveData.postValue(prefix + ". Mã lỗi: " + response.code() + ". Chi tiết: " + errorBody);
    }
}