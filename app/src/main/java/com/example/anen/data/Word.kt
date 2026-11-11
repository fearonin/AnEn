package com.example.anen.data

/**
 * Data model representing a single word entry in the database.
 *
 * @property id Unique identifier of the word in the database (Primary Key).
 * @property word The actual text of the word (Unique).
 * @property createdAt Timestamp of when the word was added.
 * @property isExported Flag indicating whether the word has been exported (1 for true, 0 for false).
 */
data class Word(
    val id: Int = 0,
    val word: String,
    val createdAt: Long,
    val isExported: Boolean = false
)