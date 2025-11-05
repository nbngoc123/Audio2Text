package com.example.audio2text.ui.main;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.audio2text.R;
import com.example.audio2text.adapter.HistoryAdapter;
import com.example.audio2text.data.db.TranscriptionDatabaseHelper;
import com.example.audio2text.model.TranscriptionRecord;
import com.example.audio2text.ui.detail.DetailFragment;
import com.google.android.material.card.MaterialCardView;

import java.util.List;

public class HomeFragment extends Fragment {

    private RecyclerView rvRecentHistory;
    private HistoryAdapter historyAdapter;
    private TranscriptionDatabaseHelper dbHelper;
    private NavigationListener navigationListener;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof NavigationListener) {
            navigationListener = (NavigationListener) context;
        } else {
            throw new RuntimeException(context.toString() + " must implement NavigationListener");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        dbHelper = new TranscriptionDatabaseHelper(requireContext());
        rvRecentHistory = view.findViewById(R.id.rv_recent_history);
        MaterialCardView cardStartUpload = view.findViewById(R.id.card_start_upload);

        setupRecyclerView();
        loadRecentTranscripts();

        cardStartUpload.setOnClickListener(v -> {
            if (navigationListener != null) {
                navigationListener.navigateToUploadTab();
            }
        });
    }

    private void setupRecyclerView() {
        HistoryAdapter.OnHistoryItemListener listener = new HistoryAdapter.OnHistoryItemListener() {
            @Override
            public void onItemClick(TranscriptionRecord record) {
                DetailFragment detailFragment = DetailFragment.newInstance(record.getId(), record.getTranscript());

                requireActivity().getSupportFragmentManager().beginTransaction()
                        .replace(R.id.main_container, detailFragment)
                        .addToBackStack(null)
                        .commit();
            }

            @Override
            public void onDeleteClick(TranscriptionRecord record) {
                dbHelper.deleteTranscript(record.getId());
                loadRecentTranscripts();
            }
        };

        historyAdapter = new HistoryAdapter(new java.util.ArrayList<>(), listener);
        rvRecentHistory.setLayoutManager(new LinearLayoutManager(getContext()));
        rvRecentHistory.setAdapter(historyAdapter);
    }

    private void loadRecentTranscripts() {
        List<TranscriptionRecord> recentRecords = dbHelper.getRecentTranscriptions(5);
        historyAdapter.updateData(recentRecords);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        navigationListener = null;
    }
}