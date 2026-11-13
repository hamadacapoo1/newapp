package com.mohamed.voicenotes;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class NotesDb extends SQLiteOpenHelper {

    public NotesDb(Context c) {
        super(c, "voicenotes.db", null, 1);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE notes (id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "title TEXT, body TEXT, created INTEGER)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
    }

    public long insert(String title, String body) {
        ContentValues v = new ContentValues();
        v.put("title", title);
        v.put("body", body);
        v.put("created", System.currentTimeMillis());
        return getWritableDatabase().insert("notes", null, v);
    }

    public void update(long id, String title, String body) {
        ContentValues v = new ContentValues();
        v.put("title", title);
        v.put("body", body);
        getWritableDatabase().update("notes", v, "id=?", new String[]{String.valueOf(id)});
    }

    public void delete(long id) {
        getWritableDatabase().delete("notes", "id=?", new String[]{String.valueOf(id)});
    }

    public Note get(long id) {
        Cursor c = getReadableDatabase().query("notes", null, "id=?",
                new String[]{String.valueOf(id)}, null, null, null);
        try {
            if (c.moveToFirst()) return from(c);
        } finally {
            c.close();
        }
        return null;
    }

    public List<Note> search(String q) {
        List<Note> out = new ArrayList<Note>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c;
        if (q == null || q.trim().length() == 0) {
            c = db.query("notes", null, null, null, null, null, "created DESC");
        } else {
            String like = "%" + q.trim() + "%";
            c = db.query("notes", null, "title LIKE ? OR body LIKE ?",
                    new String[]{like, like}, null, null, "created DESC");
        }
        try {
            while (c.moveToNext()) out.add(from(c));
        } finally {
            c.close();
        }
        return out;
    }

    private Note from(Cursor c) {
        Note n = new Note();
        n.id = c.getLong(c.getColumnIndexOrThrow("id"));
        n.title = c.getString(c.getColumnIndexOrThrow("title"));
        n.body = c.getString(c.getColumnIndexOrThrow("body"));
        n.created = c.getLong(c.getColumnIndexOrThrow("created"));
        return n;
    }
}
