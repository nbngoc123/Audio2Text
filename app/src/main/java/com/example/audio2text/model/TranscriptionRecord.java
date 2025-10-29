package com.example.audio2text.model;

public class TranscriptionRecord {
    public int id;
    public String filename;
    public String audioUri;
    public String transcript; // full text (optional)
    public String createdAt;

    public TranscriptionRecord(int id, String filename, String audioUri, String transcript, String createdAt) {
        this.id = id;
        this.filename = filename;
        this.audioUri = audioUri;
        this.transcript = transcript;
        this.createdAt = createdAt;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getAudioUri() {
        return audioUri;
    }

    public void setAudioUri(String audioUri) {
        this.audioUri = audioUri;
    }

    public String getTranscript() {
        return transcript;
    }

    public void setTranscript(String transcript) {
        this.transcript = transcript;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}
