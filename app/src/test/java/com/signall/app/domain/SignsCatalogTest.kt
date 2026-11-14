package com.signall.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El catálogo es el contrato de nombres entre el backend y la escena: el backend
 * manda `signs/<id>.glb` y la escena lo resuelve por nombre. Un cambio en estos
 * métodos rompe la representación sin que ningún compilador se entere.
 */
class SignsCatalogTest {

    @Test
    fun `las 50 glosas de LSC50 tienen su clip`() {
        assertEquals(50, SignsCatalog.available.size)
        assertEquals(50, SignsCatalog.available.toSet().size)
    }

    @Test
    fun `los identificadores son los del catalogo del backend`() {
        assertTrue(SignsCatalog.available.first() == "0000_0000_0000")
        assertTrue(SignsCatalog.available.last() == "0049_0000_0000")
    }

    @Test
    fun `la sena por defecto es la primera del catalogo`() {
        assertEquals(SignsCatalog.available.first(), SignsCatalog.DEFAULT)
    }

    @Test
    fun `assetUrl arma la ruta del contrato`() {
        assertEquals("signs/0007_0000_0000.glb", SignsCatalog.assetUrl("0007_0000_0000"))
    }

    @Test
    fun `localAssetOrNull acepta una sena que existe`() {
        val id = SignsCatalog.available[3]
        assertEquals("signs/$id.glb", SignsCatalog.localAssetOrNull("  signs/$id.glb  "))
    }

    @Test
    fun `localAssetOrNull rechaza lo que no es una ruta propia`() {
        assertNull(SignsCatalog.localAssetOrNull(""))
        assertNull(SignsCatalog.localAssetOrNull("https://ejemplo.com/signs/0000_0000_0000.glb"))
        assertNull(SignsCatalog.localAssetOrNull("signs/../secreto.glb"))
        assertNull(SignsCatalog.localAssetOrNull("signs//0000_0000_0000.glb"))
        assertNull(SignsCatalog.localAssetOrNull("signs/0000_0000_0000.json"))
        assertNull(SignsCatalog.localAssetOrNull("signs/no-existe.glb"))
    }

    @Test
    fun `isOwnAssetPath reconoce la ruta aunque la sena no este`() {
        assertTrue(SignsCatalog.isOwnAssetPath("signs/9999_0000_0000.glb"))
        assertFalse(SignsCatalog.isOwnAssetPath("https://ejemplo.com/a.glb"))
        assertFalse(SignsCatalog.isOwnAssetPath("signs/../a.glb"))
        assertFalse(SignsCatalog.isOwnAssetPath("signs//a.glb"))
        assertFalse(SignsCatalog.isOwnAssetPath("signs/a.json"))
    }
}
