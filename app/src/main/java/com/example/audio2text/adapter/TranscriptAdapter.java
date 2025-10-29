package com.example.audio2text.adapter;

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
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(int position, long seekToMs);
    }

    public void setOnItemClickListener(OnItemClickListener l) { this.listener = l; }

    public TranscriptAdapter(List<TranscriptItem> items) { this.items = items; }

    public void setSelectedPosition(int pos) {
        int prev = selected;
        selected = pos;
        if (prev != -1) notifyItemChanged(prev);
        if (selected != -1) notifyItemChanged(selected);
    }

    public int getSelectedPosition() { return selected; }

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
        holder.itemView.setSelected(position == selected);
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onItemClick(position, it.startTimeMs);
        });
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvTime, tvText;
        VH(@NonNull View v) {
            super(v);
            tvTime = v.findViewById(R.id.tv_timestamp);
            tvText = v.findViewById(R.id.tv_text);
        }
    }
}
