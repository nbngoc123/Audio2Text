package com.example.audio2text.model;

public class TranscriptItem {
    public String timestamp;
    public String text;
    public long startTimeMs;
    public long endTimeMs;

    public TranscriptItem(String timestamp, String text, long startTimeMs, long endTimeMs) {
        this.timestamp = timestamp;
        this.text = text;
        this.startTimeMs = startTimeMs;
        this.endTimeMs = endTimeMs;
    }
}