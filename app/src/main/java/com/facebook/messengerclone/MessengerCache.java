package com.facebook.messengerclone;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

final class MessengerCache extends SQLiteOpenHelper {
    MessengerCache(Context context) { super(context, "messenger_native.db", null, 1); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE snapshots(cache_key TEXT PRIMARY KEY, json TEXT NOT NULL, updated_at INTEGER NOT NULL)");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {}

    synchronized void put(String key, String json) {
        ContentValues v = new ContentValues();
        v.put("cache_key", key); v.put("json", json); v.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict("snapshots", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    synchronized String get(String key) {
        try (Cursor c = getReadableDatabase().query("snapshots", new String[]{"json"}, "cache_key=?", new String[]{key}, null, null, null)) {
            return c.moveToFirst() ? c.getString(0) : null;
        }
    }

    synchronized void clear() { getWritableDatabase().delete("snapshots", null, null); }
}
