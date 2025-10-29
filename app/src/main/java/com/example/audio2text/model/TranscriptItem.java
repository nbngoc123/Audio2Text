package com.example.audio2text.model;

public class TranscriptItem {
    public final String label; // e.g. "0:12"
    public final String text;
    public final long startTimeMs;
    public final long endTimeMs;
    public final String speaker;

    public TranscriptItem(String label, String text, long startTimeMs, long endTimeMs, String speaker) {
        this.label = label;
        this.text = text;
        this.startTimeMs = startTimeMs;
        this.endTimeMs = endTimeMs;
        this.speaker = speaker;
    }
}
