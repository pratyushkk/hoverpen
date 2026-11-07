package com.wordpilot.assistant.overlay

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.N)
class WordPilotTileService : TileService() {

    companion object {
        private const val TAG = "WordPilotTile"
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val isCurrentlyActive = WordPilotAccessibilityService.instance != null

        if (hasWriteSecureSettings()) {
            // Direct 1-tap hardware toggle without navigating away from current app
            val newState = !isCurrentlyActive
            val success = setAccessibilityServiceEnabledDirectly(newState)
            if (success) {
                updateTileState(isActive = newState)
                val msg = if (newState) "Hoverpen Active — Ready when typing" else "Hoverpen Paused"
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                com.wordpilot.assistant.widget.WordPilotWidgetProvider.updateAllWidgets(this, newState)
                return
            }
        }

        // Standard OS fallback if WRITE_SECURE_SETTINGS is not granted
        if (isCurrentlyActive) {
            WordPilotAccessibilityService.instance?.disableSelf()
            updateTileState(isActive = false)
            com.wordpilot.assistant.widget.WordPilotWidgetProvider.updateAllWidgets(this, false)
            Toast.makeText(this, "Hoverpen Paused for Banking", Toast.LENGTH_SHORT).show()
        } else {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pendingIntent = PendingIntent.getActivity(
                    this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                startActivityAndCollapse(pendingIntent)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        }
    }

    private fun hasWriteSecureSettings(): Boolean {
        return checkCallingOrSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED
    }

    private fun setAccessibilityServiceEnabledDirectly(enable: Boolean): Boolean {
        val serviceComponent = ComponentName(this, WordPilotAccessibilityService::class.java).flattenToString()
        val resolver = contentResolver

        return try {
            val enabledServices = Settings.Secure.getString(
                resolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""

            val servicesList = enabledServices.split(":").filter { it.isNotBlank() }.toMutableSet()

            if (enable) {
                servicesList.add(serviceComponent)
            } else {
                servicesList.remove(serviceComponent)
            }

            val updatedServices = servicesList.joinToString(":")

            Settings.Secure.putString(
                resolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                updatedServices
            )
            Settings.Secure.putString(
                resolver,
                Settings.Secure.ACCESSIBILITY_ENABLED,
                if (servicesList.isNotEmpty()) "1" else "0"
            )
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "Security error writing secure settings directly", e)
            false
        } catch (e: IllegalStateException) {
            Log.e(TAG, "State error writing secure settings directly", e)
            false
        }
    }

    private fun updateTileState(isActive: Boolean = WordPilotAccessibilityService.instance != null) {
        val tile = qsTile ?: return
        tile.state = if (isActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = if (isActive) "Hoverpen Active" else "Hoverpen Paused"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (isActive) "Tap to pause for banking" else "Tap to enable"
        }
        tile.updateTile()
    }
}
