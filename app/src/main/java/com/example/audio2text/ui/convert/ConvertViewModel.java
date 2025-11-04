package com.example.audio2text.ui.convert;

import android.content.Context;
import android.net.Uri;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.example.audio2text.data.repository.ConversionRepository;

public class ConvertViewModel extends ViewModel {
    private ConversionRepository conversionRepository;
    private MutableLiveData<String> conversionStatus;

    public ConvertViewModel() {
        this.conversionRepository = new ConversionRepository();
        this.conversionStatus = new MutableLiveData<>();
    }

    public LiveData<String> getConversionStatus() {
        return conversionStatus;
    }

    public void startConversion(Context context, Uri fileUri) {
        conversionRepository.startMp4ToMp3Conversion(context, fileUri, conversionStatus);
    }

    // ================== THÊM PHƯƠNG THỨC MỚI ==================
    @Override
    protected void onCleared() {
        super.onCleared();
        // Tự động dừng polling khi ViewModel bị hủy
        conversionRepository.stopPolling();
    }
}