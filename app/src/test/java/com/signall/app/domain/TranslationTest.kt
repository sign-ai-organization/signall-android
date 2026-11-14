package com.signall.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * El tipo de oración decide las marcas faciales de la escena. Un valor que no se
 * reconoce no puede tumbar la app: cae en [PhraseType.UNKNOWN].
 */
class TranslationTest {

    @Test
    fun `cada tipo del contrato se reconoce por su nombre de cable`() {
        assertEquals(PhraseType.AFFIRMATIVE, PhraseType.from("AFFIRMATIVE"))
        assertEquals(PhraseType.YES_NO_QUESTION, PhraseType.from("YES_NO_QUESTION"))
        assertEquals(PhraseType.CONTENT_QUESTION, PhraseType.from("CONTENT_QUESTION"))
        assertEquals(PhraseType.NEGATIVE, PhraseType.from("NEGATIVE"))
        assertEquals(PhraseType.IMPERATIVE, PhraseType.from("IMPERATIVE"))
    }

    @Test
    fun `un tipo desconocido o ausente degrada a sin clasificar`() {
        assertEquals(PhraseType.UNKNOWN, PhraseType.from(null))
        assertEquals(PhraseType.UNKNOWN, PhraseType.from(""))
        assertEquals(PhraseType.UNKNOWN, PhraseType.from("INTERROGATIVE"))
        assertEquals(PhraseType.UNKNOWN, PhraseType.from("affirmative"))
    }

    @Test
    fun `cada tipo tiene su etiqueta en espanol`() {
        assertEquals("Afirmativa", PhraseType.AFFIRMATIVE.label)
        assertEquals("Pregunta sí/no", PhraseType.YES_NO_QUESTION.label)
        assertEquals("Pregunta de contenido", PhraseType.CONTENT_QUESTION.label)
        assertEquals("Negativa", PhraseType.NEGATIVE.label)
        assertEquals("Orden", PhraseType.IMPERATIVE.label)
        assertEquals("Sin clasificar", PhraseType.UNKNOWN.label)
    }

    @Test
    fun `una glosa conserva su texto, disponibilidad y ruta`() {
        val gloss = TranslationGloss("HOLA", available = true, glbPath = "signs/0000_0000_0000.glb")
        assertEquals("HOLA", gloss.text)
        assertEquals(true, gloss.available)
        assertEquals("signs/0000_0000_0000.glb", gloss.glbPath)
    }
}
