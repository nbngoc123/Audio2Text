package com.example.audio2text.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.example.audio2text.R;
import com.example.audio2text.model.TranscriptionRecord;

import java.util.List;

public class HistoryAdapter extends ArrayAdapter<TranscriptionRecord> {
    private Context context;
    private List<TranscriptionRecord> records;
    private OnDeleteClickListener listener;

    public interface OnDeleteClickListener {
        void onDelete(TranscriptionRecord record);
    }

    public HistoryAdapter(Context context, List<TranscriptionRecord> records, OnDeleteClickListener listener) {
        super(context, 0, records);
        this.context = context;
        this.records = records;
        this.listener = listener;
    }

    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_history, parent, false);
        }

        TranscriptionRecord record = records.get(position);

        TextView fileName = convertView.findViewById(R.id.fileName);
        ImageButton btnDelete = convertView.findViewById(R.id.btnDelete);

        fileName.setText(record.getFilename());

        btnDelete.setOnClickListener(v -> {
            if (listener != null) listener.onDelete(record);
        });

        return convertView;
    }
}
