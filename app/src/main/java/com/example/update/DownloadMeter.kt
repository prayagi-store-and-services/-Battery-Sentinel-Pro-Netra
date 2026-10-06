package com.example.update

/** Builds the download progress line. The percent only goes up, and the time left never goes up while a download runs. */
class DownloadMeter {
    private val samples = ArrayDeque<Pair<Long, Long>>()
    private var lastEta: Long? = null
    private var lastAt = 0L

    fun line(done: Long, total: Long, nowMs: Long): String {
        samples.addLast(nowMs to done)
        while (samples.size > 2 && nowMs - samples.first().first > 5000L) samples.removeFirst()
        var raw: Long? = null
        if (samples.size >= 2 && total > 0L && done <= total) {
            val (t0, b0) = samples.first()
            val (t1, b1) = samples.last()
            val dt = t1 - t0
            if (dt >= 1000L && b1 > b0) raw = Math.ceil((total - done) / ((b1 - b0) * 1000.0 / dt)).toLong()
        }
        val prev = lastEta
        val shown: Long? = when {
            raw != null && prev == null -> raw
            raw != null && prev != null -> minOf(raw, maxOf(1L, prev - (nowMs - lastAt) / 1000L))
            prev != null -> maxOf(1L, prev - (nowMs - lastAt) / 1000L)
            else -> null
        }
        if (shown != null) { lastEta = shown; lastAt = nowMs }
        val pct = percent(done, total)?.let { "$it% downloaded" } ?: "Unavailable"
        return pct + (if (total > 0L) " - " + mb(done) + " of " + mb(total) else "") + ", time left: " + eta(shown) + " (estimated)"
    }

    companion object {
        fun percent(done: Long, total: Long): Int? = if (total <= 0L || done < 0L || done > total) null else (done * 100 / total).toInt()
        fun fraction(done: Long, total: Long): Float = if (total <= 0L) 0f else (done.toFloat() / total).coerceIn(0f, 1f)
        fun mb(b: Long): String = String.format(java.util.Locale.US, "%.1f MB", b / 1048576.0)
        fun eta(s: Long?): String = when {
            s == null -> "Unavailable"
            s < 60 -> "$s s"
            s < 3600 -> "${s / 60} min ${s % 60} s"
            else -> "${s / 3600} h ${(s % 3600) / 60} min"
        }
    }
}
