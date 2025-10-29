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
    public static final String COL_ID = "id";
    public static final String COL_FILENAME = "filename";
    public static final String COL_AUDIOURI = "audio_uri";
    public static final String COL_TRANSCRIPT = "transcript";
    public static final String COL_CREATED = "created_at";

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
                COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_FILENAME + " TEXT, " +
                COL_AUDIOURI + " TEXT, " +
                COL_TRANSCRIPT + " TEXT, " +
                COL_CREATED + " DATETIME DEFAULT CURRENT_TIMESTAMP" +
                ")";

        String createSentences = "CREATE TABLE " + TABLE_SENTENCES + " (" +
                S_COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                S_COL_RECORD_ID + " INTEGER, " +
                S_COL_TEXT + " TEXT, " +
                S_COL_START + " INTEGER, " +
                S_COL_END + " INTEGER, " +
                S_COL_SPEAKER_LABEL + " TEXT, " +
                "FOREIGN KEY(" + S_COL_RECORD_ID + ") REFERENCES " + TABLE_TRANSCRIPTS + "(" + COL_ID + ")" +
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

    // Insert transcript -> return inserted rowId
    public long insertTranscript(String filename, String audioUri, String transcript) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues v = new ContentValues();
        v.put(COL_FILENAME, filename);
        v.put(COL_AUDIOURI, audioUri);
        v.put(COL_TRANSCRIPT, transcript);
        long id = db.insert(TABLE_TRANSCRIPTS, null, v);
        return id;
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

    // Get all transcripts
    public List<TranscriptionRecord> getAllTranscriptions() {
        List<TranscriptionRecord> out = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE_TRANSCRIPTS, null, null, null, null, null, COL_ID + " DESC");

        if (c != null && c.moveToFirst()) {
            do {
                TranscriptionRecord r = new TranscriptionRecord(
                        c.getInt(c.getColumnIndexOrThrow(COL_ID)),
                        c.getString(c.getColumnIndexOrThrow(COL_FILENAME)),
                        c.getString(c.getColumnIndexOrThrow(COL_AUDIOURI)),
                        c.getString(c.getColumnIndexOrThrow(COL_TRANSCRIPT)),
                        c.getString(c.getColumnIndexOrThrow(COL_CREATED))
                );
                out.add(r);
            } while (c.moveToNext());
            c.close(); // CHỈ ĐÓNG CURSOR
        }
        // KHÔNG db.close()
        return out;
    }

    // Get sentences for a record
    public Cursor getSentencesCursor(long recordId) {
        SQLiteDatabase db = getReadableDatabase();
        return db.query(TABLE_SENTENCES, null,
                S_COL_RECORD_ID + "=?", new String[]{String.valueOf(recordId)},
                null, null, S_COL_START + " ASC");
    }

    // Get latest record
    public TranscriptionRecord getLatestRecord() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE_TRANSCRIPTS, null, null, null, null, null, COL_ID + " DESC", "1");

        if (c != null && c.moveToFirst()) {
            TranscriptionRecord r = new TranscriptionRecord(
                    c.getInt(c.getColumnIndexOrThrow(COL_ID)),
                    c.getString(c.getColumnIndexOrThrow(COL_FILENAME)),
                    c.getString(c.getColumnIndexOrThrow(COL_AUDIOURI)),
                    c.getString(c.getColumnIndexOrThrow(COL_TRANSCRIPT)),
                    c.getString(c.getColumnIndexOrThrow(COL_CREATED))
            );
            c.close();
            // KHÔNG db.close()
            return r;
        }
        if (c != null) c.close();
        // KHÔNG db.close()
        return null;
    }

    // MỚI: Lấy record theo ID (dùng trong MainFragment)
    public TranscriptionRecord getRecordById(int recordId) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE_TRANSCRIPTS, null,
                COL_ID + "=?", new String[]{String.valueOf(recordId)},
                null, null, null);

        if (c != null && c.moveToFirst()) {
            TranscriptionRecord r = new TranscriptionRecord(
                    c.getInt(c.getColumnIndexOrThrow(COL_ID)),
                    c.getString(c.getColumnIndexOrThrow(COL_FILENAME)),
                    c.getString(c.getColumnIndexOrThrow(COL_AUDIOURI)),
                    c.getString(c.getColumnIndexOrThrow(COL_TRANSCRIPT)),
                    c.getString(c.getColumnIndexOrThrow(COL_CREATED))
            );
            c.close();
            return r;
        }
        if (c != null) c.close();
        return null;
    }

    // Xóa một transcript theo ID
    public void deleteTranscript(int id) {
        SQLiteDatabase db = getWritableDatabase();
        // Xóa luôn các câu liên quan trong bảng sentences
        db.delete(TABLE_SENTENCES, S_COL_RECORD_ID + "=?", new String[]{String.valueOf(id)});
        // Xóa record chính
        db.delete(TABLE_TRANSCRIPTS, COL_ID + "=?", new String[]{String.valueOf(id)});
    }

}