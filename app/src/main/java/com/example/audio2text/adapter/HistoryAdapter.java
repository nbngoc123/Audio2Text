package com.example.audio2text.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import com.example.audio2text.R;
import com.example.audio2text.model.TranscriptionRecord;

import java.util.List;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder> {

    private final List<TranscriptionRecord> records;
    private OnHistoryItemListener listener;

    public interface OnHistoryItemListener {
        void onItemClick(TranscriptionRecord record);
        void onDeleteClick(TranscriptionRecord record);
    }

    public HistoryAdapter(List<TranscriptionRecord> records, OnHistoryItemListener listener) {
        this.records = records;
        this.listener = listener;
    }

    @NonNull
    @Override
    public HistoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_history, parent, false);
        return new HistoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull HistoryViewHolder holder, int position) {
        TranscriptionRecord record = records.get(position);
        holder.bind(record, listener);
    }

    @Override
    public int getItemCount() {
        return records.size();
    }

    /**
     * Phương thức mới để cập nhật dữ liệu cho Adapter.
     * @param newRecords Danh sách các bản ghi mới.
     */
    public void updateData(List<TranscriptionRecord> newRecords) {
        records.clear();
        records.addAll(newRecords);
        notifyDataSetChanged(); // Thông báo cho RecyclerView rằng dữ liệu đã thay đổi
    }

    static class HistoryViewHolder extends RecyclerView.ViewHolder {
        TextView tvFileName;
        TextView tvFileDate;
        ImageButton btnMore;

        public HistoryViewHolder(@NonNull View itemView) {
            super(itemView);
            tvFileName = itemView.findViewById(R.id.tv_file_name);
            tvFileDate = itemView.findViewById(R.id.tv_file_date);
            btnMore = itemView.findViewById(R.id.btn_more);
        }

        public void bind(final TranscriptionRecord record, final OnHistoryItemListener listener) {
            tvFileName.setText(record.getFilename());
            tvFileDate.setText(record.getCreatedAt());

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onItemClick(record);
                }
            });

            btnMore.setOnClickListener(v -> {
                if (listener != null) {
                    showDeleteConfirmationDialog(itemView.getContext(), record, listener);
                }
            });
        }

        private void showDeleteConfirmationDialog(Context context, TranscriptionRecord record, OnHistoryItemListener listener) {
            new AlertDialog.Builder(context)
                    .setTitle("Xác nhận xóa")
                    .setMessage("Bạn có chắc muốn xóa bản ghi '" + record.getFilename() + "' không?")
                    .setPositiveButton("Xóa", (dialog, which) -> listener.onDeleteClick(record))
                    .setNegativeButton("Hủy", null)
                    .show();
        }
    }
}