# Medición de los marcadores faciales (morph targets)

Generado por `tools/measure_face_markers.py` el 2026-11-04 sobre los GLB empaquetados de la app (`models/juan.glb` y `models/pauline.glb`).

Cómo se mide: cada target de malla trae el desplazamiento de sus vértices a peso 1 (mm). El clip dice cuánto pesa cada target en cada momento. `peso × alcance` es cuánto se mueve la cara de verdad. El **Δ contra el clip base** es lo que la variante agrega sobre la cara que ya se ve hoy, comparando en tiempo normalizado. El parpadeo (`eyeBlink`) se excluye del Δ porque está en todas las variantes. `≈px` usa el encuadre por defecto (1 mm ≈ 1,26 px en un canvas de 2400 px): es una cuenta, no una captura.

## Juan

| Malla | Targets | Faciales | Alcance máx. (mm) |
|---|---:|---:|---:|
| Female_Body_Default.001 | 31 | 29 | 15.76 |
| Mesh.008 | 29 | 29 | 9.44 |

### Qué hace cada variante

Pesos sostenidos del clip (los mismos en las 50 señas; ver abajo). Solo targets con peso ≥ 0,10. Entre paréntesis, el desplazamiento en mm con ese peso.

| Variante | Targets que mueve (peso → mm) |
|---|---|
| `_alegria` | `mouthSmile_L` 0.75 → 7.28, `mouthSmile_R` 0.75 → 6.89, `cheekSquint_L` 0.5 → 3.71, `cheekSquint_R` 0.5 → 3.71, `mouthDimple_L` 0.2 → 1.46, `mouthDimple_R` 0.2 → 1.45, `eyeSquint_L` 0.25 → 0.73, `eyeSquint_R` 0.25 → 0.73 |
| `_tristeza` | `browInnerUp` 0.75 → 6.23, `mouthFrown_R` 0.65 → 2.75, `mouthFrown_L` 0.65 → 2.44, `mouthShrugLower` 0.3 → 2.44, `browDown_L` 0.15 → 0.78, `browDown_R` 0.15 → 0.78, `eyeSquint_L` 0.1 → 0.29, `eyeSquint_R` 0.1 → 0.29 |
| `_sorpresa` | `browInnerUp` 0.85 → 7.07, `browOuterUp_L` 0.85 → 6.39, `browOuterUp_R` 0.85 → 6.39, `mouthFunnel` 0.2 → 2.5, `eyeWide_L` 0.7 → 1.95, `eyeWide_R` 0.7 → 1.95, `mouthLowerDown_L` 0.25 → 1.42, `mouthLowerDown_R` 0.25 → 1.42 |
| `_pregunta` | `browInnerUp` 1 → 8.31, `browOuterUp_L` 1 → 7.51, `browOuterUp_R` 1 → 7.51, `mouthPress_L` 0.3 → 2.89, `mouthPress_R` 0.3 → 2.89, `mouthRollLower` 0.15 → 2.36, `eyeWide_L` 0.55 → 1.53, `eyeWide_R` 0.55 → 1.53 (+1 más) |
| `_negacion` | `mouthShrugLower` 0.45 → 3.66, `mouthPress_L` 0.35 → 3.37, `mouthPress_R` 0.35 → 3.37, `browDown_L` 0.45 → 2.34, `browDown_R` 0.45 → 2.34, `mouthFrown_R` 0.55 → 2.33, `mouthFrown_L` 0.55 → 2.06, `noseSneer_L` 0.15 → 0.94 (+4 más) |

### Δ contra el clip base, por variante

Mediana de las 50 señas; entre paréntesis, mínimo y máximo. «Δ RMS» mide el parecido de toda la curva, no solo el pico.

| Variante | Δ pico (mm) | ≈px | Δ RMS (mm) | Cambia durante la seña (mm) | Target que más mueve | Misma cara en las 50 señas |
|---|---:|---:|---:|---:|---|---|
| `_alegria` | 7.28 (7.28–7.28) | 9.2 | 2.22 | 0.00 | `mouthSmile_L` (peso 0.75) | sí |
| `_tristeza` | 6.24 (6.24–6.24) | 7.9 | 1.49 | 0.00 | `browInnerUp` (peso 0.75) | sí |
| `_sorpresa` | 7.07 (7.07–7.07) | 8.9 | 2.35 | 0.00 | `browInnerUp` (peso 0.85) | sí |
| `_pregunta` | 8.31 (8.31–8.31) | 10.5 | 2.79 | 0.00 | `browInnerUp` (peso 1) | sí |
| `_negacion` | 3.66 (3.66–3.66) | 4.6 | 1.49 | 0.00 | `mouthShrugLower` (peso 0.45) | sí |

### Distinción entre variantes

Diferencia de las curvas completas. `Δ pico` es el target que más difiere; `Δ RMS` es el parecido de la cara entera. Regiones: dónde está la mayor diferencia.

| Par | Δ pico medio (mm) | Δ RMS medio (mm) | Δ pico mínimo (mm) | Región que más difiere |
|---|---:|---:|---:|---|
| `alegria` vs `_tristeza` | 7.28 | 1.92 | 7.28 | boca (7.28 mm) |
| `alegria` vs `_sorpresa` | 7.28 | 2.38 | 7.28 | boca (7.28 mm) |
| `alegria` vs `_pregunta` | 8.31 | 2.61 | 8.31 | cejas (8.31 mm) |
| `alegria` vs `_negacion` | 7.28 | 1.92 | 7.28 | boca (7.28 mm) |
| `tristeza` vs `_sorpresa` | 6.39 | 1.60 | 6.39 | cejas (6.39 mm) |
| `tristeza` vs `_pregunta` | 7.51 | 1.85 | 7.51 | cejas (7.51 mm) |
| `tristeza` vs `_negacion` | 5.40 | 1.07 | 5.40 | cejas (5.40 mm) |
| `sorpresa` vs `_pregunta` | 2.89 | 0.86 | 2.89 | boca (2.89 mm) |
| `sorpresa` vs `_negacion` | 6.39 | 2.00 | 6.39 | cejas (6.39 mm) |
| `pregunta` vs `_negacion` | 7.51 | 2.10 | 7.51 | cejas (7.51 mm) |

### Por seña: Δ pico contra el clip base (mm)

Cada celda es el máximo desplazamiento facial que agrega esa variante sobre el clip base de la misma seña. Si una columna sale idéntica en las 50 filas, no es un error de la tabla: la variante es la misma cara en todas las señas.

| Seña | `_alegria` | `_tristeza` | `_sorpresa` | `_pregunta` | `_negacion` |
|---|---:|---:|---:|---:|---:|
| 0000 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0001 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0002 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0003 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0004 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0005 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0006 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0007 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0008 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0009 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0010 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0011 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0012 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0013 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0014 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0015 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0016 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0017 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0018 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0019 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0020 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0021 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0022 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0023 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0024 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0025 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0026 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0027 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0028 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0029 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0030 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0031 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0032 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0033 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0034 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0035 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0036 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0037 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0038 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0039 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0040 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0041 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0042 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0043 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0044 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0045 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0046 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0047 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0048 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0049 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |

### Huesos: base contra variantes

Ninguna diferencia: los 250 clips variantes tienen los mismos canales de hueso y los mismos valores que su base (tolerancia 0,01° / 0,01 mm). **Las variantes mueven la cara y nada más**: no agregan cabeza, tronco ni manos.


## Pauline

| Malla | Targets | Faciales | Alcance máx. (mm) |
|---|---:|---:|---:|
| Mesh.017 | 29 | 29 | 7.90 |
| Female_Body_Default.005 | 31 | 29 | 15.76 |

### Qué hace cada variante

Pesos sostenidos del clip (los mismos en las 50 señas; ver abajo). Solo targets con peso ≥ 0,10. Entre paréntesis, el desplazamiento en mm con ese peso.

| Variante | Targets que mueve (peso → mm) |
|---|---|
| `_alegria` | `mouthSmile_L` 0.75 → 7.28, `mouthSmile_R` 0.75 → 6.89, `cheekSquint_L` 0.5 → 3.71, `cheekSquint_R` 0.5 → 3.71, `mouthDimple_L` 0.2 → 1.46, `mouthDimple_R` 0.2 → 1.45, `eyeSquint_L` 0.25 → 0.73, `eyeSquint_R` 0.25 → 0.73 |
| `_tristeza` | `browInnerUp` 0.75 → 6.23, `mouthFrown_R` 0.65 → 2.75, `mouthFrown_L` 0.65 → 2.44, `mouthShrugLower` 0.3 → 2.44, `browDown_L` 0.15 → 0.78, `browDown_R` 0.15 → 0.78, `eyeSquint_L` 0.1 → 0.29, `eyeSquint_R` 0.1 → 0.29 |
| `_sorpresa` | `browInnerUp` 0.85 → 7.07, `browOuterUp_R` 0.85 → 6.39, `browOuterUp_L` 0.85 → 6.39, `mouthFunnel` 0.2 → 2.5, `eyeWide_L` 0.7 → 1.95, `eyeWide_R` 0.7 → 1.95, `mouthLowerDown_L` 0.25 → 1.42, `mouthLowerDown_R` 0.25 → 1.42 |
| `_pregunta` | `browInnerUp` 1 → 8.31, `browOuterUp_R` 1 → 7.51, `browOuterUp_L` 1 → 7.51, `mouthPress_L` 0.3 → 2.89, `mouthPress_R` 0.3 → 2.89, `mouthRollLower` 0.15 → 2.36, `eyeWide_L` 0.55 → 1.53, `eyeWide_R` 0.55 → 1.53 (+1 más) |
| `_negacion` | `mouthShrugLower` 0.45 → 3.66, `mouthPress_L` 0.35 → 3.37, `mouthPress_R` 0.35 → 3.37, `browDown_L` 0.45 → 2.34, `browDown_R` 0.45 → 2.34, `mouthFrown_R` 0.55 → 2.33, `mouthFrown_L` 0.55 → 2.06, `noseSneer_L` 0.15 → 0.94 (+4 más) |

### Δ contra el clip base, por variante

Mediana de las 50 señas; entre paréntesis, mínimo y máximo. «Δ RMS» mide el parecido de toda la curva, no solo el pico.

| Variante | Δ pico (mm) | ≈px | Δ RMS (mm) | Cambia durante la seña (mm) | Target que más mueve | Misma cara en las 50 señas |
|---|---:|---:|---:|---:|---|---|
| `_alegria` | 7.28 (7.28–7.28) | 9.2 | 2.22 | 0.00 | `mouthSmile_L` (peso 0.75) | sí |
| `_tristeza` | 6.24 (6.24–6.24) | 7.9 | 1.49 | 0.00 | `browInnerUp` (peso 0.75) | sí |
| `_sorpresa` | 7.07 (7.07–7.07) | 8.9 | 2.35 | 0.00 | `browInnerUp` (peso 0.85) | sí |
| `_pregunta` | 8.31 (8.31–8.31) | 10.5 | 2.79 | 0.00 | `browInnerUp` (peso 1) | sí |
| `_negacion` | 3.66 (3.66–3.66) | 4.6 | 1.49 | 0.00 | `mouthShrugLower` (peso 0.45) | sí |

### Distinción entre variantes

Diferencia de las curvas completas. `Δ pico` es el target que más difiere; `Δ RMS` es el parecido de la cara entera. Regiones: dónde está la mayor diferencia.

| Par | Δ pico medio (mm) | Δ RMS medio (mm) | Δ pico mínimo (mm) | Región que más difiere |
|---|---:|---:|---:|---|
| `alegria` vs `_tristeza` | 7.28 | 1.90 | 7.28 | boca (7.28 mm) |
| `alegria` vs `_sorpresa` | 7.28 | 2.40 | 7.28 | boca (7.28 mm) |
| `alegria` vs `_pregunta` | 8.31 | 2.65 | 8.31 | cejas (8.31 mm) |
| `alegria` vs `_negacion` | 7.28 | 1.93 | 7.28 | boca (7.28 mm) |
| `tristeza` vs `_sorpresa` | 6.39 | 1.65 | 6.39 | cejas (6.39 mm) |
| `tristeza` vs `_pregunta` | 7.51 | 1.93 | 7.51 | cejas (7.51 mm) |
| `tristeza` vs `_negacion` | 5.40 | 1.09 | 5.40 | cejas (5.40 mm) |
| `sorpresa` vs `_pregunta` | 2.89 | 0.86 | 2.89 | boca (2.89 mm) |
| `sorpresa` vs `_negacion` | 6.39 | 2.07 | 6.39 | cejas (6.39 mm) |
| `pregunta` vs `_negacion` | 7.51 | 2.19 | 7.51 | cejas (7.51 mm) |

### Por seña: Δ pico contra el clip base (mm)

Cada celda es el máximo desplazamiento facial que agrega esa variante sobre el clip base de la misma seña. Si una columna sale idéntica en las 50 filas, no es un error de la tabla: la variante es la misma cara en todas las señas.

| Seña | `_alegria` | `_tristeza` | `_sorpresa` | `_pregunta` | `_negacion` |
|---|---:|---:|---:|---:|---:|
| 0000 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0001 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0002 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0003 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0004 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0005 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0006 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0007 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0008 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0009 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0010 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0011 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0012 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0013 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0014 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0015 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0016 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0017 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0018 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0019 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0020 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0021 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0022 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0023 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0024 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0025 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0026 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0027 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0028 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0029 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0030 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0031 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0032 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0033 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0034 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0035 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0036 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0037 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0038 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0039 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0040 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0041 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0042 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0043 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0044 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0045 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0046 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0047 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0048 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |
| 0049 | 7.28 | 6.24 | 7.07 | 8.31 | 3.66 |

### Huesos: base contra variantes

Ninguna diferencia: los 250 clips variantes tienen los mismos canales de hueso y los mismos valores que su base (tolerancia 0,01° / 0,01 mm). **Las variantes mueven la cara y nada más**: no agregan cabeza, tronco ni manos.


