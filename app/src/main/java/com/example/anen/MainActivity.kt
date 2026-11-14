package com.example.anen

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.example.anen.data.AppInfo
import com.example.anen.data.DBHelper
import com.example.anen.data.Source
import com.example.anen.service.OverlayAccessibilityService
import com.example.anen.utils.AppSelectionAdapter
import com.example.anen.utils.PreferencesManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var switchService: SwitchMaterial
    private lateinit var btnOverlayPermission: MaterialButton
    private lateinit var btnAccessibilityPermission: MaterialButton
    private lateinit var btnSelectApp: MaterialButton
    private lateinit var btnDbNormal: MaterialButton
    private lateinit var btnDbRaw: MaterialButton
    private lateinit var btnExport: MaterialButton
    private lateinit var btnImport: MaterialButton
    private lateinit var btnExportAll: MaterialButton
    private lateinit var btnClearAll: MaterialButton
    private lateinit var btnLanguages: MaterialButton
    private lateinit var btnSources: MaterialButton
    private lateinit var btnSettings: MaterialButton
    private lateinit var btnInvocationMethod: MaterialButton

    private lateinit var dbHelper: DBHelper
    private lateinit var preferencesManager: PreferencesManager

    private var pendingExportWords: List<String> = emptyList()
    private var pendingMetadata: String = ""
    private var isExportAllMode = false
    private var exportDelimiter = "\t"

    private var selectedLangIds: MutableSet<Int> = mutableSetOf()
    private var selectedSrcIds: MutableSet<Int> = mutableSetOf()

    private val delimiterSymbols = arrayOf("\t", "|", ";", ":", ",", " ")

    private val createDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? -> if (uri != null) writeWordsToFile(uri) }

    private val openDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> if (uri != null) importWordsFromFile(uri) }

    override fun onCreate(savedInstanceState: Bundle?) {
        preferencesManager = PreferencesManager(this)

        when (preferencesManager.getTheme()) {
            "cyan" -> setTheme(R.style.Theme_AnEn_Cyan)
            "green" -> setTheme(R.style.Theme_AnEn_Green)
            "red" -> setTheme(R.style.Theme_AnEn_Red)
            "purple" -> setTheme(R.style.Theme_AnEn_Purple)
            "light" -> setTheme(R.style.Theme_AnEn_Light)
            else -> setTheme(R.style.Theme_AnEn)
        }

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        dbHelper = DBHelper(this)

        if (!preferencesManager.isOnboardingCompleted()) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        initViews()
        setupListeners()
        updateTargetAppButton()
        updateSwitchState()
    }

    override fun onResume() {
        super.onResume()
        if (preferencesManager.isOnboardingCompleted()) {
            updatePermissionButtonsState()
            updateUiState()
            updateLanguageAndSourceButtons()
        }
    }

    private fun initViews() {
        switchService = findViewById(R.id.switchService)
        btnOverlayPermission = findViewById(R.id.btnOverlayPermission)
        btnAccessibilityPermission = findViewById(R.id.btnAccessibilityPermission)
        btnSelectApp = findViewById(R.id.btnSelectApp)
        btnDbNormal = findViewById(R.id.btnDbNormal)
        btnDbRaw = findViewById(R.id.btnDbRaw)
        btnExport = findViewById(R.id.btnExport)
        btnImport = findViewById(R.id.btnImport)
        btnExportAll = findViewById(R.id.btnExportAll)
        btnClearAll = findViewById(R.id.btnClearAll)
        btnSettings = findViewById(R.id.btnSettings)
        btnInvocationMethod = findViewById(R.id.btnInvocationMethod)
        btnLanguages = findViewById(R.id.btnLanguages)
        btnSources = findViewById(R.id.btnSources)
    }

    private fun setupListeners() {
        switchService.setOnCheckedChangeListener { _, isChecked ->
            preferencesManager.setServiceEnabled(isChecked)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                android.service.quicksettings.TileService.requestListeningState(
                    this,
                    android.content.ComponentName(this, com.example.anen.service.AddWordTileService::class.java)
                )
            }
        }

        btnOverlayPermission.setOnClickListener { requestOverlayPermission() }
        btnAccessibilityPermission.setOnClickListener { openAccessibilitySettings() }

        btnSelectApp.setOnClickListener { withPermissions { showAppSelectionDialog() } }
        btnInvocationMethod.setOnClickListener { withPermissions { showInvocationMethodDialog() } }
        btnLanguages.setOnClickListener { withPermissions { showLanguageSelectionDialog() } }
        btnSources.setOnClickListener { withPermissions { showSourceSelectionDialog() } }

        btnDbNormal.setOnClickListener { withPermissionsAndLanguages { showNormalDatabaseView() } }
        btnDbRaw.setOnClickListener { withPermissionsAndLanguages { showRawDatabaseView() } }
        btnExport.setOnClickListener { withPermissionsAndLanguages { showExportDialog(isExportAll = false) } }
        btnExportAll.setOnClickListener { withPermissionsAndLanguages { showExportDialog(isExportAll = true) } }
        btnImport.setOnClickListener { withPermissions { openDocumentLauncher.launch(arrayOf("text/plain")) } }

        btnClearAll.setOnClickListener {
            withPermissionsAndLanguages {
                if (dbHelper.getTotalWordsCount() == 0) {
                    Toast.makeText(this, R.string.toast_no_words_to_clear, Toast.LENGTH_SHORT).show()
                } else {
                    val intent = Intent(this, WordManagerActivity::class.java).apply {
                        putExtra("LANG_ID", preferencesManager.getDefaultLanguageId())
                        putExtra("SRC_ID", preferencesManager.getDefaultSourceId())
                    }
                    startActivity(intent)
                }
            }
        }

        btnSettings.setOnClickListener { showSettingsMenu() }
    }

    private fun hasPermissions(): Boolean {
        return Settings.canDrawOverlays(this) && isAccessibilityServiceEnabled()
    }

    private fun withPermissions(action: () -> Unit) {
        if (hasPermissions()) {
            action()
        } else {
            Toast.makeText(this, R.string.toast_need_permissions, Toast.LENGTH_SHORT).show()
        }
    }

    private fun withPermissionsAndLanguages(action: () -> Unit) {
        if (dbHelper.getAllLanguages().isEmpty()) {
            Toast.makeText(this, R.string.toast_add_language, Toast.LENGTH_SHORT).show()
            return
        }
        withPermissions(action)
    }

    private fun updateUiState() {
        val allPermissionsGranted = hasPermissions()
        val alphaValue = if (allPermissionsGranted) 1.0f else 0.4f

        val restrictedViews = listOf(
            btnSelectApp, btnDbNormal, btnDbRaw, btnExport, btnImport, btnExportAll, btnClearAll, btnInvocationMethod,
            btnLanguages, btnSources
        )

        restrictedViews.forEach { view ->
            view.isEnabled = allPermissionsGranted
            view.alpha = alphaValue
        }
    }

    private fun updatePermissionButtonsState() {
        btnOverlayPermission.visibility = if (Settings.canDrawOverlays(this)) View.GONE else View.VISIBLE
        btnAccessibilityPermission.visibility = if (isAccessibilityServiceEnabled()) View.GONE else View.VISIBLE
    }

    // ==========================================
    // --- DATABASE VIEWING ---
    // ==========================================

    private fun getDelimiterNames(): Array<String> {
        return arrayOf(
            getString(R.string.delimiter_tab),
            getString(R.string.delimiter_pipe),
            getString(R.string.delimiter_semicolon),
            getString(R.string.delimiter_colon),
            getString(R.string.delimiter_comma),
            getString(R.string.delimiter_space)
        )
    }

    private fun setupSpinner(spinner: Spinner) {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, getDelimiterNames())
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
    }

    private fun showNormalDatabaseView() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_db_view, null)
        val spinner = dialogView.findViewById<Spinner>(R.id.spinnerDelimiter)
        val tvContent = dialogView.findViewById<TextView>(R.id.tvDbContent)
        val btnLang = dialogView.findViewById<MaterialButton>(R.id.btnFilterLang)
        val btnSrc = dialogView.findViewById<MaterialButton>(R.id.btnFilterSrc)

        setupSpinner(spinner)
        selectedLangIds.clear()
        selectedSrcIds.clear()

        val refreshContent = {
            val words = dbHelper.getWordsFiltered(
                if (selectedLangIds.isEmpty()) null else selectedLangIds.toList(),
                if (selectedSrcIds.isEmpty()) null else selectedSrcIds.toList()
            )

            if (words.isEmpty()) {
                tvContent.text = getString(R.string.dialog_db_empty)
            } else {
                val delimiter = delimiterSymbols[spinner.selectedItemPosition]
                val sb = SpannableStringBuilder()
                val calendar = Calendar.getInstance()
                var lastYear = -1; var lastMonth = -1; var lastDay = -1
                val dayWords = mutableListOf<String>()

                val flushDayWords = {
                    if (dayWords.isNotEmpty()) {
                        sb.append("      ${dayWords.joinToString(delimiter)}\n")
                        dayWords.clear()
                    }
                }

                for (word in words) {
                    calendar.timeInMillis = word.createdAt
                    val year = calendar.get(Calendar.YEAR)
                    val month = calendar.get(Calendar.MONTH)
                    val day = calendar.get(Calendar.DAY_OF_MONTH)
                    val monthName = calendar.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault()) ?: ""

                    if (year != lastYear) {
                        flushDayWords()
                        val yearText = "$year\n"
                        val start = sb.length
                        sb.append(yearText)
                        sb.setSpan(RelativeSizeSpan(1.6f), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        sb.setSpan(StyleSpan(Typeface.BOLD), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        lastYear = year; lastMonth = -1; lastDay = -1
                    }
                    if (month != lastMonth) {
                        flushDayWords()
                        val monthText = "  📁 $monthName\n"
                        val start = sb.length
                        sb.append(monthText)
                        sb.setSpan(RelativeSizeSpan(1.3f), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        sb.setSpan(StyleSpan(Typeface.BOLD), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        lastMonth = month; lastDay = -1
                    }
                    if (day != lastDay) {
                        flushDayWords()
                        val dayText = "    $day\n"
                        val start = sb.length
                        sb.append(dayText)
                        sb.setSpan(RelativeSizeSpan(1.15f), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        sb.setSpan(StyleSpan(Typeface.BOLD), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        lastDay = day
                    }
                    dayWords.add(word.word)
                }
                flushDayWords()
                tvContent.text = sb
            }
        }

        btnLang.text = getString(R.string.btn_languages_all)
        btnSrc.text = getString(R.string.btn_sources_all)

        btnLang.setOnClickListener {
            val allLangs = dbHelper.getAllLanguages()
            val langs = allLangs.map { Pair(it.id, "${it.name} (${it.abbreviation})") }
            val showAll = allLangs.size > 1

            showMultiSelectDialog(getString(R.string.filter_languages), langs, selectedLangIds, showAllOption = showAll) { selection ->
                selectedLangIds = selection.toMutableSet()
                btnLang.text = if (selection.isEmpty()) getString(R.string.btn_languages_all) else getString(R.string.btn_languages_count, selection.size)
                selectedSrcIds.clear()

                val langsToFetch = if (selectedLangIds.isEmpty()) dbHelper.getAllLanguages().map { it.id } else selectedLangIds.toList()
                val allSources = mutableListOf<Source>()
                langsToFetch.forEach { allSources.addAll(dbHelper.getSourcesForLanguage(it)) }

                when {
                    allSources.size == 1 -> {
                        val singleSource = allSources.first()
                        selectedSrcIds.add(singleSource.id)
                        val langAbbrev = dbHelper.getLanguageById(singleSource.languageId)?.abbreviation ?: ""
                        btnSrc.text = "${singleSource.name} ($langAbbrev)"
                    }
                    allSources.size > 1 -> btnSrc.text = getString(R.string.btn_sources_all)
                    else -> btnSrc.text = getString(R.string.btn_sources_none)
                }
                refreshContent()
            }
        }

        btnSrc.setOnClickListener {
            val langsToFetch = if (selectedLangIds.isEmpty()) dbHelper.getAllLanguages().map { it.id } else selectedLangIds.toList()
            val allSources = mutableListOf<Source>()
            langsToFetch.forEach { allSources.addAll(dbHelper.getSourcesForLanguage(it)) }

            val srcPairs = allSources.map {
                val lang = dbHelper.getLanguageById(it.languageId)
                Pair(it.id, "${it.name} (${lang?.abbreviation ?: ""})")
            }

            if (srcPairs.size > 1) {
                showMultiSelectDialog(getString(R.string.filter_sources), srcPairs, selectedSrcIds, showAllOption = true) { selection ->
                    selectedSrcIds = selection.toMutableSet()
                    btnSrc.text = if (selection.isEmpty()) getString(R.string.btn_sources_all) else getString(R.string.btn_sources_count, selection.size)
                    refreshContent()
                }
            } else if (srcPairs.size == 1) {
                selectedSrcIds = mutableSetOf(srcPairs.first().first)
                btnSrc.text = srcPairs.first().second
                refreshContent()
                Toast.makeText(this, R.string.toast_selected_only_source, Toast.LENGTH_SHORT).show()
            }
        }

        refreshContent()

        spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) { refreshContent() }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_db_title)
            .setView(dialogView)
            .setPositiveButton(R.string.btn_close, null)
            .show()
    }

    private fun showRawDatabaseView() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_db_view, null)
        val spinner = dialogView.findViewById<Spinner>(R.id.spinnerDelimiter)
        val tvContent = dialogView.findViewById<TextView>(R.id.tvDbContent)
        val btnLang = dialogView.findViewById<MaterialButton>(R.id.btnFilterLang)
        val btnSrc = dialogView.findViewById<MaterialButton>(R.id.btnFilterSrc)

        setupSpinner(spinner)
        selectedLangIds.clear()
        selectedSrcIds.clear()

        val refreshContent = {
            val words = dbHelper.getWordsFiltered(
                if (selectedLangIds.isEmpty()) null else selectedLangIds.toList(),
                if (selectedSrcIds.isEmpty()) null else selectedSrcIds.toList()
            )
            val delimiter = delimiterSymbols[spinner.selectedItemPosition]
            tvContent.text = if (words.isEmpty()) getString(R.string.dialog_db_empty) else words.joinToString(delimiter) { it.word }
        }

        btnLang.text = getString(R.string.btn_languages_all)
        btnSrc.text = getString(R.string.btn_sources_all)

        btnLang.setOnClickListener {
            val allLangs = dbHelper.getAllLanguages()
            val langs = allLangs.map { Pair(it.id, "${it.name} (${it.abbreviation})") }
            val showAll = allLangs.size > 1

            showMultiSelectDialog(getString(R.string.filter_languages), langs, selectedLangIds, showAllOption = showAll) { selection ->
                selectedLangIds = selection.toMutableSet()
                btnLang.text = if (selection.isEmpty()) getString(R.string.btn_languages_all) else getString(R.string.btn_languages_count, selection.size)
                selectedSrcIds.clear()

                val langsToFetch = if (selectedLangIds.isEmpty()) dbHelper.getAllLanguages().map { it.id } else selectedLangIds.toList()
                val allSources = mutableListOf<Source>()
                langsToFetch.forEach { allSources.addAll(dbHelper.getSourcesForLanguage(it)) }

                when {
                    allSources.size == 1 -> {
                        val singleSource = allSources.first()
                        selectedSrcIds.add(singleSource.id)
                        val langAbbrev = dbHelper.getLanguageById(singleSource.languageId)?.abbreviation ?: ""
                        btnSrc.text = "${singleSource.name} ($langAbbrev)"
                    }
                    allSources.size > 1 -> btnSrc.text = getString(R.string.btn_sources_all)
                    else -> btnSrc.text = getString(R.string.btn_sources_none)
                }
                refreshContent()
            }
        }

        btnSrc.setOnClickListener {
            val langsToFetch = if (selectedLangIds.isEmpty()) dbHelper.getAllLanguages().map { it.id } else selectedLangIds.toList()
            val allSources = mutableListOf<Source>()
            langsToFetch.forEach { allSources.addAll(dbHelper.getSourcesForLanguage(it)) }

            val srcPairs = allSources.map {
                val lang = dbHelper.getLanguageById(it.languageId)
                Pair(it.id, "${it.name} (${lang?.abbreviation ?: ""})")
            }

            if (srcPairs.size > 1) {
                showMultiSelectDialog(getString(R.string.filter_sources), srcPairs, selectedSrcIds, showAllOption = true) { selection ->
                    selectedSrcIds = selection.toMutableSet()
                    btnSrc.text = if (selection.isEmpty()) getString(R.string.btn_sources_all) else getString(R.string.btn_sources_count, selection.size)
                    refreshContent()
                }
            } else if (srcPairs.size == 1) {
                selectedSrcIds = mutableSetOf(srcPairs.first().first)
                btnSrc.text = srcPairs.first().second
                refreshContent()
                Toast.makeText(this, R.string.toast_selected_only_source, Toast.LENGTH_SHORT).show()
            }
        }

        refreshContent()

        spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) { refreshContent() }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_db_raw_title)
            .setView(dialogView)
            .setPositiveButton(R.string.btn_close, null)
            .show()
    }

    // ==========================================
    // --- EXPORT DIALOG ---
    // ==========================================

    @SuppressLint("MissingInflatedId")
    private fun showExportDialog(isExportAll: Boolean) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_export_all, null)
        val spinner = dialogView.findViewById<Spinner>(R.id.spinnerExportDelimiter)
        val cbMetadata = dialogView.findViewById<CheckBox>(R.id.cbIncludeMetadata)
        val tvWarning = dialogView.findViewById<TextView>(R.id.tvMetadataWarning)
        val btnLang = dialogView.findViewById<MaterialButton>(R.id.btnExportFilterLang)
        val btnSrc = dialogView.findViewById<MaterialButton>(R.id.btnExportFilterSrc)

        setupSpinner(spinner)

        if (!isExportAll) {
            cbMetadata.visibility = View.GONE
            tvWarning.visibility = View.GONE
        }

        var exportLangIds: Set<Int> = emptySet()
        var exportSrcIds: Set<Int> = emptySet()

        fun updateFilterLabels() {
            btnLang.text = if (exportLangIds.isEmpty()) getString(R.string.btn_languages_all) else getString(R.string.btn_languages_count, exportLangIds.size)

            val langsToFetch = if (exportLangIds.isEmpty()) dbHelper.getAllLanguages().map { it.id } else exportLangIds.toList()
            val allSources = mutableListOf<Source>()
            langsToFetch.forEach { allSources.addAll(dbHelper.getSourcesForLanguage(it)) }

            if (allSources.size <= 1) {
                btnSrc.text = if (allSources.isEmpty()) getString(R.string.btn_sources_none) else allSources.first().name
            } else {
                btnSrc.text = if (exportSrcIds.isEmpty()) getString(R.string.btn_sources_all) else getString(R.string.btn_sources_count, exportSrcIds.size)
            }
        }

        updateFilterLabels()

        btnLang.setOnClickListener {
            val allLangs = dbHelper.getAllLanguages()
            val langs = allLangs.map { Pair(it.id, "${it.name} (${it.abbreviation})") }
            val showAll = allLangs.size > 1

            showMultiSelectDialog(getString(R.string.filter_languages), langs, exportLangIds, showAllOption = showAll) { selection ->
                exportLangIds = selection
                exportSrcIds = emptySet()
                updateFilterLabels()
            }
        }

        btnSrc.setOnClickListener {
            val langsToFetch = if (exportLangIds.isEmpty()) dbHelper.getAllLanguages().map { it.id } else exportLangIds.toList()
            val allSources = mutableListOf<Source>()
            langsToFetch.forEach { allSources.addAll(dbHelper.getSourcesForLanguage(it)) }

            val srcPairs = allSources.map {
                val lang = dbHelper.getLanguageById(it.languageId)
                Pair(it.id, "${it.name} (${lang?.abbreviation ?: ""})")
            }

            if (srcPairs.size > 1) {
                showMultiSelectDialog(getString(R.string.filter_sources), srcPairs, exportSrcIds, showAllOption = true) { selection ->
                    exportSrcIds = selection
                    updateFilterLabels()
                }
            } else {
                Toast.makeText(this, R.string.toast_selected_only_source, Toast.LENGTH_SHORT).show()
            }
        }

        AlertDialog.Builder(this)
            .setTitle(if (isExportAll) R.string.export_all_title else R.string.btn_export)
            .setView(dialogView)
            .setPositiveButton(R.string.btn_save) { _, _ ->
                val delimiter = delimiterSymbols[spinner.selectedItemPosition]
                val includeMetadata = if (isExportAll) cbMetadata.isChecked else false
                executeExport(isExportAll, exportLangIds, exportSrcIds, delimiter, includeMetadata)
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun executeExport(isExportAll: Boolean, langIds: Set<Int>, srcIds: Set<Int>, delimiter: String, includeMetadata: Boolean) {
        val wordsToExport = dbHelper.getWordsFiltered(
            if (langIds.isEmpty()) null else langIds.toList(),
            if (srcIds.isEmpty()) null else srcIds.toList(),
            onlyUnexported = !isExportAll
        )

        if (wordsToExport.isEmpty()) {
            if (!isExportAll) Toast.makeText(this, R.string.toast_export_empty, Toast.LENGTH_SHORT).show()
            else Toast.makeText(this, R.string.dialog_db_empty, Toast.LENGTH_SHORT).show()
            return
        }

        pendingExportWords = wordsToExport.map { it.word }
        isExportAllMode = isExportAll
        exportDelimiter = delimiter
        pendingMetadata = if (includeMetadata) buildMetadataString(wordsToExport) else ""

        val dateStr = SimpleDateFormat("dd_MM_yyyy", Locale.getDefault()).format(Date())
        val fileName = if (isExportAll) "anen_all_words_$dateStr.txt" else "anen_$dateStr.txt"

        createDocumentLauncher.launch(fileName)
    }

    private fun buildMetadataString(words: List<com.example.anen.data.Word>): String {
        val sb = StringBuilder()
        sb.append("\n\n--- METADATA START ---\n")
        sb.append("id|word|language_name|source_name|created_at|is_exported\n")

        for (w in words) {
            val langName = dbHelper.getLanguageById(w.languageId)?.name ?: "English"
            val srcName = dbHelper.getSourceById(w.sourceId)?.name ?: "Default"
            sb.append("${w.id}|${w.word}|${langName}|${srcName}|${w.createdAt}|${if (w.isExported) 1 else 0}\n")
        }
        sb.append("--- METADATA END ---\n")
        return sb.toString()
    }

    // ==========================================
    // --- FILE I/O ---
    // ==========================================

    private fun writeWordsToFile(uri: Uri) {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri)?.use { outputStream ->
                        val writer = OutputStreamWriter(outputStream)
                        writer.write(pendingExportWords.joinToString(exportDelimiter))
                        if (pendingMetadata.isNotEmpty()) writer.write(pendingMetadata)
                        writer.flush()
                    }
                }
                if (!isExportAllMode) {
                    withContext(Dispatchers.IO) { dbHelper.markAsExported(pendingExportWords) }
                }
                Toast.makeText(this@MainActivity, R.string.toast_export_success, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, R.string.toast_io_error, Toast.LENGTH_SHORT).show()
            } finally {
                pendingExportWords = emptyList()
                pendingMetadata = ""
            }
        }
    }

    private fun importWordsFromFile(uri: Uri) {
        lifecycleScope.launch {
            try {
                val fileContent = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { inputStream ->
                        inputStream.bufferedReader().use { it.readText() }
                    } ?: ""
                }

                if (fileContent.isBlank()) {
                    Toast.makeText(this@MainActivity, R.string.toast_file_empty, Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val hasMetadata = fileContent.contains("--- METADATA START ---")
                if (hasMetadata) processImportWithMetadata(fileContent)
                else processImportWithoutMetadata(fileContent)

            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, R.string.toast_io_error, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun processImportWithMetadata(fullContent: String) {
        val wordsPart = fullContent.substringBefore("--- METADATA START ---").trim()
        val metadataPart = fullContent.substringAfter("--- METADATA START ---").substringBefore("--- METADATA END ---").trim()

        val requiredLangs = mutableSetOf<String>()
        val requiredSrcs = mutableSetOf<Pair<String, String>>()

        val lines = metadataPart.lines().filter { it.isNotBlank() && !it.contains("id|word") }
        for (line in lines) {
            val parts = line.split("|")
            if (parts.size >= 4) {
                val langName = parts[2].trim()
                val srcName = parts[3].trim()
                if (langName.isNotEmpty() && srcName.isNotEmpty()) {
                    requiredLangs.add(langName)
                    requiredSrcs.add(Pair(srcName, langName))
                }
            }
        }

        val existingLangs = dbHelper.getAllLanguages().map { it.name }
        val missingLangs = requiredLangs.filter { !existingLangs.contains(it) }

        val missingSrcs = mutableListOf<Pair<String, String>>()
        for (srcPair in requiredSrcs) {
            val lang = dbHelper.getAllLanguages().find { it.name == srcPair.second }
            if (lang != null) {
                val existingSrcs = dbHelper.getSourcesForLanguage(lang.id).map { it.name }
                if (!existingSrcs.contains(srcPair.first)) missingSrcs.add(srcPair)
            } else {
                if (!missingSrcs.contains(srcPair)) missingSrcs.add(srcPair)
            }
        }

        if (missingLangs.isNotEmpty() || missingSrcs.isNotEmpty()) {
            val langsText = if (missingLangs.isNotEmpty()) getString(R.string.dialog_missing_components_lang_header, missingLangs.joinToString(", ")) else ""
            val srcsText = if (missingSrcs.isNotEmpty()) getString(R.string.dialog_missing_components_src_header, missingSrcs.map { "${it.first} (${it.second})" }.joinToString(", ")) else ""

            val message = buildString {
                append(langsText)
                if (langsText.isNotEmpty() && srcsText.isNotEmpty()) append("\n")
                append(srcsText)
                append("\n\n")
                append(getString(R.string.dialog_missing_components_question))
            }

            AlertDialog.Builder(this)
                .setTitle(R.string.dialog_missing_components_title)
                .setMessage(message)
                .setPositiveButton(R.string.btn_create_import) { _, _ ->
                    for (l in missingLangs) {
                        val newLangId = dbHelper.addLanguage(l, l.take(3))
                        if (newLangId > 0) dbHelper.addSource(newLangId.toInt(), "Default", "Def")
                    }
                    for (s in missingSrcs) {
                        val lang = dbHelper.getAllLanguages().find { it.name == s.second }
                        if (lang != null) dbHelper.addSource(lang.id, s.first, s.first.take(3))
                    }
                    performActualImport(wordsPart, metadataPart)
                }
                .setNegativeButton(R.string.btn_skip, null)
                .show()
        } else {
            performActualImport(wordsPart, metadataPart)
        }
    }

    private fun performActualImport(wordsPart: String, metadataPart: String) {
        val validWords = wordsPart.split(Regex("[|;:,\\t\\n\\s]+"))
            .map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()

        if (validWords.isEmpty()) {
            Toast.makeText(this, R.string.toast_no_valid_header, Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            var count = 0
            val lines = metadataPart.lines().filter { it.isNotBlank() && !it.contains("id|word") }

            for (line in lines) {
                val parts = line.split("|")
                if (parts.size >= 6) {
                    val wordText = parts[1].trim().lowercase()
                    if (!validWords.contains(wordText)) continue

                    val langName = parts[2].trim()
                    val srcName = parts[3].trim()
                    val timestamp = parts[4].toLongOrNull() ?: System.currentTimeMillis()
                    val isExported = parts[5] == "1"

                    val lang = dbHelper.getAllLanguages().find { it.name == langName }
                    if (lang != null) {
                        val src = dbHelper.getSourcesForLanguage(lang.id).find { it.name == srcName }
                            ?: dbHelper.getSourcesForLanguage(lang.id).firstOrNull()

                        if (src != null) {
                            val db = dbHelper.writableDatabase
                            val values = android.content.ContentValues().apply {
                                put("word", wordText)
                                put("language_id", lang.id)
                                put("source_id", src.id)
                                put("created_at", timestamp)
                                put("is_exported", if (isExported) 1 else 0)
                            }
                            val id = db.insertWithOnConflict("words", null, values, android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE)
                            if (id != -1L) count++
                            db.close()
                        }
                    }
                }
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, getString(R.string.toast_imported_history, count), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun processImportWithoutMetadata(rawContent: String) {
        val wordsToImport = rawContent.split(Regex("[|;:,\\t\\n\\s]+"))
            .map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct()

        if (wordsToImport.isEmpty()) {
            Toast.makeText(this, R.string.toast_no_valid_words, Toast.LENGTH_SHORT).show()
            return
        }

        val view = LayoutInflater.from(this).inflate(R.layout.dialog_import_no_meta, null)
        val spinnerLang = view.findViewById<Spinner>(R.id.spinnerImportLang)
        val spinnerSrc = view.findViewById<Spinner>(R.id.spinnerImportSrc)

        val languages = dbHelper.getAllLanguages()
        val langNames = languages.map { it.name }.toTypedArray()
        spinnerLang.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, langNames)

        fun updateSources() {
            val selectedLang = languages[spinnerLang.selectedItemPosition]
            val sources = dbHelper.getSourcesForLanguage(selectedLang.id)
            val srcNames = sources.map { it.name }.toTypedArray()
            spinnerSrc.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, srcNames)
        }

        updateSources()

        spinnerLang.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) { updateSources() }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_import_destination_title)
            .setMessage(R.string.dialog_import_destination_message)
            .setView(view)
            .setPositiveButton(R.string.btn_import_action) { _, _ ->
                val selectedLang = languages[spinnerLang.selectedItemPosition]
                val sources = dbHelper.getSourcesForLanguage(selectedLang.id)
                val selectedSrc = sources[spinnerSrc.selectedItemPosition]

                lifecycleScope.launch(Dispatchers.IO) {
                    var count = 0
                    for (word in wordsToImport) {
                        if (dbHelper.addWord(word, selectedLang.id, selectedSrc.id)) count++
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, getString(R.string.toast_imported_words, count), Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    // ==========================================
    // --- SETTINGS ---
    // ==========================================

    private fun showSettingsMenu() {
        val options = arrayOf(
            getString(R.string.settings_theme),
            getString(R.string.working_languages_title),
            getString(R.string.working_sources_title),
            getString(R.string.settings_general),
            getString(R.string.settings_about)
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_menu_title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showThemeDialog()
                    1 -> startActivity(Intent(this, WorkingLanguagesActivity::class.java))
                    2 -> {
                        if (dbHelper.getAllLanguages().isEmpty()) {
                            Toast.makeText(this, R.string.toast_add_language, Toast.LENGTH_SHORT).show()
                        } else {
                            startActivity(Intent(this, WorkingSourcesActivity::class.java))
                        }
                    }
                    3 -> startActivity(Intent(this, GeneralSettingsActivity::class.java))
                    4 -> showAboutDialog()
                }
            }
            .show()
    }

    private fun showThemeDialog() {
        val themes = arrayOf(
            getString(R.string.theme_monochrome),
            getString(R.string.theme_light),
            getString(R.string.theme_green),
            getString(R.string.theme_cyan),
            getString(R.string.theme_red),
            getString(R.string.theme_purple)
        )
        val themeKeys = arrayOf("monochrome", "light", "green", "cyan", "red", "purple")
        val currentThemeKey = preferencesManager.getTheme()
        val currentIndex = themeKeys.indexOf(currentThemeKey)

        AlertDialog.Builder(this)
            .setTitle(R.string.settings_theme)
            .setSingleChoiceItems(themes, currentIndex) { dialog, which ->
                val selected = themeKeys[which]
                if (selected != currentThemeKey) {
                    preferencesManager.setTheme(selected)
                    dialog.dismiss()
                    recreate()
                } else {
                    dialog.dismiss()
                }
            }
            .show()
    }

    private fun showAboutDialog() {
        val message = """
            ${getString(R.string.about_version)}
            
            ${getString(R.string.about_description)}
            
            ${getString(R.string.about_license)}
            
            ${getString(R.string.about_license_text)}
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle(R.string.about_title)
            .setMessage(message)
            .setPositiveButton(R.string.btn_close, null)
            .show()
    }

    private fun showInvocationMethodDialog() {
        val methodKeys = arrayOf("swipe", "back_button", "tile_only")
        val methodNames = arrayOf(
            getString(R.string.method_swipe),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) getString(R.string.method_back_button_unavailable)
            else getString(R.string.method_back_button),
            getString(R.string.method_tile_only)
        )

        val currentMethod = preferencesManager.getInvocationMethod()
        val currentIndex = methodKeys.indexOf(currentMethod).coerceAtLeast(0)

        val adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_single_choice, methodNames) {
            override fun isEnabled(position: Int): Boolean = !(position == 1 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                view.alpha = if (isEnabled(position)) 1f else 0.4f
                return view
            }
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_invocation_title)
            .setSingleChoiceItems(adapter, currentIndex) { dialog, which ->
                if (which == 1 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    Toast.makeText(this, R.string.toast_method_unavailable, Toast.LENGTH_SHORT).show()
                    return@setSingleChoiceItems
                }
                preferencesManager.setInvocationMethod(methodKeys[which])
                dialog.dismiss()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    // ==========================================
    // --- PERMISSIONS ---
    // ==========================================

    private fun requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:$packageName".toUri())
            startActivity(intent)
        }
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedService = "$packageName/${OverlayAccessibilityService::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false

        val colonSplitter = android.text.TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServices)
        while (colonSplitter.hasNext()) {
            if (colonSplitter.next().equals(expectedService, ignoreCase = true)) return true
        }
        return false
    }

    // ==========================================
    // --- APP SELECTION ---
    // ==========================================

    private fun showAppSelectionDialog() {
        val pm = packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }

        if (apps.isEmpty()) {
            Toast.makeText(this, R.string.toast_no_apps_found, Toast.LENGTH_SHORT).show()
            return
        }

        val appList = apps.map {
            val label = pm.getApplicationLabel(it).toString()
            val icon = pm.getApplicationIcon(it)
            AppInfo(label, it.packageName, icon)
        }.sortedBy { it.name }

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_select_app, null)
        val etSearch = dialogView.findViewById<TextInputEditText>(R.id.etSearchApp)
        val lvApps = dialogView.findViewById<ListView>(R.id.lvApps)

        val adapter = AppSelectionAdapter(this, appList, preferencesManager.getTargetPackages())
        lvApps.adapter = adapter

        etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { adapter.filter.filter(s) }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_select_app_title)
            .setView(dialogView)
            .setPositiveButton(R.string.btn_close) { _, _ ->
                preferencesManager.setTargetPackages(adapter.getSelectedPackages())
                updateTargetAppButton()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun updateTargetAppButton() {
        val targetPackages = preferencesManager.getTargetPackages()

        when {
            targetPackages.isEmpty() -> btnSelectApp.text = getString(R.string.btn_select_app)
            targetPackages.size == 1 -> {
                try {
                    val appInfo = packageManager.getApplicationInfo(targetPackages.first(), 0)
                    val appName = packageManager.getApplicationLabel(appInfo).toString()
                    btnSelectApp.text = getString(R.string.btn_select_app_selected, appName)
                } catch (_: PackageManager.NameNotFoundException) {
                    btnSelectApp.text = getString(R.string.btn_select_app)
                }
            }
            else -> btnSelectApp.text = getString(R.string.btn_select_app_multiple, targetPackages.size)
        }
    }

    // ==========================================
    // --- MULTI-SELECT DIALOG ---
    // ==========================================

    private fun showMultiSelectDialog(
        title: String,
        items: List<Pair<Int, String>>,
        currentlySelected: Set<Int>,
        showAllOption: Boolean = true,
        onSave: (Set<Int>) -> Unit
    ) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_multi_select, null)
        val lv = dialogView.findViewById<ListView>(R.id.lvMultiSelect)

        val listItems = mutableListOf<Pair<Int, String>>()
        if (showAllOption) listItems.add(Pair(-1, getString(R.string.option_all)))
        listItems.addAll(items)

        val checkedStates = BooleanArray(listItems.size)
        val offset = if (showAllOption) 1 else 0

        if (currentlySelected.isEmpty()) {
            for (i in checkedStates.indices) checkedStates[i] = true
        } else {
            if (showAllOption) checkedStates[0] = false
            for (i in offset until listItems.size) {
                if (currentlySelected.contains(listItems[i].first)) checkedStates[i] = true
            }
        }

        val adapter = object : BaseAdapter() {
            override fun getCount() = listItems.size
            override fun getItem(position: Int) = listItems[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                val context = parent?.context ?: this@MainActivity
                val view: View = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_multi_select, parent, false)
                val tvName = view.findViewById<TextView>(R.id.tvItemName)
                val cb = view.findViewById<CheckBox>(R.id.cbItem)

                tvName.text = listItems[position].second
                cb.isChecked = checkedStates[position]

                view.setOnClickListener {
                    if (showAllOption && position == 0) {
                        val newState = !checkedStates[0]
                        for (i in checkedStates.indices) checkedStates[i] = newState
                    } else {
                        checkedStates[position] = !checkedStates[position]
                        if (showAllOption) {
                            var allChecked = true
                            for (i in 1 until checkedStates.size) {
                                if (!checkedStates[i]) { allChecked = false; break }
                            }
                            checkedStates[0] = allChecked
                        }
                    }
                    notifyDataSetChanged()
                }
                return view
            }
        }

        lv.adapter = adapter

        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(dialogView)
            .setPositiveButton(R.string.btn_close) { _, _ ->
                val newSelection = mutableSetOf<Int>()
                var isAllSelected = if (showAllOption) checkedStates[0] else false

                for (i in offset until checkedStates.size) {
                    if (checkedStates[i]) newSelection.add(listItems[i].first)
                }

                if (showAllOption && newSelection.size == items.size && items.isNotEmpty()) isAllSelected = true

                if (isAllSelected || newSelection.isEmpty()) onSave(emptySet())
                else onSave(newSelection)
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    // ==========================================
    // --- LANGUAGE & SOURCE BUTTONS ---
    // ==========================================

    private fun updateSwitchState() {
        switchService.isChecked = preferencesManager.isServiceEnabled()
    }

    private fun updateLanguageAndSourceButtons() {
        val languages = dbHelper.getAllLanguages()

        if (languages.isEmpty()) {
            btnLanguages.visibility = View.GONE
            btnSources.visibility = View.GONE
            return
        } else {
            btnSources.visibility = View.VISIBLE
        }

        var defaultLangId = preferencesManager.getDefaultLanguageId()
        var defaultLang = dbHelper.getLanguageById(defaultLangId)

        if (defaultLang == null) {
            defaultLang = languages.first()
            defaultLangId = defaultLang.id
            preferencesManager.setDefaultLanguageId(defaultLangId)
        }

        if (languages.size <= 1) {
            btnLanguages.visibility = View.GONE
            val params = btnSources.layoutParams as LinearLayout.LayoutParams
            params.weight = 2f
            params.marginStart = 0
            btnSources.layoutParams = params
        } else {
            btnLanguages.visibility = View.VISIBLE
            val params = btnSources.layoutParams as LinearLayout.LayoutParams
            params.weight = 1f
            params.marginStart = 16
            btnSources.layoutParams = params
        }

        btnLanguages.text = defaultLang.abbreviation

        val sources = dbHelper.getSourcesForLanguage(defaultLangId)
        var defaultSrcId = preferencesManager.getDefaultSourceId()
        var defaultSrc = sources.find { it.id == defaultSrcId }

        if (defaultSrc == null && sources.isNotEmpty()) {
            defaultSrc = sources.first()
            preferencesManager.setDefaultSourceId(defaultSrc.id)
        }

        btnSources.text = defaultSrc?.abbreviation ?: "Src"
    }

    private fun showLanguageSelectionDialog() {
        val languages = dbHelper.getAllLanguages()
        if (languages.isEmpty()) return

        val names = languages.map { "${it.name} (${it.abbreviation})" }.toTypedArray()
        val currentId = preferencesManager.getDefaultLanguageId()
        val currentIndex = languages.indexOfFirst { it.id == currentId }.coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_select_lang_title)
            .setSingleChoiceItems(names, currentIndex) { dialog, which ->
                val selected = languages[which]
                preferencesManager.setDefaultLanguageId(selected.id)

                val sources = dbHelper.getSourcesForLanguage(selected.id)
                if (sources.isNotEmpty()) {
                    preferencesManager.setDefaultSourceId(sources.first().id)
                }

                updateLanguageAndSourceButtons()
                dialog.dismiss()
            }
            .show()
    }

    private fun showSourceSelectionDialog() {
        val defaultLangId = preferencesManager.getDefaultLanguageId()
        val sources = dbHelper.getSourcesForLanguage(defaultLangId)

        if (sources.isEmpty()) return

        val names = sources.map { "${it.name} (${it.abbreviation})" }.toTypedArray()
        val currentId = preferencesManager.getDefaultSourceId()
        val currentIndex = sources.indexOfFirst { it.id == currentId }.coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_select_src_title)
            .setSingleChoiceItems(names, currentIndex) { dialog, which ->
                preferencesManager.setDefaultSourceId(sources[which].id)
                updateLanguageAndSourceButtons()
                dialog.dismiss()
            }
            .show()
    }
}