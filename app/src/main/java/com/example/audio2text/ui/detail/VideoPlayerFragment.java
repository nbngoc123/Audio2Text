package com.example.audio2text.ui.detail;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.VideoView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.audio2text.R;

import java.util.Locale;

public class VideoPlayerFragment extends Fragment {
    private static final String ARG_PATH = "video_path";
    private static final String ARG_NAME = "file_name";

    private VideoView videoView;
    private ProgressBar loadingProgress;
    private SeekBar seekBar;
    private TextView tvCurrentTime, tvTotalTime, tvTitle;
    private ImageButton btnPlay, btnRewind, btnForward;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isUserSeeking = false;

    // Chỉ cần Path và Name, không cần ID để truy vấn transcript nữa
    public static VideoPlayerFragment newInstance(int id, String path, String name) {
        VideoPlayerFragment fragment = new VideoPlayerFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PATH, path);
        args.putString(ARG_NAME, name);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_video_player, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initViews(view);
        setupVideoPlayer();
    }

    private void initViews(View v) {
        videoView = v.findViewById(R.id.videoView);
        loadingProgress = v.findViewById(R.id.videoLoadingProgress);
        seekBar = v.findViewById(R.id.seekbar);
        tvCurrentTime = v.findViewById(R.id.tv_current_time);
        tvTotalTime = v.findViewById(R.id.tv_total_time);
        tvTitle = v.findViewById(R.id.tv_title);
        btnPlay = v.findViewById(R.id.btn_play);
        btnRewind = v.findViewById(R.id.btn_rewind);
        btnForward = v.findViewById(R.id.btn_fast_forward);

        if (getArguments() != null) {
            tvTitle.setText(getArguments().getString(ARG_NAME));
        }
    }

    private void setupVideoPlayer() {
        String path = getArguments().getString(ARG_PATH);
        if (path == null) return;

        loadingProgress.setVisibility(View.VISIBLE);
        videoView.setVideoPath(path);

        videoView.setOnPreparedListener(mp -> {
            loadingProgress.setVisibility(View.GONE);
            int duration = videoView.getDuration();
            seekBar.setMax(duration);
            tvTotalTime.setText("/ " + formatTime(duration));

            videoView.start();
            btnPlay.setImageResource(R.drawable.ic_pause_circle);
            startProgressUpdate();
        });

        btnPlay.setOnClickListener(v -> {
            if (videoView.isPlaying()) {
                videoView.pause();
                btnPlay.setImageResource(R.drawable.ic_play_circle);
            } else {
                videoView.start();
                btnPlay.setImageResource(R.drawable.ic_pause_circle);
            }
        });

        btnForward.setOnClickListener(v -> videoView.seekTo(videoView.getCurrentPosition() + 10000));
        btnRewind.setOnClickListener(v -> videoView.seekTo(videoView.getCurrentPosition() - 10000));

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) tvCurrentTime.setText(formatTime(progress));
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) { isUserSeeking = true; }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                isUserSeeking = false;
                videoView.seekTo(seekBar.getProgress());
            }
        });

        videoView.setOnCompletionListener(mp -> btnPlay.setImageResource(R.drawable.ic_play_circle));
    }

    private void startProgressUpdate() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (videoView != null && videoView.isPlaying() && !isUserSeeking) {
                    int currentPos = videoView.getCurrentPosition();
                    seekBar.setProgress(currentPos);
                    tvCurrentTime.setText(formatTime(currentPos));
                }
                handler.postDelayed(this, 1000);
            }
        });
    }

    private String formatTime(int millis) {
        int seconds = (millis / 1000) % 60;
        int minutes = (millis / (1000 * 60)) % 60;
        int hours = (millis / (1000 * 60 * 60));
        if (hours > 0) {
            return String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}