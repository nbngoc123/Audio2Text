package com.example.audio2text.ui.convert;


import android.Manifest;
import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.example.audio2text.R;
import com.google.android.material.card.MaterialCardView;

public class ConvertFragment extends Fragment {

    private ConvertViewModel viewModel;
    private Button btnSelectFile;
    private Button btnConvert;
    private TextView tvSelectedFileName;
    private TextView tvStatus;
    private ProgressBar progressBar;
    private MaterialCardView cardSelectedFile;

    // MỚI: Biến để lưu trữ URI của file đã chọn
    private Uri selectedFileUri = null;

    // MỚI: Trình khởi chạy để xử lý kết quả xin quyền
    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    // Người dùng đã cấp quyền, mở trình chọn file
                    launchFilePicker();
                } else {
                    // Người dùng từ chối, hiển thị thông báo
                    Toast.makeText(getContext(), "Permission denied to read storage", Toast.LENGTH_SHORT).show();
                }
            });

    // MỚI: Trình khởi chạy để xử lý kết quả từ trình chọn file
    private final ActivityResultLauncher<String> filePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    // Người dùng đã chọn một file
                    this.selectedFileUri = uri;
                    String fileName = getFileName(uri); // Lấy tên file từ URI
                    tvSelectedFileName.setText(fileName);
                    cardSelectedFile.setVisibility(View.VISIBLE);
                    btnConvert.setEnabled(true);
                }
            });
    // ================== THÊM BROADCAST RECEIVER ĐỂ LẮNG NGHE SỰ KIỆN TẢI XONG ==================
    private BroadcastReceiver onDownloadComplete = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
            // Bạn có thể thêm logic để kiểm tra ID nếu cần
            Toast.makeText(getContext(), "Tải file MP3 thành công!", Toast.LENGTH_LONG).show();
            tvStatus.setText("Trạng thái: Đã tải file MP3 về thư mục Downloads.");
        }
    };
    @SuppressLint("UnspecifiedRegisterReceiverFlag") // Báo cho Lint bỏ qua cảnh báo
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Đăng ký receiver với việc kiểm tra phiên bản Android chính xác

        // Từ Android 8.0 (Oreo, API 26) trở lên, phương thức có 3 tham số mới tồn tại.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            requireActivity().registerReceiver(
                    onDownloadComplete,
                    new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                    Context.RECEIVER_NOT_EXPORTED
            );
        } else {
            // Trên các phiên bản cũ hơn (API 24, 25), sử dụng phương thức cũ không có flag.
            // Chú thích @SuppressLint ở trên sẽ ngăn Lint báo lỗi ở đây.
            requireActivity().registerReceiver(
                    onDownloadComplete,
                    new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            );
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Hủy đăng ký receiver để tránh rò rỉ bộ nhớ
        requireActivity().unregisterReceiver(onDownloadComplete);
    }
    // ====================================================================================

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_convert, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(ConvertViewModel.class);

        btnSelectFile = view.findViewById(R.id.btnSelectFile);
        btnConvert = view.findViewById(R.id.btnConvert);
        tvSelectedFileName = view.findViewById(R.id.tvSelectedFileName);
        tvStatus = view.findViewById(R.id.tvStatus);
        progressBar = view.findViewById(R.id.progressBar);
        cardSelectedFile = view.findViewById(R.id.cardSelectedFile);

        setupListeners();
        setupObservers();
    }

    // THAY ĐỔI: Cập nhật hoàn toàn logic của phương thức này
    private void setupListeners() {
        btnSelectFile.setOnClickListener(v -> {
            String permissionToRequest;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissionToRequest = Manifest.permission.READ_MEDIA_VIDEO;
            } else {
                permissionToRequest = Manifest.permission.READ_EXTERNAL_STORAGE;
            }

            // SỬA LỖI: Sử dụng đúng biến "permissionToRequest" ở đây
            if (ContextCompat.checkSelfPermission(requireContext(), permissionToRequest) ==
                    PackageManager.PERMISSION_GRANTED) {
                // Nếu đã có quyền, mở trình chọn file
                launchFilePicker();
            } else {
                // SỬA LỖI: Yêu cầu đúng biến "permissionToRequest" ở đây
                requestPermissionLauncher.launch(permissionToRequest);
            }
        });

        btnConvert.setOnClickListener(v -> {
            if (selectedFileUri == null) {
                Toast.makeText(getContext(), "Please select a file first", Toast.LENGTH_SHORT).show();
                return;
            }
            progressBar.setVisibility(View.VISIBLE);
            tvStatus.setText("Đang tạo job chuyển đổi...");
            btnConvert.setEnabled(false);
            btnSelectFile.setEnabled(false);
            viewModel.startConversion(requireContext(), selectedFileUri);
        });
    }

    // MỚI: Phương thức để khởi chạy trình chọn file hệ thống
    private void launchFilePicker() {
        // Chỉ cho phép chọn các file có định dạng video
        filePickerLauncher.launch("video/mp4");
    }

    // MỚI: Phương thức tiện ích để lấy tên file từ một content URI
    private String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (Cursor cursor = requireActivity().getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex != -1) {
                        result = cursor.getString(nameIndex);
                    }
                }
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }

    // Phương thức này giữ nguyên
    private void setupObservers() {
        viewModel.getConversionStatus().observe(getViewLifecycleOwner(), status -> {
            // Cập nhật ProgressBar dựa trên trạng thái
            if (status.contains("thất bại") || status.contains("Hoàn thành") || status.contains("Đã tải")) {
                progressBar.setVisibility(View.GONE);
                btnConvert.setEnabled(true);
                btnSelectFile.setEnabled(true);
            } else {
                progressBar.setVisibility(View.VISIBLE);
            }
            tvStatus.setText("Trạng thái: " + status);
        });
    }
}