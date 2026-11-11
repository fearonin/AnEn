package com.example.anen

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.view.View
import android.widget.ScrollView
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
import java.util.Calendar
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

    private lateinit var dbHelper: DBHelper
    private lateinit var preferencesManager: PreferencesManager

    private var pendingExportWords: List<String> = emptyList()
    private var isExportAllMode = false

    private val createDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? -> if (uri != null) writeWordsToFile(uri) }

    private val openDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> if (uri != null) importWordsFromFile(uri) }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply theme BEFORE super.onCreate
        preferencesManager = PreferencesManager(this)
        when (preferencesManager.getTheme()) {
            "cyan" -> setTheme(R.style.Theme_AnEn_Cyan)
            "green" -> setTheme(R.style.Theme_AnEn_Green)
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
    }

    private fun setupListeners() {
        switchService.setOnCheckedChangeListener { _, isChecked ->
            preferencesManager.setServiceEnabled(isChecked)
        }

        btnOverlayPermission.setOnClickListener { requestOverlayPermission() }
        btnAccessibilityPermission.setOnClickListener { openAccessibilitySettings() }
        btnSelectApp.setOnClickListener { showAppSelectionDialog() }

        btnDbNormal.setOnClickListener { showNormalDatabaseView() }
        btnDbRaw.setOnClickListener { showRawDatabaseView() }
        btnExport.setOnClickListener { handleExport(exportAll = false) }
        btnImport.setOnClickListener { openDocumentLauncher.launch(arrayOf("text/plain")) }
        btnExportAll.setOnClickListener { handleExport(exportAll = true) }
        btnClearAll.setOnClickListener { showClearDatabaseDialog() }
        btnSettings.setOnClickListener { showSettingsDialog() }
    }

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

    private fun showSettingsDialog() {
        val themes = arrayOf(
            getString(R.string.theme_monochrome),
            getString(R.string.theme_light),
            getString(R.string.theme_green),
            getString(R.string.theme_cyan)
        )
        val themeKeys = arrayOf("monochrome", "light", "green", "cyan")
        val currentThemeKey = preferencesManager.getTheme()
        val currentIndex = themeKeys.indexOf(currentThemeKey)

        AlertDialog.Builder(this)
            .setTitle(R.string.settings_theme)
            .setSingleChoiceItems(themes, currentIndex) { dialog, which ->
                val selected = themeKeys[which]
                if (selected != currentThemeKey) {
                    preferencesManager.setTheme(selected)
                    dialog.dismiss()
                    recreate() // Recreate activity to apply new theme
                } else {
                    dialog.dismiss()
                }
            }
            .show()
    }

    // ==========================================
    // --- DATABASE VIEWING (FIXED DATE HIERARCHY) ---
    // ==========================================

    private fun showNormalDatabaseView() {
        val words = dbHelper.getAllWords()
        if (words.isEmpty()) {
            Toast.makeText(this, R.string.dialog_db_empty, Toast.LENGTH_SHORT).show()
            return
        }

        val sb = SpannableStringBuilder()
        val calendar = Calendar.getInstance()

        var lastYear = -1
        var lastMonth = -1
        var lastDay = -1

        for (word in words) {
            calendar.timeInMillis = word.createdAt
            val year = calendar.get(Calendar.YEAR)
            val month = calendar.get(Calendar.MONTH)
            val day = calendar.get(Calendar.DAY_OF_MONTH)
            val monthName = calendar.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault()) ?: ""

            // 1. Year
            if (year != lastYear) {
                val yearText = "$year\n"
                val start = sb.length
                sb.append(yearText)
                sb.setSpan(RelativeSizeSpan(1.6f), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.setSpan(StyleSpan(Typeface.BOLD), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                lastYear = year
                lastMonth = -1
                lastDay = -1
            }

            // 2. Month
            if (month != lastMonth) {
                val monthText = "  📁 $monthName\n"
                val start = sb.length
                sb.append(monthText)
                sb.setSpan(RelativeSizeSpan(1.3f), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.setSpan(StyleSpan(Typeface.BOLD), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                lastMonth = month
                lastDay = -1
            }

            // 3. Day
            if (day != lastDay) {
                val dayText = "    $day\n"
                val start = sb.length
                sb.append(dayText)
                sb.setSpan(RelativeSizeSpan(1.15f), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.setSpan(StyleSpan(Typeface.BOLD), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                lastDay = day
            }

            // 4. Words (Normal size, separated by newline)
            sb.append("      ${word.word}\n")
        }

        showScrollableDialog(getString(R.string.dialog_db_title), sb, isSelectable = true)
    }

    private fun showRawDatabaseView() {
        val words = dbHelper.getAllWords()
        if (words.isEmpty()) {
            Toast.makeText(this, R.string.dialog_db_empty, Toast.LENGTH_SHORT).show()
            return
        }
        val rawText = words.joinToString("\n") { it.word }
        showScrollableDialog(getString(R.string.dialog_db_raw_title), rawText, isSelectable = true)
    }

    private fun showScrollableDialog(title: String, content: CharSequence, isSelectable: Boolean) {
        val textView = TextView(this).apply {
            text = content
            setPadding(48, 48, 48, 48)
            textSize = 16f
            setTextIsSelectable(isSelectable)
        }

        val scrollView = ScrollView(this).apply { addView(textView) }

        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(scrollView)
            .setPositiveButton(R.string.btn_close, null)
            .show()
    }

    // ==========================================
    // --- PERMISSIONS (UI FIX) ---
    // ==========================================

    private fun updatePermissionButtonsState() {
        // Hide overlay button if granted, show if not
        if (Settings.canDrawOverlays(this)) {
            btnOverlayPermission.visibility = View.GONE
        } else {
            btnOverlayPermission.visibility = View.VISIBLE
        }

        // Hide accessibility button if granted, show if not
        if (isAccessibilityServiceEnabled()) {
            btnAccessibilityPermission.visibility = View.GONE
        } else {
            btnAccessibilityPermission.visibility = View.VISIBLE
        }
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
        val service = "$packageName/${OverlayAccessibilityService::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return enabledServices.split(":").any { it.equals(service, ignoreCase = true) }
    }

    // ==========================================
    // --- EXPORT / IMPORT / APP SELECT ---
    // ==========================================

    private fun handleExport(exportAll: Boolean) {
        val wordsToExport = if (exportAll) dbHelper.getAllWords() else dbHelper.getUnexportedWords()

        if (wordsToExport.isEmpty()) {
            if (!exportAll) Toast.makeText(this, R.string.toast_export_empty, Toast.LENGTH_SHORT).show()
            else Toast.makeText(this, R.string.dialog_db_empty, Toast.LENGTH_SHORT).show()
            return
        }

        pendingExportWords = wordsToExport.map { it.word }
        isExportAllMode = exportAll

        val fileName = if (exportAll) "anen_all_words.txt" else "anen_new_words.txt"
        createDocumentLauncher.launch(fileName)
    }

    private fun writeWordsToFile(uri: Uri) {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri)?.use { outputStream ->
                        val writer = OutputStreamWriter(outputStream)
                        writer.write(pendingExportWords.joinToString("\t"))
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
                        val words = content.split(Regex("[\\t\\n\\s]+")).map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct()
                        for (word in words) { if (dbHelper.addWord(word)) count++ }
                    }
                    count
                }
                Toast.makeText(this@MainActivity, getString(R.string.toast_import_success) + " ($importedCount)", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, R.string.toast_io_error, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showAppSelectionDialog() {
        val pm = packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA).filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
        if (apps.isEmpty()) {
            Toast.makeText(this, R.string.toast_no_apps_found, Toast.LENGTH_SHORT).show()
            return
        }
        val appList = apps.map { Pair<String, String>(pm.getApplicationLabel(it).toString(), it.packageName) }.sortedBy { it.first }
        val appNames = appList.map { it.first }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_select_app_title)
            .setItems(appNames) { _, which ->
                preferencesManager.setTargetPackage(appList[which].second)
                updateTargetAppButton()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun updateTargetAppButton() {
        val targetPackage = preferencesManager.getTargetPackage()
        if (targetPackage != null) {
            try {
                val appInfo = packageManager.getApplicationInfo(targetPackage, 0)
                val appName = packageManager.getApplicationLabel(appInfo).toString()
                btnSelectApp.text = getString(R.string.btn_select_app_selected, appName)
            } catch (_: PackageManager.NameNotFoundException) {
                btnSelectApp.text = getString(R.string.btn_select_app)
            }
        } else {
            btnSelectApp.text = getString(R.string.btn_select_app)
        }
    }

    private fun updateSwitchState() {
        switchService.isChecked = preferencesManager.isServiceEnabled()
    }
}