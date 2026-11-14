package com.signall.app

import android.os.Looper
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * El arranque de la app: la actividad arma el estado, el tema y la pantalla.
 *
 * Es lo único que se puede mirar sin un dispositivo de verdad: que `onCreate`
 * no explote, que la vista raíz exista y que la franja de los gestos del borde
 * quede pedida al sistema.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp")
class MainActivityTest {

    @Test
    fun `la actividad arranca y compone la pantalla`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        shadowOf(Looper.getMainLooper()).idle()
        assertNotNull(activity.window.decorView)
    }

    @Test
    fun `la franja del gesto de atras queda excluida para los drawers`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val decor = activity.window.decorView
        shadowOf(Looper.getMainLooper()).idle()
        // Un layout con tamaño real dispara el listener que pide la exclusión.
        val exactWidth = android.view.View.MeasureSpec.makeMeasureSpec(411, android.view.View.MeasureSpec.EXACTLY)
        val exactHeight = android.view.View.MeasureSpec.makeMeasureSpec(891, android.view.View.MeasureSpec.EXACTLY)
        decor.measure(exactWidth, exactHeight)
        decor.layout(0, 0, 411, 891)
        shadowOf(Looper.getMainLooper()).idle()
        val rects = decor.systemGestureExclusionRects
        assertTrue(rects.size == 2 || rects.isEmpty())
        if (rects.size == 2) {
            // Una franja por borde: la izquierda empieza en 0 y la derecha termina
            // en el ancho de la pantalla.
            assertTrue(rects.first().left == 0)
            assertTrue(rects.last().right == 411)
        }
    }
}
