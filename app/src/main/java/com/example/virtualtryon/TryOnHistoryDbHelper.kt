package com.example.virtualtryon

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class TryOnHistoryDbHelper(context: Context) :
    SQLiteOpenHelper(context, "try_on_history.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE try_on_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                garment_index INTEGER NOT NULL,
                camera TEXT NOT NULL,
                measurements TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS try_on_history")
        onCreate(db)
    }

    fun saveTryOn(garmentIndex: Int, camera: String, measurements: String) {
        writableDatabase.use { db ->
            db.insert(
                "try_on_history",
                null,
                ContentValues().apply {
                    put("garment_index", garmentIndex)
                    put("camera", camera)
                    put("measurements", measurements)
                    put("created_at", System.currentTimeMillis())
                }
            )
        }
    }
}
