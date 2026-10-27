package com.example.update

import android.content.Context
import java.io.File

/** No APK downloader or installer remains. Eco owns app downloads and updates. */
object AppUpdater {
    const val ECO_PACKAGE = "com.prayagi.netraeco"
    internal val LEGACY_WORK_NAMES = listOf("netra_update_check")
    fun retireLegacyUpdater(context: Context) {
        val c = context.applicationContext
        // Some test/disabled-startup contexts have no WorkManager, hence no persisted worker can run there.
        try {
            val wm = androidx.work.WorkManager.getInstance(c)
            LEGACY_WORK_NAMES.forEach { wm.cancelUniqueWork(it) }
        } catch (e: IllegalStateException) {
            android.util.Log.i("UpdaterRetirement", "WorkManager unavailable; legacy worker itself is inert", e)
        }
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.cancel(7430)
        if (android.os.Build.VERSION.SDK_INT >= 26) nm.deleteNotificationChannel("netra_app_update")
        listOf("netra_release_update_cache", "netra_app_update", "verified_update_artifacts").forEach {
            c.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().apply()
        }
        Thread { File(c.cacheDir, "updates").deleteRecursively() }.start()
    }
    const val MIN_ECO_HANDOFF_VERSION = 59L
    const val ECO_PAGE = "https://prayagi-store-and-services.github.io/netra-eco/app/bspn/"
    const val METADATA_URL = "https://api.github.com/repos/prayagi-store-and-services/-Battery-Sentinel-Pro-Netra/releases/latest"
    data class Newer(val code:Long,val name:String)
    fun installedCode(context:Context):Long {
        val p=context.packageManager.getPackageInfo(context.packageName,0)
        return if(android.os.Build.VERSION.SDK_INT>=28)p.longVersionCode else p.versionCode.toLong()
    }
    fun parseNewer(json:String,installed:Long):Newer? {
        val o=org.json.JSONObject(json)
        val codes=Regex("(?m)^versionCode: ([0-9]+)\\r?$").findAll(o.getString("body")).toList()
        require(codes.size==1)
        val code=codes.single().groupValues[1].toLong();require(code>0)
        val name=o.getString("tag_name").removePrefix("v")
        require(name.length in 1..80 && name.none { it.isISOControl() })
        if(code<=installed)return null
        return Newer(code,name)
    }
    /** Metadata only. Never follows or reads artifact fields, never writes an installer file. */
    internal fun readMetadata(input:java.io.InputStream):ByteArray {
        val out=java.io.ByteArrayOutputStream();val b=ByteArray(4096)
        while(true){val n=input.read(b);if(n<0)break;require(out.size()+n<=131072);out.write(b,0,n)}
        return out.toByteArray()
    }
    private const val CACHE_MS=6*60*60*1000L
    private val metadataLock=Any()
    /** Share launch/settings checks for six hours, including failed-attempt backoff. */
    internal fun cachedNewer(context:Context, installed:Long, now:Long, force:Boolean=false, fetch:()->String):Newer? = synchronized(metadataLock) {
        val p=context.getSharedPreferences("eco_version_metadata",Context.MODE_PRIVATE)
        val attempted=p.getLong("attempted",0)
        val json=p.getString("json",null)
        val successful=p.getLong("successful",0)
        if(!force && attempted>0 && now>=attempted && now-attempted<CACHE_MS) {
            if(json!=null && successful>0 && now>=successful && now-successful<CACHE_MS) return@synchronized parseNewer(json,installed)
            throw java.io.IOException("Version check temporarily unavailable; retry later")
        }
        p.edit().putLong("attempted",now).commit()
        val fresh=fetch()
        parseNewer(fresh,0) // Validate before saving; artifact fields are never used.
        p.edit().putString("json",fresh).putLong("successful",now).commit()
        parseNewer(fresh,installed)
    }
    // Fixed HTTPS API endpoint. Hosting redirects are allowed; no redirect-host allowlist is claimed.
    fun newer(context:Context,force:Boolean=false):Newer? = cachedNewer(context,installedCode(context),System.currentTimeMillis(),force) {
        val connection=java.net.URL(METADATA_URL).openConnection() as javax.net.ssl.HttpsURLConnection
        connection.connectTimeout=10000;connection.readTimeout=10000
        connection.setRequestProperty("Accept","application/vnd.github+json")
        try {
            require(connection.responseCode==200)
            connection.inputStream.use { readMetadata(it) }.toString(Charsets.UTF_8)
        } finally { connection.disconnect() }
    }
    fun openEco(context: Context): Boolean {
        val page=android.net.Uri.parse(ECO_PAGE)
        val compatible=try {
            val info=context.packageManager.getPackageInfo(ECO_PACKAGE,0)
            (if(android.os.Build.VERSION.SDK_INT>=28)info.longVersionCode else info.versionCode.toLong())>=MIN_ECO_HANDOFF_VERSION
        }catch(_:android.content.pm.PackageManager.NameNotFoundException){false}
        if(compatible)try {
            context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,page).setPackage(ECO_PACKAGE).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));return true
        } catch (_: android.content.ActivityNotFoundException) { } catch (_:SecurityException) { }
        return try {
            val browser=android.content.Intent(android.content.Intent.ACTION_VIEW,page).addCategory(android.content.Intent.CATEGORY_BROWSABLE).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            // Generic web capability, not the target app-link resolver list. Android marks
            // browsers that handle all web URIs; target-specific app handlers are not browsers.
            val generic=android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse("https://"))
                .addCategory(android.content.Intent.CATEGORY_BROWSABLE)
            val candidates=context.packageManager.queryIntentActivities(generic,android.content.pm.PackageManager.MATCH_DEFAULT_ONLY or android.content.pm.PackageManager.GET_RESOLVED_FILTER)
            val handler=candidates.firstOrNull {
                it.filter?.let { filter -> filter.hasDataScheme("https") && filter.countDataAuthorities()==0 && filter.countDataPaths()==0 } == true && it.activityInfo.exported &&
                it.activityInfo.packageName!=ECO_PACKAGE && it.activityInfo.packageName!=context.packageName
            } ?: return false
            context.startActivity(browser.setPackage(handler.activityInfo.packageName));true
        } catch (_:android.content.ActivityNotFoundException){false} catch (_:SecurityException){false}
    }
}
