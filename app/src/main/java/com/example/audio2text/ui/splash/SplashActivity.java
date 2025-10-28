package com.example.audio2text.ui.splash;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import androidx.appcompat.app.AppCompatActivity;

import com.example.audio2text.ui.main.MainActivity;
import com.example.audio2text.ui.onboarding.OnboardingActivity;
import com.example.audio2text.R;

public class SplashActivity extends AppCompatActivity {

    private static final int SPLASH_DELAY = 2000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        new Handler().postDelayed(() -> {
            SharedPreferences prefs = getSharedPreferences("prefs", MODE_PRIVATE);
            boolean isOnboardingDone = prefs.getBoolean("onboarding_complete", false);

            Intent intent;
            if (isOnboardingDone) {
//                intent = new Intent(SplashActivity.this, MainActivity.class);
                intent = new Intent(SplashActivity.this, OnboardingActivity.class);

            } else {
                intent = new Intent(SplashActivity.this, OnboardingActivity.class);
            }

            startActivity(intent);
            finish();
        }, SPLASH_DELAY);
    }
}
