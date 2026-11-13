package com.example.anen.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DBHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "anen_database.db"
        private const val DATABASE_VERSION = 2 // UPGRADED FROM 1 TO 2

        // Table Names
        const val TABLE_LANGUAGES = "languages"
        const val TABLE_SOURCES = "sources"
        const val TABLE_WORDS = "words"

        // Common Columns
        const val COLUMN_ID = "id"
        const val COLUMN_NAME = "name"
        const val COLUMN_ABBREVIATION = "abbreviation"

        // Sources Columns
        const val COLUMN_LANGUAGE_ID = "language_id"

        // Words Columns
        const val COLUMN_WORD = "word"
        const val COLUMN_SOURCE_ID = "source_id"
        const val COLUMN_CREATED_AT = "created_at"
        const val COLUMN_IS_EXPORTED = "is_exported"
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        // Create Languages Table
        db.execSQL("""
            CREATE TABLE $TABLE_LANGUAGES (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_NAME TEXT UNIQUE,
                $COLUMN_ABBREVIATION TEXT
            )
        """.trimIndent())

        // Create Sources Table
        db.execSQL("""
            CREATE TABLE $TABLE_SOURCES (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_LANGUAGE_ID INTEGER,
                $COLUMN_NAME TEXT,
                $COLUMN_ABBREVIATION TEXT,
                FOREIGN KEY($COLUMN_LANGUAGE_ID) REFERENCES $TABLE_LANGUAGES($COLUMN_ID) ON DELETE CASCADE
            )
        """.trimIndent())

        // Create Words Table
        db.execSQL("""
            CREATE TABLE $TABLE_WORDS (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_WORD TEXT UNIQUE,
                $COLUMN_LANGUAGE_ID INTEGER,
                $COLUMN_SOURCE_ID INTEGER,
                $COLUMN_CREATED_AT INTEGER,
                $COLUMN_IS_EXPORTED INTEGER,
                FOREIGN KEY($COLUMN_LANGUAGE_ID) REFERENCES $TABLE_LANGUAGES($COLUMN_ID) ON DELETE CASCADE,
                FOREIGN KEY($COLUMN_SOURCE_ID) REFERENCES $TABLE_SOURCES($COLUMN_ID) ON DELETE CASCADE
            )
        """.trimIndent())
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            // Migration from V1 to V2
            db.beginTransaction()
            try {
                // 1. Create new tables
                db.execSQL("""
                    CREATE TABLE $TABLE_LANGUAGES (
                        $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                        $COLUMN_NAME TEXT UNIQUE,
                        $COLUMN_ABBREVIATION TEXT
                    )
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE $TABLE_SOURCES (
                        $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                        $COLUMN_LANGUAGE_ID INTEGER,
                        $COLUMN_NAME TEXT,
                        $COLUMN_ABBREVIATION TEXT,
                        FOREIGN KEY($COLUMN_LANGUAGE_ID) REFERENCES $TABLE_LANGUAGES($COLUMN_ID) ON DELETE CASCADE
                    )
                """.trimIndent())

                // 2. Insert default English Language and Default Source
                db.execSQL("INSERT INTO $TABLE_LANGUAGES ($COLUMN_ID, $COLUMN_NAME, $COLUMN_ABBREVIATION) VALUES (1, 'English', 'Eng')")
                db.execSQL("INSERT INTO $TABLE_SOURCES ($COLUMN_ID, $COLUMN_LANGUAGE_ID, $COLUMN_NAME, $COLUMN_ABBREVIATION) VALUES (1, 1, 'Default', 'Def')")

                // 3. Rename old words table
                db.execSQL("ALTER TABLE $TABLE_WORDS RENAME TO words_old")

                // 4. Create new words table
                db.execSQL("""
                    CREATE TABLE $TABLE_WORDS (
                        $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                        $COLUMN_WORD TEXT UNIQUE,
                        $COLUMN_LANGUAGE_ID INTEGER,
                        $COLUMN_SOURCE_ID INTEGER,
                        $COLUMN_CREATED_AT INTEGER,
                        $COLUMN_IS_EXPORTED INTEGER,
                        FOREIGN KEY($COLUMN_LANGUAGE_ID) REFERENCES $TABLE_LANGUAGES($COLUMN_ID) ON DELETE CASCADE,
                        FOREIGN KEY($COLUMN_SOURCE_ID) REFERENCES $TABLE_SOURCES($COLUMN_ID) ON DELETE CASCADE
                    )
                """.trimIndent())

                // 5. Copy data from old table to new table with default lang=1 and source=1
                db.execSQL("""
                    INSERT INTO $TABLE_WORDS ($COLUMN_ID, $COLUMN_WORD, $COLUMN_LANGUAGE_ID, $COLUMN_SOURCE_ID, $COLUMN_CREATED_AT, $COLUMN_IS_EXPORTED) 
                    SELECT $COLUMN_ID, $COLUMN_WORD, 1, 1, $COLUMN_CREATED_AT, $COLUMN_IS_EXPORTED FROM words_old
                """.trimIndent())

                // 6. Drop old table
                db.execSQL("DROP TABLE words_old")

                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    // ==========================================
    // --- LEGACY METHODS (For Stage 1 compilation) ---
    // ==========================================

    fun addWord(wordText: String): Boolean {
        // Temporarily default to language 1 (English) and source 1 (Default)
        return addWord(wordText, 1, 1)
    }

    fun getAllWords(): List<Word> {
        // Temporarily fetch all, regardless of language/source
        return getWordsFiltered(null, null)
    }

    fun getUnexportedWords(): List<Word> {
        return getWordsFiltered(null, null).filter { !it.isExported }
    }

    // ==========================================
    // --- NEW METHODS: LANGUAGES & SOURCES ---
    // ==========================================

    fun addLanguage(name: String, abbreviation: String): Long {
        val db = this.writableDatabase

        // Check for case-insensitive duplicates
        val cursor = db.rawQuery("SELECT $COLUMN_ID FROM $TABLE_LANGUAGES WHERE LOWER($COLUMN_NAME) = LOWER(?)", arrayOf(name))
        if (cursor.count > 0) {
            cursor.close()
            db.close()
            return -1 // Indicates a duplicate was found
        }
        cursor.close()

        val values = ContentValues().apply {
            put(COLUMN_NAME, name)
            put(COLUMN_ABBREVIATION, abbreviation)
        }
        val id = db.insert(TABLE_LANGUAGES, null, values)
        db.close()
        return id
    }

    fun getAllLanguages(): List<Language> {
        val list = mutableListOf<Language>()
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_LANGUAGES ORDER BY $COLUMN_NAME ASC", null)
        if (cursor.moveToFirst()) {
            do {
                list.add(Language(
                    cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ID)),
                    cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_NAME)),
                    cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ABBREVIATION))
                ))
            } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()
        return list
    }

    fun addSource(languageId: Int, name: String, abbreviation: String): Long {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_LANGUAGE_ID, languageId)
            put(COLUMN_NAME, name)
            put(COLUMN_ABBREVIATION, abbreviation)
        }
        val id = db.insert(TABLE_SOURCES, null, values)
        db.close()
        return id
    }

    fun getSourcesForLanguage(languageId: Int): List<Source> {
        val list = mutableListOf<Source>()
        val db = this.readableDatabase
        val cursor = db.query(TABLE_SOURCES, null, "$COLUMN_LANGUAGE_ID = ?", arrayOf(languageId.toString()), null, null, "$COLUMN_NAME ASC")
        if (cursor.moveToFirst()) {
            do {
                list.add(Source(
                    cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ID)),
                    cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_LANGUAGE_ID)),
                    cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_NAME)),
                    cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ABBREVIATION))
                ))
            } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()
        return list
    }

    // ==========================================
    // --- NEW METHODS: WORDS ---
    // ==========================================

    fun addWord(wordText: String, languageId: Int, sourceId: Int): Boolean {
        // Check if word exists for this specific language
        if (isWordExists(wordText, languageId)) {
            return false
        }

        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_WORD, wordText)
            put(COLUMN_LANGUAGE_ID, languageId)
            put(COLUMN_SOURCE_ID, sourceId)
            put(COLUMN_CREATED_AT, System.currentTimeMillis())
            put(COLUMN_IS_EXPORTED, 0)
        }

        val id = db.insert(TABLE_WORDS, null, values)
        db.close()
        return id != -1L
    }

    private fun isWordExists(wordText: String, languageId: Int): Boolean {
        val db = this.readableDatabase
        val cursor = db.query(
            TABLE_WORDS,
            arrayOf(COLUMN_ID),
            "$COLUMN_WORD = ? AND $COLUMN_LANGUAGE_ID = ?",
            arrayOf(wordText, languageId.toString()),
            null, null, null
        )
        val exists = cursor.count > 0
        cursor.close()
        db.close()
        return exists
    }

    fun getWordsFiltered(languageIds: List<Int>?, sourceIds: List<Int>?, onlyUnexported: Boolean = false): List<Word> {
        val wordList = mutableListOf<Word>()
        val db = this.readableDatabase

        var whereClause = ""
        val whereArgs = mutableListOf<String>()

        if (languageIds != null && languageIds.isNotEmpty()) {
            val placeholders = languageIds.joinToString(",") { "?" }
            whereClause += " AND $COLUMN_LANGUAGE_ID IN ($placeholders)"
            whereArgs.addAll(languageIds.map { it.toString() })
        }

        if (sourceIds != null && sourceIds.isNotEmpty()) {
            val placeholders = sourceIds.joinToString(",") { "?" }
            whereClause += " AND $COLUMN_SOURCE_ID IN ($placeholders)"
            whereArgs.addAll(sourceIds.map { it.toString() })
        }

        // NEW: Filter only unexported words if requested
        if (onlyUnexported) {
            whereClause += " AND $COLUMN_IS_EXPORTED = 0"
        }

        val query = "SELECT * FROM $TABLE_WORDS WHERE 1=1 $whereClause ORDER BY $COLUMN_CREATED_AT DESC"
        val cursor = db.rawQuery(query, if (whereArgs.isEmpty()) null else whereArgs.toTypedArray())

        if (cursor.moveToFirst()) {
            do {
                wordList.add(Word(
                    cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ID)),
                    cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_WORD)),
                    cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_LANGUAGE_ID)),
                    cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_SOURCE_ID)),
                    cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_CREATED_AT)),
                    cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_IS_EXPORTED)) == 1
                ))
            } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()
        return wordList
    }

    fun markAsExported(words: List<String>) {
        if (words.isEmpty()) return
        val db = this.writableDatabase
        val placeholders = words.joinToString(",") { "?" }
        val values = ContentValues().apply { put(COLUMN_IS_EXPORTED, 1) }
        db.update(TABLE_WORDS, values, "$COLUMN_WORD IN ($placeholders)", words.toTypedArray())
        db.close()
    }

    fun clearAllWords() {
        val db = this.writableDatabase
        db.delete(TABLE_WORDS, null, null)
        db.close()
    }

    fun updateLanguage(id: Int, name: String, abbreviation: String) {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_NAME, name)
            put(COLUMN_ABBREVIATION, abbreviation)
        }
        db.update(TABLE_LANGUAGES, values, "$COLUMN_ID = ?", arrayOf(id.toString()))
        db.close()
    }

    fun deleteLanguage(id: Int) {
        val db = this.writableDatabase
        // Due to ON DELETE CASCADE, this will also delete sources and words!
        db.delete(TABLE_LANGUAGES, "$COLUMN_ID = ?", arrayOf(id.toString()))
        db.close()
    }

    fun getWordCountForLanguage(languageId: Int): Int {
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT COUNT(*) FROM $TABLE_WORDS WHERE $COLUMN_LANGUAGE_ID = ?", arrayOf(languageId.toString()))
        var count = 0
        if (cursor.moveToFirst()) {
            count = cursor.getInt(0)
        }
        cursor.close()
        db.close()
        return count
    }

    fun updateSource(id: Int, name: String, abbreviation: String) {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_NAME, name)
            put(COLUMN_ABBREVIATION, abbreviation)
        }
        db.update(TABLE_SOURCES, values, "$COLUMN_ID = ?", arrayOf(id.toString()))
        db.close()
    }

    fun deleteSource(id: Int) {
        val db = this.writableDatabase
        // Due to ON DELETE CASCADE, this deletes all words associated with this source!
        db.delete(TABLE_SOURCES, "$COLUMN_ID = ?", arrayOf(id.toString()))
        db.close()
    }

    fun getWordCountForSource(sourceId: Int): Int {
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT COUNT(*) FROM $TABLE_WORDS WHERE $COLUMN_SOURCE_ID = ?", arrayOf(sourceId.toString()))
        var count = 0
        if (cursor.moveToFirst()) {
            count = cursor.getInt(0)
        }
        cursor.close()
        db.close()
        return count
    }

    fun getLanguageById(id: Int): Language? {
        val db = this.readableDatabase
        val cursor = db.query(TABLE_LANGUAGES, null, "$COLUMN_ID = ?", arrayOf(id.toString()), null, null, null)
        var language: Language? = null
        if (cursor.moveToFirst()) {
            language = Language(
                cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ID)),
                cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_NAME)),
                cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ABBREVIATION))
            )
        }
        cursor.close()
        db.close()
        return language
    }

    fun getSourceById(id: Int): Source? {
        val db = this.readableDatabase
        val cursor = db.query(TABLE_SOURCES, null, "$COLUMN_ID = ?", arrayOf(id.toString()), null, null, null)
        var source: Source? = null
        if (cursor.moveToFirst()) {
            source = Source(
                cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ID)),
                cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_LANGUAGE_ID)),
                cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_NAME)),
                cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ABBREVIATION))
            )
        }
        cursor.close()
        db.close()
        return source
    }

    fun deleteWordById(id: Int) {
        val db = this.writableDatabase
        db.delete(TABLE_WORDS, "$COLUMN_ID = ?", arrayOf(id.toString()))
        db.close()
    }

    fun clearDatabase(keepLanguages: Boolean, keepSources: Boolean) {
        val db = this.writableDatabase
        if (!keepLanguages) {
            // Deleting languages will CASCADE delete sources and words
            db.delete(TABLE_LANGUAGES, null, null)
        } else if (!keepSources) {
            // Keep languages, delete sources (and their words)
            db.delete(TABLE_SOURCES, null, null)
        } else {
            // Keep languages and sources, just delete words
            db.delete(TABLE_WORDS, null, null)
        }
        db.close()
    }

    // Helper to reset settings via preferences
    fun getAllLanguageNames(): String {
        return getAllLanguages().joinToString(", ") { it.name }
    }

    fun getTotalWordsCount(): Int {
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT COUNT(*) FROM $TABLE_WORDS", null)
        var count = 0
        if (cursor.moveToFirst()) count = cursor.getInt(0)
        cursor.close()
        db.close()
        return count
    }

}