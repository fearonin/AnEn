package com.example.anen

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.anen.data.DBHelper
import com.example.anen.data.Language
import com.example.anen.data.Source
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

private const val TYPE_HEADER = 0
private const val TYPE_ITEM = 1

class WorkingSourcesActivity : AppCompatActivity() {

    private lateinit var dbHelper: DBHelper
    private lateinit var adapter: SourcesAdapter
    private lateinit var rvSources: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_working_sources)

        dbHelper = DBHelper(this)

        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbarSources).setNavigationOnClickListener {
            finish()
        }

        rvSources = findViewById(R.id.rvSources)
        rvSources.layoutManager = LinearLayoutManager(this)

        buildDataAndUpdateAdapter()
    }

    private fun buildDataAndUpdateAdapter() {
        val languages = dbHelper.getAllLanguages()
        val flatList = mutableListOf<Any>()

        for (lang in languages) {
            flatList.add(lang) // Add Language Header
            val sources = dbHelper.getSourcesForLanguage(lang.id)
            flatList.addAll(sources) // Add Sources under it
        }

        // FIX: Use ::adapter.isInitialized instead of adapter == null
        if (!::adapter.isInitialized) {
            adapter = SourcesAdapter(flatList, dbHelper)
            rvSources.adapter = adapter
        } else {
            adapter.updateList(flatList)
        }
    }

    // Adapter with two view types (Header and Item)
    inner class SourcesAdapter(
        private var items: List<Any>,
        private val dbHelper: DBHelper
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        fun updateList(newItems: List<Any>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun getItemViewType(position: Int): Int {
            return if (items[position] is Language) TYPE_HEADER else TYPE_ITEM
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return if (viewType == TYPE_HEADER) {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_source_header, parent, false)
                HeaderViewHolder(view)
            } else {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_source_manage, parent, false)
                ItemViewHolder(view)
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            if (holder is HeaderViewHolder) {
                val lang = items[position] as Language
                holder.tvHeader.text = "${lang.name} (${lang.abbreviation})"

                holder.btnAdd.setOnClickListener {
                    showAddSourceDialog(lang)
                }
            } else if (holder is ItemViewHolder) {
                val source = items[position] as Source
                holder.tvName.text = source.name
                holder.etAbbrev.setText(source.abbreviation)

                holder.etAbbrev.setOnFocusChangeListener { _, hasFocus ->
                    if (!hasFocus) {
                        val newAbbrev = holder.etAbbrev.text.toString().take(3)
                        if (newAbbrev != source.abbreviation && newAbbrev.isNotEmpty()) {
                            dbHelper.updateSource(source.id, source.name, newAbbrev)
                            buildDataAndUpdateAdapter()
                        }
                    }
                }

                holder.btnDelete.setOnClickListener {
                    val wordCount = dbHelper.getWordCountForSource(source.id)
                    if (wordCount == 0) {
                        // Simple Delete
                        AlertDialog.Builder(this@WorkingSourcesActivity)
                            .setTitle("Delete Source")
                            .setMessage("Delete source '${source.name}'?")
                            .setPositiveButton("Delete") { _, _ ->
                                dbHelper.deleteSource(source.id)
                                buildDataAndUpdateAdapter()
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    } else {
                        // Secure Delete
                        showSecureDeleteDialog(source, wordCount)
                    }
                }
            }
        }

        override fun getItemCount() = items.size

        inner class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvHeader: TextView = view.findViewById(R.id.tvLanguageHeader)
            val btnAdd: MaterialButton = view.findViewById(R.id.btnAddSource)
        }

        inner class ItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(R.id.tvSourceName)
            val etAbbrev: TextInputEditText = view.findViewById(R.id.etSourceAbbrev)
            val btnDelete: MaterialButton = view.findViewById(R.id.btnDeleteSource)
        }
    }

    private fun showAddSourceDialog(language: Language) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_source, null)
        val etName = dialogView.findViewById<EditText>(R.id.etSourceName)
        val etAbbrev = dialogView.findViewById<EditText>(R.id.etSourceAbbrev)

        AlertDialog.Builder(this)
            .setTitle("Add Source to ${language.name}")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val name = etName.text.toString().trim()
                var abbrev = etAbbrev.text.toString().trim()
                if (name.isNotEmpty()) {
                    if (abbrev.isEmpty()) abbrev = name.take(3)
                    val id = dbHelper.addSource(language.id, name, abbrev)
                    if (id > 0) {
                        buildDataAndUpdateAdapter()
                    } else {
                        Toast.makeText(this, "Source already exists", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showSecureDeleteDialog(source: Source, wordCount: Int) {
        val input = EditText(this).apply {
            hint = "Enter source name"
            inputType = InputType.TYPE_CLASS_TEXT
        }

        AlertDialog.Builder(this)
            .setTitle("Warning!")
            .setMessage("This source contains $wordCount words. Deleting it will permanently delete all its words. \n\nType '${source.name}' to confirm.")
            .setView(input)
            .setPositiveButton("Delete") { _, _ ->
                if (input.text.toString().trim().equals(source.name, ignoreCase = true)) {
                    dbHelper.deleteSource(source.id)
                    buildDataAndUpdateAdapter()
                    Toast.makeText(this, "Source deleted", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Name does not match. Deletion cancelled.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}