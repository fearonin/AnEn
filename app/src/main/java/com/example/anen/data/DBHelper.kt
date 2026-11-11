package com.example.anen.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Helper class for managing the SQLite database.
 * Provides methods to add, retrieve, and update words.
 */
class DBHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "anen_database.db"
        private const val DATABASE_VERSION = 1

        // Table name and columns
        const val TABLE_WORDS = "words"
        const val COLUMN_ID = "id"
        const val COLUMN_WORD = "word"
        const val COLUMN_CREATED_AT = "created_at"
        const val COLUMN_IS_EXPORTED = "is_exported"
    }

    override fun onCreate(db: SQLiteDatabase) {
        // SQL statement to create the words table
        val createTableQuery = """
            CREATE TABLE $TABLE_WORDS (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_WORD TEXT UNIQUE,
                $COLUMN_CREATED_AT INTEGER,
                $COLUMN_IS_EXPORTED INTEGER
            )
        """.trimIndent()
        db.execSQL(createTableQuery)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Drop older table if existed and create fresh table
        db.execSQL("DROP TABLE IF EXISTS $TABLE_WORDS")
        onCreate(db)
    }

    /**
     * Adds a new word to the database.
     * Returns true if the word was added successfully, false if it already exists.
     */
    fun addWord(wordText: String): Boolean {
        // Check for duplicates first
        if (isWordExists(wordText)) {
            return false
        }

        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_WORD, wordText)
            put(COLUMN_CREATED_AT, System.currentTimeMillis())
            put(COLUMN_IS_EXPORTED, 0) // 0 means false
        }

        val id = db.insert(TABLE_WORDS, null, values)
        db.close()
        return id != -1L
    }

    /**
     * Checks if a word already exists in the database.
     */
    private fun isWordExists(wordText: String): Boolean {
        val db = this.readableDatabase
        val cursor = db.query(
            TABLE_WORDS,
            arrayOf(COLUMN_ID),
            "$COLUMN_WORD = ?",
            arrayOf(wordText),
            null, null, null
        )
        val exists = cursor.count > 0
        cursor.close()
        db.close()
        return exists
    }

    /**
     * Retrieves all words from the database.
     */
    fun getAllWords(): List<Word> {
        val wordList = mutableListOf<Word>()
        val selectQuery = "SELECT * FROM $TABLE_WORDS ORDER BY $COLUMN_CREATED_AT DESC"
        val db = this.readableDatabase
        val cursor: Cursor = db.rawQuery(selectQuery, null)

        if (cursor.moveToFirst()) {
            do {
                val word = Word(
                    id = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ID)),
                    word = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_WORD)),
                    createdAt = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_CREATED_AT)),
                    isExported = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_IS_EXPORTED)) == 1
                )
                wordList.add(word)
            } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()
        return wordList
    }

    /**
     * Retrieves only words that have not been exported yet (is_exported == 0).
     */
    fun getUnexportedWords(): List<Word> {
        val wordList = mutableListOf<Word>()
        val db = this.readableDatabase
        val cursor = db.query(
            TABLE_WORDS,
            null,
            "$COLUMN_IS_EXPORTED = ?",
            arrayOf("0"),
            null, null,
            "$COLUMN_CREATED_AT ASC"
        )

        if (cursor.moveToFirst()) {
            do {
                val word = Word(
                    id = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ID)),
                    word = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_WORD)),
                    createdAt = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_CREATED_AT)),
                    isExported = false
                )
                wordList.add(word)
            } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()
        return wordList
    }

    /**
     * Marks a list of words as exported (is_exported = 1).
     */
    fun markAsExported(words: List<String>) {
        if (words.isEmpty()) return

        val db = this.writableDatabase
        // Create a string with placeholders for the IN clause: ?, ?, ?
        val placeholders = words.joinToString(",") { "?" }
        val values = ContentValues().apply {
            put(COLUMN_IS_EXPORTED, 1)
        }

        // Update all matching words
        db.update(
            TABLE_WORDS,
            values,
            "$COLUMN_WORD IN ($placeholders)",
            words.toTypedArray()
        )
        db.close()
    }

    /**
     * Deletes all words from the database.
     */
    fun clearAllWords() {
        val db = this.writableDatabase
        db.delete(TABLE_WORDS, null, null)
        db.close()
    }
}