package com.example.audio2text.ui.main;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.example.audio2text.R;
import com.example.audio2text.ui.history.HistoryFragment;
import com.example.audio2text.ui.upload.UploadFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity implements NavigationListener { // Triển khai Interface

    private BottomNavigationView bottomNavigationView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        addControls();

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.main_container, new HomeFragment())
                    .commit();
        }

        addEvents();
    }

    private void addEvents() {
        bottomNavigationView.setOnItemSelectedListener(item -> {
            Fragment selectedFragment = null;
            int itemId = item.getItemId();

            if (itemId == R.id.nav_home) {
                selectedFragment = new HomeFragment();
            } else if (itemId == R.id.nav_search) {
                selectedFragment = new HistoryFragment();
            } else if (itemId == R.id.nav_upload) {
                selectedFragment = new UploadFragment();
            } else if (itemId == R.id.nav_profile) {
                selectedFragment = new ProfileFragment();
            }

            if (selectedFragment != null) {
                getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.main_container, selectedFragment)
                        .commit();
            }
            return true;
        });
    }

    private void addControls() {
        bottomNavigationView = findViewById(R.id.bottom_nav);
    }

    /**
     * Phương thức mới được yêu cầu bởi Interface.
     * Dùng để chuyển sang tab Upload.
     */
    @Override
    public void navigateToUploadTab() {
        // Chọn item Upload trên BottomNavigationView
        bottomNavigationView.setSelectedItemId(R.id.nav_upload);
        // Đoạn code trong setOnItemSelectedListener sẽ tự động xử lý việc chuyển Fragment
    }
}