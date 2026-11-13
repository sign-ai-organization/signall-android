package com.signall.app.ui.state

/**
 * One phrase sent during the current app session, shown in the left
 * "Conversación" drawer (spec `CNV-*`).
 *
 * The conversation is session-scoped: it lives in memory in [MainScreenState]
 * and is gone when the process dies. It is intentionally independent from the
 * persisted `TUS FRASES` history (which survives restarts).
 */
data class ConversationTurn(
    val id: Long,
    val text: String,
    val gloss: String,
)
