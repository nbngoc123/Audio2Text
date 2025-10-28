package com.example.audio2text.ui.history;

import android.database.Cursor;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.audio2text.R;
import com.example.audio2text.data.db.TranscriptionDatabaseHelper;
import com.example.audio2text.ui.main.MainFragment;

import java.util.ArrayList;
import java.util.List;

public class HistoryFragment extends Fragment {

    private EditText searchBar;
    private ListView historyList;
    private ArrayAdapter<String> adapter;
    private List<String> filenames = new ArrayList<>();
    private List<String> filepaths = new ArrayList<>();
    private List<String> transcripts = new ArrayList<>();
    private TranscriptionDatabaseHelper dbHelper;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_history, container, false);

        searchBar = view.findViewById(R.id.searchBar);
        historyList = view.findViewById(R.id.historyList);

        dbHelper = new TranscriptionDatabaseHelper(requireContext());
        loadHistory();

        adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_list_item_1, filenames);
        historyList.setAdapter(adapter);

        // 🔍 Tìm kiếm theo tên file
        searchBar.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.getFilter().filter(s);
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        // 🎧 Khi click vào 1 item trong danh sách
        historyList.setOnItemClickListener((parent, v, position, id) -> {
            String filepath = filepaths.get(position);
            String transcript = transcripts.get(position);

            // Tạo fragment mới với dữ liệu từ SQLite
            MainFragment fragment = MainFragment.newInstance(filepath, transcript);

            // Chuyển sang màn hình nghe phát lại
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.main_container, fragment)
                    .addToBackStack(null)
                    .commit();
        });

        return view;
    }

    private void loadHistory() {
        Cursor cursor = dbHelper.getAllTranscriptions();
        filenames.clear();
        filepaths.clear();
        transcripts.clear();

        if (cursor != null && cursor.moveToFirst()) {
            int idxName = cursor.getColumnIndex(TranscriptionDatabaseHelper.COLUMN_FILENAME);
            int idxPath = cursor.getColumnIndex(TranscriptionDatabaseHelper.COLUMN_FILEPATH);
            int idxTranscript = cursor.getColumnIndex(TranscriptionDatabaseHelper.COLUMN_TRANSCRIPT);

            do {
                filenames.add(cursor.getString(idxName));
                filepaths.add(cursor.getString(idxPath));
                transcripts.add(cursor.getString(idxTranscript));
            } while (cursor.moveToNext());

            cursor.close();
        }
    }
}
