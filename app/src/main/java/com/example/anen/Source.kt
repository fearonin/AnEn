package com.example.anen.data

/**
 * Data model representing a source for a specific language.
 */
data class Source(
    val id: Int = 0,
    val languageId: Int,
    val name: String,
    val abbreviation: String
)