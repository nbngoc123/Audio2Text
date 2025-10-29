package com.example.audio2text.ui.history;

import android.database.Cursor;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.audio2text.R;
import com.example.audio2text.data.db.TranscriptionDatabaseHelper;
import com.example.audio2text.model.TranscriptionRecord;
import com.example.audio2text.ui.main.MainFragment;

import java.util.ArrayList;
import java.util.List;

public class HistoryFragment extends Fragment {

    private EditText searchBar;
    private ListView historyList;
    private ArrayAdapter<String> adapter;
    private List<TranscriptionRecord> records = new ArrayList<>();
    private List<String> filenames = new ArrayList<>();
    private TranscriptionDatabaseHelper db;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_history, container, false);
        searchBar = v.findViewById(R.id.searchBar);
        historyList = v.findViewById(R.id.historyList);
        db = new TranscriptionDatabaseHelper(requireContext());
        load();
        adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, filenames);
        historyList.setAdapter(adapter);

        historyList.setOnItemClickListener((parent, view, position, id) -> {
            TranscriptionRecord r = records.get(position);
            // open MainFragment with filepath and transcript
            MainFragment f = MainFragment.newInstance(r.id, r.transcript);
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.main_container, f)
                    .addToBackStack(null)
                    .commit();
        });

        return v;
    }

    private void load() {
        records.clear();
        filenames.clear();
        for (TranscriptionRecord r : db.getAllTranscriptions()) {
            records.add(r);
            filenames.add(r.filename + " — " + r.createdAt);
        }
    }
}
