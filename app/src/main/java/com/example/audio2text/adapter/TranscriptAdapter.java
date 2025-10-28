package com.example.audio2text.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.example.audio2text.R;
import com.example.audio2text.model.TranscriptItem;

import java.util.List;

public class TranscriptAdapter extends RecyclerView.Adapter<TranscriptAdapter.ViewHolder> {
    private List<TranscriptItem> items;
    private int selectedPosition = -1;
    private OnItemClickListener listener;

    public TranscriptAdapter(List<TranscriptItem> items) {
        this.items = items;
    }

    public int getSelectedPosition() {
        return selectedPosition;
    }
    public void setSelectedPosition(int position) {
        int prev = selectedPosition;
        selectedPosition = position;
        if (prev != -1) notifyItemChanged(prev);
        notifyItemChanged(selectedPosition);
    }

    public void setOnItemClickListener(OnItemClickListener l) {
        this.listener = l;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_transcript, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        TranscriptItem item = items.get(position);
        holder.tvTimestamp.setText(item.timestamp);
        holder.tvText.setText(item.text);
        holder.itemView.setSelected(position == selectedPosition);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onItemClick(position, item.startTimeMs);
        });
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTimestamp, tvText;
        public ViewHolder(View itemView) {
            super(itemView);
            tvTimestamp = itemView.findViewById(R.id.tv_timestamp);
            tvText = itemView.findViewById(R.id.tv_text);
        }
    }

    public interface OnItemClickListener {
        void onItemClick(int position, long seekToMs);
    }
}