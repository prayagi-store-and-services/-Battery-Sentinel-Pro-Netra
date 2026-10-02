package com.example.update

import java.net.URI
import org.json.JSONObject

/** Fail closed: only stable releases and a digest-bearing APK from this repository. */
internal object GitHubReleasePolicy {
    const val OWNER = "prayagideepak-collab"
    const val REPO = "-Battery-Sentinel-Pro-Netra"
    const val API_URL = "https://api.github.com/repos/$OWNER/$REPO/releases/latest"
    const val RELEASES_URL = "https://github.com/$OWNER/$REPO/releases"
    /** No-API backup source (GitHub API allows only 60 anonymous requests/hour per IP). Published from docs/latest.json. */
    const val FALLBACK_URL = "https://prayagideepak-collab.github.io/$REPO/latest.json"
    const val MAX_APK_BYTES = 200L * 1024 * 1024
    private val versionTag = Regex("v[0-9]+\\.[0-9]+\\.[0-9]+")
    private val digestPattern = Regex("sha256:([0-9a-fA-F]{64})")

    data class Candidate(val versionCode: Long, val url: String, val sha256: String, val size: Long)

    fun parse(json: String, installedVersion: Long): Candidate? {
        return try {
            val release = JSONObject(json)
            if (release.optBoolean("draft", true) || release.optBoolean("prerelease", true)) return null
            val tag = release.optString("tag_name")
            if (!versionTag.matches(tag)) return null
            val version = Regex("(?m)^versionCode\\s*[:=]\\s*([0-9]+)\\s*$")
                .find(release.optString("body"))?.groupValues?.get(1)?.toLongOrNull() ?: return null
            if (version <= installedVersion || version > Int.MAX_VALUE) return null
            val assets = release.optJSONArray("assets") ?: return null
            val matches = mutableListOf<Candidate>()
            for (index in 0 until assets.length()) {
                val asset = assets.optJSONObject(index) ?: continue
                if (asset.optString("name") != "app-release.apk") continue
                val size = asset.optLong("size", -1)
                if (size !in 1..MAX_APK_BYTES) continue
                val digest = digestPattern.matchEntire(asset.optString("digest"))?.groupValues?.get(1) ?: continue
                val url = asset.optString("browser_download_url")
                val uri = URI(url)
                if (uri.scheme != "https" || uri.host != "github.com" || uri.port != -1 ||
                    uri.userInfo != null || uri.fragment != null || uri.query != null ||
                    uri.rawPath != "/$OWNER/$REPO/releases/download/$tag/app-release.apk") continue
                matches.add(Candidate(version, url, digest.lowercase(), size))
            }
            matches.singleOrNull()
        } catch (_: Exception) { null }
    }

    /** Parses docs/latest.json: {"tag","versionCode","sha256","size","notes"}. Same strict rules as [parse]. */
    fun parseFallback(json: String, installedVersion: Long): Candidate? {
        return try {
            val o = JSONObject(json)
            val tag = o.optString("tag")
            if (!versionTag.matches(tag)) return null
            val version = o.optLong("versionCode", -1)
            if (version <= installedVersion || version > Int.MAX_VALUE) return null
            val size = o.optLong("size", -1)
            if (size !in 1..MAX_APK_BYTES) return null
            val sha = o.optString("sha256")
            if (!Regex("[0-9a-fA-F]{64}").matches(sha)) return null
            Candidate(version, "https://github.com/$OWNER/$REPO/releases/download/$tag/app-release.apk", sha.lowercase(), size)
        } catch (_: Exception) { null }
    }

    /** Must also pass PackageManager's package name/signing-certificate check before staging. */
    fun canStage(candidate: Candidate, actualVersion: Long, actualSize: Long, actualSha256: String,
                 packageMatches: Boolean, signingMatches: Boolean, installedVersion: Long): Boolean =
        candidate.versionCode > installedVersion && candidate.versionCode == actualVersion &&
            candidate.size == actualSize && candidate.sha256.equals(actualSha256, ignoreCase = true) &&
            packageMatches && signingMatches
}
