package com.example.anen

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.anen.data.DBHelper
import com.example.anen.utils.PreferencesManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class OnboardingActivity : AppCompatActivity() {

    private lateinit var dbHelper: DBHelper
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var llContainer: LinearLayout
    private lateinit var etNewLanguage: TextInputEditText
    private lateinit var btnAdd: MaterialButton
    private lateinit var btnFinish: MaterialButton

    private val tempLanguages = mutableListOf<Pair<String, String>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        dbHelper = DBHelper(this)
        preferencesManager = PreferencesManager(this)

        llContainer = findViewById(R.id.llLanguagesContainer)
        etNewLanguage = findViewById(R.id.etNewLanguage)
        btnAdd = findViewById(R.id.btnAddLanguage)
        btnFinish = findViewById(R.id.btnFinishOnboarding)

        tempLanguages.add(Pair("English", "Eng"))
        renderLanguages()

        btnAdd.setOnClickListener {
            val name = etNewLanguage.text.toString().trim()
            if (name.isNotEmpty()) {
                val abbrev = name.take(3)
                tempLanguages.add(Pair(name, abbrev))
                etNewLanguage.text?.clear()
                renderLanguages()
            }
        }

        btnFinish.setOnClickListener {
            if (tempLanguages.isEmpty()) {
                Toast.makeText(this, "Please add at least one language", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            tempLanguages.forEach { (name, abbrev) ->
                val langId = dbHelper.addLanguage(name, abbrev)
                if (langId > 0) {
                    dbHelper.addSource(langId.toInt(), "Default", "Def")
                }
            }

            val firstLanguage = dbHelper.getAllLanguages().firstOrNull()
            if (firstLanguage != null) {
                preferencesManager.setDefaultLanguageId(firstLanguage.id)
            }

            preferencesManager.setOnboardingCompleted(true)

            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    private fun renderLanguages() {
        llContainer.removeAllViews()
        for (i in tempLanguages.indices) {
            val (name, abbrev) = tempLanguages[i]
            val view = LayoutInflater.from(this).inflate(R.layout.item_language_manage, llContainer, false)

            // Безопасный поиск с ?.
            val etName = view.findViewById<TextInputEditText>(R.id.etLanguageName)
            etName?.setText(name)
            etName?.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    tempLanguages[i] = Pair(etName.text.toString().trim(), tempLanguages[i].second)
                }
            }

            val etAbbrev = view.findViewById<TextInputEditText>(R.id.etAbbreviation)
            etAbbrev?.setText(abbrev)
            etAbbrev?.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    tempLanguages[i] = Pair(tempLanguages[i].first, etAbbrev.text.toString().take(3))
                }
            }

            val btnDelete = view.findViewById<MaterialButton>(R.id.btnDeleteLanguage)
            btnDelete?.setOnClickListener {
                tempLanguages.removeAt(i)
                renderLanguages()
            }

            llContainer.addView(view)
        }
    }
}