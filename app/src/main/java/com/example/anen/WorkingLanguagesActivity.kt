package com.example.anen

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.anen.data.DBHelper
import com.example.anen.data.Language
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class WorkingLanguagesActivity : AppCompatActivity() {

    private lateinit var dbHelper: DBHelper
    private lateinit var adapter: LanguageAdapter
    private var isEditMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_working_languages)

        dbHelper = DBHelper(this)

        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbarLanguages)

        toolbar.setNavigationOnClickListener {
            finish()
        }

        toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_edit) {
                isEditMode = !isEditMode
                adapter.notifyDataSetChanged()
                Toast.makeText(this, if (isEditMode) "Editing Language Names" else "Editing Abbreviations", Toast.LENGTH_SHORT).show()
                true
            } else false
        }

        val rv = findViewById<RecyclerView>(R.id.rvLanguages)
        rv.layoutManager = LinearLayoutManager(this)
        adapter = LanguageAdapter(dbHelper.getAllLanguages(), dbHelper)
        rv.adapter = adapter

        val etNew = findViewById<TextInputEditText>(R.id.etNewLanguage)
        findViewById<MaterialButton>(R.id.btnAddLanguage).setOnClickListener {
            val name = etNew.text.toString().trim()
            if (name.isNotEmpty()) {
                val abbrev = name.take(3)
                val id = dbHelper.addLanguage(name, abbrev)
                if (id > 0) {
                    dbHelper.addSource(id.toInt(), "Default", "Def")
                    adapter.updateList(dbHelper.getAllLanguages())
                    etNew.text?.clear()
                } else {
                    Toast.makeText(this, "Language already exists", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    inner class LanguageAdapter(
        private var items: List<Language>,
        private val dbHelper: DBHelper
    ) : RecyclerView.Adapter<LanguageAdapter.ViewHolder>() {

        fun updateList(newItems: List<Language>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_language_manage, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val language = items[position]

            // Set text without triggering focus listeners immediately
            holder.etName.setText(language.name)
            holder.etAbbrev.setText(language.abbreviation)

            // Toggle editability based on isEditMode
            holder.etName.isEnabled = isEditMode
            holder.etAbbrev.isEnabled = !isEditMode

            // Save Name on focus loss
            holder.etName.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus && isEditMode) {
                    val newName = holder.etName.text.toString().trim()
                    if (newName.isNotEmpty() && newName != language.name) {
                        dbHelper.updateLanguage(language.id, newName, language.abbreviation)
                        updateList(dbHelper.getAllLanguages())
                    }
                }
            }

            // Save Abbreviation on focus loss
            holder.etAbbrev.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus && !isEditMode) {
                    val newAbbrev = holder.etAbbrev.text.toString().take(3)
                    if (newAbbrev != language.abbreviation && newAbbrev.isNotEmpty()) {
                        dbHelper.updateLanguage(language.id, language.name, newAbbrev)
                        updateList(dbHelper.getAllLanguages())
                    }
                }
            }

            holder.btnDelete.setOnClickListener {
                val wordCount = dbHelper.getWordCountForLanguage(language.id)
                if (wordCount == 0) {
                    AlertDialog.Builder(this@WorkingLanguagesActivity)
                        .setTitle("Delete Language")
                        .setMessage("Delete '${language.name}'? This cannot be undone.")
                        .setPositiveButton("Delete") { _, _ ->
                            dbHelper.deleteLanguage(language.id)
                            updateList(dbHelper.getAllLanguages())
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                } else {
                    showSecureDeleteDialog(language, wordCount)
                }
            }
        }

        override fun getItemCount() = items.size

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val etName: TextInputEditText = view.findViewById(R.id.etLanguageName)
            val etAbbrev: TextInputEditText = view.findViewById(R.id.etAbbreviation)
            val btnDelete: MaterialButton = view.findViewById(R.id.btnDeleteLanguage)
        }
    }

    private fun showSecureDeleteDialog(language: Language, wordCount: Int) {
        val input = EditText(this).apply {
            hint = "Enter language name"
            inputType = InputType.TYPE_CLASS_TEXT
        }

        AlertDialog.Builder(this)
            .setTitle("Warning!")
            .setMessage("This language contains $wordCount words. Deleting it will permanently delete all its sources and words. \n\nType '${language.name}' to confirm.")
            .setView(input)
            .setPositiveButton("Delete") { _, _ ->
                if (input.text.toString().trim().equals(language.name, ignoreCase = true)) {
                    dbHelper.deleteLanguage(language.id)
                    adapter.updateList(dbHelper.getAllLanguages())
                    Toast.makeText(this, "Language deleted", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Name does not match. Deletion cancelled.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}