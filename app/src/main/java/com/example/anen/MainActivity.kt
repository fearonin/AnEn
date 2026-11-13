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
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.LinearLayout
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.example.anen.data.DBHelper
import com.example.anen.service.OverlayAccessibilityService
import com.example.anen.data.AppInfo
import com.example.anen.data.Source
import com.example.anen.utils.PreferencesManager
import com.example.anen.utils.AppSelectionAdapter
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
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

    // State for export
    private var pendingExportWords: List<String> = emptyList()
    private var pendingMetadata: String = ""
    private var isExportAllMode = false
    private var exportDelimiter = "\t"

    // Filter states (Empty means ALL)
    private var selectedLangIds: MutableSet<Int> = mutableSetOf()
    private var selectedSrcIds: MutableSet<Int> = mutableSetOf()

    // Delimiter arrays
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

        if (!preferencesManager.isOnboardingCompleted()) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        dbHelper = DBHelper(this)
        initViews()
        setupListeners()
        updateTargetAppButton()
        updateSwitchState()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionButtonsState()
        updateUiState()
        updateLanguageAndSourceButtons()
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

        val checkPermissionsAndRun: (() -> Unit) -> Unit = { action ->
            if (Settings.canDrawOverlays(this) && isAccessibilityServiceEnabled()) {
                action()
            } else {
                Toast.makeText(this, "Please grant all permissions first", Toast.LENGTH_SHORT).show()
            }
        }

        btnSelectApp.setOnClickListener { checkPermissionsAndRun { showAppSelectionDialog() } }
        btnDbNormal.setOnClickListener { checkPermissionsAndRun { showNormalDatabaseView() } }
        btnDbRaw.setOnClickListener { checkPermissionsAndRun { showRawDatabaseView() } }
        btnExport.setOnClickListener { checkPermissionsAndRun { showExportDialog(isExportAll = false) } }
        btnExportAll.setOnClickListener { checkPermissionsAndRun { showExportDialog(isExportAll = true) } }
        btnImport.setOnClickListener { checkPermissionsAndRun { openDocumentLauncher.launch(arrayOf("text/plain")) } }
        btnClearAll.setOnClickListener { checkPermissionsAndRun { showClearDatabaseDialog() } }
        btnInvocationMethod.setOnClickListener { checkPermissionsAndRun { showInvocationMethodDialog() } }
        btnLanguages.setOnClickListener {
            checkPermissionsAndRun { showLanguageSelectionDialog() }
        }
        btnSources.setOnClickListener {
            checkPermissionsAndRun { showSourceSelectionDialog() }
        }


        btnSettings.setOnClickListener { showSettingsMenu() }
    }

    private fun updateUiState() {
        val hasOverlay = Settings.canDrawOverlays(this)
        val hasAccessibility = isAccessibilityServiceEnabled()
        val allPermissionsGranted = hasOverlay && hasAccessibility
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
        if (Settings.canDrawOverlays(this)) {
            btnOverlayPermission.visibility = View.GONE
        } else {
            btnOverlayPermission.visibility = View.VISIBLE
        }

        if (isAccessibilityServiceEnabled()) {
            btnAccessibilityPermission.visibility = View.GONE
        } else {
            btnAccessibilityPermission.visibility = View.VISIBLE
        }
    }

    // ==========================================
    // --- DATABASE VIEWING WITH DELIMITERS ---
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
        selectedLangIds.clear() // Reset to ALL on open
        selectedSrcIds.clear()

        val refreshContent = {
            // Fetch filtered words
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
                        sb.setSpan(android.text.style.RelativeSizeSpan(1.6f), start, sb.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        sb.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), start, sb.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        lastYear = year; lastMonth = -1; lastDay = -1
                    }
                    if (month != lastMonth) {
                        flushDayWords()
                        val monthText = "  📁 $monthName\n"
                        val start = sb.length
                        sb.append(monthText)
                        sb.setSpan(android.text.style.RelativeSizeSpan(1.3f), start, sb.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        sb.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), start, sb.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        lastMonth = month; lastDay = -1
                    }
                    if (day != lastDay) {
                        flushDayWords()
                        val dayText = "    $day\n"
                        val start = sb.length
                        sb.append(dayText)
                        sb.setSpan(android.text.style.RelativeSizeSpan(1.15f), start, sb.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        sb.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), start, sb.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        lastDay = day
                    }

                    dayWords.add(word.word)
                }
                flushDayWords()
                tvContent.text = sb
            }
        }

        // Initial setup
        btnLang.text = "Languages (All)"
        btnSrc.text = "Sources (All)"

        btnLang.setOnClickListener {
            val allLangs = dbHelper.getAllLanguages()
            val langs = allLangs.map { Pair(it.id, "${it.name} (${it.abbreviation})") }
            val showAll = allLangs.size > 1

            showMultiSelectDialog("Filter Languages", langs, selectedLangIds, showAllOption = showAll) { selection ->
                selectedLangIds = selection.toMutableSet()
                btnLang.text = if (selection.isEmpty()) "Languages (All)" else "Languages (${selection.size})"

                selectedSrcIds.clear()

                // FIXED LOGIC: Check how many sources exist for the new language
                val langsToFetch = if (selectedLangIds.isEmpty()) dbHelper.getAllLanguages().map { it.id } else selectedLangIds.toList()
                val allSources = mutableListOf<Source>()
                langsToFetch.forEach { allSources.addAll(dbHelper.getSourcesForLanguage(it)) }

                if (allSources.size == 1) {
                    val singleSource = allSources.first()
                    selectedSrcIds.add(singleSource.id)
                    val langAbbrev = dbHelper.getLanguageById(singleSource.languageId)?.abbreviation ?: ""
                    btnSrc.text = "${singleSource.name} ($langAbbrev)"
                } else if (allSources.size > 1) {
                    btnSrc.text = "Sources (All)"
                } else {
                    btnSrc.text = "Sources (None)"
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
                showMultiSelectDialog("Filter Sources", srcPairs, selectedSrcIds, showAllOption = true) { selection ->
                    selectedSrcIds = selection.toMutableSet()
                    btnSrc.text = if (selection.isEmpty()) "Sources (All)" else "Sources (${selection.size})"
                    refreshContent()
                }
            } else if (srcPairs.size == 1) {
                selectedSrcIds = mutableSetOf(srcPairs.first().first)
                btnSrc.text = srcPairs.first().second
                refreshContent()
                Toast.makeText(this, "Selected only available source", Toast.LENGTH_SHORT).show()
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

        btnLang.text = "Languages (All)"
        btnSrc.text = "Sources (All)"

        btnLang.setOnClickListener {
            val allLangs = dbHelper.getAllLanguages()
            val langs = allLangs.map { Pair(it.id, "${it.name} (${it.abbreviation})") }
            val showAll = allLangs.size > 1

            showMultiSelectDialog("Filter Languages", langs, selectedLangIds, showAllOption = showAll) { selection ->
                selectedLangIds = selection.toMutableSet()
                btnLang.text = if (selection.isEmpty()) "Languages (All)" else "Languages (${selection.size})"

                selectedSrcIds.clear()

                // FIXED LOGIC: Check how many sources exist for the new language
                val langsToFetch = if (selectedLangIds.isEmpty()) dbHelper.getAllLanguages().map { it.id } else selectedLangIds.toList()
                val allSources = mutableListOf<Source>()
                langsToFetch.forEach { allSources.addAll(dbHelper.getSourcesForLanguage(it)) }

                if (allSources.size == 1) {
                    val singleSource = allSources.first()
                    selectedSrcIds.add(singleSource.id)
                    val langAbbrev = dbHelper.getLanguageById(singleSource.languageId)?.abbreviation ?: ""
                    btnSrc.text = "${singleSource.name} ($langAbbrev)"
                } else if (allSources.size > 1) {
                    btnSrc.text = "Sources (All)"
                } else {
                    btnSrc.text = "Sources (None)"
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
                showMultiSelectDialog("Filter Sources", srcPairs, selectedSrcIds, showAllOption = true) { selection ->
                    selectedSrcIds = selection.toMutableSet()
                    btnSrc.text = if (selection.isEmpty()) "Sources (All)" else "Sources (${selection.size})"
                    refreshContent()
                }
            } else if (srcPairs.size == 1) {
                selectedSrcIds = mutableSetOf(srcPairs.first().first)
                btnSrc.text = srcPairs.first().second
                refreshContent()
                Toast.makeText(this, "Selected only available source", Toast.LENGTH_SHORT).show()
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
    // --- EXPORT ALL WITH METADATA ---
    // ==========================================

    @SuppressLint("MissingInflatedId")
    private fun showExportDialog(isExportAll: Boolean) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_export_all, null)
        val spinner = dialogView.findViewById<Spinner>(R.id.spinnerExportDelimiter)
        val cbMetadata = dialogView.findViewById<CheckBox>(R.id.cbIncludeMetadata)
        val tvWarning = dialogView.findViewById<TextView>(R.id.tvMetadataWarning) // Assuming you add an ID to the warning text in XML
        val btnLang = dialogView.findViewById<MaterialButton>(R.id.btnExportFilterLang)
        val btnSrc = dialogView.findViewById<MaterialButton>(R.id.btnExportFilterSrc)

        setupSpinner(spinner)

        // Hide metadata option if it's a normal export
        if (!isExportAll) {
            cbMetadata.visibility = View.GONE
            tvWarning.visibility = View.GONE
        }

        // Local filter states for this dialog
        var exportLangIds: Set<Int> = emptySet()
        var exportSrcIds: Set<Int> = emptySet()

        // Update button labels based on current selection
        fun updateFilterLabels() {
            btnLang.text = if (exportLangIds.isEmpty()) "Languages (All)" else "Languages (${exportLangIds.size})"

            // If no language selected (All), but only 1 language exists in DB, we display that 1 source directly
            val langsToFetch = if (exportLangIds.isEmpty()) dbHelper.getAllLanguages().map { it.id } else exportLangIds.toList()
            val allSources = mutableListOf<Source>()
            langsToFetch.forEach { allSources.addAll(dbHelper.getSourcesForLanguage(it)) }

            if (allSources.size <= 1) {
                // If there is 0 or 1 source total in this context, show its name
                btnSrc.text = if (allSources.isEmpty()) "Sources (None)" else allSources.first().name
            } else {
                btnSrc.text = if (exportSrcIds.isEmpty()) "Sources (All)" else "Sources (${exportSrcIds.size})"
            }
        }

        updateFilterLabels()

        btnLang.setOnClickListener {
            val allLangs = dbHelper.getAllLanguages()
            val langs = allLangs.map { Pair(it.id, "${it.name} (${it.abbreviation})") }
            val showAll = allLangs.size > 1

            showMultiSelectDialog("Filter Languages", langs, exportLangIds, showAllOption = showAll) { selection ->
                exportLangIds = selection
                exportSrcIds = emptySet() // Reset sources when language changes
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
                showMultiSelectDialog("Filter Sources", srcPairs, exportSrcIds, showAllOption = true) { selection ->
                    exportSrcIds = selection
                    updateFilterLabels()
                }
            } else {
                Toast.makeText(this, "Only one source available for the selected languages", Toast.LENGTH_SHORT).show()
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
        // Fetch words based on filters
        val wordsToExport = dbHelper.getWordsFiltered(
            if (langIds.isEmpty()) null else langIds.toList(),
            if (srcIds.isEmpty()) null else srcIds.toList(),
            onlyUnexported = !isExportAll // If not "Export All", only take unexported
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
        sb.append("id|word|created_at|is_exported\n")
        for (w in words) {
            sb.append("${w.id}|${w.word}|${w.createdAt}|${if (w.isExported) 1 else 0}\n")
        }
        sb.append("--- METADATA END ---\n")
        return sb.toString()
    }

    // ==========================================
    // --- OTHER EXPORT / IMPORT / APP SELECT ---
    // ==========================================


    private fun writeWordsToFile(uri: Uri) {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri)?.use { outputStream ->
                        val writer = OutputStreamWriter(outputStream)
                        val mainContent = pendingExportWords.joinToString(exportDelimiter)
                        writer.write(mainContent)
                        if (pendingMetadata.isNotEmpty()) {
                            writer.write(pendingMetadata)
                        }
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
                val importedCount = withContext(Dispatchers.IO) {
                    var count = 0
                    contentResolver.openInputStream(uri)?.use { inputStream ->
                        val reader = BufferedReader(InputStreamReader(inputStream))
                        val content = reader.readText()

                        // Extract only the words part (before metadata)
                        val wordsPart = if (content.contains("--- METADATA START ---")) {
                            content.substringBefore("--- METADATA START ---")
                        } else {
                            content
                        }

                        // Split by regex matching common delimiters and newlines
                        val words = wordsPart.split(Regex("[|;:,\\t\\n\\s]+"))
                            .map { it.trim().lowercase() }
                            .filter { it.isNotEmpty() }
                            .distinct()

                        for (word in words) {
                            if (dbHelper.addWord(word)) count++
                        }
                    }
                    count
                }
                Toast.makeText(this@MainActivity, getString(R.string.toast_import_success) + " ($importedCount)", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, R.string.toast_io_error, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ... (showSettingsMenu, showThemeDialog, showAboutDialog, showClearDatabaseDialog,
    // showInvocationMethodDialog, requestOverlayPermission, openAccessibilitySettings,
    // isAccessibilityServiceEnabled, showAppSelectionDialog, updateTargetAppButton,
    // updateSwitchState — leave as they were in the previous step) ...

    private fun showClearDatabaseDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.clear_db_title)
            .setMessage(R.string.clear_db_message)
            .setPositiveButton(R.string.btn_yes) { _, _ ->
                dbHelper.clearAllWords()
                Toast.makeText(this, "Database cleared", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun showSettingsMenu() {
        val options = arrayOf(
            getString(R.string.settings_theme),
            "Working Languages",
            "Working Sources",
            getString(R.string.settings_about)
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_menu_title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showThemeDialog()
                    1 -> startActivity(Intent(this, WorkingLanguagesActivity::class.java))
                    2 -> startActivity(Intent(this, WorkingSourcesActivity::class.java)) // NEW
                    3 -> showAboutDialog()
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                getString(R.string.method_back_button_unavailable)
            } else {
                getString(R.string.method_back_button)
            },
            getString(R.string.method_tile_only)
        )

        val currentMethod = preferencesManager.getInvocationMethod()
        val currentIndex = methodKeys.indexOf(currentMethod).coerceAtLeast(0)

        val adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_single_choice, methodNames) {
            override fun isEnabled(position: Int): Boolean {
                return !(position == 1 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            }
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
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
            val componentName = colonSplitter.next()
            if (componentName.equals(expectedService, ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    private fun showAppSelectionDialog() {
        val pm = packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }

        if (apps.isEmpty()) {
            Toast.makeText(this, R.string.toast_no_apps_found, Toast.LENGTH_SHORT).show()
            return
        }

        // Map to our custom AppInfo data class, including the icon
        val appList = apps.map {
            val label = pm.getApplicationLabel(it).toString()
            val icon = pm.getApplicationIcon(it)
            AppInfo(label, it.packageName, icon)
        }.sortedBy { it.name }

        // Inflate custom dialog layout
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_select_app, null)
        val etSearch = dialogView.findViewById<TextInputEditText>(R.id.etSearchApp)
        val lvApps = dialogView.findViewById<ListView>(R.id.lvApps)

        val currentTargets = preferencesManager.getTargetPackages()
        val adapter = AppSelectionAdapter(this, appList, currentTargets)
        lvApps.adapter = adapter

        // Add text watcher for live search
        etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                adapter.filter.filter(s)
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_select_app_title)
            .setView(dialogView)
            .setPositiveButton(R.string.btn_close) { _, _ ->
                // Save the selected packages from the adapter
                preferencesManager.setTargetPackages(adapter.getSelectedPackages())
                updateTargetAppButton()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun updateTargetAppButton() {
        val targetPackages = preferencesManager.getTargetPackages()

        if (targetPackages.isEmpty()) {
            btnSelectApp.text = getString(R.string.btn_select_app)
        } else if (targetPackages.size == 1) {
            try {
                val appInfo = packageManager.getApplicationInfo(targetPackages.first(), 0)
                val appName = packageManager.getApplicationLabel(appInfo).toString()
                btnSelectApp.text = getString(R.string.btn_select_app_selected, appName)
            } catch (_: PackageManager.NameNotFoundException) {
                btnSelectApp.text = getString(R.string.btn_select_app)
            }
        } else {
            btnSelectApp.text = getString(R.string.btn_select_app_multiple, targetPackages.size)
        }
    }

    private fun showMultiSelectDialog(
        title: String,
        items: List<Pair<Int, String>>,
        currentlySelected: Set<Int>,
        showAllOption: Boolean = true, // NEW: Toggle for "All" option
        onSave: (Set<Int>) -> Unit
    ) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_multi_select, null)
        val lv = dialogView.findViewById<ListView>(R.id.lvMultiSelect)

        // Create list with optional "All" at the top
        val listItems = mutableListOf<Pair<Int, String>>()
        if (showAllOption) {
            listItems.add(Pair(-1, "All"))
        }
        listItems.addAll(items)

        val checkedStates = BooleanArray(listItems.size)
        val offset = if (showAllOption) 1 else 0

        // Setup initial checks
        if (currentlySelected.isEmpty()) {
            // Empty means ALL (check everything)
            for (i in checkedStates.indices) checkedStates[i] = true
        } else {
            if (showAllOption) checkedStates[0] = false
            for (i in offset until listItems.size) {
                if (currentlySelected.contains(listItems[i].first)) {
                    checkedStates[i] = true
                }
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
                        // Clicked "All"
                        val newState = !checkedStates[0]
                        for (i in checkedStates.indices) checkedStates[i] = newState
                    } else {
                        // Clicked specific item
                        checkedStates[position] = !checkedStates[position]

                        if (showAllOption) {
                            // Update "All" checkbox state
                            var allChecked = true
                            for (i in 1 until checkedStates.size) {
                                if (!checkedStates[i]) {
                                    allChecked = false
                                    break
                                }
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
                    if (checkedStates[i]) {
                        newSelection.add(listItems[i].first)
                    }
                }

                if (showAllOption && newSelection.size == items.size && items.isNotEmpty()) {
                    isAllSelected = true
                }

                if (isAllSelected || newSelection.isEmpty()) {
                    onSave(emptySet()) // Empty set means ALL
                } else {
                    onSave(newSelection)
                }
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun updateSwitchState() {
        switchService.isChecked = preferencesManager.isServiceEnabled()
    }

    private fun updateLanguageAndSourceButtons() {
        val languages = dbHelper.getAllLanguages()

        // If only 1 language, hide the Languages button and make Sources full width
        if (languages.size <= 1) {
            btnLanguages.visibility = View.GONE
            val params = btnSources.layoutParams as LinearLayout.LayoutParams
            params.weight = 2f // Take full width
            params.marginStart = 0
            btnSources.layoutParams = params
        } else {
            btnLanguages.visibility = View.VISIBLE
            val params = btnSources.layoutParams as LinearLayout.LayoutParams
            params.weight = 1f // Half width
            params.marginStart = 16
            btnSources.layoutParams = params
        }

        // Update text
        val defaultLangId = preferencesManager.getDefaultLanguageId()
        val defaultLang = dbHelper.getLanguageById(defaultLangId)
        btnLanguages.text = defaultLang?.abbreviation ?: "Lang"

        val defaultSourceId = preferencesManager.getDefaultSourceId()
        val defaultSource = dbHelper.getSourceById(defaultSourceId)
        btnSources.text = defaultSource?.abbreviation ?: "Src"
    }

    private fun showLanguageSelectionDialog() {
        val languages = dbHelper.getAllLanguages()
        if (languages.isEmpty()) return

        val names = languages.map { "${it.name} (${it.abbreviation})" }.toTypedArray()
        val currentId = preferencesManager.getDefaultLanguageId()
        val currentIndex = languages.indexOfFirst { it.id == currentId }.coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle("Select Default Language")
            .setSingleChoiceItems(names, currentIndex) { dialog, which ->
                val selected = languages[which]
                preferencesManager.setDefaultLanguageId(selected.id)

                // When language changes, default source must change to the first available source of that language
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
            .setTitle("Select Default Source")
            .setSingleChoiceItems(names, currentIndex) { dialog, which ->
                preferencesManager.setDefaultSourceId(sources[which].id)
                updateLanguageAndSourceButtons()
                dialog.dismiss()
            }
            .show()
    }
}