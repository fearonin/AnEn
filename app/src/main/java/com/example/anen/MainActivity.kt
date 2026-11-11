package com.example.anen

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.net.toUri
import com.example.anen.data.DBHelper
import com.example.anen.service.OverlayAccessibilityService
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial

/**
 * Main screen of the application.
 * Handles service toggling, permission requests, and target app selection.
 */
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

    private lateinit var dbHelper: DBHelper
    private val prefsName = "AnEnPrefs"
    private val keyTargetPackage = "target_package"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        dbHelper = DBHelper(this)
        initViews()
        setupListeners()
        updateTargetAppButton()
    }

    override fun onResume() {
        super.onResume()
        // Check permissions every time the user returns to the app
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
    }

    private fun setupListeners() {
        // Global toggle (State saving to SharedPreferences will be used in Stage 4)
        switchService.setOnCheckedChangeListener { _, _ ->
            // TODO: Implement service toggle logic in Stage 4
        }

        btnOverlayPermission.setOnClickListener { requestOverlayPermission() }
        btnAccessibilityPermission.setOnClickListener { openAccessibilitySettings() }
        btnSelectApp.setOnClickListener { showAppSelectionDialog() }

        // Temporary stubs for Stage 6
        btnDbNormal.setOnClickListener { showComingSoonToast() }
        btnDbRaw.setOnClickListener { showComingSoonToast() }
        btnExport.setOnClickListener { showComingSoonToast() }
        btnImport.setOnClickListener { showComingSoonToast() }
        btnExportAll.setOnClickListener { showComingSoonToast() }
    }

    private fun showComingSoonToast() {
        Toast.makeText(this, R.string.toast_coming_soon, Toast.LENGTH_SHORT).show()
    }

    // --- PERMISSIONS LOGIC ---

    private fun updatePermissionButtonsState() {
        // Overlay Permission Check
        if (Settings.canDrawOverlays(this)) {
            btnOverlayPermission.setText(R.string.overlay_granted)
            btnOverlayPermission.isEnabled = false
        } else {
            btnOverlayPermission.setText(R.string.btn_grant_overlay)
            btnOverlayPermission.isEnabled = true
        }

        // Accessibility Service Check
        if (isAccessibilityServiceEnabled()) {
            btnAccessibilityPermission.setText(R.string.accessibility_granted)
            btnAccessibilityPermission.isEnabled = false
        } else {
            btnAccessibilityPermission.setText(R.string.btn_grant_accessibility)
            btnAccessibilityPermission.isEnabled = true
        }
    }

    private fun requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                "package:$packageName".toUri()
            )
            startActivity(intent)
        }
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
    }

    /**
     * Checks if our specific AccessibilityService is currently enabled in system settings.
     */
    private fun isAccessibilityServiceEnabled(): Boolean {
        val service = "$packageName/${OverlayAccessibilityService::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        return enabledServices.split(":").any { it.equals(service, ignoreCase = true) }
    }

    // --- APP SELECTION LOGIC ---

    private fun showAppSelectionDialog() {
        val pm = packageManager
        // Get all installed apps. We filter out system apps for a cleaner user experience.
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }

        if (apps.isEmpty()) {
            Toast.makeText(this, R.string.toast_no_apps_found, Toast.LENGTH_SHORT).show()
            return
        }

        // Sort apps alphabetically by their label. Explicitly typing Pair to avoid compiler confusion.
        val appList = apps.map {
            val label = pm.getApplicationLabel(it).toString()
            Pair<String, String>(label, it.packageName)
        }.sortedBy { it.first }

        val appNames = appList.map { it.first }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_select_app_title)
            .setItems(appNames) { _, which ->
                val selectedPackage = appList[which].second
                saveTargetPackage(selectedPackage)
                updateTargetAppButton()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveTargetPackage(packageName: String) {
        val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        prefs.edit { putString(keyTargetPackage, packageName) }
    }

    private fun getTargetPackage(): String? {
        val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        return prefs.getString(keyTargetPackage, null)
    }

    private fun updateTargetAppButton() {
        val targetPackage = getTargetPackage()
        if (targetPackage != null) {
            try {
                val appInfo = packageManager.getApplicationInfo(targetPackage, 0)
                val appName = packageManager.getApplicationLabel(appInfo).toString()
                btnSelectApp.text = getString(R.string.btn_select_app_selected, appName)
            } catch (_: PackageManager.NameNotFoundException) {
                // App was uninstalled. Ignored parameter "_" fixes the unused variable warning.
                btnSelectApp.text = getString(R.string.btn_select_app)
            }
        } else {
            btnSelectApp.text = getString(R.string.btn_select_app)
        }
    }
}