package com.example.audio2text.data.db;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.example.audio2text.model.TranscriptionRecord;

public class TranscriptionDatabaseHelper extends SQLiteOpenHelper {

    public static final String DB_NAME = "audio_transcripts.db";
    public static final int DB_VERSION = 1;

    public static final String TABLE_NAME = "transcripts";
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_FILENAME = "filename";
    public static final String COLUMN_FILEPATH = "filepath";
    public static final String COLUMN_TRANSCRIPT = "transcript";

    public TranscriptionDatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_NAME + " (" +
                COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COLUMN_FILENAME + " TEXT, " +
                COLUMN_FILEPATH + " TEXT, " +
                COLUMN_TRANSCRIPT + " TEXT" +
                ")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
        onCreate(db);
    }

    public Cursor getAllTranscriptions() {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.query(TABLE_NAME, null, null, null, null, null, COLUMN_ID + " DESC");
    }

    public Cursor getTranscriptionById(int id) {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.query(TABLE_NAME, null, COLUMN_ID + "=?",
                new String[]{String.valueOf(id)}, null, null, null);
    }
    public TranscriptionRecord getLatestRecord() {
        SQLiteDatabase db = this.getReadableDatabase();
        // ✅ SỬA: Bảng đúng + cột đúng + ORDER BY ID
        Cursor cursor = db.rawQuery(
                "SELECT * FROM " + TABLE_NAME + " ORDER BY " + COLUMN_ID + " DESC LIMIT 1",
                null
        );

        TranscriptionRecord record = null;
        if (cursor != null && cursor.moveToFirst()) {
            int idIdx = cursor.getColumnIndex(COLUMN_ID);
            int filepathIdx = cursor.getColumnIndex(COLUMN_FILEPATH);  // ✅ filepath, không phải audio_uri
            int transcriptIdx = cursor.getColumnIndex(COLUMN_TRANSCRIPT);

            record = new TranscriptionRecord(
                    cursor.getInt(idIdx),
                    cursor.getString(filepathIdx),     // ✅ filepath
                    cursor.getString(transcriptIdx),
                    ""  // created_at không có → để trống
            );
        }

        if (cursor != null) cursor.close();
        db.close();
        return record;
    }


}
