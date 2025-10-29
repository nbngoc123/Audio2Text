package com.example.audio2text.data.repository;

import android.content.Context;
import android.database.Cursor;

import com.example.audio2text.data.db.TranscriptionDatabaseHelper;
import com.example.audio2text.model.TranscriptionRecord;

import java.util.List;

public class TranscriptionRepository {
    private final TranscriptionDatabaseHelper db;

    public TranscriptionRepository(Context ctx) {
        db = new TranscriptionDatabaseHelper(ctx);
    }

    public long insertTranscript(String filename, String audioUri, String transcript) {
        return db.insertTranscript(filename, audioUri, transcript);
    }

    public void insertSentence(long recordId, String text, long startMs, long endMs, String speakerLabel) {
        db.insertSentence(recordId, text, startMs, endMs, speakerLabel);
    }

    public List<TranscriptionRecord> getAllTranscriptions() {
        return db.getAllTranscriptions();
    }

    public Cursor getSentencesCursor(long recordId) {
        return db.getSentencesCursor(recordId);
    }

    public TranscriptionRecord getLatestRecord() {
        return db.getLatestRecord();
    }
    public void deleteTranscript(int id) {
        db.deleteTranscript(id);
    }

}
