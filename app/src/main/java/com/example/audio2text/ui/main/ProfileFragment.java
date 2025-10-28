package com.example.audio2text.ui.main;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.example.audio2text.R;
import com.example.audio2text.util.ApiKey;

public class ProfileFragment extends Fragment {

    private TextView tv_API_key_info;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);

        EditText etApiKey = view.findViewById(R.id.et_api_key);
        tv_API_key_info = view.findViewById(R.id.tv_API_key_info);
        Button btnSave = view.findViewById(R.id.btn_save_api_key);
        handleApiKey();

        btnSave.setOnClickListener(v -> {
            ApiKey.apiKey = etApiKey.getText().toString();
            // aarn bớt ký tự
            String key = ApiKey.apiKey;
            String displayKey;
            if (key.length() <= 2) {
                displayKey = key;
            } else {
                int hiddenLength = key.length() - 2;
                String stars = "*".repeat(hiddenLength);
                displayKey = key.substring(0, 2) + stars;
            }
            tv_API_key_info.setText("API Key hiện tại: " + displayKey);
            Toast.makeText(getContext(), "API Key đã lưu", Toast.LENGTH_SHORT).show();
        });

        return view;
    }

    private void handleApiKey() {
        String key = ApiKey.apiKey;
        if (key == null || key.isEmpty()) {
            tv_API_key_info.setText("Chưa có API Key");
            return;
        }
        int hiddenLength = Math.max(0, key.length() - 2);
        String stars = "*".repeat(hiddenLength);
        tv_API_key_info.setText("API Key hiện tại: " + key.substring(0, 2) + stars);
    }
}

//cách dùng
//TextView tvShowKey = view.findViewById(R.id.tv_show_key);
//
//        if (TempApiKey.apiKey != null) {
//        tvShowKey.setText("API Key hiện tại: " + TempApiKey.apiKey);
//        } else {
//                tvShowKey.setText("chưa có API Key");
//        }
