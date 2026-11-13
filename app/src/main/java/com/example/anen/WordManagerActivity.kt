package com.example.anen

import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.anen.data.DBHelper
import com.example.anen.data.Word
import com.example.anen.utils.PreferencesManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class WordManagerActivity : AppCompatActivity() {

    private lateinit var dbHelper: DBHelper
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var adapter: WordAdapter

    private var allWords: List<Word> = emptyList()
    private var filteredWords: List<Word> = emptyList()
    private var langId: Int = -1
    private var srcId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_word_manager)

        dbHelper = DBHelper(this)
        preferencesManager = PreferencesManager(this)

        langId = intent.getIntExtra("LANG_ID", -1)
        srcId = intent.getIntExtra("SRC_ID", -1)

        if (langId == -1 || srcId == -1) {
            Toast.makeText(this, "No language or source selected", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbarWordManager).setNavigationOnClickListener {
            finish()
        }

        val rv = findViewById<RecyclerView>(R.id.rvWords)
        rv.layoutManager = LinearLayoutManager(this)
        adapter = WordAdapter()
        rv.adapter = adapter

        val etSearch = findViewById<TextInputEditText>(R.id.etSearchWord)
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterWords(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        findViewById<MaterialButton>(R.id.btnExitManager).setOnClickListener { finish() }

        findViewById<MaterialButton>(R.id.btnClearData).setOnClickListener {
            if (dbHelper.getTotalWordsCount() == 0) {
                Toast.makeText(this, R.string.toast_no_words_to_clear, Toast.LENGTH_SHORT).show()
            } else {
                showClearDatabaseConfirmDialog()
            }
        }

        loadWords()
    }

    private fun loadWords() {
        allWords = dbHelper.getWordsFiltered(listOf(langId), listOf(srcId))
        filteredWords = allWords
        adapter.notifyDataSetChanged()
    }

    private fun filterWords(query: String) {
        filteredWords = if (query.isEmpty()) {
            allWords
        } else {
            allWords.filter { it.word.contains(query, ignoreCase = true) }
        }
        adapter.notifyDataSetChanged()
    }

    private fun showDeleteConfirmation(word: Word) {
        if (preferencesManager.shouldSkipDeleteConfirmation()) {
            deleteWord(word)
            return
        }

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_confirm_delete, null)
        val cbDontAsk = dialogView.findViewById<CheckBox>(R.id.cbDontAskAgain)

        AlertDialog.Builder(this)
            .setTitle(R.string.confirm_delete_title)
            .setMessage(getString(R.string.confirm_delete_message, word.word))
            .setView(dialogView)
            .setPositiveButton(R.string.btn_yes) { _, _ ->
                if (cbDontAsk.isChecked) {
                    preferencesManager.setSkipDeleteConfirmation(true)
                }
                deleteWord(word)
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun deleteWord(word: Word) {
        dbHelper.deleteWordById(word.id)
        loadWords()
        Toast.makeText(this, "Word deleted", Toast.LENGTH_SHORT).show()
    }

    private fun showClearDatabaseConfirmDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_clear_confirm, null)
        val cbSaveLang = dialogView.findViewById<CheckBox>(R.id.cbSaveLanguages)
        val cbSaveSrc = dialogView.findViewById<CheckBox>(R.id.cbSaveSources)

        cbSaveLang.isChecked = false
        cbSaveSrc.isChecked = false
        cbSaveSrc.isEnabled = false
        cbSaveSrc.alpha = 0.4f

        cbSaveLang.setOnCheckedChangeListener { _, isChecked ->
            cbSaveSrc.isEnabled = isChecked
            cbSaveSrc.alpha = if (isChecked) 1.0f else 0.4f
            if (!isChecked) {
                cbSaveSrc.isChecked = false
            } else {
                cbSaveSrc.isChecked = true
            }
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.clear_tool_title)
            .setMessage(R.string.clear_tool_message)
            .setView(dialogView)
            .setPositiveButton(R.string.btn_ok) { _, _ ->
                showLanguageInputDialog(cbSaveLang.isChecked, cbSaveSrc.isChecked)
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun showLanguageInputDialog(keepLanguages: Boolean, keepSources: Boolean) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.clear_tool_title)
            .setMessage(R.string.clear_tool_type_langs)
            .setView(input)
            .setPositiveButton(R.string.btn_ok) { _, _ ->
                val typedText = input.text.toString().trim()
                if (typedText.isEmpty()) {
                    Toast.makeText(this, "Confirmation cancelled", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val existingLangs = dbHelper.getAllLanguages().map { it.name.lowercase().trim() }.toSet()
                val typedLangs = typedText.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()

                if (existingLangs == typedLangs) {
                    dbHelper.clearDatabase(keepLanguages, keepSources)
                    Toast.makeText(this, "Database cleared successfully", Toast.LENGTH_SHORT).show()
                    loadWords() // Refresh list
                } else {
                    Toast.makeText(this, "Language names do not match. Wipe cancelled.", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    inner class WordAdapter : RecyclerView.Adapter<WordAdapter.ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_word_manage, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val word = filteredWords[position]
            holder.tvWord.text = word.word
            holder.btnDelete.setOnClickListener {
                showDeleteConfirmation(word)
            }
        }

        override fun getItemCount() = filteredWords.size

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvWord: TextView = view.findViewById(R.id.tvWordText)
            val btnDelete: MaterialButton = view.findViewById(R.id.btnDeleteWord)
        }
    }
}