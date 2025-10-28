package com.example.audio2text.ui.main;

import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.audio2text.R;
import com.example.audio2text.adapter.TranscriptAdapter;
import com.example.audio2text.data.db.TranscriptionDatabaseHelper;
import com.example.audio2text.model.TranscriptItem;
import com.example.audio2text.model.TranscriptionRecord;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MainFragment extends Fragment {

    private RecyclerView rvTranscript;
    private TranscriptAdapter adapter;
    private MediaPlayer mediaPlayer;
    private Handler handler = new Handler(Looper.getMainLooper());
    private SeekBar seekBar;
    private TextView tvCurrentTime, tvTotalTime;
    private ImageButton btnPlay, btnRewind, btnFastForward;
    private List<TranscriptItem> transcriptList = new ArrayList<>();
    private TranscriptionDatabaseHelper dbHelper;
    private TranscriptionRecord latestRecord;

    public static MainFragment newInstance(String filepath, String transcript) {
        MainFragment fragment = new MainFragment();
        Bundle args = new Bundle();
        args.putString("filepath", filepath);
        args.putString("transcript", transcript);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_main, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 1. GÁN CÁC VIEW TRƯỚC
        rvTranscript = view.findViewById(R.id.rv_transcript);
        seekBar = view.findViewById(R.id.seekbar);
        tvCurrentTime = view.findViewById(R.id.tv_current_time);
        tvTotalTime = view.findViewById(R.id.tv_total_time);
        btnPlay = view.findViewById(R.id.btn_play);
        btnRewind = view.findViewById(R.id.btn_rewind);
        btnFastForward = view.findViewById(R.id.btn_fast_forward);

        // 2. KHỞI TẠO DB TRƯỚC
        dbHelper = new TranscriptionDatabaseHelper(requireContext());

        // 3. BÂY GIỜ MỚI DÙNG rvTranscript VÀ dbHelper
        rvTranscript.setLayoutManager(new LinearLayoutManager(requireContext()));

        // 4. XỬ LÝ DỮ LIỆU
        String filepath = null;
        String transcript = null;

        if (getArguments() != null) {
            filepath = getArguments().getString("filepath");
            transcript = getArguments().getString("transcript");
        }

        if (filepath != null && transcript != null) {
            // Từ History
            setupMediaPlayer(filepath); // → onPrepared sẽ gọi setupTranscriptData
        } else {
            // Từ Home
            latestRecord = dbHelper.getLatestRecord();
            if (latestRecord == null) {
                Toast.makeText(requireContext(), "Chưa có bản ghi nào. Hãy upload audio trước.", Toast.LENGTH_LONG).show();
                return;
            }
            setupMediaPlayer(latestRecord.getAudioUri()); // → onPrepared sẽ gọi
        }

        // 5. CHỈ GẮN ADAPTER KHI CÓ DỮ LIỆU
        adapter = new TranscriptAdapter(transcriptList);
        rvTranscript.setAdapter(adapter);

        // 6. CÁC SỰ KIỆN KHÁC
        adapter.setOnItemClickListener((position, seekToMs) -> {
            if (mediaPlayer != null) {
                mediaPlayer.seekTo((int) seekToMs);
                updateSeekBar();
            }
        });

        startTranscriptSync();

        btnPlay.setOnClickListener(v -> togglePlayPause());
        btnRewind.setOnClickListener(v -> rewind10s());
        btnFastForward.setOnClickListener(v -> fastForward10s());

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && mediaPlayer != null) mediaPlayer.seekTo(progress);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }


    /** Chia transcript thành các đoạn có timestamp ảo (5s mỗi câu) */
    /**
     * Chia transcript thành các câu, mỗi câu 5 giây, gán timestamp đều theo độ dài audio.
     * GỌI TRONG onPreparedListener CỦA MediaPlayer (đảm bảo getDuration() có giá trị).
     */
    private void setupTranscriptData(String transcriptText) {
        transcriptList.clear();

        if (transcriptText == null || transcriptText.trim().isEmpty() || mediaPlayer == null) {
            return;
        }

        // Tách thành các từ (loại bỏ dấu câu thừa)
        String[] words = transcriptText.trim()
                .replaceAll("[^\\p{L}\\p{N}']+", " ")
                .split("\\s+");

        if (words.length == 0) return;

        int totalDurationMs = mediaPlayer.getDuration();
        if (totalDurationMs <= 0) totalDurationMs = 60000; // fallback 60s

        int segmentDurationMs = 5000; // Mỗi 5 giây 1 câu
        int totalSegments = (totalDurationMs + segmentDurationMs - 1) / segmentDurationMs; // ceil
        int wordsPerSegment = Math.max(1, words.length / totalSegments);

        int wordIndex = 0;
        long currentTimeMs = 0;

        while (wordIndex < words.length && currentTimeMs < totalDurationMs) {
            int endIndex = Math.min(wordIndex + wordsPerSegment, words.length);
            StringBuilder sentence = new StringBuilder();

            for (int i = wordIndex; i < endIndex; i++) {
                if (i > wordIndex) sentence.append(" ");
                sentence.append(words[i]);
            }

            String text = sentence.toString().trim();
            if (endIndex == words.length && !text.matches(".*[.!?]$")) {
                text += ".";
            }

            long endTimeMs = Math.min(currentTimeMs + segmentDurationMs, totalDurationMs);

            transcriptList.add(new TranscriptItem(
                    formatTime((int) currentTimeMs),
                    text,
                    currentTimeMs,
                    endTimeMs
            ));

            currentTimeMs = endTimeMs;
            wordIndex = endIndex;
        }

        // Cập nhật adapter
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    /** Khởi tạo MediaPlayer */
    private void setupMediaPlayer(String audioUriString) {
        try {
            Uri uri = Uri.parse(audioUriString);
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(requireContext(), uri);

            mediaPlayer.setOnPreparedListener(mp -> {
                seekBar.setMax(mp.getDuration());
                tvTotalTime.setText(formatTime(mp.getDuration()));

                // GỌI HÀM CHIA 5S SAU KHI AUDIO SẴN SÀNG
                String transcript = latestRecord != null
                        ? latestRecord.getTranscript()
                        : getArguments().getString("transcript");

                setupTranscriptData(transcript);

                // Gắn adapter nếu chưa
                if (adapter == null) {
                    adapter = new TranscriptAdapter(transcriptList);
                    rvTranscript.setAdapter(adapter);
                    adapter.setOnItemClickListener((pos, seekToMs) -> {
                        if (mediaPlayer != null) mediaPlayer.seekTo((int) seekToMs);
                    });
                } else {
                    adapter.notifyDataSetChanged();
                }
            });

            mediaPlayer.prepareAsync(); // Non-blocking

            mediaPlayer.setOnCompletionListener(mp -> {
                btnPlay.setImageResource(R.drawable.ic_play_circle);
                seekBar.setProgress(0);
                tvCurrentTime.setText("0:00");
            });

        } catch (Exception e) {
            Toast.makeText(getContext(), "Không thể phát audio!", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }
    private void togglePlayPause() {
        if (mediaPlayer == null) return;
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            btnPlay.setImageResource(R.drawable.ic_play_circle);
        } else {
            mediaPlayer.start();
            btnPlay.setImageResource(R.drawable.ic_pause_circle);
            updateSeekBar();
        }
    }

    private void rewind10s() {
        if (mediaPlayer != null) {
            int newPos = mediaPlayer.getCurrentPosition() - 10000;
            mediaPlayer.seekTo(Math.max(0, newPos));
        }
    }

    private void fastForward10s() {
        if (mediaPlayer != null) {
            int newPos = mediaPlayer.getCurrentPosition() + 10000;
            mediaPlayer.seekTo(Math.min(mediaPlayer.getDuration(), newPos));
        }
    }

    private void updateSeekBar() {
        if (mediaPlayer != null) {
            int current = mediaPlayer.getCurrentPosition();
            seekBar.setProgress(current);
            tvCurrentTime.setText(formatTime(current));
        }
        handler.postDelayed(this::updateSeekBar, 100);
    }

    private void startTranscriptSync() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                    int currentMs = mediaPlayer.getCurrentPosition();
                    int newPos = -1;
                    for (int i = 0; i < transcriptList.size(); i++) {
                        TranscriptItem item = transcriptList.get(i);
                        if (currentMs >= item.startTimeMs && currentMs <= item.endTimeMs) {
                            newPos = i;
                            break;
                        }
                    }
                    if (newPos != adapter.getSelectedPosition()) {
                        adapter.setSelectedPosition(newPos);
                        if (newPos != -1) rvTranscript.smoothScrollToPosition(newPos);
                    }
                }
                handler.postDelayed(this, 300);
            }
        });
    }

    private String formatTime(int ms) {
        int seconds = ms / 1000;
        int minutes = seconds / 60;
        seconds %= 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        handler.removeCallbacksAndMessages(null);
    }



    /**
     * Chia transcript thành các câu (6-10 từ/câu), gán timestamp đều theo độ dài audio.
     * Gọi SAU KHI mediaPlayer đã prepare xong (trong onPreparedListener).
     */
}
