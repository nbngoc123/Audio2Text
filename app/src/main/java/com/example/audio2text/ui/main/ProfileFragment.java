package com.example.audio2text.ui.main;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.audio2text.R;

public class ProfileFragment extends Fragment {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        LinearLayout settingsLayout = view.findViewById(R.id.layout_settings);
        settingsLayout.setOnClickListener(v -> {
            // Mở màn hình cài đặt API Key
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.main_container, new ProfileSettingsFragment()) // main_container là ID của FrameLayout trong MainActivity
                    .addToBackStack(null) // Cho phép quay lại
                    .commit();
        });

        Button btnLogin = view.findViewById(R.id.btn_logout);
        btnLogin.setOnClickListener(v -> {
            Toast.makeText(getContext(), "Chức năng này sẽ sớm được phát triển!", Toast.LENGTH_SHORT).show();
        });
    }
}