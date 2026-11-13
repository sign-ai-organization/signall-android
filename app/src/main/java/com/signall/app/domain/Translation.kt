package com.signall.app.domain

/**
 * Translation result coming from `../signall-backend`. Mirrors the Python
 * `PhraseType` / `Gloss` / `ValidatedSegment` of `app/domain/models.py`; the
 * wire's snake_case keys are mapped to camelCase here.
 */

/**
 * Sentence type, which drives the non-manual markers (RF-AVT-03). The backend
 * always sends one of the five real values, but an unknown one must not crash
 * the app, so it degrades to [UNKNOWN].
 */
enum class PhraseType(val wire: String, val label: String) {
    AFFIRMATIVE("AFFIRMATIVE", "Afirmativa"),
    YES_NO_QUESTION("YES_NO_QUESTION", "Pregunta sí/no"),
    CONTENT_QUESTION("CONTENT_QUESTION", "Pregunta de contenido"),
    NEGATIVE("NEGATIVE", "Negativa"),
    IMPERATIVE("IMPERATIVE", "Orden"),
    UNKNOWN("", "Sin clasificar"),
    ;

    companion object {
        fun from(wire: String?): PhraseType = entries.firstOrNull { it.wire == wire } ?: UNKNOWN
    }
}

/**
 * One gloss: its text, whether the backend's catalog knows it, and the path of
 * its animation. `glbPath` is empty when [available] is false.
 */
data class TranslationGloss(
    val text: String,
    val available: Boolean,
    val glbPath: String,
)

/** A phrase split into segments. The backend returns exactly one today. */
data class TranslationSegment(
    val type: PhraseType,
    val glosses: List<TranslationGloss>,
)
