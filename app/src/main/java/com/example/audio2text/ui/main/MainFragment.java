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

import android.database.Cursor;
import java.util.ArrayList;
import java.util.List;

public class MainFragment extends Fragment {

    private static final String ARG_RECORD_ID = "record_id";
    private static final String ARG_TRANSCRIPT = "transcript";

    private RecyclerView rv;
    private TranscriptAdapter adapter;
    private MediaPlayer mediaPlayer;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SeekBar seekBar;
    private TextView tvCurrent, tvTotal;
    private ImageButton btnPlay;
    private final List<TranscriptItem> transcriptList = new ArrayList<>();
    private TranscriptionDatabaseHelper db;

    // Tạo instance mới với recordId
    public static MainFragment newInstance(int recordId, String transcript) {
        MainFragment f = new MainFragment();
        Bundle b = new Bundle();
        b.putInt(ARG_RECORD_ID, recordId);
        b.putString(ARG_TRANSCRIPT, transcript);
        f.setArguments(b);
        return f;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_main, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        initViews(view);
        setupRecyclerView();
        db = new TranscriptionDatabaseHelper(requireContext());

        int recordId = getArguments() != null ? getArguments().getInt(ARG_RECORD_ID, -1) : -1;

        if (recordId != -1) {
            loadTranscription(recordId);
        } else {
            loadLatestTranscription();
        }

        setupControls();
        startPlaybackSync();
    }

    private void initViews(View view) {
        rv = view.findViewById(R.id.rv_transcript);
        seekBar = view.findViewById(R.id.seekbar);
        tvCurrent = view.findViewById(R.id.tv_current_time);
        tvTotal = view.findViewById(R.id.tv_total_time);
        btnPlay = view.findViewById(R.id.btn_play);
    }

    private void setupRecyclerView() {
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new TranscriptAdapter(transcriptList);
        rv.setAdapter(adapter);

        adapter.setOnItemClickListener((position, seekToMs) -> {
            if (mediaPlayer != null) {
                mediaPlayer.seekTo((int) seekToMs);
            }
        });
    }

    private void loadTranscription(int recordId) {
        TranscriptionRecord record = findRecordById(recordId);
        if (record == null) {
            Toast.makeText(requireContext(), "Không tìm thấy bản ghi", Toast.LENGTH_SHORT).show();
            return;
        }

        loadSentences(recordId);
        setupMediaPlayer(record.audioUri);
    }

    private void loadLatestTranscription() {
        TranscriptionRecord latest = db.getLatestRecord();
        if (latest != null) {
            loadSentences(latest.id);
            setupMediaPlayer(latest.audioUri);
        } else {
            Toast.makeText(requireContext(), "Không có bản ghi nào", Toast.LENGTH_SHORT).show();
        }
    }

    private TranscriptionRecord findRecordById(int recordId) {
        for (TranscriptionRecord r : db.getAllTranscriptions()) {
            if (r.id == recordId) return r;
        }
        return null;
    }

    private void loadSentences(int recordId) {
        transcriptList.clear();
        Cursor c = db.getSentencesCursor(recordId);
        if (c != null && c.moveToFirst()) {
            do {
                String text = c.getString(c.getColumnIndexOrThrow(TranscriptionDatabaseHelper.S_COL_TEXT));
                String speaker = c.getString(c.getColumnIndexOrThrow(TranscriptionDatabaseHelper.S_COL_SPEAKER_LABEL));
                long start = c.getLong(c.getColumnIndexOrThrow(TranscriptionDatabaseHelper.S_COL_START));
                long end = c.getLong(c.getColumnIndexOrThrow(TranscriptionDatabaseHelper.S_COL_END));
                String label = formatTimeLabel((int) start);

                transcriptList.add(new TranscriptItem(label, text, start, end, speaker));
            } while (c.moveToNext());
            c.close();
        }
        adapter.notifyDataSetChanged();
    }

    private void setupMediaPlayer(String audioPath) {
        try {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(audioPath); // đường dẫn tuyệt đối trong files/audio
            mediaPlayer.prepareAsync();
            mediaPlayer.setOnPreparedListener(mp -> {
                seekBar.setMax(mediaPlayer.getDuration());
                tvTotal.setText(formatTime(mediaPlayer.getDuration()));
            });

            mediaPlayer.setOnCompletionListener(mp -> {
                btnPlay.setImageResource(R.drawable.ic_play_circle);
                adapter.setSelectedPosition(-1);
            });
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Không thể phát audio", Toast.LENGTH_SHORT).show();
        }
    }

    private void setupControls() {
        btnPlay.setOnClickListener(v -> togglePlayPause());

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && mediaPlayer != null) {
                    mediaPlayer.seekTo(progress);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void togglePlayPause() {
        if (mediaPlayer == null) return;
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            btnPlay.setImageResource(R.drawable.ic_play_circle);
        } else {
            mediaPlayer.start();
            btnPlay.setImageResource(R.drawable.ic_pause_circle);
        }
    }

    // GỘP 2 HANDLER THÀNH 1 → KHÔNG LAG
    private void startPlaybackSync() {
        final Runnable syncRunnable = new Runnable() {
            @Override
            public void run() {
                if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                    int current = mediaPlayer.getCurrentPosition();
                    updateSeekBar(current);
                    highlightCurrentSentence(current);
                }
                handler.postDelayed(this, 200);
            }
        };
        handler.post(syncRunnable);
    }

    private void updateSeekBar(int currentMs) {
        seekBar.setProgress(currentMs);
        tvCurrent.setText(formatTime(currentMs));
    }

    private void highlightCurrentSentence(int currentMs) {
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
            if (newPos != -1) {
                rv.smoothScrollToPosition(newPos);
            }
        }
    }

    private String formatTimeLabel(int ms) {
        int s = ms / 1000;
        int m = s / 60;
        s %= 60;
        return String.format("%d:%02d", m, s);
    }

    private String formatTime(int ms) {
        int s = ms / 1000;
        int m = s / 60;
        s %= 60;
        return String.format("%d:%02d", m, s);
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
}