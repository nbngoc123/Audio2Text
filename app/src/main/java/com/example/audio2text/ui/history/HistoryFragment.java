package com.example.audio2text.ui.history;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.audio2text.R;
import com.example.audio2text.adapter.HistoryAdapter;
import com.example.audio2text.data.db.TranscriptionDatabaseHelper;
import com.example.audio2text.model.TranscriptionRecord;
import com.example.audio2text.ui.main.MainFragment;

import java.util.ArrayList;
import java.util.List;

public class HistoryFragment extends Fragment {

    private ListView historyList;
    private List<TranscriptionRecord> records = new ArrayList<>();
    private TranscriptionDatabaseHelper db;
    private HistoryAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_history, container, false);
        historyList = v.findViewById(R.id.historyList);
        db = new TranscriptionDatabaseHelper(requireContext());

        loadRecords();

        adapter = new HistoryAdapter(requireContext(), records, record -> {
            // Xử lý xóa record khỏi database
            db.deleteTranscript(record.id);
            records.remove(record);
            adapter.notifyDataSetChanged();
        });

        historyList.setAdapter(adapter);

        historyList.setOnItemClickListener((parent, view, position, id) -> {
            TranscriptionRecord r = records.get(position);
            MainFragment f = MainFragment.newInstance(r.id, r.transcript);
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.main_container, f)
                    .addToBackStack(null)
                    .commit();
        });

        return v;
    }

    private void loadRecords() {
        records.clear();
        records.addAll(db.getAllTranscriptions());
    }
}
