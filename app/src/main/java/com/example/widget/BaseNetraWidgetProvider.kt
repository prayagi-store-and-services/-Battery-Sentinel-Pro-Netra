package com.example.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.widget.RemoteViews
import com.example.NetraApplication
import com.example.R
import com.example.model.NetraCentralState

abstract class BaseNetraWidgetProvider(private val layoutId: Int) : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // Collect state and push update to all active widget instances
        val state = NetraApplication.instance.centralDataCenter.centralState.value
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId, state)
        }
    }

    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        state: NetraCentralState
    ) {
        val views = RemoteViews(context.packageName, layoutId)
        
        // Example mapping: Link specific widget layout to state
        // This would call WidgetStateAdapter for layout-specific data model.
        // E.g., for a simple text-based widget:
        // views.setTextViewText(R.id.widget_some_text_id, state.batteryLevel.toString() + "%")
        
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    // Handle data updates to refresh active widgets
    fun notifyStateChanged(context: Context, state: NetraCentralState) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val provider = ComponentName(context, this::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(provider)
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId, state)
        }
    }
}
