package com.example.anen

import android.content.res.Configuration
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.example.anen.utils.PreferencesManager
import com.google.android.material.button.MaterialButton

class GeneralSettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_general_settings)

        val preferencesManager = PreferencesManager(this)

        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbarGeneral).setNavigationOnClickListener {
            finish()
        }

        findViewById<MaterialButton>(R.id.btnAppLanguage).setOnClickListener {
            showLanguagePicker()
        }

        findViewById<MaterialButton>(R.id.btnResetDefaults).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.reset_defaults)
                .setMessage(R.string.reset_defaults_message)
                .setPositiveButton(R.string.btn_yes) { _, _ ->
                    preferencesManager.setSkipDeleteConfirmation(false)
                    Toast.makeText(this, "Settings reset to default", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton(R.string.btn_cancel, null)
                .show()
        }
    }

    private fun showLanguagePicker() {
        val languages = arrayOf("English", "Русский", "Español", "日本語", "中文", "Deutsch", "한국어")
        val localeCodes = arrayOf("en", "ru", "es", "ja", "zh", "de", "ko")

        val currentLocale = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        val currentIndex = localeCodes.indexOfFirst { currentLocale.startsWith(it) }.coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle(R.string.app_language)
            .setSingleChoiceItems(languages, currentIndex) { dialog, which ->
                val locale = LocaleListCompat.forLanguageTags(localeCodes[which])
                AppCompatDelegate.setApplicationLocales(locale)
                dialog.dismiss()
                recreate()
            }
            .show()
    }
}