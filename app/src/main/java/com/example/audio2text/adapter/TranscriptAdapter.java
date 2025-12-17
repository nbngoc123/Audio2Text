package com.example.audio2text.adapter;

import android.graphics.Color;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.audio2text.R;
import com.example.audio2text.model.TranscriptItem;

import java.util.List;

public class TranscriptAdapter extends RecyclerView.Adapter<TranscriptAdapter.VH> {

    private final List<TranscriptItem> items;
    private int selected = -1;
    private OnTranscriptClickListener listener;

    // Interface mở rộng: Hỗ trợ cả Click (tua) và LongClick (sửa)
    public interface OnTranscriptClickListener {
        void onItemClick(int position, long seekToMs);
        void onItemLongClick(int position, TranscriptItem item);
    }

    public void setOnTranscriptClickListener(OnTranscriptClickListener l) {
        this.listener = l;
    }

    public TranscriptAdapter(List<TranscriptItem> items) {
        this.items = items;
    }

    public void setSelectedPosition(int pos) {
        if (pos == selected) return; // Nếu không đổi thì không cần vẽ lại

        int prev = selected;
        selected = pos;

        // Chỉ cập nhật 2 dòng bị thay đổi để tối ưu
        if (prev != -1) notifyItemChanged(prev);
        if (selected != -1) notifyItemChanged(selected);
    }

    public int getSelectedPosition() {
        return selected;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_transcript_line, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        TranscriptItem it = items.get(position);
        holder.tvTime.setText(it.label);
        holder.tvText.setText(it.text);

        // --- HIỆU ỨNG KARAOKE ---
        if (position == selected) {
            // Dòng đang đọc: Màu xanh dương, chữ đậm, nền xám nhẹ
            holder.tvText.setTextColor(Color.parseColor("#0A84FF"));
            holder.tvText.setTypeface(null, Typeface.BOLD);
            holder.itemView.setBackgroundColor(Color.parseColor("#1A1A1A"));
        } else {
            // Dòng thường: Màu trắng, chữ thường, nền trong suốt
            holder.tvText.setTextColor(Color.parseColor("#FFFFFF"));
            holder.tvText.setTypeface(null, Typeface.NORMAL);
            holder.itemView.setBackgroundColor(Color.TRANSPARENT);
        }

        // Sự kiện Click để tua
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onItemClick(position, it.startTimeMs);
        });

        // Sự kiện Nhấn giữ để sửa
        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) {
                listener.onItemLongClick(position, it);
                return true;
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvTime, tvText;
        VH(@NonNull View v) {
            super(v);
            tvTime = v.findViewById(R.id.tv_timestamp);
            tvText = v.findViewById(R.id.tv_text);
        }
    }
}