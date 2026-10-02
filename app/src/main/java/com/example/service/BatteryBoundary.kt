package com.example.service

/**
 * Returns [level] when the battery has just reached a multiple of 5 percent, otherwise null.
 *
 * The value is the exact level: 75 -> 74 returns null (it never rounds down to 70), and 71 -> 70
 * returns 70. [lastSeen] is the previous reading; null means no baseline yet, so nothing is announced.
 * Repeated readings of the same level do not announce twice.
 */
fun reachedFivePercentLevel(level: Int, lastSeen: Int?): Int? =
    if (level in 0..100 && level % 5 == 0 && lastSeen != null && lastSeen != level) level else null
