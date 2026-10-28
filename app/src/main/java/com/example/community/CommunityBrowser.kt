package com.example.community

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri

/** Resolve browsers, never an app that only claims this website's verified links. */
object CommunityBrowser {
    internal fun candidates(url: String, ownPackage: String, resolved: List<ResolveInfo>): List<Intent> =
        resolved.filter { it.activityInfo != null && it.activityInfo.packageName != ownPackage }
            .distinctBy { it.activityInfo.packageName }
            .map { Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)
                .setComponent(ComponentName(it.activityInfo.packageName, it.activityInfo.name)) }
    fun open(context: Context, url: String) {
        val uri = Uri.parse(url)
        require(uri.scheme == "https")
        val probe = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_BROWSER)
        val options = candidates(url, context.packageName,
            context.packageManager.queryIntentActivities(probe, PackageManager.MATCH_ALL))
        check(options.isNotEmpty()) { "No browser available" }
        val intent = if (options.size == 1) options.first() else Intent.createChooser(options.first(), "Open in browser")
            .putExtra(Intent.EXTRA_INITIAL_INTENTS, options.drop(1).toTypedArray())
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
