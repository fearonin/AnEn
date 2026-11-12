package com.example.anen

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
import com.example.anen.utils.PreferencesManager
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
    private lateinit var btnSettings: MaterialButton
    private lateinit var btnInvocationMethod: MaterialButton

    private lateinit var dbHelper: DBHelper
    private lateinit var preferencesManager: PreferencesManager

    // State for export
    private var pendingExportWords: List<String> = emptyList()
    private var pendingMetadata: String = ""
    private var isExportAllMode = false
    private var exportDelimiter = "\t"

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
        btnExport.setOnClickListener { checkPermissionsAndRun { handleExport(exportAll = false) } }
        btnImport.setOnClickListener { checkPermissionsAndRun { openDocumentLauncher.launch(arrayOf("text/plain")) } }
        btnExportAll.setOnClickListener { checkPermissionsAndRun { showExportAllSettingsDialog() } }
        btnClearAll.setOnClickListener { checkPermissionsAndRun { showClearDatabaseDialog() } }
        btnInvocationMethod.setOnClickListener { checkPermissionsAndRun { showInvocationMethodDialog() } }

        btnSettings.setOnClickListener { showSettingsMenu() }
    }

    private fun updateUiState() {
        val hasOverlay = Settings.canDrawOverlays(this)
        val hasAccessibility = isAccessibilityServiceEnabled()
        val allPermissionsGranted = hasOverlay && hasAccessibility
        val alphaValue = if (allPermissionsGranted) 1.0f else 0.4f

        val restrictedViews = listOf(
            btnSelectApp, btnDbNormal, btnDbRaw, btnExport, btnImport, btnExportAll, btnClearAll, btnInvocationMethod
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
        val words = dbHelper.getAllWords()
        if (words.isEmpty()) {
            Toast.makeText(this, R.string.dialog_db_empty, Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_db_view, null)
        val spinner = dialogView.findViewById<Spinner>(R.id.spinnerDelimiter)
        val tvContent = dialogView.findViewById<TextView>(R.id.tvDbContent)

        setupSpinner(spinner)

        val updateContent = { delimIndex: Int ->
            val delimiter = delimiterSymbols[delimIndex]
            val sb = SpannableStringBuilder()
            val calendar = Calendar.getInstance()

            var lastYear = -1
            var lastMonth = -1
            var lastDay = -1
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
                    lastYear = year
                    lastMonth = -1
                    lastDay = -1
                }

                if (month != lastMonth) {
                    flushDayWords()
                    val monthText = "  📁 $monthName\n"
                    val start = sb.length
                    sb.append(monthText)
                    sb.setSpan(RelativeSizeSpan(1.3f), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    sb.setSpan(StyleSpan(Typeface.BOLD), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    lastMonth = month
                    lastDay = -1
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
            flushDayWords() // Flush the last day
            tvContent.text = sb
        }

        // Initial rendering
        updateContent(0)

        spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateContent(position)
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_db_title)
            .setView(dialogView)
            .setPositiveButton(R.string.btn_close, null)
            .show()
    }

    private fun showRawDatabaseView() {
        val words = dbHelper.getAllWords()
        if (words.isEmpty()) {
            Toast.makeText(this, R.string.dialog_db_empty, Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_db_view, null)
        val spinner = dialogView.findViewById<Spinner>(R.id.spinnerDelimiter)
        val tvContent = dialogView.findViewById<TextView>(R.id.tvDbContent)

        setupSpinner(spinner)

        val updateContent = { delimIndex: Int ->
            val delimiter = delimiterSymbols[delimIndex]
            tvContent.text = words.joinToString(delimiter) { it.word }
        }

        updateContent(0)

        spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateContent(position)
            }
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

    private fun showExportAllSettingsDialog() {
        val words = dbHelper.getAllWords()
        if (words.isEmpty()) {
            Toast.makeText(this, R.string.dialog_db_empty, Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_export_all, null)
        val spinner = dialogView.findViewById<Spinner>(R.id.spinnerExportDelimiter)
        val cbMetadata = dialogView.findViewById<CheckBox>(R.id.cbIncludeMetadata)

        setupSpinner(spinner)

        AlertDialog.Builder(this)
            .setTitle(R.string.export_all_title)
            .setView(dialogView)
            .setPositiveButton(R.string.btn_save) { _, _ ->
                val selectedDelimIndex = spinner.selectedItemPosition
                exportDelimiter = delimiterSymbols[selectedDelimIndex]
                val includeMetadata = cbMetadata.isChecked

                pendingExportWords = words.map { it.word }
                isExportAllMode = true

                if (includeMetadata) {
                    pendingMetadata = buildMetadataString(words)
                } else {
                    pendingMetadata = ""
                }

                val dateStr = SimpleDateFormat("dd_MM_yyyy", Locale.getDefault()).format(Date())
                createDocumentLauncher.launch("anen_all_words_$dateStr.txt")
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
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

    private fun handleExport(exportAll: Boolean) {
        val wordsToExport = if (exportAll) dbHelper.getAllWords() else dbHelper.getUnexportedWords()

        if (wordsToExport.isEmpty()) {
            if (!exportAll) Toast.makeText(this, R.string.toast_export_empty, Toast.LENGTH_SHORT).show()
            else Toast.makeText(this, R.string.dialog_db_empty, Toast.LENGTH_SHORT).show()
            return
        }

        pendingExportWords = wordsToExport.map { it.word }
        pendingMetadata = ""
        isExportAllMode = exportAll
        exportDelimiter = "\t" // Default to Tab for standard export

        val dateStr = SimpleDateFormat("dd_MM_yyyy", Locale.getDefault()).format(Date())
        val fileName = if (exportAll) "anen_all_words_$dateStr.txt" else "anen_$dateStr.txt"

        createDocumentLauncher.launch(fileName)
    }

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
        val options = arrayOf(getString(R.string.settings_theme), getString(R.string.settings_about))
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_menu_title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showThemeDialog()
                    1 -> showAboutDialog()
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

        val appList = apps.map {
            val label = pm.getApplicationLabel(it).toString()
            Pair<String, String>(label, it.packageName)
        }.sortedBy { it.first }

        val appNames = appList.map { it.first }.toTypedArray()

        val currentTargets = preferencesManager.getTargetPackages().toMutableSet()
        val checkedItems = BooleanArray(appList.size) { currentTargets.contains(appList[it].second) }

        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_select_app_title)
            .setMultiChoiceItems(appNames, checkedItems) { _, which, isChecked ->
                val pkg = appList[which].second
                if (isChecked) currentTargets.add(pkg) else currentTargets.remove(pkg)
            }
            .setPositiveButton(R.string.btn_close) { _, _ ->
                preferencesManager.setTargetPackages(currentTargets)
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

    private fun updateSwitchState() {
        switchService.isChecked = preferencesManager.isServiceEnabled()
    }
}