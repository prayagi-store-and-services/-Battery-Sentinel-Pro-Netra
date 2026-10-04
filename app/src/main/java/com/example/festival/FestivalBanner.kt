package com.example.festival

import java.util.Calendar

enum class BannerKind { SORROW, FEST, SOON }

/** accent / accent2 are ARGB colours chosen to match the occasion. */
data class Banner(val kind: BannerKind, val title: String, val text: String, val accent: Long, val accent2: Long)

internal object FestivalBanner {
    private fun themeFor(n: String): Pair<Long, Long> = when {
        Regex("Holi|Dolyatra", RegexOption.IGNORE_CASE).containsMatchIn(n) -> 0xFFF472B6L to 0xFFFACC15L
        Regex("Diwali|Deepavali|Govardhan|Bhai Duj|Naraka", RegexOption.IGNORE_CASE).containsMatchIn(n) -> 0xFFFBBF24L to 0xFFFB923CL
        Regex("Ramzan|Ramadan|Bakrid|Milad|Jamat|\\bId\\b", RegexOption.IGNORE_CASE).containsMatchIn(n) -> 0xFF34D399L to 0xFFA7F3D0L
        Regex("Christmas|Easter|Good Friday|Maundy", RegexOption.IGNORE_CASE).containsMatchIn(n) -> 0xFFF87171L to 0xFF4ADE80L
        Regex("Buddha|Mahavir|Navratri|Durga|Dussehra|Ganesh|Janmashtami|Rama|Shivaratri|Rath|Raksha|Onam|Pongal|Sankranti|Lohri|Ugadi|Gudi|Vaisakhi|Guru|Navami|Ashtami|Saptami|Chhat|Karaka", RegexOption.IGNORE_CASE).containsMatchIn(n) -> 0xFFFB923CL to 0xFFFBBF24L
        Regex("Republic Day", RegexOption.IGNORE_CASE).containsMatchIn(n) -> 0xFFFF9933L to 0xFF22C55EL
        else -> 0xFFA78BFAL to 0xFFF472B6L
    }

    private fun ymd(c: Calendar) = c.get(Calendar.YEAR) * 10000 + (c.get(Calendar.MONTH) + 1) * 100 + c.get(Calendar.DAY_OF_MONTH)

    /** Condolence first, then today's festivals/independence days (India first), then "coming soon" within 3 days. */
    fun pick(today: Calendar, condolences: List<Condolence> = FestivalData.CONDOLENCES): Banner? {
        val t = ymd(today)
        condolences.firstOrNull { t in it.from..it.to }?.let {
            val who = if (it.name.isNotBlank()) it.name + (if (it.country.isNotBlank()) " (" + it.country + ")" else "") + " ke nidhan par shok. " else ""
            return Banner(BannerKind.SORROW, "Shok sandesh", who + it.text, 0xFFCBD5E1L, 0xFF94A3B8L)
        }
        val m = today.get(Calendar.MONTH) + 1
        val d = today.get(Calendar.DAY_OF_MONTH)
        val fests = FestivalData.FEST.filter { it.ymd == t }
        val indep = FestivalData.INDEP.filter { it.month == m && it.day == d }.map { it.country }
        if (fests.isNotEmpty() || indep.isNotEmpty()) {
            val parts = mutableListOf<String>()
            if (fests.isNotEmpty()) parts.add("Aaj: " + fests.joinToString(", ") { it.name + if (it.dateMayShift) " (date may differ by a day)" else "" })
            if (indep.isNotEmpty()) parts.add("Independence Day: " + indep.joinToString(", "))
            val (a, b) = when {
                "India" in indep -> 0xFFFF9933L to 0xFF22C55EL
                else -> themeFor(fests[0].name)
            }
            return Banner(BannerKind.FEST, "Shubhkamnayein", parts.joinToString(" - "), a, b)
        }
        for (k in 1..3) {
            val n = today.clone() as Calendar
            n.add(Calendar.DAY_OF_MONTH, k)
            val y = ymd(n)
            val names = FestivalData.FEST.filter { it.ymd == y }.map { it.name }
            if (names.isNotEmpty()) {
                val (a, b) = themeFor(names[0])
                return Banner(BannerKind.SOON, "Aane wala", names.joinToString(", ") + if (k == 1) " - kal" else " - $k din baad", a, b)
            }
        }
        return null
    }
}
