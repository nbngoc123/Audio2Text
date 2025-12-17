package com.example.audio2text.ui.detail;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.textfield.TextInputEditText;
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
import com.example.audio2text.network.GeminiHelper;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;

import io.noties.markwon.Markwon;

public class DetailFragment extends Fragment {

    private static final String ARG_RECORD_ID = "record_id";
    private static final String ARG_TRANSCRIPT = "transcript";

    private RecyclerView rv;
    private TranscriptAdapter adapter;
    private MediaPlayer mediaPlayer;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SeekBar seekBar;
    private TextView tvCurrent, tvTotal;
    private ImageButton btnPlay;
    private ImageButton btnRewind, btnFastForward;
    private TextView tvTitle;

    // --- KHAI BÁO BIẾN CHO AI ---
    private FloatingActionButton fabAi;
    private GeminiHelper geminiHelper;

    private final List<TranscriptItem> transcriptList = new ArrayList<>();
    private TranscriptionDatabaseHelper db;

    public static DetailFragment newInstance(int recordId, String transcript) {
        DetailFragment f = new DetailFragment();
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

        // Khởi tạo AI Helper
        geminiHelper = new GeminiHelper();

        setupRecyclerView();
        db = new TranscriptionDatabaseHelper(requireContext());

        int recordId = getArguments() != null ? getArguments().getInt(ARG_RECORD_ID, -1) : -1;

        if (recordId != -1) {
            loadTranscription(recordId);
        } else {
            loadLatestTranscription();
        }

        setupControls();
        setupAiButton();
        startPlaybackSync();

        ImageView ivCopy = view.findViewById(R.id.iv_copy_transcript);
        ivCopy.setOnClickListener(v -> copyTranscriptToClipboard());
    }

    private void initViews(View view) {
        rv = view.findViewById(R.id.rv_transcript);
        seekBar = view.findViewById(R.id.seekbar);
        tvCurrent = view.findViewById(R.id.tv_current_time);
        tvTotal = view.findViewById(R.id.tv_total_time);
        btnPlay = view.findViewById(R.id.btn_play);
        tvTitle = view.findViewById(R.id.tv_title);
        btnRewind = view.findViewById(R.id.btn_rewind);
        btnFastForward = view.findViewById(R.id.btn_fast_forward);
        fabAi = view.findViewById(R.id.fab_ai_magic);
    }

    // --- HÀM XỬ LÝ NÚT AI MAGIC ---
    private void setupAiButton() {
        fabAi.setOnClickListener(v -> {
            String cleanText = getCleanTranscriptText();

            if (cleanText.isEmpty()) {
                Toast.makeText(getContext(), "Chưa có nội dung để phân tích!", Toast.LENGTH_SHORT).show();
                return;
            }

            fabAi.setEnabled(false);
            Toast.makeText(getContext(), "AI đang đọc và tóm tắt...", Toast.LENGTH_SHORT).show();

            geminiHelper.summarizeText(cleanText, new GeminiHelper.AiResponseCallback() {
                @Override
                public void onSuccess(String result) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            fabAi.setEnabled(true);
                            showResultDialog(result);
                        });
                    }
                }

                @Override
                public void onError(Throwable t) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            fabAi.setEnabled(true);
                            Toast.makeText(getContext(), "Lỗi AI: " + t.getMessage(), Toast.LENGTH_LONG).show();
                            Log.e("GeminiError", t.getMessage());
                        });
                    }
                }
            });
        });
    }

    // --- HIỂN THỊ KẾT QUẢ AI (Dùng Markdown) ---
    private void showResultDialog(String aiResult) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());

        // Sử dụng layout custom dialog_ai_result.xml
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_ai_result, null);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.bg_dialog_rounded);
        }

        TextView tvContent = dialogView.findViewById(R.id.tv_markdown_content);
        Button btnCopy = dialogView.findViewById(R.id.btn_dialog_copy);
        Button btnClose = dialogView.findViewById(R.id.btn_dialog_close);

        // Render Markdown đẹp
        final Markwon markwon = Markwon.create(requireContext());
        markwon.setMarkdown(tvContent, aiResult);

        btnCopy.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("AI Summary", aiResult);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(getContext(), "Đã sao chép!", Toast.LENGTH_SHORT).show();
        });

        btnClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    // --- HIỂN THỊ DIALOG CHỈNH SỬA VĂN BẢN (Khi nhấn giữ) ---
    private void showEditDialog(TranscriptItem item, int position) {
        // Tạm dừng player
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            btnPlay.setImageResource(R.drawable.ic_play_circle);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());

        // Inflate layout custom
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_edit_sentence, null);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();

        // Làm nền dialog trong suốt để thấy được cái bo tròn của layout con
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        // Ánh xạ View
        EditText input = dialogView.findViewById(R.id.et_input);
        Button btnSave = dialogView.findViewById(R.id.btn_save);
        Button btnCancel = dialogView.findViewById(R.id.btn_cancel);

        // Gán dữ liệu cũ
        input.setText(item.text);
        input.setSelection(item.text.length()); // Đưa con trỏ về cuối
        input.requestFocus(); // Tự động focus để hiện bàn phím

        // Xử lý nút Lưu
        btnSave.setOnClickListener(v -> {
            String newText = input.getText().toString().trim();
            if (!newText.isEmpty()) {
                int recordId = getArguments() != null ? getArguments().getInt(ARG_RECORD_ID, -1) : -1;
                if (recordId != -1) {
                    // Update Database
                    db.updateSentenceText(recordId, item.startTimeMs, newText);
                }
                // Update UI
                item.text = newText;
                adapter.notifyItemChanged(position);
                Toast.makeText(getContext(), "Đã cập nhật!", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            } else {
                input.setError("Không được để trống");
            }
        });

        // Xử lý nút Hủy
        btnCancel.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private String getCleanTranscriptText() {
        if (transcriptList.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (TranscriptItem item : transcriptList) {
            sb.append(item.text).append(" ");
        }
        return sb.toString().trim();
    }

    private void setupRecyclerView() {
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new TranscriptAdapter(transcriptList);
        rv.setAdapter(adapter);

        // Cài đặt sự kiện Click (Tua) và LongClick (Sửa)
        adapter.setOnTranscriptClickListener(new TranscriptAdapter.OnTranscriptClickListener() {
            @Override
            public void onItemClick(int position, long seekToMs) {
                if (mediaPlayer != null) {
                    mediaPlayer.seekTo((int) seekToMs);
                    if (!mediaPlayer.isPlaying()) {
                        mediaPlayer.start();
                        btnPlay.setImageResource(R.drawable.ic_pause_circle);
                    }
                }
            }

            @Override
            public void onItemLongClick(int position, TranscriptItem item) {
                showEditDialog(item, position);
            }
        });
    }

    private void loadTranscription(int recordId) {
        TranscriptionRecord record = findRecordById(recordId);
        if (record == null) {
            Toast.makeText(requireContext(), "Không tìm thấy bản ghi", Toast.LENGTH_SHORT).show();
            return;
        }
        if (record.filename != null && !record.filename.isEmpty()) {
            tvTitle.setText(record.filename);
        } else {
            tvTitle.setText("Chi tiết bản ghi");
        }
        loadSentences(recordId);
        setupMediaPlayer(record.audioUri);
    }

    private void loadLatestTranscription() {
        TranscriptionRecord latest = db.getLatestRecord();
        if (latest != null) {
            if (latest.filename != null && !latest.filename.isEmpty()) {
                tvTitle.setText(latest.filename);
            } else {
                tvTitle.setText("Bản ghi mới nhất");
            }
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
            mediaPlayer.setDataSource(audioPath);
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
        btnRewind.setOnClickListener(v -> {
            if (mediaPlayer != null) {
                int current = mediaPlayer.getCurrentPosition();
                mediaPlayer.seekTo(Math.max(current - 5000, 0));
            }
        });
        btnFastForward.setOnClickListener(v -> {
            if (mediaPlayer != null) {
                int current = mediaPlayer.getCurrentPosition();
                mediaPlayer.seekTo(Math.min(current + 5000, mediaPlayer.getDuration()));
            }
        });
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && mediaPlayer != null) mediaPlayer.seekTo(progress);
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
        // Logic tìm vị trí chính xác hơn
        for (int i = 0; i < transcriptList.size(); i++) {
            TranscriptItem item = transcriptList.get(i);
            if (currentMs >= item.startTimeMs && currentMs < item.endTimeMs) {
                newPos = i;
                break;
            }
            // Fallback nếu không tìm thấy khoảng chính xác
            if (currentMs >= item.startTimeMs) newPos = i;
        }

        if (newPos != adapter.getSelectedPosition()) {
            adapter.setSelectedPosition(newPos);
            if (newPos != -1) {
                // Tự động cuộn mượt mà
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

    private void copyTranscriptToClipboard() {
        String fullText = getFullTranscriptText();
        if (fullText == null || fullText.isEmpty()) {
            Toast.makeText(getContext(), "Không có nội dung để sao chép", Toast.LENGTH_SHORT).show();
            return;
        }
        ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("transcript_text", fullText);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(getContext(), "Đã sao chép nội dung", Toast.LENGTH_SHORT).show();
    }

    private String getFullTranscriptText() {
        if (transcriptList.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (TranscriptItem item : transcriptList) {
            sb.append(item.label).append(" ").append(item.text).append("\n");
        }
        return sb.toString();
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