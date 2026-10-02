package com.example.update

import android.content.pm.PackageInstaller
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.example.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data class UpToDate(val versionName: String) : UpdateUiState
    data class Available(val release: GitHubReleaseInfo) : UpdateUiState
    data class Downloading(val release: GitHubReleaseInfo) : UpdateUiState
    data class Error(val message: String) : UpdateUiState
}

data class GitHubReleaseInfo(
    val tagName: String,
    val versionName: String,
    val versionCode: Long,
    val notes: String,
    val apkUrl: String,
    val sha256: String?,
    val sizeBytes: Long = -1L
)

class GitHubReleaseUpdater(context: Context) {
    companion object {
        const val API_URL = "https://api.github.com/repos/prayagideepak-collab/-Battery-Sentinel-Pro-Netra/releases/latest"
        @Volatile private var instance: GitHubReleaseUpdater? = null
        fun shared(context: Context): GitHubReleaseUpdater =
            instance ?: synchronized(this) { instance ?: GitHubReleaseUpdater(context).also { instance = it } }
        private const val PREFS = "netra_release_update_cache"
        private const val CHECK_INTERVAL_MS = 6L * 60L * 60L * 1000L
    }

    private val appContext = context.applicationContext
    private val client = OkHttpClient()
    private val _state = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()
    private var periodicJob: Job? = null

    fun checkNow() {
        if (_state.value is UpdateUiState.Checking || _state.value is UpdateUiState.Downloading) return
        CoroutineScope(Dispatchers.IO).launch { checkInternal() }
    }

    fun startPeriodicChecks(scope: CoroutineScope) {
        periodicJob?.cancel()
        periodicJob = scope.launch {
            while (isActive) {
                checkInternal()
                delay(CHECK_INTERVAL_MS)
            }
        }
    }

    fun stopPeriodicChecks() {
        periodicJob?.cancel()
        periodicJob = null
    }

    fun installLatest(onInstallStarted: () -> Unit = {}, onError: (String) -> Unit = {}) {
        val release = (_state.value as? UpdateUiState.Available)?.release ?: readCache()
        if (release == null) {
            onError("No cached release is available.")
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            _state.value = UpdateUiState.Downloading(release)
            try {
                val apk = downloadApk(release)
                withContext(Dispatchers.Main) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                        !appContext.packageManager.canRequestPackageInstalls()
                    ) {
                        _state.value = UpdateUiState.Available(release)
                        appContext.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:" + appContext.packageName)
                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                        onError("Allow installs from this source, then tap Update again.")
                        return@withContext
                    }
                    stageInstall(apk)
                    onInstallStarted()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    // Keep the update offered (so the user can retry) and tell them exactly why it failed.
                    _state.value = UpdateUiState.Available(release)
                    onError(e.message ?: "Update failed.")
                }
            }
        }
    }

    private fun fetchText(url: String, githubApi: Boolean): String {
        val builder = Request.Builder().url(url)
            .header("User-Agent", "Battery-Sentinel-Pro-Netra/" + BuildConfig.VERSION_NAME)
        if (githubApi) {
            builder.header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2026-03-10")
        }
        return client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("update source answered " + response.code)
            val input = response.body?.byteStream() ?: throw IllegalStateException("update source returned no data")
            input.use { stream ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= 1024 * 1024) { "Release metadata too large." }
                    output.write(buffer, 0, count)
                }
                output.toString("UTF-8")
            }
        }
    }

    private suspend fun checkInternal() = withContext(Dispatchers.IO) {
        _state.value = UpdateUiState.Checking
        val installed = BuildConfig.VERSION_CODE.toLong()
        var apiFailure: String? = null
        var metadata: String? = null
        try {
            metadata = fetchText(API_URL, true)
        } catch (e: Exception) {
            apiFailure = e.message ?: "GitHub API unreachable"
        }
        var candidate: GitHubReleasePolicy.Candidate? = null
        var tag = ""
        var notes = ""
        try {
            if (metadata != null) {
                candidate = GitHubReleasePolicy.parse(metadata, installed)
                if (candidate != null) {
                    val json = JSONObject(metadata)
                    tag = json.getString("tag_name")
                    notes = json.optString("body")
                }
            } else {
                // GitHub API failed (rate limit or network): use the backup source instead of silently doing nothing.
                val backup = fetchText(GitHubReleasePolicy.FALLBACK_URL, false)
                candidate = GitHubReleasePolicy.parseFallback(backup, installed)
                if (candidate != null) {
                    val json = JSONObject(backup)
                    tag = json.getString("tag")
                    notes = json.optString("notes")
                }
            }
        } catch (e: Exception) {
            val cached = readCache()
            _state.value = if (cached != null && cached.versionCode > installed) {
                UpdateUiState.Available(cached)
            } else {
                UpdateUiState.Error(((apiFailure ?: "") + " " + (e.message ?: "backup source failed")).trim())
            }
            return@withContext
        }
        if (candidate == null) {
            _state.value = UpdateUiState.UpToDate(BuildConfig.VERSION_NAME)
            return@withContext
        }
        val release = GitHubReleaseInfo(
            tagName = tag,
            versionName = tag.removePrefix("v"),
            versionCode = candidate.versionCode,
            notes = notes, apkUrl = candidate.url, sha256 = candidate.sha256, sizeBytes = candidate.size
        )
        writeCache(release)
        _state.value = UpdateUiState.Available(release)
    }

    private fun downloadApk(release: GitHubReleaseInfo): File {
        require(release.versionCode > BuildConfig.VERSION_CODE && release.sizeBytes in 1..GitHubReleasePolicy.MAX_APK_BYTES)
        require(Regex("v[0-9]+\\.[0-9]+\\.[0-9]+").matches(release.tagName))
        require(release.apkUrl == "https://github.com/${GitHubReleasePolicy.OWNER}/${GitHubReleasePolicy.REPO}/releases/download/${release.tagName}/app-release.apk")
        val dir = File(appContext.cacheDir, "updates").apply { mkdirs() }
        val target = File(dir, "battery-sentinel-pro-netra-" + release.versionCode + ".apk")
        val request = Request.Builder()
            .url(release.apkUrl)
            .header("User-Agent", "Battery-Sentinel-Pro-Netra/" + BuildConfig.VERSION_NAME)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("APK download failed (" + response.code + ").")
            val body = response.body ?: throw IllegalStateException("APK download returned no data.")
            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= release.sizeBytes && total <= GitHubReleasePolicy.MAX_APK_BYTES) { "APK is too large." }
                        output.write(buffer, 0, count)
                    }
                }
            }
        }
        require(target.length() == release.sizeBytes) { "Incomplete APK download." }
        val expected = release.sha256 ?: throw SecurityException("Release digest missing.")
        require(Regex("[0-9a-fA-F]{64}").matches(expected)) { "Invalid release digest." }
        run {
            if (!sha256(target).equals(expected, ignoreCase = true)) {
                target.delete()
                throw SecurityException("Downloaded APK checksum does not match the GitHub release digest.")
            }
        }
        verifyApk(target, release)
        return target
    }

    @Suppress("DEPRECATION")
    private fun verifyApk(apk: File, release: GitHubReleaseInfo) {
        val flags = if (Build.VERSION.SDK_INT >= 28)
            android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES
            else android.content.pm.PackageManager.GET_SIGNATURES
        val pm = appContext.packageManager
        val archive = pm.getPackageArchiveInfo(apk.path, flags) ?: throw SecurityException("Invalid APK.")
        val current = pm.getPackageInfo(appContext.packageName, flags)
        val code = if (Build.VERSION.SDK_INT >= 28) archive.longVersionCode else archive.versionCode.toLong()
        val left = if (Build.VERSION.SDK_INT >= 28) current.signingInfo?.apkContentsSigners else current.signatures
        val right = if (Build.VERSION.SDK_INT >= 28) archive.signingInfo?.apkContentsSigners else archive.signatures
        require(archive.packageName == appContext.packageName && code == release.versionCode &&
            code > BuildConfig.VERSION_CODE && !left.isNullOrEmpty() && !right.isNullOrEmpty() &&
            left.map { it.toCharsString() }.toSet() == right.map { it.toCharsString() }.toSet()) {
            "APK identity, version or signing certificate does not match."
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(32 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun stageInstall(apk: File) {
        val installer = appContext.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(appContext.packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("base.apk", 0, apk.length()).use { output ->
                    input.copyTo(output)
                    session.fsync(output)
                }
            }
            val intent = Intent(appContext, InstallResultReceiver::class.java).setPackage(appContext.packageName)
            val pendingIntent = PendingIntent.getBroadcast(
                appContext,
                sessionId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            )
            session.commit(pendingIntent.intentSender)
        }
    }

    private fun writeCache(release: GitHubReleaseInfo) {
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("tag", release.tagName)
            .putString("version_name", release.versionName)
            .putLong("version_code", release.versionCode)
            .putString("notes", release.notes)
            .putString("apk_url", release.apkUrl)
            .putString("sha256", release.sha256)
            .putLong("size", release.sizeBytes)
            .apply()
    }

    private fun readCache(): GitHubReleaseInfo? {
        val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val code = prefs.getLong("version_code", -1L)
        val name = prefs.getString("version_name", null) ?: return null
        val url = prefs.getString("apk_url", null) ?: return null
        if (code < 0) return null
        return GitHubReleaseInfo(
            tagName = prefs.getString("tag", name) ?: name,
            versionName = name,
            versionCode = code,
            notes = prefs.getString("notes", "").orEmpty(),
            apkUrl = url,
            sha256 = prefs.getString("sha256", null),
            sizeBytes = prefs.getLong("size", -1L)
        )
    }
}

class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)?.let { confirmation ->
                confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(confirmation)
            }
        }
    }
}
