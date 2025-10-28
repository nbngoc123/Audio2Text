package com.example.audio2text.ui.onboarding;


import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.example.audio2text.R;
import com.example.audio2text.adapter.OnboardingAdapter;
import com.example.audio2text.model.OnboardingItem;
import com.example.audio2text.ui.main.MainActivity;

import java.util.ArrayList;
import java.util.List;

import me.relex.circleindicator.CircleIndicator3;

public class OnboardingActivity extends AppCompatActivity {

    private ViewPager2 viewPager;
    private CircleIndicator3 indicator;
    private Button btnNext;
    private OnboardingAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);

        viewPager = findViewById(R.id.viewPager);
        indicator = findViewById(R.id.indicator);
        btnNext = findViewById(R.id.btnNext);

        setupOnboardingItems();
    }

    private void setupOnboardingItems() {
        List<OnboardingItem> items = new ArrayList<>();
        items.add(new OnboardingItem(
                R.drawable.ic_audio_logo,
                "Convert Audio Effortlessly",
                "Easily turn your voice recordings into readable, editable text."
        ));
        items.add(new OnboardingItem(
                R.drawable.ic_audio_logo,
                "Smart Transcription",
                "Automatically detect languages and optimize your text output."
        ));
        items.add(new OnboardingItem(
                R.drawable.ic_audio_logo,
                "Ready to Use",
                "Export and share your transcriptions instantly."
        ));

        adapter = new OnboardingAdapter(items);
        viewPager.setAdapter(adapter);
        indicator.setViewPager(viewPager);

        btnNext.setOnClickListener(v -> {
            if (viewPager.getCurrentItem() + 1 < adapter.getItemCount()) {
                viewPager.setCurrentItem(viewPager.getCurrentItem() + 1);
            } else {
//                finish(); // test
                // Lưu trạng thái đã xem Onboarding
                getSharedPreferences("prefs", MODE_PRIVATE)
                        .edit()
                        .putBoolean("onboarding_complete", true)
                        .apply();

                // Chuyển sang MainActivity
                startActivity(new Intent(OnboardingActivity.this, MainActivity.class));
                finish();
            }
        });
    }
}
