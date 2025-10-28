package com.example.audio2text.model;

public class TranscriptionRecord {
    private int id;
    private String audioUri;
    private String transcript;
    private String createdAt;

    public TranscriptionRecord(int id, String audioUri, String transcript, String createdAt) {
        this.id = id;
        this.audioUri = audioUri;
        this.transcript = transcript;
        this.createdAt = createdAt;
    }

    // Constructor không có id (dùng khi insert mới)
    public TranscriptionRecord(String audioUri, String transcript, String createdAt) {
        this.audioUri = audioUri;
        this.transcript = transcript;
        this.createdAt = createdAt;
    }

    public int getId() {
        return id;
    }

    public String getAudioUri() {
        return audioUri;
    }

    public String getTranscript() {
        return transcript;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setId(int id) {
        this.id = id;
    }

    public void setAudioUri(String audioUri) {
        this.audioUri = audioUri;
    }

    public void setTranscript(String transcript) {
        this.transcript = transcript;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}
