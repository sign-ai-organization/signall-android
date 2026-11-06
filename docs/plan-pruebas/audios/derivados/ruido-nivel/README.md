# Los diez audios con ruido, con el nivel igualado

Copia derivada de los diez audios `AU-01-006` … `AU-01-060`, la condición de ruido de H4.
**Los originales no se tocaron**: están intactos en `../../AU-01/` y en `../../16k/`.

## Qué se hizo

Los diez venían **6,3 dB más bajos** que el bloque con ruido del owner, con la misma relación
voz/ruido: 21,9 dB contra 21,8 dB. Esa diferencia era de ganancia de grabación, y es lo único
que se corrige acá: el ruido en sí es otro (televisión a 10 m contra el iPad a 10 cm del owner)
y eso no se toca. Por eso se sube el nivel entero y no se mezcla nada:

- Ganancia aplicada: **+6,3 dB** a los diez por igual (no audio por audio), para conservar
  las diferencias naturales entre tomas.
- Se calculó sobre la copia de 16 kHz, que es la que consume el servicio, con
  `ffmpeg -af volume=6.3dB`.
- Picos después de subirlos: entre −3,8 y −1,0 dBFS. **No satura ninguno.**

## La comparación

Nivel medido en tramas de 100 ms. «Piso» es el percentil 25 (los tramos donde nadie habla)
y «techo» el percentil 95 (los tramos de voz).

| Audio | Piso antes | Piso después | Techo antes | Techo después | Pico después |
|---|---|---|---|---|---|
| `AU-01-006` | -41,9 dB | **-35,6 dB** | -22,1 dB | **-15,8 dB** | -3,5 dB |
| `AU-01-012` | -42,3 dB | **-36,0 dB** | -22,8 dB | **-16,5 dB** | -3,4 dB |
| `AU-01-018` | -45,2 dB | **-38,9 dB** | -23,8 dB | **-17,5 dB** | -3,8 dB |
| `AU-01-024` | -45,1 dB | **-38,8 dB** | -22,5 dB | **-16,2 dB** | -2,6 dB |
| `AU-01-030` | -42,6 dB | **-36,3 dB** | -22,3 dB | **-16,0 dB** | -3,0 dB |
| `AU-01-036` | -43,9 dB | **-37,6 dB** | -21,3 dB | **-15,0 dB** | -1,1 dB |
| `AU-01-042` | -47,3 dB | **-41,0 dB** | -20,4 dB | **-14,1 dB** | -1,3 dB |
| `AU-01-048` | -39,1 dB | **-32,8 dB** | -22,5 dB | **-16,2 dB** | -2,3 dB |
| `AU-01-054` | -41,7 dB | **-35,4 dB** | -17,6 dB | **-11,3 dB** | -1,9 dB |
| `AU-01-060` | -45,5 dB | **-39,2 dB** | -20,3 dB | **-14,0 dB** | -1,0 dB |

| Bloque | Piso | Techo | Relación voz/ruido |
|---|---|---|---|
| Los 10 con ruido del owner (referencia) | -37,2 dB | -15,3 dB | 21,8 dB |
| Los 10 de Sergio, antes | -43,5 dB | -21,6 dB | 21,9 dB |
| Los 10 de Sergio, después | **-37,2 dB** | **-15,3 dB** | **21,9 dB** |

Los dos bloques quedan con el mismo piso y el mismo techo. La comparación entre las dos
condiciones de ruido ya se puede hacer sin corregir nada a mano.

## Qué NO cambia

- **La voz sigue siendo la de Sergio (H4)** y el ruido sigue siendo la televisión a 10 m de
  su cuarto, no el iPad del owner. Subir el nivel no cambia ninguna de las dos cosas: lo que
  iguala es solo el volumen.
- La transcripción no cambia: los diez se volvieron a transcribir después de subirlos y dan
  exactamente el mismo texto que antes.
- Sigue siendo el mismo ruido: subir el nivel entero no cambia la forma del espectro, solo
  su tamaño.

## Qué hay acá

```
ruido-nivel/
  AU-01-006.m4a … AU-01-060.m4a        para escuchar (AAC mono, del WAV de 16 kHz)
  16k/AU-01-006.wav … AU-01-060.wav     lo que consume el servicio
```

Si se aprueban, se reemplazan los de `../../16k/` y se rehace el manifiesto; los `.m4a`
originales de `../../AU-01/` se quedan como están, porque son la toma de verdad.

