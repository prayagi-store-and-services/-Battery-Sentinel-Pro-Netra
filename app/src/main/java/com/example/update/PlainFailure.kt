package com.example.update

import java.io.IOException

/**
 * Turns an exception into one plain sentence for the user.
 * The app's own messages (IllegalStateException or SecurityException written in this updater) are
 * already plain and are kept. Network errors get a fixed sentence instead of the raw system text
 * (for example "Unable to resolve host ..."). Anything else uses the given fallback.
 */
fun plainFailure(e: Throwable, fallback: String): String = when {
    (e is IllegalStateException || e is SecurityException) && !e.message.isNullOrBlank() -> e.message!!
    e is IOException -> "No internet, or the server did not answer. Check your connection and try again."
    else -> fallback
}
