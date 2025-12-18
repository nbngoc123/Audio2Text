package com.example.audio2text.ui.history;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import com.example.audio2text.R;
import com.example.audio2text.adapter.HistoryAdapter;
import com.example.audio2text.data.db.TranscriptionDatabaseHelper;
import com.example.audio2text.model.TranscriptionRecord;
import com.example.audio2text.ui.detail.DetailFragment;
import com.example.audio2text.ui.detail.VideoPlayerFragment;

import java.util.ArrayList;
import java.util.List;

public class HistoryFragment extends Fragment {

    private RecyclerView rvHistory;
    private EditText etSearch;
    private TextView tvEmptyHistory;

    private List<TranscriptionRecord> allRecords = new ArrayList<>();
    private List<TranscriptionRecord> filteredRecords = new ArrayList<>();
    private TranscriptionDatabaseHelper db;
    private HistoryAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_history, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Khởi tạo DB và các View
        db = new TranscriptionDatabaseHelper(requireContext());
        rvHistory = view.findViewById(R.id.rv_history);
        etSearch = view.findViewById(R.id.et_search);
        tvEmptyHistory = view.findViewById(R.id.tv_empty_history);

        setupRecyclerView();
        loadRecordsFromDb();
        setupSearch();
    }

    private void setupRecyclerView() {
        HistoryAdapter.OnHistoryItemListener listener = new HistoryAdapter.OnHistoryItemListener() {
            @Override
            public void onItemClick(TranscriptionRecord record) {
                // Kiểm tra loại file từ record
                if (record.getFileType() == 1) {
                    VideoPlayerFragment videoPlayerFragment = VideoPlayerFragment.newInstance(
                            record.getId(),
                            record.getAudioUri(),
                            record.getFilename()
                    );
                    requireActivity().getSupportFragmentManager()
                            .beginTransaction()
                            .replace(R.id.main_container, videoPlayerFragment)
                            .addToBackStack(null)
                            .commit();
                } else {
                    DetailFragment detailFragment = DetailFragment.newInstance(record.getId(), record.getTranscript());

                    requireActivity().getSupportFragmentManager()
                            .beginTransaction()
                            .replace(R.id.main_container, detailFragment)
                            .addToBackStack(null)
                            .commit();
                }
            }

            @Override
            public void onDeleteClick(TranscriptionRecord record) {
                db.deleteTranscript(record.getId());
                int position = allRecords.indexOf(record);
                if (position != -1) {
                    allRecords.remove(position);
                }
                filterRecords(etSearch.getText().toString());
            }
        };

        adapter = new HistoryAdapter(filteredRecords, listener);
        rvHistory.setAdapter(adapter);
    }

    private void loadRecordsFromDb() {
        // Tải toàn bộ bản ghi từ database
        allRecords.clear();
        allRecords.addAll(db.getAllTranscriptions());
        // Lọc và hiển thị dữ liệu
        filterRecords("");
    }

    private void setupSearch() {
        // Thêm listener để theo dõi sự thay đổi văn bản trong ô tìm kiếm
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Mỗi khi người dùng gõ, lọc lại danh sách
                filterRecords(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void filterRecords(String query) {
        // Xóa danh sách hiện tại
        filteredRecords.clear();

        if (query.isEmpty()) {
            // Nếu query rỗng, hiển thị tất cả bản ghi
            filteredRecords.addAll(allRecords);
        } else {
            // Nếu có query, lọc danh sách `allRecords`
            String lowerCaseQuery = query.toLowerCase();
            for (TranscriptionRecord record : allRecords) {
                if (record.getFilename().toLowerCase().contains(lowerCaseQuery)) {
                    filteredRecords.add(record);
                }
            }
        }

        // Cập nhật lại RecyclerView
        adapter.notifyDataSetChanged();
        // Kiểm tra và hiển thị/ẩn thông báo "danh sách rỗng"
        checkIfEmpty();
    }

    private void checkIfEmpty() {
        if (filteredRecords.isEmpty()) {
            rvHistory.setVisibility(View.GONE);
            tvEmptyHistory.setVisibility(View.VISIBLE);
        } else {
            rvHistory.setVisibility(View.VISIBLE);
            tvEmptyHistory.setVisibility(View.GONE);
        }
    }
}