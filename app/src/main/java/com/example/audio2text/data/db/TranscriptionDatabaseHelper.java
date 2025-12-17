package com.example.audio2text.data.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.example.audio2text.model.TranscriptionRecord;

import java.util.ArrayList;
import java.util.List;

public class TranscriptionDatabaseHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "audio_transcripts.db";
    private static final int DB_VERSION = 2;

    public static final String TABLE_TRANSCRIPTS = "transcripts";
    public static final String T_COL_ID = "id";
    public static final String T_COL_FILENAME = "filename";
    public static final String T_COL_AUDIO_URI = "audio_uri";
    public static final String T_COL_TRANSCRIPT = "transcript";
    public static final String T_COL_CREATED_AT = "created_at";

    public static final String TABLE_SENTENCES = "sentences";
    public static final String S_COL_ID = "id";
    public static final String S_COL_RECORD_ID = "record_id";
    public static final String S_COL_TEXT = "text";
    public static final String S_COL_START = "start_ms";
    public static final String S_COL_END = "end_ms";
    public static final String S_COL_SPEAKER_LABEL = "speaker_label";

    public TranscriptionDatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTranscripts = "CREATE TABLE " + TABLE_TRANSCRIPTS + " (" +
                T_COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                T_COL_FILENAME + " TEXT, " +
                T_COL_AUDIO_URI + " TEXT, " +
                T_COL_TRANSCRIPT + " TEXT, " +
                T_COL_CREATED_AT + " DATETIME DEFAULT CURRENT_TIMESTAMP" +
                ")";

        String createSentences = "CREATE TABLE " + TABLE_SENTENCES + " (" +
                S_COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                S_COL_RECORD_ID + " INTEGER, " +
                S_COL_TEXT + " TEXT, " +
                S_COL_START + " INTEGER, " +
                S_COL_END + " INTEGER, " +
                S_COL_SPEAKER_LABEL + " TEXT, " +
                "FOREIGN KEY(" + S_COL_RECORD_ID + ") REFERENCES " + TABLE_TRANSCRIPTS + "(" + T_COL_ID + ")" +
                ")";

        db.execSQL(createTranscripts);
        db.execSQL(createSentences);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_SENTENCES);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_TRANSCRIPTS);
        onCreate(db);
    }

    public long insertTranscript(String filename, String audioUri, String transcript) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues v = new ContentValues();
        v.put(T_COL_FILENAME, filename);
        v.put(T_COL_AUDIO_URI, audioUri);
        v.put(T_COL_TRANSCRIPT, transcript);
        return db.insert(TABLE_TRANSCRIPTS, null, v);
    }

    public void insertSentence(long recordId, String text, long startMs, long endMs, String speakerLabel) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues v = new ContentValues();
        v.put(S_COL_RECORD_ID, recordId);
        v.put(S_COL_TEXT, text);
        v.put(S_COL_START, startMs);
        v.put(S_COL_END, endMs);
        v.put(S_COL_SPEAKER_LABEL, speakerLabel);
        db.insert(TABLE_SENTENCES, null, v);
    }

    // --- HÀM MỚI: CẬP NHẬT NỘI DUNG CÂU THOẠI ---
    public void updateSentenceText(int recordId, long startTime, String newText) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(S_COL_TEXT, newText);

        // Điều kiện: Cập nhật đúng bản ghi và đúng thời gian bắt đầu
        String whereClause = S_COL_RECORD_ID + " = ? AND " + S_COL_START + " = ?";
        String[] whereArgs = { String.valueOf(recordId), String.valueOf(startTime) };

        db.update(TABLE_SENTENCES, values, whereClause, whereArgs);
        db.close();
    }

    public List<TranscriptionRecord> getAllTranscriptions() {
        List<TranscriptionRecord> out = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE_TRANSCRIPTS, null, null, null, null, null, T_COL_ID + " DESC");

        if (c != null && c.moveToFirst()) {
            do {
                TranscriptionRecord r = new TranscriptionRecord(
                        c.getInt(c.getColumnIndexOrThrow(T_COL_ID)),
                        c.getString(c.getColumnIndexOrThrow(T_COL_FILENAME)),
                        c.getString(c.getColumnIndexOrThrow(T_COL_AUDIO_URI)),
                        c.getString(c.getColumnIndexOrThrow(T_COL_TRANSCRIPT)),
                        c.getString(c.getColumnIndexOrThrow(T_COL_CREATED_AT))
                );
                out.add(r);
            } while (c.moveToNext());
            c.close();
        }
        return out;
    }

    public Cursor getSentencesCursor(long recordId) {
        SQLiteDatabase db = getReadableDatabase();
        return db.query(TABLE_SENTENCES, null,
                S_COL_RECORD_ID + "=?", new String[]{String.valueOf(recordId)},
                null, null, S_COL_START + " ASC");
    }

    public TranscriptionRecord getLatestRecord() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE_TRANSCRIPTS, null, null, null, null, null, T_COL_ID + " DESC", "1");

        if (c != null && c.moveToFirst()) {
            TranscriptionRecord r = new TranscriptionRecord(
                    c.getInt(c.getColumnIndexOrThrow(T_COL_ID)),
                    c.getString(c.getColumnIndexOrThrow(T_COL_FILENAME)),
                    c.getString(c.getColumnIndexOrThrow(T_COL_AUDIO_URI)),
                    c.getString(c.getColumnIndexOrThrow(T_COL_TRANSCRIPT)),
                    c.getString(c.getColumnIndexOrThrow(T_COL_CREATED_AT))
            );
            c.close();
            return r;
        }
        if (c != null) c.close();
        return null;
    }

    public TranscriptionRecord getRecordById(int recordId) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE_TRANSCRIPTS, null,
                T_COL_ID + "=?", new String[]{String.valueOf(recordId)},
                null, null, null);

        if (c != null && c.moveToFirst()) {
            TranscriptionRecord r = new TranscriptionRecord(
                    c.getInt(c.getColumnIndexOrThrow(T_COL_ID)),
                    c.getString(c.getColumnIndexOrThrow(T_COL_FILENAME)),
                    c.getString(c.getColumnIndexOrThrow(T_COL_AUDIO_URI)),
                    c.getString(c.getColumnIndexOrThrow(T_COL_TRANSCRIPT)),
                    c.getString(c.getColumnIndexOrThrow(T_COL_CREATED_AT))
            );
            c.close();
            return r;
        }
        if (c != null) c.close();
        return null;
    }

    public void deleteTranscript(int id) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_SENTENCES, S_COL_RECORD_ID + "=?", new String[]{String.valueOf(id)});
        db.delete(TABLE_TRANSCRIPTS, T_COL_ID + "=?", new String[]{String.valueOf(id)});
    }

    public List<TranscriptionRecord> getRecentTranscriptions(int limit) {
        List<TranscriptionRecord> records = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_TRANSCRIPTS, null, null, null, null, null, T_COL_ID + " DESC", String.valueOf(limit));

        if (cursor != null && cursor.moveToFirst()) {
            do {
                int id = cursor.getInt(cursor.getColumnIndexOrThrow(T_COL_ID));
                String filename = cursor.getString(cursor.getColumnIndexOrThrow(T_COL_FILENAME));
                String audioUri = cursor.getString(cursor.getColumnIndexOrThrow(T_COL_AUDIO_URI));
                String transcript = cursor.getString(cursor.getColumnIndexOrThrow(T_COL_TRANSCRIPT));
                String createdAt = cursor.getString(cursor.getColumnIndexOrThrow(T_COL_CREATED_AT));
                records.add(new TranscriptionRecord(id, filename, audioUri, transcript, createdAt));
            } while (cursor.moveToNext());
            cursor.close();
        }
        return records;
    }
}