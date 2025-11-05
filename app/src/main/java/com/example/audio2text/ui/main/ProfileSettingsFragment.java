package com.example.audio2text.ui.main;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.audio2text.R;
import com.example.audio2text.util.ApiKey;
import com.google.android.material.textfield.TextInputEditText;

public class ProfileSettingsFragment extends Fragment {

    private TextInputEditText etApiKey;
    private TextView tvApiKeyInfo;
    private Button btnSaveApiKey;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        etApiKey = view.findViewById(R.id.et_api_key);
        tvApiKeyInfo = view.findViewById(R.id.tv_api_key_info);
        btnSaveApiKey = view.findViewById(R.id.btn_save_api_key);

        displayCurrentApiKey();

        btnSaveApiKey.setOnClickListener(v -> {
            String apiKey = etApiKey.getText().toString().trim();
            if (apiKey.isEmpty()) {
                Toast.makeText(getContext(), "Vui lòng không để trống API Key", Toast.LENGTH_SHORT).show();
                return;
            }

            ApiKey.saveApiKey(requireContext(), apiKey);

            etApiKey.setText("");
            displayCurrentApiKey();
            Toast.makeText(getContext(), "Đã lưu API Key thành công!", Toast.LENGTH_SHORT).show();
        });
    }

    private void displayCurrentApiKey() {
        String maskedKey = ApiKey.getMaskedApiKey(requireContext());
        tvApiKeyInfo.setText(maskedKey);
    }
}