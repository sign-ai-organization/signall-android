package com.signai.webview.ui.state

/**
 * Estados de la interfaz, tal como los define la tabla `tab:estados-ui` del SRS
 * (`main.tex:726-773`): **Inactivo, Grabando, Texto listo, Procesando,
 * Reproduciendo, Error, Desconectado**. Son siete y son esos.
 *
 * Antes eran nueve, con nombres que la app se había hecho sola:[Listo para
 * traducir, Dictando, Escuchando, Procesando, Traduciendo, Reproduciendo
 * señas, Reproducción completada, Conectando con el servidor, Sin conexión con
 * el servidor. Tres de los nueve no existían en el SRS —"Traduciendo",
 * "Conectando" y "Reproducción completada"— y eran justo donde la app pasa
 * casi todo el tiempo esperando; y faltaban dos de los siete, *Error* y
 * *Desconectado*, que son los que explican lo que le pasa al usuario.
 *
 * Tres fusiones, todas deliberadas:
 *
 * - **Grabando** es uno solo. El SRS no distingue dictado de TR en la lista, así
 *   que la distinción vive en la etiqueta —"Dictando..." o "Escuchando..."— y no
 *   en el estado: el enum tiene los siete exactos y el usuario sigue viendo en
 *   qué modo está. Ver [MainScreenState.stateLabel].
 * - **Procesando** es esperar la transcripción y esperar las glosas. Para quien
 *   espera, es la misma espera.
 * - **Desconectado** es conectar y no conectar. Consecuencia conocida y
 *   aceptada: cada arranque muestra "Sin conexión con el servidor" durante el
 *   instante que tarda el socket, donde antes decía "Conectando con el
 *   servidor". El propio estado ya dice que el sistema intenta reconectarse.
 *
 * Lo que se sacó y **no** se tiró: "Reproducción completada" era un estado y
 * decoraba la interfaz. Ahora es el flag [MainScreenState.doneVisible], que es lo
 * mismo que ya se hacía con `dictationReadyToSend`.
 */
enum class PlaybackState(val label: String) {
    /** No hay nada en curso: se espera que el usuario escriba o hable. */
    Inactivo("Listo para traducir"),

    /**
     * 🎤 Dictado o ◢◢ en vivo: el micrófono está capturing.
     *
     * La etiqueta de este estado la pone [MainScreenState.stateLabel], porque
     * depende del modo: acá [label] solo sirve como valor por defecto.
     */
    Grabando("Dictando..."),

    /**
     * Hay una frase en el campo esperando que el usuario pulse Traducir
     * (`main.tex:746`). Es el tercer estado de RF-UI-01 y el que el SRS llama
     * *Texto listo*.
     */
    TextoListo("Texto listo"),

    /** El backend está trabajando: transcripción del audio o glosas de la frase. */
    Procesando("Procesando..."),

    /** El avatar está representando señas. */
    Reproduciendo("Reproduciendo señas..."),

    /** Algo falló y la interfaz lo dice como estado, no como un mensaje de 3 s. */
    Error("Error"),

    /**
     * Se perdió la sesión y se está reconectando (`main.tex:761-772`).
     *
     * El SRS dice que la reconexión no tiene límite de intentos y espera de forma
     * creciente hasta un máximo de 5 s, así que este estado es normal y no
     * necesariamente termina: la app nunca queda esperando que el usuario la
     * reinicie.
     */
    Desconectado("Sin conexión con el servidor"),
}