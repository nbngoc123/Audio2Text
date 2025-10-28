package com.example.audio2text.data.repository;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import com.example.audio2text.data.db.TranscriptionDatabaseHelper;

public class TranscriptionRepository {
    private final TranscriptionDatabaseHelper dbHelper;

    public TranscriptionRepository(Context context) {
        dbHelper = new TranscriptionDatabaseHelper(context);
    }

    public void insertTranscription(String filename, String filepath, String transcript) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(TranscriptionDatabaseHelper.COLUMN_FILENAME, filename);
        values.put(TranscriptionDatabaseHelper.COLUMN_FILEPATH, filepath);
        values.put(TranscriptionDatabaseHelper.COLUMN_TRANSCRIPT, transcript);
        db.insert(TranscriptionDatabaseHelper.TABLE_NAME, null, values);
        db.close();
    }
}
