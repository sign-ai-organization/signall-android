package com.signall.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.signall.app.domain.Character
import com.signall.app.domain.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * La persistencia es lo que sobrevive a cerrar la app: si se pierde el ajuste o
 * el historial se reordena solo, el usuario lo nota al volver a abrir.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RepositoriesTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("signall", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `sin nada guardado se devuelven los defaults`() {
        val repo = SharedPrefsSettingsRepository(context)
        assertEquals(Settings(), repo.load())
    }

    @Test
    fun `los ajustes sobreviven a un guardado y a una lectura nueva`() {
        val repo = SharedPrefsSettingsRepository(context)
        val saved = Settings(
            freeCam = true,
            bg = true,
            character = Character.Pauline,
            speed = 80,
            interfaceSize = 20,
            bold = true,
            contrast = true,
            large = true,
            reduceMotion = true,
            darkTheme = false,
            demoSignFallback = true,
        )
        repo.save(saved)
        assertEquals(saved, SharedPrefsSettingsRepository(context).load())
    }

    @Test
    fun `la velocidad y el tamano se recortan al rango valido`() {
        context.getSharedPreferences("signall", Context.MODE_PRIVATE)
            .edit()
            .putInt("speed", 999)
            .putInt("interface_size", -5)
            .commit()
        val loaded = SharedPrefsSettingsRepository(context).load()
        assertEquals(100, loaded.speed)
        assertEquals(0, loaded.interfaceSize)
    }

    @Test
    fun `un personaje que ya no existe vuelve al default`() {
        context.getSharedPreferences("signall", Context.MODE_PRIVATE)
            .edit()
            .putString("character", "Ximena")
            .commit()
        assertEquals(Character.Juan, SharedPrefsSettingsRepository(context).load().character)
    }

    @Test
    fun `guardar borra la preferencia vieja de modo debug`() {
        val prefs = context.getSharedPreferences("signall", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("debug", true).commit()
        SharedPrefsSettingsRepository(context).save(Settings())
        assertFalse(prefs.contains("debug"))
    }

    @Test
    fun `el historial arranca vacio y guarda la frase mas nueva primero`() {
        val repo = SharedPrefsSignHistoryRepository(context)
        assertTrue(repo.load().isEmpty())
        assertEquals(listOf("primera"), repo.record("primera"))
        assertEquals(listOf("segunda", "primera"), repo.record("segunda"))
        assertEquals(listOf("segunda", "primera"), SharedPrefsSignHistoryRepository(context).load())
    }

    @Test
    fun `una frase en blanco no entra al historial`() {
        val repo = SharedPrefsSignHistoryRepository(context)
        repo.record("   ")
        assertTrue(repo.load().isEmpty())
    }

    @Test
    fun `una frase de las cinco mas nuevas no se reordena`() {
        val repo = SharedPrefsSignHistoryRepository(context)
        repo.record("uno")
        repo.record("dos")
        // "uno" sigue entre las cinco más nuevas: repetirla no la mueve.
        assertEquals(listOf("dos", "uno"), repo.record("uno"))
    }

    @Test
    fun `una frase vieja si vuelve al principio`() {
        val repo = SharedPrefsSignHistoryRepository(context)
        repeat(6) { repo.record("frase $it") }
        assertEquals(
            listOf("frase 5", "frase 4", "frase 3", "frase 2", "frase 1", "frase 0"),
            repo.load(),
        )
        // "frase 0" ya quedó fuera de las cinco protegidas, así que repetirla la
        // sube al principio.
        assertEquals(
            listOf("frase 0", "frase 5", "frase 4", "frase 3", "frase 2", "frase 1"),
            repo.record("frase 0"),
        )
    }
}
