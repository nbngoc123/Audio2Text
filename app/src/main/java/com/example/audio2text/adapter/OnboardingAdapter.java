package com.example.audio2text.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.audio2text.R;
import com.example.audio2text.model.OnboardingItem;

import java.util.List;

public class OnboardingAdapter extends RecyclerView.Adapter<OnboardingAdapter.OnboardingViewHolder> {

    private List<OnboardingItem> onboardingItems;

    public OnboardingAdapter(List<OnboardingItem> onboardingItems) {
        this.onboardingItems = onboardingItems;
    }

    @NonNull
    @Override
    public OnboardingViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_onboarding, parent, false);
        return new OnboardingViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull OnboardingViewHolder holder, int position) {
        OnboardingItem item = onboardingItems.get(position);
        holder.imageOnboarding.setImageResource(item.getImageRes());
        holder.titleOnboarding.setText(item.getTitle());
        holder.descOnboarding.setText(item.getDesc());
    }

    @Override
    public int getItemCount() {
        return onboardingItems.size();
    }

    static class OnboardingViewHolder extends RecyclerView.ViewHolder {
        ImageView imageOnboarding;
        TextView titleOnboarding, descOnboarding;

        OnboardingViewHolder(@NonNull View itemView) {
            super(itemView);
            imageOnboarding = itemView.findViewById(R.id.imageOnboarding);
            titleOnboarding = itemView.findViewById(R.id.titleOnboarding);
            descOnboarding = itemView.findViewById(R.id.descOnboarding);
        }
    }
}