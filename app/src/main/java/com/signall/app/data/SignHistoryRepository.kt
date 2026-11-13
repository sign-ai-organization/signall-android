package com.signall.app.data

import android.content.Context

interface SignHistoryRepository {
    fun load(): List<String>

    fun record(text: String): List<String>
}

class SharedPrefsSignHistoryRepository(context: Context) : SignHistoryRepository {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun load(): List<String> =
        prefs.getString(KEY_HISTORY, "")
            ?.takeIf { it.isNotEmpty() }
            ?.split(SEPARATOR)
            .orEmpty()

    override fun record(text: String): List<String> {
        val current = load().toMutableList()
        if (text.isNotBlank() && !current.take(TOP_PROTECTED).contains(text)) {
            current.remove(text)
            current.add(0, text)
            while (current.size > MAX_ITEMS) current.removeAt(current.size - 1)
        }
        save(current)
        return current
    }

    private fun save(list: List<String>) {
        prefs.edit().putString(KEY_HISTORY, list.joinToString(SEPARATOR)).apply()
    }

    private companion object {
        const val PREFS_NAME = "signall"
        const val KEY_HISTORY = "history"
        const val SEPARATOR = "\n"
        const val MAX_ITEMS = 20
        const val TOP_PROTECTED = 5
    }
}
