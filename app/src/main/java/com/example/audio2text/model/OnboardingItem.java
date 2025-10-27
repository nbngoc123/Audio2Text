package com.example.audio2text.model;

public class OnboardingItem {
    private int imageRes;
    private String title;
    private String desc;

    public OnboardingItem(int imageRes, String title, String desc) {
        this.imageRes = imageRes;
        this.title = title;
        this.desc = desc;
    }

    public int getImageRes() {
        return imageRes;
    }

    public String getTitle() {
        return title;
    }

    public String getDesc() {
        return desc;
    }
}