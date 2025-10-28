package com.example.audio2text.ui.upload;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.audio2text.R;
import com.example.audio2text.data.repository.TranscriptionRepository;
import com.example.audio2text.network.TranscriptionService;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class UploadFragment extends Fragment {

    private static final int PICK_AUDIO_REQUEST = 1;

    private Button btnChoose, btnUpload;
    private TextView txtStatus;
    private ProgressBar progressBar;
    private Uri selectedAudioUri;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_upload, container, false);

        btnChoose = view.findViewById(R.id.btnChoose);
        btnUpload = view.findViewById(R.id.btnUpload);
        txtStatus = view.findViewById(R.id.txtStatus);
        progressBar = view.findViewById(R.id.progressBar);

        btnChoose.setOnClickListener(v -> openFileChooser());
        btnUpload.setOnClickListener(v -> {
            if (selectedAudioUri != null) {
                uploadAudio();
            } else {
                Toast.makeText(getContext(), "Hãy chọn file audio trước!", Toast.LENGTH_SHORT).show();
            }
        });

        return view;
    }

    private void openFileChooser() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("audio/*");
        startActivityForResult(Intent.createChooser(intent, "Chọn file audio"), PICK_AUDIO_REQUEST);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_AUDIO_REQUEST && resultCode == Activity.RESULT_OK && data != null) {
            selectedAudioUri = data.getData();
            txtStatus.setText("Đã chọn: " + selectedAudioUri.getLastPathSegment());
        }
    }

    private void uploadAudio() {
        progressBar.setVisibility(View.VISIBLE);
        txtStatus.setText("Đang tải và xử lý...");

        new Thread(() -> {
            try {
                // Chuyển Uri → File tạm trong cache
                File audioFile = copyUriToFile(selectedAudioUri);

                // Gọi service upload
                TranscriptionService service = new TranscriptionService(requireContext());
                String transcriptText = service.uploadAndTranscribe(Uri.fromFile(audioFile));

                requireActivity().runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    if (transcriptText != null) {
                        txtStatus.setText("Hoàn tất:\n" + transcriptText);

                        // Lưu vào SQLite
                        TranscriptionRepository repo = new TranscriptionRepository(requireContext());
                        repo.insertTranscription(audioFile.getName(), audioFile.getAbsolutePath(), transcriptText);
                        Toast.makeText(getContext(), "Đã lưu vào SQLite", Toast.LENGTH_SHORT).show();

                    } else {
                        txtStatus.setText("Lỗi khi xử lý audio!");
                    }
                });
            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    txtStatus.setText("Lỗi: " + e.getMessage());
                });
            }
        }).start();
    }

    private File copyUriToFile(Uri uri) throws Exception {
        InputStream inputStream = requireContext().getContentResolver().openInputStream(uri);
        File tempFile = new File(requireContext().getCacheDir(),
                System.currentTimeMillis() + "_audio.mp3");
        OutputStream outputStream = new FileOutputStream(tempFile);

        byte[] buffer = new byte[4096];
        int length;
        while ((length = inputStream.read(buffer)) > 0) {
            outputStream.write(buffer, 0, length);
        }

        inputStream.close();
        outputStream.close();
        return tempFile;
    }
}
