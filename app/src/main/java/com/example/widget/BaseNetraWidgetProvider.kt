package com.example.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
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
        // Link to Central Unit's state flow mapping in WidgetStateAdapter
        // (Implementation details here would map state to views)
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}
