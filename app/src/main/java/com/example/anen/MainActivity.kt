package com.example.anen

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * Main entry point of the application.
 * This screen will be used to manage settings, permissions, and database exports.
 * Full UI implementation will be added in Stage 3.
 */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }
}