package com.wordpilot.assistant.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.TileService
import android.util.Log
import android.widget.RemoteViews
import android.widget.Toast
import com.wordpilot.assistant.R
import com.wordpilot.assistant.overlay.WordPilotAccessibilityService
import com.wordpilot.assistant.overlay.WordPilotTileService

class WordPilotWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        for (widgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, widgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TOGGLE_WORDPILOT) {
            handleToggle(context)
        }
    }

    private fun handleToggle(context: Context) {
        val isCurrentlyActive = WordPilotAccessibilityService.instance != null

        if (hasWriteSecureSettings(context)) {
            val newState = !isCurrentlyActive
            val success = setAccessibilityServiceEnabledDirectly(context, newState)
            if (success) {
                updateAllWidgets(context, isActiveOverride = newState)
                val msg = if (newState) "Hoverpen Active — Ready when typing" else "Hoverpen Paused"
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    try {
                        TileService.requestListeningState(
                            context,
                            ComponentName(context, WordPilotTileService::class.java)
                        )
                    } catch (e: SecurityException) {
                        Log.w(TAG, "Security exception updating QS tile", e)
                    } catch (e: IllegalStateException) {
                        Log.w(TAG, "State exception updating QS tile", e)
                    }
                }
                return
            }
        }

        // Fallback if WRITE_SECURE_SETTINGS is not granted
        if (isCurrentlyActive) {
            WordPilotAccessibilityService.instance?.disableSelf()
            updateAllWidgets(context, isActiveOverride = false)
            Toast.makeText(context, "Hoverpen Paused for Banking", Toast.LENGTH_SHORT).show()
        } else {
            val settingsIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            context.startActivity(settingsIntent)
            Toast.makeText(context, "Enable Hoverpen in Accessibility Settings", Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        private const val TAG = "WordPilotWidget"
        const val ACTION_TOGGLE_WORDPILOT = "com.wordpilot.assistant.widget.ACTION_TOGGLE"

        fun updateAllWidgets(context: Context, isActiveOverride: Boolean? = null) {
            val appWidgetManager = AppWidgetManager.getInstance(context) ?: return
            val componentName = ComponentName(context, WordPilotWidgetProvider::class.java)
            val widgetIds = appWidgetManager.getAppWidgetIds(componentName)
            if (widgetIds == null || widgetIds.isEmpty()) return

            for (widgetId in widgetIds) {
                updateAppWidget(context, appWidgetManager, widgetId, isActiveOverride)
            }
        }

        private fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            isActiveOverride: Boolean? = null
        ) {
            val isActive = isActiveOverride ?: (WordPilotAccessibilityService.instance != null)

            val views = RemoteViews(context.packageName, R.layout.widget_wordpilot_toggle).apply {
                // Background circle state
                setInt(
                    R.id.widget_circle_container,
                    "setBackgroundResource",
                    if (isActive) R.drawable.bg_widget_active else R.drawable.bg_widget_inactive
                )

                // Status dot indicator
                setImageViewResource(
                    R.id.widget_status_dot,
                    if (isActive) R.drawable.ic_widget_dot_active else R.drawable.ic_widget_dot_inactive
                )

                // PendingIntent for 1-tap direct toggle
                val toggleIntent = Intent(context, WordPilotWidgetProvider::class.java).apply {
                    action = ACTION_TOGGLE_WORDPILOT
                }
                val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
                val pendingIntent = PendingIntent.getBroadcast(context, appWidgetId, toggleIntent, flags)
                setOnClickPendingIntent(R.id.widget_root, pendingIntent)
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun hasWriteSecureSettings(context: Context): Boolean {
            return context.checkCallingOrSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) ==
                PackageManager.PERMISSION_GRANTED
        }

        private fun setAccessibilityServiceEnabledDirectly(context: Context, enable: Boolean): Boolean {
            val serviceComponent = ComponentName(context, WordPilotAccessibilityService::class.java).flattenToString()
            val resolver = context.contentResolver

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
                Log.w(TAG, "Security exception writing secure settings from widget", e)
                false
            } catch (e: IllegalStateException) {
                Log.w(TAG, "State exception writing secure settings from widget", e)
                false
            }
        }
    }
}
