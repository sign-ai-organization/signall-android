# Medición de los marcadores faciales (morph targets)

Generado por `tools/measure_face_markers.py` el 2026-11-05 sobre los GLB empaquetados de la app (`models/juan.glb` y `models/pauline.glb`), con el layout del 2026-11-04: **200 clips por avatar** (50 señas × `_afirmativo`, `_pregunta`, `_negacion`, `_orden`) y **sin clip base**.

Cómo se mide: cada target de malla trae el desplazamiento de sus vértices a peso 1 (mm). El clip dice cuánto pesa cada target en cada momento. `peso × alcance` es cuánto se mueve la cara de verdad. El **Δ contra el clip de referencia** (`_afirmativo`, la variante que la app usa por defecto) es lo que cada cara agrega sobre esa, comparando en tiempo normalizado. El parpadeo (`eyeBlink`) se excluye del Δ porque está en todas las variantes. `≈px` usa el encuadre por defecto (1 mm ≈ 1,26 px en un canvas de 2400 px): es una cuenta, no una captura.

## Juan

| Malla | Targets | Faciales | Alcance máx. (mm) |
|---|---:|---:|---:|
| Female_Body_Default.001 | 28 | 26 | 15.76 |
| Mesh.008 | 26 | 26 | 9.44 |

### Qué hace cada variante

Pesos sostenidos del clip (los mismos en las 50 señas; ver abajo). Solo targets con peso ≥ 0,10. Entre paréntesis, el desplazamiento en mm con ese peso.

| Variante | Targets que mueve (peso → mm) |
|---|---|
| `_afirmativo` | `mouthSmile_L` 0.55 → 5.34, `mouthSmile_R` 0.55 → 5.06, `cheekSquint_L` 0.25 → 1.85, `cheekSquint_R` 0.25 → 1.85, `mouthDimple_L` 0.1 → 0.73, `mouthDimple_R` 0.1 → 0.73 |
| `_pregunta` | `browInnerUp` 1.25 → 10.39, `browOuterUp_L` 1.25 → 9.39, `browOuterUp_R` 1.25 → 9.39, `mouthPress_L` 0.3 → 2.89, `mouthPress_R` 0.3 → 2.89, `mouthRollLower` 0.15 → 2.36, `eyeWide_L` 0.55 → 1.53, `eyeWide_R` 0.55 → 1.53 (+1 más) |
| `_negacion` | `mouthShrugLower` 0.45 → 3.66, `mouthPress_L` 0.35 → 3.37, `mouthPress_R` 0.35 → 3.37, `mouthFrown_R` 0.7 → 2.96, `browDown_L` 0.55 → 2.86, `browDown_R` 0.55 → 2.86, `mouthFrown_L` 0.7 → 2.62, `noseSneer_L` 0.15 → 0.94 (+4 más) |
| `_orden` | `browDown_L` 0.75 → 3.9, `browDown_R` 0.75 → 3.9, `mouthPress_L` 0.4 → 3.85, `mouthPress_R` 0.4 → 3.85, `eyeSquint_L` 0.3 → 0.88, `eyeSquint_R` 0.3 → 0.88, `mouthFrown_R` 0.18 → 0.76, `mouthFrown_L` 0.18 → 0.67 |

### Δ contra el clip de referencia, por variante

La referencia es `_afirmativo` (la cara que la app usa por defecto). Mediana de las 50 señas; entre paréntesis, mínimo y máximo. «Δ RMS» mide el parecido de toda la curva, no solo el pico. La referencia no se compara consigo misma.

| Variante | Δ pico (mm) | ≈px | Δ RMS (mm) | Cambia durante la seña (mm) | Target que más mueve | Misma cara en las 50 señas |
|---|---:|---:|---:|---:|---|---|
| `_pregunta` | 10.39 (10.39–10.39) | 13.1 | 3.96 | 0.00 | `browInnerUp` (peso 1.25) | sí |
| `_negacion` | 5.34 (5.34–5.34) | 6.7 | 2.35 | 0.00 | `mouthShrugLower` (peso 0.45) | sí |
| `_orden` | 5.34 (5.34–5.34) | 6.7 | 2.27 | 0.00 | `browDown_L` (peso 0.75) | sí |

`_afirmativo` (referencia) también es la misma cara en las 50 señas: sí.

### Distinción entre variantes

Diferencia de las curvas completas. `Δ pico` es el target que más difiere; `Δ RMS` es el parecido de la cara entera. Regiones: dónde está la mayor diferencia.

| Par | Δ pico medio (mm) | Δ RMS medio (mm) | Δ pico mínimo (mm) | Región que más difiere |
|---|---:|---:|---:|---|
| `afirmativo` vs `_pregunta` | 10.39 | 2.90 | 10.39 | cejas (10.39 mm) |
| `afirmativo` vs `_negacion` | 5.34 | 1.69 | 5.34 | boca (5.34 mm) |
| `afirmativo` vs `_orden` | 5.34 | 1.65 | 5.34 | boca (5.34 mm) |
| `pregunta` vs `_negacion` | 9.56 | 2.73 | 9.56 | cejas (9.56 mm) |
| `pregunta` vs `_orden` | 10.39 | 2.75 | 10.39 | cejas (10.39 mm) |
| `negacion` vs `_orden` | 3.66 | 0.76 | 3.66 | boca (3.66 mm) |

### Por seña: Δ pico contra la referencia (mm)

Cada celda es el máximo desplazamiento facial que agrega esa variante sobre `_afirmativo` en la misma seña. Si una columna sale idéntica en las 50 filas, no es un error de la tabla: la variante es la misma cara en todas las señas.

| Seña | `_pregunta` | `_negacion` | `_orden` |
|---|---:|---:|---:|
| 0000 | 10.39 | 5.34 | 5.34 |
| 0001 | 10.39 | 5.34 | 5.34 |
| 0002 | 10.39 | 5.34 | 5.34 |
| 0003 | 10.39 | 5.34 | 5.34 |
| 0004 | 10.39 | 5.34 | 5.34 |
| 0005 | 10.39 | 5.34 | 5.34 |
| 0006 | 10.39 | 5.34 | 5.34 |
| 0007 | 10.39 | 5.34 | 5.34 |
| 0008 | 10.39 | 5.34 | 5.34 |
| 0009 | 10.39 | 5.34 | 5.34 |
| 0010 | 10.39 | 5.34 | 5.34 |
| 0011 | 10.39 | 5.34 | 5.34 |
| 0012 | 10.39 | 5.34 | 5.34 |
| 0013 | 10.39 | 5.34 | 5.34 |
| 0014 | 10.39 | 5.34 | 5.34 |
| 0015 | 10.39 | 5.34 | 5.34 |
| 0016 | 10.39 | 5.34 | 5.34 |
| 0017 | 10.39 | 5.34 | 5.34 |
| 0018 | 10.39 | 5.34 | 5.34 |
| 0019 | 10.39 | 5.34 | 5.34 |
| 0020 | 10.39 | 5.34 | 5.34 |
| 0021 | 10.39 | 5.34 | 5.34 |
| 0022 | 10.39 | 5.34 | 5.34 |
| 0023 | 10.39 | 5.34 | 5.34 |
| 0024 | 10.39 | 5.34 | 5.34 |
| 0025 | 10.39 | 5.34 | 5.34 |
| 0026 | 10.39 | 5.34 | 5.34 |
| 0027 | 10.39 | 5.34 | 5.34 |
| 0028 | 10.39 | 5.34 | 5.34 |
| 0029 | 10.39 | 5.34 | 5.34 |
| 0030 | 10.39 | 5.34 | 5.34 |
| 0031 | 10.39 | 5.34 | 5.34 |
| 0032 | 10.39 | 5.34 | 5.34 |
| 0033 | 10.39 | 5.34 | 5.34 |
| 0034 | 10.39 | 5.34 | 5.34 |
| 0035 | 10.39 | 5.34 | 5.34 |
| 0036 | 10.39 | 5.34 | 5.34 |
| 0037 | 10.39 | 5.34 | 5.34 |
| 0038 | 10.39 | 5.34 | 5.34 |
| 0039 | 10.39 | 5.34 | 5.34 |
| 0040 | 10.39 | 5.34 | 5.34 |
| 0041 | 10.39 | 5.34 | 5.34 |
| 0042 | 10.39 | 5.34 | 5.34 |
| 0043 | 10.39 | 5.34 | 5.34 |
| 0044 | 10.39 | 5.34 | 5.34 |
| 0045 | 10.39 | 5.34 | 5.34 |
| 0046 | 10.39 | 5.34 | 5.34 |
| 0047 | 10.39 | 5.34 | 5.34 |
| 0048 | 10.39 | 5.34 | 5.34 |
| 0049 | 10.39 | 5.34 | 5.34 |

### Huesos: referencia contra las otras caras

Ninguna diferencia: los 150 clips variantes (50 señas × 3 caras) tienen los mismos canales de hueso y los mismos valores que la referencia de su seña (tolerancia 0,01° / 0,01 mm). **Las variantes mueven la cara y nada más**: no agregan cabeza, tronco ni manos.


## Pauline

| Malla | Targets | Faciales | Alcance máx. (mm) |
|---|---:|---:|---:|
| Mesh.017 | 26 | 26 | 7.90 |
| Female_Body_Default.005 | 28 | 26 | 15.76 |

### Qué hace cada variante

Pesos sostenidos del clip (los mismos en las 50 señas; ver abajo). Solo targets con peso ≥ 0,10. Entre paréntesis, el desplazamiento en mm con ese peso.

| Variante | Targets que mueve (peso → mm) |
|---|---|
| `_afirmativo` | `mouthSmile_L` 0.55 → 5.34, `mouthSmile_R` 0.55 → 5.06, `cheekSquint_L` 0.25 → 1.85, `cheekSquint_R` 0.25 → 1.85, `mouthDimple_L` 0.1 → 0.73, `mouthDimple_R` 0.1 → 0.73 |
| `_pregunta` | `browInnerUp` 1 → 8.31, `browOuterUp_R` 1 → 7.51, `browOuterUp_L` 1 → 7.51, `mouthPress_L` 0.3 → 2.89, `mouthPress_R` 0.3 → 2.89, `mouthRollLower` 0.15 → 2.36, `eyeWide_L` 0.55 → 1.53, `eyeWide_R` 0.55 → 1.53 (+1 más) |
| `_negacion` | `mouthShrugLower` 0.45 → 3.66, `mouthPress_L` 0.35 → 3.37, `mouthPress_R` 0.35 → 3.37, `mouthFrown_R` 0.7 → 2.96, `browDown_L` 0.55 → 2.86, `browDown_R` 0.55 → 2.86, `mouthFrown_L` 0.7 → 2.62, `noseSneer_L` 0.15 → 0.94 (+4 más) |
| `_orden` | `browDown_L` 0.75 → 3.9, `browDown_R` 0.75 → 3.9, `mouthPress_L` 0.4 → 3.85, `mouthPress_R` 0.4 → 3.85, `eyeSquint_L` 0.3 → 0.88, `eyeSquint_R` 0.3 → 0.88, `mouthFrown_R` 0.18 → 0.76, `mouthFrown_L` 0.18 → 0.67 |

### Δ contra el clip de referencia, por variante

La referencia es `_afirmativo` (la cara que la app usa por defecto). Mediana de las 50 señas; entre paréntesis, mínimo y máximo. «Δ RMS» mide el parecido de toda la curva, no solo el pico. La referencia no se compara consigo misma.

| Variante | Δ pico (mm) | ≈px | Δ RMS (mm) | Cambia durante la seña (mm) | Target que más mueve | Misma cara en las 50 señas |
|---|---:|---:|---:|---:|---|---|
| `_pregunta` | 8.31 (8.31–8.31) | 10.5 | 3.37 | 0.00 | `browInnerUp` (peso 1) | sí |
| `_negacion` | 5.34 (5.34–5.34) | 6.7 | 2.35 | 0.00 | `mouthShrugLower` (peso 0.45) | sí |
| `_orden` | 5.34 (5.34–5.34) | 6.7 | 2.27 | 0.00 | `browDown_L` (peso 0.75) | sí |

`_afirmativo` (referencia) también es la misma cara en las 50 señas: sí.

### Distinción entre variantes

Diferencia de las curvas completas. `Δ pico` es el target que más difiere; `Δ RMS` es el parecido de la cara entera. Regiones: dónde está la mayor diferencia.

| Par | Δ pico medio (mm) | Δ RMS medio (mm) | Δ pico mínimo (mm) | Región que más difiere |
|---|---:|---:|---:|---|
| `afirmativo` vs `_pregunta` | 8.31 | 2.53 | 8.31 | cejas (8.31 mm) |
| `afirmativo` vs `_negacion` | 5.34 | 1.73 | 5.34 | boca (5.34 mm) |
| `afirmativo` vs `_orden` | 5.34 | 1.73 | 5.34 | boca (5.34 mm) |
| `pregunta` vs `_negacion` | 7.51 | 2.39 | 7.51 | cejas (7.51 mm) |
| `pregunta` vs `_orden` | 8.31 | 2.43 | 8.31 | cejas (8.31 mm) |
| `negacion` vs `_orden` | 3.66 | 0.77 | 3.66 | boca (3.66 mm) |

### Por seña: Δ pico contra la referencia (mm)

Cada celda es el máximo desplazamiento facial que agrega esa variante sobre `_afirmativo` en la misma seña. Si una columna sale idéntica en las 50 filas, no es un error de la tabla: la variante es la misma cara en todas las señas.

| Seña | `_pregunta` | `_negacion` | `_orden` |
|---|---:|---:|---:|
| 0000 | 8.31 | 5.34 | 5.34 |
| 0001 | 8.31 | 5.34 | 5.34 |
| 0002 | 8.31 | 5.34 | 5.34 |
| 0003 | 8.31 | 5.34 | 5.34 |
| 0004 | 8.31 | 5.34 | 5.34 |
| 0005 | 8.31 | 5.34 | 5.34 |
| 0006 | 8.31 | 5.34 | 5.34 |
| 0007 | 8.31 | 5.34 | 5.34 |
| 0008 | 8.31 | 5.34 | 5.34 |
| 0009 | 8.31 | 5.34 | 5.34 |
| 0010 | 8.31 | 5.34 | 5.34 |
| 0011 | 8.31 | 5.34 | 5.34 |
| 0012 | 8.31 | 5.34 | 5.34 |
| 0013 | 8.31 | 5.34 | 5.34 |
| 0014 | 8.31 | 5.34 | 5.34 |
| 0015 | 8.31 | 5.34 | 5.34 |
| 0016 | 8.31 | 5.34 | 5.34 |
| 0017 | 8.31 | 5.34 | 5.34 |
| 0018 | 8.31 | 5.34 | 5.34 |
| 0019 | 8.31 | 5.34 | 5.34 |
| 0020 | 8.31 | 5.34 | 5.34 |
| 0021 | 8.31 | 5.34 | 5.34 |
| 0022 | 8.31 | 5.34 | 5.34 |
| 0023 | 8.31 | 5.34 | 5.34 |
| 0024 | 8.31 | 5.34 | 5.34 |
| 0025 | 8.31 | 5.34 | 5.34 |
| 0026 | 8.31 | 5.34 | 5.34 |
| 0027 | 8.31 | 5.34 | 5.34 |
| 0028 | 8.31 | 5.34 | 5.34 |
| 0029 | 8.31 | 5.34 | 5.34 |
| 0030 | 8.31 | 5.34 | 5.34 |
| 0031 | 8.31 | 5.34 | 5.34 |
| 0032 | 8.31 | 5.34 | 5.34 |
| 0033 | 8.31 | 5.34 | 5.34 |
| 0034 | 8.31 | 5.34 | 5.34 |
| 0035 | 8.31 | 5.34 | 5.34 |
| 0036 | 8.31 | 5.34 | 5.34 |
| 0037 | 8.31 | 5.34 | 5.34 |
| 0038 | 8.31 | 5.34 | 5.34 |
| 0039 | 8.31 | 5.34 | 5.34 |
| 0040 | 8.31 | 5.34 | 5.34 |
| 0041 | 8.31 | 5.34 | 5.34 |
| 0042 | 8.31 | 5.34 | 5.34 |
| 0043 | 8.31 | 5.34 | 5.34 |
| 0044 | 8.31 | 5.34 | 5.34 |
| 0045 | 8.31 | 5.34 | 5.34 |
| 0046 | 8.31 | 5.34 | 5.34 |
| 0047 | 8.31 | 5.34 | 5.34 |
| 0048 | 8.31 | 5.34 | 5.34 |
| 0049 | 8.31 | 5.34 | 5.34 |

### Huesos: referencia contra las otras caras

Ninguna diferencia: los 150 clips variantes (50 señas × 3 caras) tienen los mismos canales de hueso y los mismos valores que la referencia de su seña (tolerancia 0,01° / 0,01 mm). **Las variantes mueven la cara y nada más**: no agregan cabeza, tronco ni manos.


